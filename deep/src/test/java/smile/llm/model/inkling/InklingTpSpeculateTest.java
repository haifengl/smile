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
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Speculative decoding of {@link InklingTp} through the executor API: greedy rounds of {@code speculateStep} against plain
 * greedy decoding of the same prompts, reporting the tokens per round, the first divergence and the speed of both.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_TP}, {@code INKLING_PROMPTS} (json with {@code results[].prompt_ids}),
 * {@code INKLING_LAYERS}, {@code INKLING_BATCHES} (default {@code 1,2,4}), {@code INKLING_STEPS} (tokens per request,
 * default 96).
 *
 * @author Haifeng Li
 */
public class InklingTpSpeculateTest {
    private static int argmax(float[] x) {
        int best = 0;
        for (int i = 1; i < x.length; i++) {
            if (x[i] > x[best]) {
                best = i;
            }
        }
        return best;
    }

    @Test
    public void testGivenPromptsWhenSpeculatedThenTokensPerRoundAndSpeedAreReported() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String tp = System.getenv("INKLING_TP");
        String prompts = System.getenv("INKLING_PROMPTS");
        Assumptions.assumeTrue(ckpt != null && tp != null && prompts != null, "set INKLING_CKPT, INKLING_TP, INKLING_PROMPTS");
        int steps = Integer.parseInt(System.getenv().getOrDefault("INKLING_STEPS", "96"));
        String[] ids = tp.split(",");
        byte[] devices = new byte[ids.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(ids[i].trim());
        }
        int layers = Integer.parseInt(System.getenv().getOrDefault("INKLING_LAYERS", "0"));
        InklingTp model = InklingTp.build(ckpt, layers, 4096, devices);
        assertTrue(model.hasDraft(), "the model has no usable draft");
        JsonNode results = new ObjectMapper().readTree(new File(prompts)).get("results");
        int nd = model.draftBlock();
        try {
            for (String b : System.getenv().getOrDefault("INKLING_BATCHES", "1,2,4").split(",")) {
                int n = Integer.parseInt(b.trim());
                int[][] prompt = new int[n][];
                for (int i = 0; i < n; i++) {
                    JsonNode p = results.get(i % results.size()).get("prompt_ids");
                    prompt[i] = new int[p.size()];
                    for (int j = 0; j < prompt[i].length; j++) {
                        prompt[i][j] = p.get(j).asInt();
                    }
                }
                // plain greedy
                List<List<Integer>> plain = new ArrayList<>();
                int[] req = new int[n];
                int[] last = new int[n];
                int[] pos = new int[n];
                for (int i = 0; i < n; i++) {
                    plain.add(new ArrayList<>());
                    req[i] = model.bind(prompt[i], prompt[i].length + 2 * steps + 64);
                    last[i] = argmax(model.prefill(req[i], prompt[i], 0).to(Device.CPU()).floatArray());
                    pos[i] = prompt[i].length;
                }
                int vocab = model.args().unpaddedVocabSize();
                long t0 = System.nanoTime();
                for (int s = 0; s < steps; s++) {
                    for (int i = 0; i < n; i++) {
                        plain.get(i).add(last[i]);
                    }
                    Tensor logits = model.decodeStep(req, last, pos);
                    float[] all = logits.to(Device.CPU()).floatArray();
                    for (int i = 0; i < n; i++) {
                        last[i] = argmax(Arrays.copyOfRange(all, i * vocab, (i + 1) * vocab));
                        pos[i]++;
                    }
                    logits.close();
                }
                double plainSecs = (System.nanoTime() - t0) / 1e9;
                for (int r : req) {
                    model.evict(r);
                }
                // speculative greedy
                List<List<Integer>> spec = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    spec.add(new ArrayList<>());
                    req[i] = model.bind(prompt[i], prompt[i].length + 2 * steps + 64);
                    last[i] = argmax(model.prefill(req[i], prompt[i], 0).to(Device.CPU()).floatArray());
                    pos[i] = prompt[i].length;
                }
                int rounds = 0;
                long tokens = 0;
                long t1 = System.nanoTime();
                int warm = 2;                      // the first rounds capture/prime; time from the third on
                long t2 = t1;
                long tok2 = 0;
                while (spec.stream().anyMatch(l -> l.size() < steps)) {
                    int[][] out = model.speculateStep(req, last, pos, nd, 0.0, 1.0);
                    for (int i = 0; i < n; i++) {
                        spec.get(i).add(last[i]);
                        for (int k = 0; k < out[i].length - 1; k++) {
                            spec.get(i).add(out[i][k]);
                        }
                        last[i] = out[i][out[i].length - 1];
                        pos[i] += out[i].length;
                        tokens += out[i].length;
                    }
                    rounds++;
                    if (rounds == warm) {
                        t2 = System.nanoTime();
                        tok2 = tokens;
                    }
                }
                double specSecs = (System.nanoTime() - t2) / 1e9;
                double perRound = (double) tokens / rounds / n;
                int firstDiff = -1;
                for (int i = 0; i < n && firstDiff < 0; i++) {
                    for (int k = 0; k < Math.min(steps, spec.get(i).size()); k++) {
                        if (!plain.get(i).get(k).equals(spec.get(i).get(k))) {
                            firstDiff = k;
                            break;
                        }
                    }
                }
                System.out.printf("batch %d: plain %.1f tok/s aggregate; speculative %.2f tokens per request per round, %.1f tok/s aggregate"
                                + " (%.1f ms per round), first divergence from plain greedy at token %d%n",
                        n, n * steps / plainSecs, perRound, (tokens - tok2) / specSecs, 1000 * specSecs / Math.max(1, rounds - warm),
                        firstDiff);
                for (int r : req) {
                    model.evict(r);
                }
            }
        } finally {
            model.close();
        }
    }
}
