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
package smile.llm.model.deepseek;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

/**
 * Prefill time by prompt length and decode step time by batch of the full model, with the device-0 kernels (CUPTI) of one
 * prefill and of ten decode steps for attributing them. Opt-in: {@code DEEPSEEK_FULL=<checkpoint dir>};
 * {@code DS_PROF_LENGTHS} (comma separated, default {@code 1024,4096,16384}), {@code DS_PROF_KERNELS_LEN} (prefill length whose
 * kernels are printed, default the last, 0 = none), {@code DS_PROF_BATCHES} (default {@code 1,8,32}),
 * {@code DS_PROF_KERNELS_BATCH} (default 32, 0 = none), {@code DS_PROF_LAYERS}, {@code DS_PROF_DEVICES}, {@code DS_PROF_DEV}.
 */
public class DeepSeekProfileTest {
    private static int env(String name, int def) {
        return Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(def)));
    }

    private static int[] ints(String name, String def) {
        return Arrays.stream(System.getenv().getOrDefault(name, def).split(",")).filter(s -> !s.isBlank()).mapToInt(Integer::parseInt).toArray();
    }

    private static int[] prompt(int n, long seed) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            p[i] = 1000 + (int) ((seed >>> 33) % 50000);
        }
        return p;
    }

    @Test
    public void testPrintsPrefillAndDecodeTimes() throws Exception {
        String dir = System.getenv("DEEPSEEK_FULL");
        Assumptions.assumeTrue(dir != null && Files.exists(Path.of(dir, "config.json")), "set DEEPSEEK_FULL=<checkpoint dir>");
        int layers = env("DS_PROF_LAYERS", 0);
        int devices = env("DS_PROF_DEVICES", 8);
        int[] lengths = ints("DS_PROF_LENGTHS", "1024,4096,16384");
        int[] batches = ints("DS_PROF_BATCHES", "1,8,32");
        int kernelsLen = env("DS_PROF_KERNELS_LEN", lengths[lengths.length - 1]);
        int kernelsBatch = env("DS_PROF_KERNELS_BATCH", 32);
        int dev0 = env("DS_PROF_DEV", 0);
        byte[] dev = new byte[devices];
        for (int i = 0; i < devices; i++) {
            dev[i] = (byte) i;
        }
        int maxLen = Arrays.stream(lengths).max().orElse(1024) + 1024;
        int slots = Arrays.stream(batches).max().orElse(1);
        try (DeepSeek m = DeepSeek.build(dir, layers, maxLen, dev)) {
            StringBuilder line = new StringBuilder("PREFILL layers=" + (layers == 0 ? "all" : layers) + " ms:");
            for (int len : lengths) {
                int[] p = prompt(len, 12345);
                double best = Double.MAX_VALUE;
                for (int rep = 0; rep < 3; rep++) {
                    int id = m.bind(p, len + 16);
                    boolean prof = rep == 2 && len == kernelsLen;
                    if (prof) {
                        if (Aten.STATS != null) {
                            Aten.STATS.clear();
                        }
                        smile.torch.Native.profStart(dev0);
                    }
                    long t0 = System.nanoTime();
                    Tensor logits = m.prefill(id, p, 0);
                    Aten.one("aten::sum", logits).doubleValue();
                    double ms = (System.nanoTime() - t0) / 1e6;
                    logits.close();
                    if (prof) {
                        System.out.println("PREFILL KERNELS (device 0, one prompt of " + len + " tokens)\n" + smile.torch.Native.profStop());
                        if (Aten.STATS != null) {
                            // ATen calls of the prompt per rank (-Dsmile.aten.stats=true)
                            StringBuilder sb = new StringBuilder("ATEN CALLS per prompt and rank:\n");
                            Aten.STATS.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(40)
                                    .forEach(e -> sb.append(String.format("  %7.1f  %s%n", e.getValue()[0] / (double) devices, e.getKey())));
                            System.out.println(sb);
                        }
                    }
                    m.evict(id);
                    if (rep > 0) {
                        best = Math.min(best, ms);     // the first run pays graph/allocator warm-up
                    }
                }
                line.append(String.format(" %d=%.0f (%.0f tok/s)", len, best, len / (best / 1e3)));
                System.out.println(line);
            }
            System.out.println(line);

            int[] p = prompt(128, 777);
            int[] ids = new int[slots];
            int[] pos = new int[slots];
            for (int i = 0; i < slots; i++) {
                ids[i] = m.bind(p, 1024);
                m.prefill(ids[i], p, 0).close();
                pos[i] = p.length;
            }
            StringBuilder dl = new StringBuilder("DECODE step ms:");
            for (int b : batches) {
                int[] rq = Arrays.copyOf(ids, b);
                int[] last = new int[b];
                Arrays.fill(last, 1234);
                double[] ms = new double[12];
                for (int step = 0; step < 4 + ms.length; step++) {
                    int[] pp = Arrays.copyOf(pos, b);
                    long t0 = System.nanoTime();
                    Tensor logits = m.decodeStep(rq, last, pp);
                    Aten.one("aten::sum", logits).doubleValue();
                    if (step >= 4) {
                        ms[step - 4] = (System.nanoTime() - t0) / 1e6;
                    }
                    logits.close();
                    for (int i = 0; i < b; i++) {
                        pos[i]++;
                    }
                }
                Arrays.sort(ms);
                dl.append(String.format("  b%d=%.1f", b, ms[ms.length / 2]));
                if (b == kernelsBatch) {
                    smile.torch.Native.profStart(dev0);
                    for (int w = 0; w < 10; w++) {
                        int[] pp = Arrays.copyOf(pos, b);
                        Tensor lg = m.decodeStep(rq, last, pp);
                        Aten.one("aten::sum", lg).doubleValue();
                        lg.close();
                        for (int i = 0; i < b; i++) {
                            pos[i]++;
                        }
                    }
                    System.out.println("DECODE KERNELS (device 0, 10 steps at batch " + b + ")\n" + smile.torch.Native.profStop());
                }
            }
            System.out.println(dl);
        }
    }
}
