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
import java.util.Arrays;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

/**
 * Graph decode step time at several batch sizes (median of 12 steps, device synchronized), for attributing the step to
 * its parts with the ablation switches {@code GLM_PROF_SKIP=moe,attn,ar} (timing only, the output is meaningless).
 * Opt-in: {@code GLM_FULL=<checkpoint dir>}, {@code GLM_PROF_LAYERS} (0 = all), {@code GLM_FULL_DEVICES}.
 */
public class GlmDecodeProfileTest {
    @Test
    public void testPrintsDecodeStepTimes() throws Exception {
        String dir = System.getenv("GLM_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "model.safetensors.index.json")), "set GLM_FULL=<checkpoint dir>");
        int layers = Integer.parseInt(System.getenv().getOrDefault("GLM_PROF_LAYERS", "0"));
        int devices = Integer.parseInt(System.getenv().getOrDefault("GLM_FULL_DEVICES", "8"));
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        int[] prompt = new int[128];
        for (int i = 0; i < prompt.length; i++) {
            prompt[i] = 1000 + (i * 37) % 5000;
        }
        try (Glm m = Glm.build(dir, layers, 4096, dev, 32)) {
            int[] ids = new int[32];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = m.bind(prompt, 512);
                m.prefill(ids[i], prompt, 0).close();
            }
            StringBuilder line = new StringBuilder("PROFILE layers=" + (layers == 0 ? 45 : layers) + " skip=[" + System.getenv().getOrDefault("GLM_PROF_SKIP", "") + "]");
            for (int b : new int[]{1, 8, 32}) {
                int[] rq = Arrays.copyOf(ids, b);
                int[] last = new int[b];
                Arrays.fill(last, 1234);
                double[] ms = new double[12];
                for (int step = 0; step < 4 + ms.length; step++) {
                    int[] p = new int[b];
                    for (int i = 0; i < b; i++) {
                        p[i] = m.positionOf(rq[i]);
                    }
                    long t0 = System.nanoTime();
                    Tensor logits = m.decodeStep(rq, last, p);
                    Aten.one("aten::sum", logits).doubleValue();       // wait for the device
                    if (step >= 4) {
                        ms[step - 4] = (System.nanoTime() - t0) / 1e6;
                    }
                    logits.close();
                }
                Arrays.sort(ms);
                line.append(String.format("  b%d=%.1f ms", b, ms[ms.length / 2]));
            }
            System.out.println(line);
            int kb = Integer.parseInt(System.getenv().getOrDefault("GLM_PROF_KERNELS", "0"));
            if (kb > 0) {
                // record the kernels of 10 graph decode steps at batch kb on device 0 (CUPTI activity records)
                int[] rq = Arrays.copyOf(ids, kb);
                int[] last = new int[kb];
                Arrays.fill(last, 1234);
                for (int w = 0; w < 3; w++) {
                    int[] p = new int[kb];
                    for (int i = 0; i < kb; i++) {
                        p[i] = m.positionOf(rq[i]);
                    }
                    m.decodeStep(rq, last, p).close();
                }
                boolean on = smile.torch.Native.profStart(Integer.parseInt(System.getenv().getOrDefault("GLM_PROF_DEV", "0")));
                for (int w = 0; w < 10; w++) {
                    int[] p = new int[kb];
                    for (int i = 0; i < kb; i++) {
                        p[i] = m.positionOf(rq[i]);
                    }
                    Tensor lg = m.decodeStep(rq, last, p);
                    Aten.one("aten::sum", lg).doubleValue();
                    lg.close();
                }
                System.out.println("KERNELS (device 0, 10 steps at batch " + kb + ", recording " + on + ")\n" + smile.torch.Native.profStop());
            }
            if (Gx.STAGE_TIMING) {
                System.out.println("STAGES (eager, per call, summed over ranks' calls)\n" + Gx.stageReport());
            }
        }
    }
}
