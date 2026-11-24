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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decode throughput of {@link InklingTp} through the executor API: requests are bound and prefilled, then decoded in
 * lockstep with greedy tokens, for several batch sizes.
 *
 * <p>Opt-in, GPU: {@code INKLING_CKPT}, {@code INKLING_TP} (devices), {@code INKLING_PROMPTS} (json with
 * {@code results[].prompt_ids}), {@code INKLING_LAYERS}, {@code INKLING_BATCHES} (default {@code 1,2,4,8}),
 * {@code INKLING_STEPS} (default 32).
 *
 * @author Haifeng Li
 */
public class InklingTpBenchTest {
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
    public void testGivenRequestsWhenDecodedThenReportTokensPerSecond() throws Exception {
        String ckpt = System.getenv("INKLING_CKPT");
        String tp = System.getenv("INKLING_TP");
        String prompts = System.getenv("INKLING_PROMPTS");
        Assumptions.assumeTrue(ckpt != null && tp != null && prompts != null, "set INKLING_CKPT, INKLING_TP, INKLING_PROMPTS");
        int steps = Integer.parseInt(System.getenv().getOrDefault("INKLING_STEPS", "32"));
        String[] ids = tp.split(",");
        byte[] devices = new byte[ids.length];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = Byte.parseByte(ids[i].trim());
        }
        int layers = Integer.parseInt(System.getenv().getOrDefault("INKLING_LAYERS", "0"));
        long t0 = System.nanoTime();
        InklingTp model = InklingTp.build(ckpt, layers, 4096, devices);
        System.out.printf("loaded in %.1f s%n", (System.nanoTime() - t0) / 1e9);
        JsonNode results = new ObjectMapper().readTree(new File(prompts)).get("results");
        try {
            for (String b : System.getenv().getOrDefault("INKLING_BATCHES", "1,2,4,8").split(",")) {
                int n = Integer.parseInt(b.trim());
                int[] req = new int[n];
                int[] last = new int[n];
                int[] pos = new int[n];
                for (int i = 0; i < n; i++) {
                    JsonNode p = results.get(i % results.size()).get("prompt_ids");
                    int[] prompt = new int[p.size()];
                    for (int j = 0; j < prompt.length; j++) {
                        prompt[j] = p.get(j).asInt();
                    }
                    req[i] = model.bind(prompt, prompt.length + steps + 8);
                    Tensor logits = model.prefill(req[i], prompt, 0);
                    last[i] = argmax(logits.to(Device.CPU()).floatArray());
                    pos[i] = prompt.length;
                }
                // warm-up step, then timed steps
                int vocab = model.args().unpaddedVocabSize();
                double sum = 0;
                long start = 0;
                for (int s = 0; s <= steps; s++) {
                    if (s == 1) {
                        start = System.nanoTime();
                    }
                    Tensor logits = model.decodeStep(req, last, pos);
                    float[] all = logits.to(Device.CPU()).floatArray();
                    assertEquals((long) n * vocab, all.length);
                    for (int i = 0; i < n; i++) {
                        last[i] = argmax(Arrays.copyOfRange(all, i * vocab, (i + 1) * vocab));
                        pos[i]++;
                    }
                    logits.close();
                }
                double secs = (System.nanoTime() - start) / 1e9;
                System.out.printf("batch %d: %d steps in %.2f s = %.1f ms/step, %.1f tok/s aggregate, %.1f tok/s per request%n",
                        n, steps, secs, 1000 * secs / steps, n * steps / secs, steps / secs);
                for (int r : req) {
                    model.evict(r);
                }
            }
        } finally {
            model.close();
        }
    }
}
