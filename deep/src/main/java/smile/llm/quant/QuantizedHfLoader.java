/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.quant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.checkpoint.SafeTensorsLoaderThreads;
import smile.llm.model.llama.GroupedQueryAttention;
import smile.llm.model.llama.LlamaBlock;
import smile.llm.model.llama.LlamaModel;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads HuggingFace GPTQ / AWQ / FP8 / NVFP4 SafeTensors into Llama linears
 * (shard-then-pack for TP when {@code tpSize > 1}).
 *
 * <p>Marlin path: batch-reads each safetensors shard once, dequantizes AWQ/GPTQ
 * on CPU (with HF→Meta RoPE rearrange on {@code q_proj}/{@code k_proj}), requants
 * to Marlin layout in parallel, then installs on the model graph (main thread)
 * with serial H2D copies.
 *
 * @author Haifeng Li
 */
public final class QuantizedHfLoader {
    private static final Logger logger = LoggerFactory.getLogger(QuantizedHfLoader.class);

    private static final String[] LINEAR_SUFFIXES = {
            "self_attn.q_proj",
            "self_attn.k_proj",
            "self_attn.v_proj",
            "self_attn.o_proj",
            "mlp.gate_proj",
            "mlp.up_proj",
            "mlp.down_proj"
    };

    private QuantizedHfLoader() {}

    /**
     * Installs quantized projections for every Llama block.
     *
     * @see #installLlamaLinears(LlamaModel, Path, QuantFormat, WeightGemmBackend, Device, int, int, int, ScalarType, int)
     */
    public static void installLlamaLinears(LlamaModel model, Path dir, QuantFormat format,
                                           WeightGemmBackend backend, Device device,
                                           int groupSize, int tpSize, int tpRank,
                                           ScalarType outDtype) throws IOException {
        installLlamaLinears(model, dir, format, backend, device, groupSize, tpSize, tpRank,
                outDtype, 0);
    }

    /**
     * Installs quantized projections for every Llama block.
     *
     * @param modelLoaderThreads pack/read concurrency hint ({@code 0} = auto).
     */
    public static void installLlamaLinears(LlamaModel model, Path dir, QuantFormat format,
                                           WeightGemmBackend backend, Device device,
                                           int groupSize, int tpSize, int tpRank,
                                           ScalarType outDtype, int modelLoaderThreads)
            throws IOException {
        if (backend == WeightGemmBackend.DENSE) {
            return;
        }
        Map<String, String> weightMap = readWeightMap(dir);
        int numLayers = model.numLayers();
        logger.info("Installing quantized Llama linears: format={} backend={} layers={} tp={}/{}",
                format, backend, numLayers, tpRank, tpSize);

        long t0 = System.currentTimeMillis();
        if (backend == WeightGemmBackend.MARLIN) {
            installMarlinLinears(model, dir, weightMap, format, device, groupSize,
                    tpSize, tpRank, modelLoaderThreads);
        } else {
            installBatchedFp8OrNvfp4(model, dir, weightMap, backend, device,
                    tpSize, tpRank, outDtype, modelLoaderThreads);
        }
        logger.info("Quantized Llama linears installed in {} ms", System.currentTimeMillis() - t0);
    }

