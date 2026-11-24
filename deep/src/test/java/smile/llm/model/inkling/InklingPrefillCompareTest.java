/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fused prefill against the reference prefill of the same sharded model on the same tokens: layer outputs and the stage taps
 * (layer input, attention output, MoE output) are compared, and the state left behind is checked by decoding a few tokens after
 * each path and comparing the logits.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_TP}, {@code INKLING_PROMPTS}, {@code INKLING_LAYERS} (default 6),
 * {@code INKLING_PROMPT_LEN} (default 185).
 *
 * @author Haifeng Li
 */
public class InklingPrefillCompareTest {
    private static double relRms(float[] a, float[] b) {
        double num = 0, den = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            num += d * d;
            den += (double) b[i] * b[i];
        }
        return Math.sqrt(num / Math.max(den, 1e-30));
    }

    private static float[] host(Tensor t) {
        return t.to(Device.CPU()).to(ScalarType.Float).floatArray();
    }

    private static double tv(float[] a, float[] b) {
        double ma = Double.NEGATIVE_INFINITY, mb = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            ma = Math.max(ma, a[i]);
            mb = Math.max(mb, b[i]);
        }
        double sa = 0, sb = 0;
        for (int i = 0; i < a.length; i++) {
            sa += Math.exp(a[i] - ma);
            sb += Math.exp(b[i] - mb);
        }
        double d = 0;
        for (int i = 0; i < a.length; i++) {
            d += Math.abs(Math.exp(a[i] - ma) / sa - Math.exp(b[i] - mb) / sb);
        }
        return d / 2;
    }

    @Test
    public void testGivenSamePromptWhenPrefilledByBothPathsThenStagesAgree() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String tp = System.getenv("INKLING_TP");
        String prompts = System.getenv("INKLING_PROMPTS");
        Assumptions.assumeTrue(ckpt != null && tp != null && prompts != null && InklingKernels.ENABLED, "set INKLING_CKPT, INKLING_TP, INKLING_PROMPTS");
        int layers = Integer.parseInt(System.getenv().getOrDefault("INKLING_LAYERS", "6"));
        int len = Integer.parseInt(System.getenv().getOrDefault("INKLING_PROMPT_LEN", "185"));
        String[] ids = tp.split(",");
        byte[] devices = new byte[ids.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(ids[i].trim());
        }
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096).withLayers(layers, 1);
        InklingRanks ranks = InklingRanks.load(ckpt, args, devices);
        JsonNode p = new ObjectMapper().readTree(new File(prompts)).get("results").get(5).get("prompt_ids");
        long[] prompt = new long[len];
        for (int i = 0; i < len; i++) {
            prompt[i] = p.get(i % p.size()).asLong();
        }
        long[] more = {prompt[3], prompt[5], prompt[7], prompt[11]};
        @SuppressWarnings("unchecked")
        Map<String, float[]>[] taps = new Map[2];
        float[][][] trace = new float[2][][];
        float[][] after = new float[2][];
        try {
            for (int mode = 0; mode < 2; mode++) {
                InklingKernels.prefill = mode == 1;
                Map<String, float[]> got = new ConcurrentHashMap<>();
                taps[mode] = got;
                java.util.List<float[]> tr = new java.util.ArrayList<>();
                InklingState[] st = ranks.newStates(len + 64);
                // a long prompt goes in two chunks; the stages of the second one (long global cache) are compared
                int head = len > 4096 ? 4096 : 0;
                InklingDebug.hook = null;
                if (head > 0) {
                    ranks.forward(st, Arrays.copyOfRange(prompt, 0, head), new int[0], null);
                }
                InklingDebug.hook = (name, t) -> {
                    if (Thread.currentThread().getName().endsWith("rank-0") && (name.startsWith("qin") || name.startsWith("attn")
                            || name.startsWith("moe"))) {
                        got.put(name, host(t));
                    }
                };
                Tensor logits = ranks.forward(st, Arrays.copyOfRange(prompt, head, len), null, t -> tr.add(host(t)));
                float[] first = host(logits);
                InklingDebug.hook = null;
                trace[mode] = tr.toArray(new float[0][]);
                // the state the path leaves behind: decode four more tokens one by one (reference path) and keep the last logits
                float[] last = first;
                for (long tok : more) {
                    InklingKernels.prefill = false;
                    last = host(ranks.forward(st, new long[]{tok}, null, null));
                }
                after[mode] = last;
                for (InklingState s : st) {
                    s.close();
                }
            }
        } finally {
            InklingKernels.prefill = InklingKernels.ENABLED;
            InklingDebug.hook = null;
        }
        Map<String, Double> stage = new TreeMap<>();
        for (String k : taps[0].keySet()) {
            if (taps[1].containsKey(k) && taps[0].get(k).length == taps[1].get(k).length) {
                stage.put(k, relRms(taps[1].get(k), taps[0].get(k)));
            }
        }
        stage.forEach((k, v) -> System.out.printf("  stage %-8s fused vs reference rel rms %.5f%n", k, v));
        for (int a = 0; a < trace[0].length; a++) {
            System.out.printf("  app %d output: fused vs reference rel rms %.5f%n", a, relRms(trace[1][a], trace[0][a]));
        }
        System.out.printf("logits after prefill + 4 decoded tokens: TV %.5f%n", tv(after[1], after[0]));
        assertTrue(tv(after[1], after[0]) < 0.1);
    }
}
