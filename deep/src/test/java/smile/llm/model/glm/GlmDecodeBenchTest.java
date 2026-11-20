/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.glm;

import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;

/** Times {@link Glm#decodeStep} of the 4-layer slice at several batch sizes (no assertions). Opt-in: GLM_CUDA=true GLM_BENCH=1. */
public class GlmDecodeBenchTest {
    @Test
    public void testBenchmark() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")) && System.getenv("GLM_BENCH") != null, "set GLM_BENCH=1");
        Path dir = Path.of(System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw"));
        int[] prompt = new int[256];
        for (int i = 0; i < prompt.length; i++) {
            prompt[i] = 1000 + (i * 37) % 5000;
        }
        int ranks = Integer.parseInt(System.getenv().getOrDefault("GLM_EP", "1"));
        byte[] devices = new byte[ranks];
        for (int i = 0; i < ranks; i++) {
            devices[i] = (byte) i;
        }
        try (Glm model = Glm.build(dir.toString(), 4, 8192, devices, 32)) {
            int[] ids = new int[32];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = model.bind(prompt, 512);
                model.prefill(ids[i], prompt, 0).close();
            }
            for (int b : new int[]{1, 8, 32}) {
                int[] rq = java.util.Arrays.copyOf(ids, b);
                int[] last = new int[b];
                java.util.Arrays.fill(last, 1234);
                int[] pos = new int[b];
                java.util.Arrays.fill(pos, 0);
                for (int step = 0; step < 3 + 10; step++) {
                    if (step == 3) {
                        // warm-up done
                        System.gc();
                    }
                    long t0 = System.nanoTime();
                    // positions: all requests are at the same length b/c each step advances every request
                    int[] p = new int[b];
                    for (int i = 0; i < b; i++) {
                        p[i] = prompt.length + stepsDone(model, rq[i], prompt.length);
                    }
                    Tensor logits = model.decodeStep(rq, last, p);
                    logits.close();
                    if (step >= 3) {
                        System.out.printf("batch %2d step %2d: %.1f ms%n", b, step, (System.nanoTime() - t0) / 1e6);
                    }
                }
            }
        }
    }

    private static int stepsDone(Glm model, int id, int promptLen) {
        return model.positionOf(id) - promptLen;
    }
}
