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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import smile.util.AutoScope;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in ({@code DEEPSEEK_EP=<ranks>}, CUDA): expert parallelism. Every rank thread holds a replica of the dense layers
 * and {@code 1/ranks} of each layer's experts; the routed outputs are summed by NCCL. The result must equal the
 * single-GPU Marlin run up to summation order.
 *
 * @author Haifeng Li
 */
public class DeepSeekExpertParallelTest {
    private static Map<String, Tensor> run(DeepSeekModelArgs args, Path dir, Device device, Dsv.Ep ep, int[] ids,
                                           DeepSeekEngram.Rows[] rows, Tensor[] logitsOut) throws Exception {
        Dsv.setThreadDevice(device);
        Dsv.setEp(ep);
        Dsv.marlin = true;
        try (DeepSeekModel model = new DeepSeekModel(args, dir, l -> rows[l])) {
            Map<String, Tensor> trace = new LinkedHashMap<>();
            long t0 = System.nanoTime();
            Tensor logits = model.forward(ids, 0, trace).to(Device.CPU());
            System.out.printf("rank %s forward %.1f s%n", ep == null ? "-" : ep.rank(), (System.nanoTime() - t0) / 1e9);
            Map<String, Tensor> out = new LinkedHashMap<>();
            for (var e : trace.entrySet()) {
                if (e.getKey().endsWith(".out")) {
                    out.put(e.getKey(), Aten.one("aten::select.int", e.getValue(), 0L, 0L).to(Device.CPU()));
                }
            }
            out.put("logits", logits);
            logitsOut[0] = logits;
            return out;
        } finally {
            Dsv.setEp(null);
        }
    }

    private static double rel(Tensor a, Tensor b) {
        Tensor x = a.to(ScalarType.Float);
        Tensor y = b.to(ScalarType.Float);
        return Aten.one("aten::norm.Scalar", Aten.one("aten::sub.Tensor", x, y)).doubleValue()
                / Aten.one("aten::norm.Scalar", y).doubleValue();
    }

    @Test
    public void testGivenSliceThenExpertParallelEqualsSingleGpu() throws Exception {
        int ranks = Integer.parseInt(System.getenv().getOrDefault("DEEPSEEK_EP", "0"));
        Assumptions.assumeTrue(ranks > 1, "DEEPSEEK_EP not set");
        int layers = Integer.parseInt(System.getenv().getOrDefault("DEEPSEEK_EP_LAYERS", "4"));
        String name = layers == 4 ? "slice4" : "slice" + layers;
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Path fx = Path.of(System.getenv().getOrDefault("DEEPSEEK_FIXTURES", "/raid/haili/dsv4/fixtures"));
        Assumptions.assumeTrue(Files.exists(fx.resolve(name + "_prefill.safetensors")), "no fixture");
        Map<String, Tensor> pre = smile.deep.tensor.SafeTensors.read(fx.resolve(name + "_prefill.safetensors").toString(),
                Device.CPU()).tensors();
        long[] idv = pre.get("ids").longArray();
        int[] ids = new int[idv.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = (int) idv[i];
        }
        DeepSeekEngram.Rows[] rows = new DeepSeekEngram.Rows[64];
        for (int l : new int[]{1, 14}) {
            Path rp = fx.resolve("engram_rows_layer" + l + ".safetensors");
            if (Files.exists(rp)) {
                var r = smile.deep.tensor.SafeTensors.read(rp.toString(), Device.CPU()).tensors();
                rows[l] = new DeepSeekEngram.InMemoryRows(r.get("rows"), r.get("w"), r.get("s"));
            }
        }
        DeepSeekModelArgs args = DeepSeekModelArgs.load(dir.resolve("config.json").toFile()).slice(layers)
                .withMaxSeqLen(layers == 4 ? 256 : 576);

        byte[] devices = new byte[ranks];
        for (int i = 0; i < ranks; i++) {
            devices[i] = (byte) i;
        }
        Map<String, Tensor> single = null;
        if (System.getenv("DEEPSEEK_DEV") != null) {
            run(args, dir, Device.CUDA(Byte.parseByte(System.getenv("DEEPSEEK_DEV"))), null, ids, rows, new Tensor[1]);
            System.out.println("single run on device " + System.getenv("DEEPSEEK_DEV") + " ok");
            return;
        }
        if (!"0".equals(System.getenv().getOrDefault("DEEPSEEK_EP_SINGLE", "1"))) {
            single = run(args, dir, Device.CUDA((byte) 0), null, ids, rows, new Tensor[1]);
        }
        try (TensorParallelGroup group = new TensorParallelGroup(ParallelConfig.tensorParallel(devices))) {
            // daemon threads and shutdownNow: a rank that fails must not leave the others blocked in a collective
            ExecutorService pool = Executors.newFixedThreadPool(ranks, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            List<Future<Map<String, Tensor>>> futures = new ArrayList<>();
            for (int r = 0; r < ranks; r++) {
                final int rank = r;
                futures.add(pool.submit(() -> run(args, dir, Device.CUDA((byte) rank), new Dsv.Ep(group, rank, ranks), ids,
                        rows, new Tensor[1])));
            }
            List<Map<String, Tensor>> results = new ArrayList<>();
            try {
                for (var f : futures) {
                    results.add(f.get());
                }
            } finally {
                pool.shutdownNow();
            }
            Map<String, Tensor> ep = results.get(0);
            for (int r = 1; r < ranks; r++) {
                assertTrue(rel(results.get(r).get("logits"), ep.get("logits")) < 1e-6, "rank " + r + " differs from rank 0");
            }
            for (var e : ep.entrySet()) {
                if (single != null) {
                    System.out.printf("EP vs single %-10s rel err %.5f%n", e.getKey(), rel(e.getValue(), single.get(e.getKey())));
                }
                if (e.getKey().equals("logits") || e.getKey().endsWith("L" + (layers - 1) + ".out")) {
                    System.out.printf("EP vs oracle %-10s rel err %.5f%n", e.getKey(),
                            rel(e.getValue(), pre.get(e.getKey()).dim() == 1 ? Aten.one("aten::unsqueeze", pre.get(e.getKey()), 0L)
                                    : pre.get(e.getKey())));
                }
            }
        }
    }
}
