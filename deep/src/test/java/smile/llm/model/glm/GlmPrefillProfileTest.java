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

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

/**
 * Prefill time per chunk of one long prompt, and the device-0 kernels of the last chunks (CUPTI) for attributing it.
 * Opt-in: {@code GLM_FULL=<checkpoint dir>}, {@code GLM_PREFILL_TOKENS} (default 32768), {@code GLM_PREFILL_CHUNK}
 * (default 2048), {@code GLM_PROF_KERNELS=1} to print the kernel table of the last {@code GLM_PROF_CHUNKS} chunks (2),
 * {@code GLM_PROF_LAYERS}, {@code GLM_FULL_DEVICES}.
 */
public class GlmPrefillProfileTest {
    private static int env(String name, int def) {
        return Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(def)));
    }

    @Test
    public void testPrintsPrefillChunkTimes() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int layers = env("GLM_PROF_LAYERS", 0);
        int devices = env("GLM_FULL_DEVICES", 8);
        int total = env("GLM_PREFILL_TOKENS", 32768);
        int chunk = env("GLM_PREFILL_CHUNK", 2048);
        int profChunks = env("GLM_PROF_CHUNKS", 2);
        boolean kernels = env("GLM_PROF_KERNELS", 0) > 0;
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        int[] prompt = new int[total];
        long seed = 12345;
        for (int i = 0; i < total; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            prompt[i] = 1000 + (int) ((seed >>> 33) % 50000);
        }
        try (Glm m = Glm.build(dir, layers, total + 1024, dev, 2)) {
            int id = m.bind(prompt, 16);
            int nChunks = (total + chunk - 1) / chunk;
            StringBuilder line = new StringBuilder("PREFILL layers=" + (layers == 0 ? 45 : layers) + " chunk=" + chunk + " ms/chunk:");
            double all = 0;
            for (int c = 0; c < nChunks; c++) {
                int from = c * chunk;
                int to = Math.min(total, from + chunk);
                boolean prof = kernels && c == nChunks - profChunks;
                if (prof) {
                    smile.torch.Native.profStart(env("GLM_PROF_DEV", 0));
                }
                long t0 = System.nanoTime();
                Tensor logits = m.prefillChunk(id, prompt, from, to);
                if (logits != null) {
                    Aten.one("aten::sum", logits).doubleValue();
                    logits.close();
                }
                double ms = (System.nanoTime() - t0) / 1e6;
                all += ms;
                line.append(String.format(" %.0f", ms));
                if (kernels && c == nChunks - 1) {
                    System.out.println("KERNELS (device 0, last " + profChunks + " chunks)\n" + smile.torch.Native.profStop());
                }
            }
            System.out.println(line);
            System.out.printf("PREFILL total %.1f s = %.0f tok/s%n", all / 1e3, total / (all / 1e3));
            if (Gx.STAGE_TIMING) {
                System.out.println("STAGES\n" + Gx.stageReport());
            }
        }
    }
}
