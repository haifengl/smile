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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.parallel.ParallelConfig;
import smile.llm.parallel.TensorParallelGroup;
import smile.torch.Aten;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Expert parallelism of the 4-layer slice: every rank of an NCCL group holds a replica of the dense parts and a
 * quarter of the experts; the result must equal the single-GPU run (up to the summation order of the all-reduce)
 * and be identical on all ranks. Opt-in: {@code GLM_EP=<ranks>}.
 */
public class GlmExpertParallelTest {
    private static double rel(Tensor a, Tensor b) {
        Tensor x = a.to(ScalarType.Float).to(Device.CPU());
        Tensor y = b.to(ScalarType.Float).to(Device.CPU());
        return Aten.one("aten::linalg_vector_norm", Aten.one("aten::sub.Tensor", x, y), 2.0).doubleValue()
                / Aten.one("aten::linalg_vector_norm", y, 2.0).doubleValue();
    }

    private static Tensor run(Path dir, GlmModelArgs args, int[] ids, int[] generated, Device device,
                              TensorParallelGroup group, int rank, int world, List<Tensor> steps) throws Exception {
        try (GlmModel model = new GlmModel(dir, args, device, group, rank, world);
             GlmModel.Session session = model.newSession()) {
            long t0 = System.nanoTime();
            Tensor hidden = model.forward(ids, session, null).to(Device.CPU());
            System.out.printf("rank %d prefill %.2f s%n", rank, (System.nanoTime() - t0) / 1e9);
            for (int i = 0; i + 1 < generated.length; i++) {
                steps.add(model.forward(new int[]{generated[i]}, session, null).to(Device.CPU()));
            }
            return hidden;
        }
    }

    @Test
    public void testGivenSliceThenExpertParallelEqualsSingleGpu() throws Exception {
        int ranks = Integer.parseInt(System.getenv().getOrDefault("GLM_EP", "0"));
        Assumptions.assumeTrue(ranks > 1, "GLM_EP not set");
        Path dir = Path.of(System.getenv().getOrDefault("GLM_DIR", "/raid/haili/glm/raw"));
        Path fx = Path.of(System.getenv().getOrDefault("GLM_FIXTURES", "/raid/haili/glm/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve("slice4f_meta.json")), "no fixtures");
        JsonNode meta = new ObjectMapper().readTree(fx.resolve("slice4f_meta.json").toFile()).get("short");
        int[] ids = new int[meta.get("ids").size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = meta.get("ids").get(i).asInt();
        }
        int[] generated = new int[meta.get("generated").size()];
        for (int i = 0; i < generated.length; i++) {
            generated[i] = meta.get("generated").get(i).asInt();
        }
        GlmModelArgs args = GlmModelArgs.load(new File(dir.toFile(), "config.json")).slice(4);

        List<Tensor> singleSteps = new ArrayList<>();
        Tensor single = run(dir, args, ids, generated, Device.CUDA((byte) 0), null, 0, 1, singleSteps);

        byte[] devices = new byte[ranks];
        for (int i = 0; i < ranks; i++) {
            devices[i] = (byte) i;
        }
        try (TensorParallelGroup group = new TensorParallelGroup(ParallelConfig.tensorParallel(devices))) {
            ExecutorService pool = Executors.newFixedThreadPool(ranks, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            List<List<Tensor>> steps = new ArrayList<>();
            List<Future<Tensor>> futures = new ArrayList<>();
            for (int r = 0; r < ranks; r++) {
                final int rank = r;
                List<Tensor> mine = new ArrayList<>();
                steps.add(mine);
                futures.add(pool.submit(() -> run(dir, args, ids, generated, Device.CUDA((byte) rank), group, rank, ranks, mine)));
            }
            List<Tensor> results = new ArrayList<>();
            try {
                for (var f : futures) {
                    results.add(f.get());
                }
            } finally {
                pool.shutdownNow();
            }
            double worst = 0;
            // the float32 oracle: heads, dense matrices and experts are sharded, so the partial sums are added in another
            // order than on one GPU (bf16 noise); what matters is that the sharded error vs the oracle is the single GPU's
            Tensor oracle = smile.deep.tensor.SafeTensors.read(fx.resolve("slice4f_short.safetensors").toString(), Device.CPU())
                    .tensors().get("p.hidden");
            oracle = Aten.one("aten::reshape", oracle, new long[]{oracle.size(1), oracle.size(2)});
            double singleErr = rel(single, oracle);
            for (int r = 0; r < ranks; r++) {
                double e = rel(results.get(r), single);
                double same = rel(results.get(r), results.get(0));
                double vsOracle = rel(results.get(r), oracle);
                System.out.printf("rank %d prefill: vs single GPU %.2e, vs rank 0 %.2e, vs fp32 oracle %.2e (single GPU %.2e)%n",
                        r, e, same, vsOracle, singleErr);
                assertTrue(e < 1.5e-2, "rank " + r + " vs single " + e);
                assertTrue(vsOracle < 1.5 * singleErr + 2e-3, "rank " + r + " vs oracle " + vsOracle + " (single " + singleErr + ")");
                assertTrue(same < 1e-6, "rank " + r + " differs from rank 0: " + same);
                for (int s = 0; s < singleSteps.size(); s++) {
                    double d = rel(steps.get(r).get(s), singleSteps.get(s));
                    worst = Math.max(worst, d);
                    assertTrue(d < 2e-2, "rank " + r + " decode " + s + " vs single " + d);
                }
            }
            System.out.printf("decode steps worst vs single GPU %.2e%n", worst);
        }
    }
}
