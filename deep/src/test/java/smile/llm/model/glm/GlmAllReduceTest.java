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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one-shot peer-to-peer all-reduce: every rank gets exactly the rank-order float32 sum (bit-identical on all ranks),
 * for many calls and sizes in a row, and the time per call. Opt-in: {@code GLM_CUDA=true}, needs {@code GLM_AR_RANKS} (default 8) GPUs.
 */
public class GlmAllReduceTest {
    @Test
    public void testGivenRanksThenSumIsExactAndIdentical() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        int world = Integer.parseInt(System.getenv().getOrDefault("GLM_AR_RANKS", "8"));
        int[] dev = new int[world];
        for (int i = 0; i < world; i++) {
            dev[i] = i;
        }
        long group = Native.arCreate(dev);
        Assumptions.assumeTrue(group != 0, "no peer access");
        int[] sizes = {4, 1024, 4096, 16384, 4096 * 7, 4096 * 32, 4096 * 64, 262144};
        ExecutorService pool = Executors.newFixedThreadPool(world);
        try {
            List<Future<double[]>> futures = new ArrayList<>();
            for (int r = 0; r < world; r++) {
                final int rank = r;
                futures.add(pool.submit((Callable<double[]>) () -> {
                    Device d = Device.CUDA((byte) rank);
                    double[] result = new double[sizes.length + 1];
                    int calls = 0;
                    for (int round = 0; round < 40; round++) {
                        for (int si = 0; si < sizes.length; si++) {
                            int n = sizes[si];
                            float[] mine = new float[n];
                            java.util.Random rnd = new java.util.Random(1000L * round + 17L * rank + si);
                            for (int i = 0; i < n; i++) {
                                mine[i] = (float) rnd.nextGaussian();
                            }
                            Tensor t = Tensor.of(mine, n).to(d);
                            assertTrue(Native.arAllReduce(group, rank, t), "all-reduce declined");
                            float[] got = t.to(Device.CPU()).floatArray();
                            float[] want = new float[n];
                            for (int p = 0; p < world; p++) {
                                java.util.Random rp = new java.util.Random(1000L * round + 17L * p + si);
                                for (int i = 0; i < n; i++) {
                                    float v = (float) rp.nextGaussian();
                                    want[i] = p == 0 ? v : want[i] + v;
                                }
                            }
                            for (int i = 0; i < n; i++) {
                                if (Float.floatToIntBits(got[i]) != Float.floatToIntBits(want[i])) {
                                    fail("rank " + rank + " round " + round + " size " + n + " element " + i + ": " + got[i] + " != " + want[i]);
                                }
                            }
                            calls++;
                        }
                    }
                    // timing: back-to-back calls of one decode-sized message
                    Tensor t = Aten.one("aten::ones", new long[]{4096}, ScalarType.Float).to(d);
                    for (int i = 0; i < 50; i++) {
                        Native.arAllReduce(group, rank, t);
                    }
                    Aten.one("aten::sum", t).doubleValue();
                    long t0 = System.nanoTime();
                    int reps = 2000;
                    for (int i = 0; i < reps; i++) {
                        Native.arAllReduce(group, rank, t);
                        // keep values finite
                        if (i % 8 == 7) {
                            Aten.call("aten::fill_.Scalar", t, 1.0);
                        }
                    }
                    Aten.one("aten::sum", t).doubleValue();
                    result[sizes.length] = (System.nanoTime() - t0) / 1e3 / reps;
                    result[0] = calls;
                    return result;
                }));
            }
            double us = 0;
            for (Future<double[]> f : futures) {
                double[] r = f.get();
                us = Math.max(us, r[sizes.length]);
            }
            System.out.printf("one-shot all-reduce of 4096 floats on %d ranks: %.1f us per call (including the launch)%n", world, us);
        } finally {
            pool.shutdownNow();
            Native.arDestroy(group);
        }
    }
}
