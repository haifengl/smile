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
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.deepseek.DeepSeekCheckpoint;
import smile.torch.Aten;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A prefill chunk of the real layer-3 MLA + indexer on random caches: the kernel path (fused scoring, radix top-k, latent
 * attention kernel) against the torch-op path: same selected tokens per query and the same attention output.
 * Opt-in: {@code GLM_CUDA=true}.
 */
public class GlmMlaPrefillTest {
    private static final Device DEV = Device.CUDA((byte) 0);

    private static Tensor randn(long... shape) {
        return Aten.one("aten::randn", shape, ScalarType.Float).to(DEV);
    }

    private static GlmMla.State filled(GlmMla mla, Tensor lat, Tensor pool, Tensor ring, long tokens) {
        GlmMla.State st = mla.newState();
        st.latent.append(lat);
        st.pool.append(pool);
        Aten.call("aten::copy_", st.ring, ring, false);
        st.length = tokens;
        return st;
    }

    @Test
    public void testGivenRandomCachesThenKernelPrefillEqualsTorchOps() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("GLM_CUDA")), "set GLM_CUDA=true");
        Path dir = Path.of(System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw"));
        GlmModelArgs args = GlmModelArgs.load(new File(dir.toFile(), "config.json")).slice(4);
        Gx.setDevice(DEV);
        Gx.setWeightType(ScalarType.BFloat16);
        try (DeepSeekCheckpoint ck = new DeepSeekCheckpoint(dir)) {
            GlmMla mla = new GlmMla(args, ck, 3);
            for (long tokens : new long[]{2, 40, 3003, 9001}) {   // fewer pools than the top-k, around it, many more
                Tensor lat = randn(tokens, 512).to(ScalarType.BFloat16);
                Tensor pool = randn(tokens / 4, 128).to(ScalarType.BFloat16);
                Tensor ring = randn(4, 256);
                int n = 300;
                Tensor x = randn(n, 4096);
                Map<String, Tensor> t1 = new HashMap<>();
                Map<String, Tensor> t2 = new HashMap<>();
                GlmMla.State s1 = filled(mla, lat, pool, ring, tokens);
                GlmMla.State s2 = filled(mla, lat, pool, ring, tokens);
                GlmMla.prefillKernelsEnabled = true;
                Tensor a = Gx.layerScoped(() -> mla.forward(x, s1, t1));
                GlmMla.prefillKernelsEnabled = false;
                Tensor b = Gx.layerScoped(() -> mla.forward(x, s2, t2));
                GlmMla.prefillKernelsEnabled = true;
                // selected tokens per query
                int[] k = Aten.one("aten::clone", t1.get("topk").to(ScalarType.Int32)).to(Device.CPU()).intArray();
                long[] r = Aten.one("aten::clone", t2.get("topk").to(ScalarType.Int64)).to(Device.CPU()).longArray();
                int wk = k.length / n;
                int wr = r.length / n;
                int differing = 0;
                for (int i = 0; i < n; i++) {
                    TreeSet<Long> sk = new TreeSet<>();
                    TreeSet<Long> sr = new TreeSet<>();
                    for (int j = 0; j < wk; j++) {
                        if (k[i * wk + j] >= 0) {
                            sk.add((long) k[i * wk + j]);
                        }
                    }
                    for (int j = 0; j < wr; j++) {
                        if (r[i * wr + j] >= 0) {
                            sr.add(r[i * wr + j]);
                        }
                    }
                    if (!sk.equals(sr)) {
                        differing++;
                    }
                }
                double e = Aten.one("aten::linalg_vector_norm", Aten.one("aten::sub.Tensor", a, b), 2.0).doubleValue()
                        / Aten.one("aten::linalg_vector_norm", b, 2.0).doubleValue();
                System.out.printf("tokens %d: rows with a different selection %d of %d, output rel err %.2e%n", tokens, differing, n, e);
                assertTrue(differing <= n / 50, "selection differs in " + differing + " rows at " + tokens + " tokens");
                assertTrue(e < 2e-2, "output rel err " + e + " at " + tokens + " tokens");
            }
        }
    }
}
