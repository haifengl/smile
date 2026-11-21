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
package smile.llm.model.minimax;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prefill time by prompt length (in engine-sized chunks) and decode step time by batch of the full model, with the device-0
 * kernels (CUPTI) of one prefill and of ten decode steps for attributing them. Opt-in: {@code MINIMAX_MODEL_DIR},
 * {@code MINIMAX_DEVICES}; {@code MM_PROF_LENGTHS} (default {@code 1024,4096,16384}), {@code MM_PROF_CHUNK} (default 2048),
 * {@code MM_PROF_KERNELS_LEN} (prefill length whose kernels are printed, default the last, 0 = none), {@code MM_PROF_BATCHES}
 * (default {@code 1,8,32}), {@code MM_PROF_KERNELS_BATCH} (default 32, 0 = none), {@code MM_PROF_DEV} (device whose kernels are
 * recorded, default the first), {@code MM_PROF_MEM} (memory fraction, default 0.85), {@code MM_PROF_DRAFT=1} loads the draft.
 */
public class MiniMaxProfileTest {
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
        String dir = System.getenv("MINIMAX_MODEL_DIR");
        String devList = System.getenv("MINIMAX_DEVICES");
        assumeTrue(dir != null && devList != null, "MINIMAX_MODEL_DIR / MINIMAX_DEVICES not set");
        String[] ds = devList.split(",");
        byte[] devices = new byte[ds.length];
        for (int i = 0; i < ds.length; i++) {
            devices[i] = Byte.parseByte(ds[i].trim());
        }
        int[] lengths = ints("MM_PROF_LENGTHS", "1024,4096,16384");
        int[] batches = ints("MM_PROF_BATCHES", "1,8,32");
        int chunk = env("MM_PROF_CHUNK", 2048);
        int kernelsLen = env("MM_PROF_KERNELS_LEN", lengths[lengths.length - 1]);
        int kernelsBatch = env("MM_PROF_KERNELS_BATCH", 32);
        int dev0 = env("MM_PROF_DEV", devices[0]);
        double mem = Double.parseDouble(System.getenv().getOrDefault("MM_PROF_MEM", "0.85"));
        boolean draft = env("MM_PROF_DRAFT", 0) > 0;
        int maxLen = Arrays.stream(lengths).max().orElse(1024) + 1024;
        int slots = Arrays.stream(batches).max().orElse(1);
        try (MiniMax m = MiniMax.build(dir, Math.max(slots, 2), maxLen, devices, mem, 16, null, null, draft)) {
            StringBuilder line = new StringBuilder("PREFILL chunk=" + chunk + " ms:");
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
                    Tensor logits = null;
                    for (int from = 0; from < len; from += chunk) {
                        Tensor l = m.prefillChunk(id, p, from, Math.min(len, from + chunk));
                        if (l != null) {
                            logits = l;
                        }
                    }
                    Aten.one("aten::sum", logits).doubleValue();
                    double ms = (System.nanoTime() - t0) / 1e6;
                    if (rep == 2 && System.getenv("MM_PROF_TOP") != null) {
                        var top = logits.to(smile.deep.tensor.ScalarType.Float).reshape(1, -1).topk(5, 1, true, true);
                        System.out.println("TOP prefill " + len + " ids " + Arrays.toString(top._2().to(smile.deep.tensor.Device.CPU()).longArray())
                                + " logits " + Arrays.toString(top._1().to(smile.deep.tensor.Device.CPU()).floatArray()));
                    }
                    logits.close();
                    if (prof) {
                        System.out.println("PREFILL KERNELS (device " + dev0 + ", one prompt of " + len + " tokens)\n" + smile.torch.Native.profStop());
                        if (Aten.STATS != null) {
                            StringBuilder sb = new StringBuilder("ATEN CALLS per prompt and rank:\n");
                            Aten.STATS.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(40)
                                    .forEach(e -> sb.append(String.format("  %7.1f  %s%n", e.getValue()[0] / (double) devices.length, e.getKey())));
                            System.out.println(sb);
                        }
                    }
                    m.evict(id);
                    if (rep > 0) {
                        best = Math.min(best, ms);     // the first run pays allocator warm-up
                    }
                }
                line.append(String.format(" %d=%.0f (%.0f tok/s)", len, best, len / (best / 1e3)));
                System.out.println(line);
            }

            int context = 128;
            int[] p = prompt(context, 777);
            int[] ids = new int[slots];
            int[] pos = new int[slots];
            for (int i = 0; i < slots; i++) {
                int[] pi = p.clone();
                pi[0] = 20 + i;            // distinct prompts: no prefix sharing
                ids[i] = m.bind(pi, 1024);
                m.prefill(ids[i], pi, 0).close();
                pos[i] = context;
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
                    if (step == 5 && b == batches[batches.length - 1] && System.getenv("MM_PROF_TOP") != null) {
                        var top = logits.to(smile.deep.tensor.ScalarType.Float).reshape(b, -1).topk(3, 1, true, true);
                        System.out.println("TOP decode b" + b + " ids " + Arrays.toString(top._2().to(smile.deep.tensor.Device.CPU()).longArray())
                                + " logits " + Arrays.toString(top._1().to(smile.deep.tensor.Device.CPU()).floatArray()));
                    }
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
                    System.out.println("DECODE KERNELS (device " + dev0 + ", 10 steps at batch " + b + ")\n" + smile.torch.Native.profStop());
                }
            }
            System.out.println(dl);
        }
    }
}
