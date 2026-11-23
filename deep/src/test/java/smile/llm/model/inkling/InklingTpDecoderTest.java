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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fused decode step on every rank of a sharded model against the reference step from identical states: the next-token
 * distributions of the same requests must agree up to the noise two reference runs have among themselves.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_TP} (devices), {@code INKLING_PROMPTS} (json with
 * {@code results[].prompt_ids}), {@code INKLING_LAYERS}, {@code INKLING_DEC_BATCH} (default 4),
 * {@code INKLING_DEC_STEPS} (default 6).
 *
 * @author Haifeng Li
 */
public class InklingTpDecoderTest {
    private static double tv(float[] a, float[] b) {
        double ma = Double.NEGATIVE_INFINITY;
        double mb = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            ma = Math.max(ma, a[i]);
            mb = Math.max(mb, b[i]);
        }
        double sa = 0;
        double sb = 0;
        for (int i = 0; i < a.length; i++) {
            sa += Math.exp(a[i] - ma);
            sb += Math.exp(b[i] - mb);
        }
        double tv = 0;
        for (int i = 0; i < a.length; i++) {
            tv += Math.abs(Math.exp(a[i] - ma) / sa - Math.exp(b[i] - mb) / sb);
        }
        return tv / 2;
    }

    private static int argmax(float[] x) {
        int best = 0;
        for (int i = 1; i < x.length; i++) {
            if (x[i] > x[best]) {
                best = i;
            }
        }
        return best;
    }

    private static void sync(InklingRanks ranks, byte[] devices, String label) {
        try {
            ranks.onRanks(r -> Tensor.of(new float[]{1f}, 1).to(Device.CUDA(devices[r])).to(Device.CPU()).floatArray()[0]);
            System.out.println("sync ok: " + label);
        } catch (RuntimeException e) {
            throw new IllegalStateException("device error after " + label + ": " + e, e);
        }
    }

    @Test
    public void testGivenSameStatesWhenSteppedThenFusedShardedDecodeMatchesReferenceUpToNoise() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String tp = System.getenv("INKLING_TP");
        String prompts = System.getenv("INKLING_PROMPTS");
        Assumptions.assumeTrue(ckpt != null && tp != null && prompts != null && InklingDecoder.available(),
                "set INKLING_CKPT, INKLING_TP, INKLING_PROMPTS and SMILE_INKLING_LIB");
        int batch = Integer.parseInt(System.getenv().getOrDefault("INKLING_DEC_BATCH", "4"));
        int steps = Integer.parseInt(System.getenv().getOrDefault("INKLING_DEC_STEPS", "6"));
        InklingModelArgs args = InklingModelArgs.fromCheckpoint(ckpt, 4096);
        if (System.getenv("INKLING_LAYERS") != null) {
            args = args.withLayers(Integer.parseInt(System.getenv("INKLING_LAYERS")), 1);
        }
        String[] ids = tp.split(",");
        byte[] devices = new byte[ids.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(ids[i].trim());
        }
        int n = devices.length;
        final InklingModelArgs fargs = args;
        InklingRanks ranks = InklingRanks.load(ckpt, args, devices);
        InklingDecoder[] decoders = ranks.onRanks(r -> new InklingDecoder(ranks.model(r))).toArray(new InklingDecoder[0]);
        JsonNode results = new ObjectMapper().readTree(new File(prompts)).get("results");
        int apps = args.numApplications();
        int vocab = args.unpaddedVocabSize();
        InklingState[][] ref = new InklingState[batch][];
        InklingState[][] ref2 = new InklingState[batch][];
        InklingState[][] fast = new InklingState[batch][];
        long[] last = new long[batch];
        int[] positions = new int[batch];
        for (int i = 0; i < batch; i++) {
            JsonNode p = results.get(i % results.size()).get("prompt_ids");
            int want = Integer.parseInt(System.getenv().getOrDefault("INKLING_DEC_PROMPT_LEN", "0"));
            long[] prompt = new long[want > 0 ? want : p.size()];
            for (int j = 0; j < prompt.length; j++) {
                prompt[j] = p.get(j % p.size()).asLong();       // a long prompt tiles the dialog's tokens
            }
            int len = prompt.length - 1;           // the last prompt token is the first decode input
            ref[i] = ranks.newStates(len + steps + 16);
            sync(ranks, devices, "newStates " + i);
            for (int from = 0; from < len; from += ref[i][0].maxChunk(args)) {
                int to = Math.min(len, from + ref[i][0].maxChunk(args));
                ranks.forward(ref[i], Arrays.copyOfRange(prompt, from, to), new int[0], null);
            }
            sync(ranks, devices, "prefill " + i);
            last[i] = prompt[len];
            positions[i] = len;
            ref2[i] = ranks.newStates(len + steps + 16);
            fast[i] = ranks.newStates(len + steps + 16);
        }
        // snapshot/restore needs the snapshot of the same rank: do it per request on the rank threads
        for (int i = 0; i < batch; i++) {
            InklingState[] a = ref[i];
            InklingState[] b = ref2[i];
            InklingState[] c = fast[i];
            ranks.onRanks(r -> {
                InklingState.Snapshot snap = a[r].snapshot(fargs);
                b[r].restore(snap);
                c[r].restore(snap);
                snap.close();
                return null;
            });
            sync(ranks, devices, "snapshot/restore " + i);
        }
        List<Double> tvFast = new ArrayList<>();
        List<Double> tvBase = new ArrayList<>();
        for (int step = 0; step < steps; step++) {
            float[][] refLogits = new float[batch][];
            float[][] ref2Logits = new float[batch][];
            for (int i = 0; i < batch; i++) {
                refLogits[i] = ranks.forward(ref[i], new long[]{last[i]}, null, null).to(Device.CPU()).floatArray();
                ref2Logits[i] = ranks.forward(ref2[i], new long[]{last[i]}, null, null).to(Device.CPU()).floatArray();
            }
            sync(ranks, devices, "reference step " + step);
            long[] lastNow = last.clone();
            int[] posNow = positions.clone();
            List<Tensor> out = ranks.onRanks(r -> {
                Device dev = Device.CUDA(devices[r]);
                long[] table = new long[apps * batch * 8];
                for (int i = 0; i < batch; i++) {
                    long[] t = fast[i][r].table();
                    for (int a = 0; a < apps; a++) {
                        System.arraycopy(t, a * 8, table, (a * batch + i) * 8, 8);
                    }
                }
                Tensor tbl = Tensor.of(table, table.length).to(dev);
                Tensor tokens = Tensor.of(lastNow, batch).to(dev);
                Tensor pos = Tensor.of(posNow, batch).to(dev);
                Tensor logits = decoders[r].forward(batch, tokens, pos, tbl, null);
                logits.promoteToParent();
                return logits;
            });
            float[] all = out.get(0).to(Device.CPU()).floatArray();
            for (int r = 1; r < n; r++) {
                float[] other = out.get(r).to(Device.CPU()).floatArray();
                assertArrayEquals(all, other, 1e-3f, "ranks disagree on the logits");
            }
            for (int i = 0; i < batch; i++) {
                float[] f = Arrays.copyOfRange(all, i * vocab, (i + 1) * vocab);
                tvFast.add(tv(f, refLogits[i]));
                tvBase.add(tv(ref2Logits[i], refLogits[i]));
                last[i] = argmax(refLogits[i]);
                positions[i]++;
            }
        }
        Collections.sort(tvFast);
        Collections.sort(tvBase);
        System.out.printf("next-token total variation, fused vs reference: median %.4f, max %.4f; reference vs reference: median %.4f, max %.4f (%d rows)%n",
                tvFast.get(tvFast.size() / 2), tvFast.get(tvFast.size() - 1), tvBase.get(tvBase.size() / 2), tvBase.get(tvBase.size() - 1), tvFast.size());
        assertTrue(tvFast.get(tvFast.size() / 2) < 0.05, "fused sharded decode diverges from the reference");
    }
}
