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

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;
import smile.torch.Aten;

/**
 * Times one MLA + indexer layer (layer 3 of the real checkpoint) at long contexts on random caches: decode steps and
 * 512-token prefill chunks. No assertions. Opt-in: {@code GLM_CUDA=true GLM_BENCH=1}.
 */
public class GlmMlaBenchTest {
    private static Tensor randn(long... shape) {
        return Aten.one("aten::randn", shape, ScalarType.Float).to(Device.CUDA((byte) 0));
    }

    @Test
    public void testBenchmark() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")) && System.getenv("GLM_BENCH") != null, "set GLM_BENCH=1");
        Path dir = Path.of(System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw"));
        GlmModelArgs args = GlmModelArgs.load(new File(dir.toFile(), "config.json")).slice(4);
        Gx.setDevice(Device.CUDA((byte) 0));
        Gx.setWeightType(ScalarType.BFloat16);
        try (DeepSeekCheckpoint ck = new DeepSeekCheckpoint(dir)) {
            GlmMla mla = new GlmMla(args, ck, 3);
            GlmMla.State st = mla.newState();
            long filled = 0;
            for (long total : new long[]{16_384, 131_072, 524_288, 1_048_576}) {
                // grow the caches to `total` tokens with random content
                while (filled < total) {
                    long m = Math.min(65_536, total - filled);
                    final long mm = m;
                    Gx.layerScoped(() -> {
                        st.latent.append(randn(mm, args.kvLoraRank()));
                        st.pool.append(randn(mm / 4, args.indexHeadDim()));
                        return Gx.zeros(ScalarType.Float, 1);
                    });
                    filled += m;
                }
                st.length = total;
                for (int n : new int[]{1, 512}) {
                    Tensor x = randn(n, args.dim());
                    int reps = n == 1 ? 10 : 3;
                    long len = st.length;
                    Gx.layerScoped(() -> mla.forward(x, st, null));   // warm-up (appends n tokens)
                    Aten.one("aten::sum", x).doubleValue();
                    long t0 = System.nanoTime();
                    for (int i = 0; i < reps; i++) {
                        Tensor y = Gx.layerScoped(() -> mla.forward(x, st, null));
                        Aten.one("aten::sum", y).doubleValue();
                        y.close();
                    }
                    double ms = (System.nanoTime() - t0) / 1e6 / reps;
                    System.out.printf("context %8d: n=%3d  %.2f ms per call%n", len, n, ms);
                    if (GlmMla.PROFILE && GlmMla.PROFILE_NS[2] > 0) {
                        System.out.printf("   select %.2f ms, attend %.2f ms per chunk%n", GlmMla.PROFILE_NS[0] / 1e6 / GlmMla.PROFILE_NS[2],
                                GlmMla.PROFILE_NS[1] / 1e6 / GlmMla.PROFILE_NS[2]);
                        java.util.Arrays.fill(GlmMla.PROFILE_NS, 0);
                        long c = Math.max(1, GlmMla.PROFILE_PARTS[3]);
                        System.out.printf("   attend parts: query map %.2f ms, kernel %.2f ms, value map %.2f ms%n",
                                GlmMla.PROFILE_PARTS[0] / 1e6 / c, GlmMla.PROFILE_PARTS[1] / 1e6 / c, GlmMla.PROFILE_PARTS[2] / 1e6 / c);
                        java.util.Arrays.fill(GlmMla.PROFILE_PARTS, 0);
                    }
                }
                // trim back to `total` (the benchmark appended a few tokens)
                st.length = total;
            }
        }
    }
}