    private static void installMarlinLinears(LlamaModel model, Path dir,
                                             Map<String, String> weightMap, QuantFormat format,
                                             Device device, int groupSize, int tpSize, int tpRank,
                                             int modelLoaderThreads) throws IOException {
        List<LinearJob> jobs = buildJobs(model);
        List<String> keys = marlinKeys(jobs, weightMap, format);
        Map<String, Tensor> bank = batchReadCpu(dir, weightMap, keys, modelLoaderThreads);
        try {
            int packThreads = resolvePackThreads(modelLoaderThreads, jobs.size());
            logger.info("Marlin pack: jobs={} packThreads={} tensors={} (AWQ/GPTQ→FP16→Marlin; q/k RoPE permute)",
                    jobs.size(), packThreads, bank.size());

            ExecutorService pool = Executors.newFixedThreadPool(packThreads);
            List<Future<MarlinWeightPacker.Packed>> futures = new ArrayList<>(jobs.size());
            AtomicInteger done = new AtomicInteger();
            long packStart = System.currentTimeMillis();
            try {
                for (LinearJob job : jobs) {
                    futures.add(pool.submit(() -> {
                        MarlinWeightPacker.Packed packed = packMarlinJob(
                                bank, job, format, groupSize, tpSize, tpRank);
                        int n = done.incrementAndGet();
                        if (n % 32 == 0 || n == jobs.size()) {
                            logger.info("Marlin packed {}/{} linears in {} ms",
                                    n, jobs.size(), System.currentTimeMillis() - packStart);
                        }
                        return packed;
                    }));
                }

                // Install in layer order on the main thread; H2D is serial.
                int fi = 0;
                for (int layer = 0; layer < model.numLayers(); layer++) {
                    LlamaBlock block = model.layers().get(layer);
                    if (!(block.attention() instanceof GroupedQueryAttention gqa)) {
                        fi += 7;
                        continue;
                    }
                    LinearOp wq = toMarlinLinear(futures.get(fi++).get(), device);
                    LinearOp wk = toMarlinLinear(futures.get(fi++).get(), device);
                    LinearOp wv = toMarlinLinear(futures.get(fi++).get(), device);
                    LinearOp wo = toMarlinLinear(futures.get(fi++).get(), device);
                    gqa.replaceProjections(wq, wk, wv, wo);
                    LinearOp w1 = toMarlinLinear(futures.get(fi++).get(), device);
                    LinearOp w3 = toMarlinLinear(futures.get(fi++).get(), device);
                    LinearOp w2 = toMarlinLinear(futures.get(fi++).get(), device);
                    block.feedForward().replaceLinears(w1, w2, w3);
                    if ((layer + 1) % 8 == 0 || layer + 1 == model.numLayers()) {
                        logger.info("Marlin installed layer {}/{}", layer + 1, model.numLayers());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Marlin pack interrupted", e);
            } catch (ExecutionException e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                if (c instanceof RuntimeException re) {
                    throw re;
                }
                throw new IOException("Marlin pack failed: " + c.getMessage(), c);
            } finally {
                pool.shutdownNow();
            }
        } finally {
            closeAll(bank);
        }
    }

    private static void installBatchedFp8OrNvfp4(LlamaModel model, Path dir,
                                                Map<String, String> weightMap,
                                                WeightGemmBackend backend, Device device,
                                                int tpSize, int tpRank, ScalarType outDtype,
                                                int modelLoaderThreads) throws IOException {
        List<LinearJob> jobs = buildJobs(model);
        List<String> keys = new ArrayList<>();
        for (LinearJob job : jobs) {
            keys.add(job.base + ".weight");
            for (String suffix : new String[]{".weight_scale", ".scale", ".weight_scale_inv"}) {
                String name = job.base + suffix;
                if (weightMap.containsKey(name)) {
                    keys.add(name);
                }
            }
        }
        Map<String, Tensor> bank = batchReadCpu(dir, weightMap, keys, modelLoaderThreads);
        try {
            int fi = 0;
            for (int layer = 0; layer < model.numLayers(); layer++) {
                LlamaBlock block = model.layers().get(layer);
                if (!(block.attention() instanceof GroupedQueryAttention gqa)) {
                    fi += 7;
                    continue;
                }
                LinearOp wq = materializeFp8OrNvfp4(bank, jobs.get(fi++), backend, device,
                        tpSize, tpRank, outDtype);
                LinearOp wk = materializeFp8OrNvfp4(bank, jobs.get(fi++), backend, device,
                        tpSize, tpRank, outDtype);
                LinearOp wv = materializeFp8OrNvfp4(bank, jobs.get(fi++), backend, device,
                        tpSize, tpRank, outDtype);
                LinearOp wo = materializeFp8OrNvfp4(bank, jobs.get(fi++), backend, device,
                        tpSize, tpRank, outDtype);
                gqa.replaceProjections(wq, wk, wv, wo);
                LinearOp w1 = materializeFp8OrNvfp4(bank, jobs.get(fi++), backend, device,
                        tpSize, tpRank, outDtype);
                LinearOp w3 = materializeFp8OrNvfp4(bank, jobs.get(fi++), backend, device,
                        tpSize, tpRank, outDtype);
                LinearOp w2 = materializeFp8OrNvfp4(bank, jobs.get(fi++), backend, device,
                        tpSize, tpRank, outDtype);
                block.feedForward().replaceLinears(w1, w2, w3);
            }
        } finally {
            closeAll(bank);
        }
    }

    private static LinearOp materializeFp8OrNvfp4(Map<String, Tensor> bank, LinearJob job,
                                                  WeightGemmBackend backend, Device device,
                                                  int tpSize, int tpRank, ScalarType outDtype) {
        Tensor weight = require(bank, job.base + ".weight");
        Tensor scale = optionalScale(bank, job.base);
        Tensor local = job.columnParallel
                ? QuantTpSharding.shardColumn(weight, tpSize, tpRank)
                : QuantTpSharding.shardRow(weight, tpSize, tpRank);
        Tensor localOnDevice = local.device().equals(device) ? local : local.to(device);
        if (localOnDevice != local) {
            local.close();
        }
        Tensor scaleDev;
        if (scale != null) {
            Tensor sc = scale.copy().to(device);
            scaleDev = sc;
        } else {
            scaleDev = Tensor.of(new float[]{1.0f}).to(device);
        }
        return switch (backend) {
            case FP8 -> QuantLinearFactory.fp8(localOnDevice, scaleDev, null, outDtype);
            case NVFP4 -> QuantLinearFactory.nvfp4(localOnDevice, scaleDev, null);
            default -> throw new IllegalStateException("expected FP8/NVFP4, got " + backend);
        };
    }

    private static MarlinWeightPacker.Packed packMarlinJob(Map<String, Tensor> bank, LinearJob job,
                                                           QuantFormat format, int groupSize,
                                                           int tpSize, int tpRank) {
        Tensor qweight = require(bank, job.base + ".qweight");
        Tensor scales = require(bank, job.base + ".scales");
        Tensor qzeros = bank.get(job.base + ".qzeros");
        Tensor gIdx = bank.get(job.base + ".g_idx");

        Tensor qLocal;
        Tensor sLocal;
        Tensor zLocal = null;
        if (format == QuantFormat.AWQ) {
            qLocal = job.columnParallel
                    ? QuantTpSharding.shardAwqQweightColumn(qweight, tpSize, tpRank)
                    : QuantTpSharding.shardAwqQweightRow(qweight, tpSize, tpRank);
            if (job.columnParallel) {
                sLocal = QuantTpSharding.shardScalesColumn(scales, tpSize, tpRank);
                if (qzeros != null) {
                    zLocal = QuantTpSharding.shardAwqQweightColumn(qzeros, tpSize, tpRank);
                }
            } else {
                sLocal = QuantTpSharding.shardScalesRow(scales, tpSize, tpRank);
                if (qzeros != null) {
                    zLocal = QuantTpSharding.shardAwqQweightRow(qzeros, tpSize, tpRank);
                }
            }
            try {
                return MarlinWeightPacker.packAwqDirect(
                        qLocal, sLocal, zLocal, groupSize, Device.CPU(), job.ropeHeads());
            } finally {
                qLocal.close();
                sLocal.close();
                if (zLocal != null) {
                    zLocal.close();
                }
            }
        }

        qLocal = job.columnParallel
                ? QuantTpSharding.shardGptqQweightColumn(qweight, tpSize, tpRank)
                : QuantTpSharding.shardGptqQweightRow(qweight, tpSize, tpRank);
        if (job.columnParallel) {
            sLocal = QuantTpSharding.shardScalesColumn(scales, tpSize, tpRank);
        } else {
            sLocal = QuantTpSharding.shardScalesRow(scales, tpSize, tpRank);
        }
        Tensor gLocal = gIdx;
        try {
            return MarlinWeightPacker.packGptqDirect(qLocal, sLocal, qzeros, gLocal, groupSize,
                    Device.CPU(), job.ropeHeads());
        } finally {
            qLocal.close();
            sLocal.close();
        }
    }

    private static LinearOp toMarlinLinear(MarlinWeightPacker.Packed packed, Device device) {
        try {
            Tensor qw = packed.qweight();
            Tensor sc = packed.scales();
            Tensor qwDev = qw.device().equals(device) ? qw : qw.to(device);
            Tensor scDev = sc.device().equals(device) ? sc : sc.to(device);
            if (qwDev != qw) {
                qw.close();
            }
            if (scDev != sc) {
                sc.close();
            }
            return new MarlinLinear(qwDev, scDev, null,
                    packed.inFeatures(), packed.outFeatures(), packed.groupSize());
        } catch (RuntimeException e) {
            packed.close();
            throw e;
        }
    }

    private static List<LinearJob> buildJobs(LlamaModel model) {
        int numLayers = model.numLayers();
        int numHeads = 0;
        int numKvHeads = 0;
        if (!model.layers().isEmpty()
                && model.layers().getFirst().attention() instanceof GroupedQueryAttention gqa) {
            numHeads = gqa.numQueryHeads();
            numKvHeads = gqa.numKeyValueHeads();
        }
        List<LinearJob> jobs = new ArrayList<>(numLayers * 7);
        for (int layer = 0; layer < numLayers; layer++) {
            for (String suffix : LINEAR_SUFFIXES) {
                boolean column = !suffix.endsWith("o_proj") && !suffix.endsWith("down_proj");
                Integer ropeHeads = null;
                if (suffix.endsWith("q_proj") && numHeads > 0) {
                    ropeHeads = numHeads;
                } else if (suffix.endsWith("k_proj") && numKvHeads > 0) {
                    ropeHeads = numKvHeads;
                }
                jobs.add(new LinearJob(layer, prefix(layer, suffix), column, ropeHeads));
            }
        }
        return jobs;
    }

    private static List<String> marlinKeys(List<LinearJob> jobs, Map<String, String> weightMap,
                                           QuantFormat format) {
        List<String> keys = new ArrayList<>();
        for (LinearJob job : jobs) {
            keys.add(job.base + ".qweight");
            keys.add(job.base + ".scales");
            String z = job.base + ".qzeros";
            if (weightMap.containsKey(z)) {
                keys.add(z);
            }
            if (format == QuantFormat.GPTQ) {
                String g = job.base + ".g_idx";
                if (weightMap.containsKey(g)) {
                    keys.add(g);
                }
            }
        }
        return keys;
    }

    /**
     * One SafeTensors open per shard for the given key set (CPU staging).
     */
    static Map<String, Tensor> batchReadCpu(Path dir, Map<String, String> weightMap,
                                            List<String> keys, int modelLoaderThreads)
            throws IOException {
        Map<String, List<String>> byShard = new LinkedHashMap<>();
        for (String key : keys) {
            String shard = weightMap.get(key);
            if (shard == null) {
                throw new IOException("Missing quantized tensor in weight map: " + key);
            }
            byShard.computeIfAbsent(shard, s -> new ArrayList<>()).add(key);
        }
        Map<String, Tensor> bank = new HashMap<>();
        List<Map.Entry<String, List<String>>> shardEntries = new ArrayList<>(byShard.entrySet());
        int readThreads = SafeTensorsLoaderThreads.resolve(modelLoaderThreads, shardEntries.size());
        if (readThreads <= 1 || shardEntries.size() <= 1) {
            for (var e : shardEntries) {
                readShardInto(dir, e.getKey(), e.getValue(), bank);
            }
            return bank;
        }
        ExecutorService pool = Executors.newFixedThreadPool(readThreads);
        try {
            List<Future<Map<String, Tensor>>> futures = new ArrayList<>();
            for (var e : shardEntries) {
                futures.add(pool.submit(() -> {
                    Map<String, Tensor> partial = new HashMap<>();
                    readShardInto(dir, e.getKey(), e.getValue(), partial);
                    return partial;
                }));
            }
            for (Future<Map<String, Tensor>> f : futures) {
                bank.putAll(f.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeAll(bank);
            throw new IOException("shard read interrupted", e);
        } catch (ExecutionException e) {
            closeAll(bank);
            Throwable c = e.getCause() != null ? e.getCause() : e;
            if (c instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("shard read failed: " + c.getMessage(), c);
        } finally {
            pool.shutdownNow();
        }
        return bank;
    }

    private static void readShardInto(Path dir, String shard, List<String> keys,
                                      Map<String, Tensor> bank) throws IOException {
        Path shardPath = dir.resolve(shard);
        SafeTensors st = SafeTensors.read(shardPath.toString(), Device.CPU(), keys);
        try {
            for (String key : keys) {
                Tensor src = st.tensors().get(key);
                if (src == null) {
                    throw new IOException("Missing tensor " + key + " in " + shard);
                }
                bank.put(key, src.copy());
            }
        } finally {
            for (Tensor t : st.tensors().values()) {
                t.close();
            }
        }
    }

    private static int resolvePackThreads(int modelLoaderThreads, int numJobs) {
        if (numJobs <= 0) {
            return 1;
        }
        int auto = Math.min(SafeTensorsLoaderThreads.AUTO_CAP,
                Math.max(1, Runtime.getRuntime().availableProcessors()));
        int threads = modelLoaderThreads > 0 ? modelLoaderThreads : auto;
        return Math.max(1, Math.min(threads, numJobs));
    }

    private static String prefix(int layer, String suffix) {
        return "model.layers." + layer + "." + suffix;
    }

    private static Tensor require(Map<String, Tensor> bank, String name) {
        Tensor t = bank.get(name);
        if (t == null) {
            throw new IllegalStateException("Missing tensor in bank: " + name);
        }
        return t;
    }

    private static Tensor optionalScale(Map<String, Tensor> bank, String base) {
        for (String suffix : new String[]{".weight_scale", ".scale", ".weight_scale_inv"}) {
            Tensor t = bank.get(base + suffix);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static void closeAll(Map<String, Tensor> bank) {
        for (Tensor t : bank.values()) {
            try {
                t.close();
            } catch (Throwable ignored) {
                // best-effort
            }
        }
        bank.clear();
    }

    public static int groupSizeFromConfig(Path checkpointDir) throws IOException {
        Path config = checkpointDir.resolve("config.json");
        if (!Files.exists(config)) {
            return 128;
        }
        JsonNode root = new ObjectMapper().readTree(config.toFile());
        JsonNode qc = root.get("quantization_config");
        if (qc == null) {
            return 128;
        }
        JsonNode gs = qc.get("group_size");
        if (gs != null && gs.isNumber()) {
            return gs.asInt();
        }
        return 128;
    }

    private static Map<String, String> readWeightMap(Path dir) throws IOException {
        Path indexPath = dir.resolve("model.safetensors.index.json");
        Map<String, String> map = new HashMap<>();
        if (Files.exists(indexPath)) {
            JsonNode root = new ObjectMapper().readTree(indexPath.toFile());
            JsonNode weightMap = root.get("weight_map");
            if (weightMap != null && weightMap.isObject()) {
                for (var e : weightMap.properties()) {
                    map.put(e.getKey(), e.getValue().asString());
                }
            }
            return map;
        }
        try (var stream = Files.list(dir)) {
            var shards = stream.filter(p -> p.getFileName().toString().endsWith(".safetensors"))
                    .toList();
            if (shards.size() != 1) {
                throw new IOException("Need model.safetensors.index.json or a single .safetensors");
            }
            String shard = shards.getFirst().getFileName().toString();
            for (String name : SafeTensors.listTensors(shards.getFirst().toString())) {
                map.put(name, shard);
            }
        }
        return map;
    }

    /**
     * @param ropeHeads non-null for {@code q_proj}/{@code k_proj}: HF→Meta RoPE rearrange.
     */
    private record LinearJob(int layer, String base, boolean columnParallel, Integer ropeHeads) {}
}
