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
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.llama.GroupedQueryAttention;
import smile.llm.model.llama.LlamaBlock;
import smile.llm.model.llama.LlamaModel;

/**
 * Loads HuggingFace GPTQ / AWQ / FP8 / NVFP4 SafeTensors into Llama linears
 * (shard-then-pack for TP when {@code tpSize > 1}).
 *
 * @author Haifeng Li
 */
public final class QuantizedHfLoader {
    private static final Logger logger = LoggerFactory.getLogger(QuantizedHfLoader.class);

    private QuantizedHfLoader() {}

    /**
     * Installs quantized projections for every Llama block, then loads embeddings /
     * norms / lm_head via the caller's dense residual path when present.
     *
     * @param model    target model (already on {@code device}).
     * @param dir      checkpoint directory.
     * @param format   detected format.
     * @param backend  selected backend (must match format policy).
     * @param device   CUDA device.
     * @param groupSize GPTQ/AWQ group size (from config, default 128).
     * @param tpSize   tensor-parallel size.
     * @param tpRank   this rank.
     * @param outDtype compute dtype for FP8 outputs.
     */
    public static void installLlamaLinears(LlamaModel model, Path dir, QuantFormat format,
                                           WeightGemmBackend backend, Device device,
                                           int groupSize, int tpSize, int tpRank,
                                           ScalarType outDtype) throws IOException {
        if (backend == WeightGemmBackend.DENSE) {
            return;
        }
        Map<String, String> weightMap = readWeightMap(dir);
        int numLayers = model.numLayers();
        logger.info("Installing quantized Llama linears: format={} backend={} layers={} tp={}/{}",
                format, backend, numLayers, tpRank, tpSize);

        for (int layer = 0; layer < numLayers; layer++) {
            LlamaBlock block = model.layers().get(layer);
            if (!(block.attention() instanceof GroupedQueryAttention gqa)) {
                continue;
            }
            LinearOp wq = loadLinear(dir, weightMap, prefix(layer, "self_attn.q_proj"),
                    format, backend, device, groupSize, tpSize, tpRank, true, outDtype);
            LinearOp wk = loadLinear(dir, weightMap, prefix(layer, "self_attn.k_proj"),
                    format, backend, device, groupSize, tpSize, tpRank, true, outDtype);
            LinearOp wv = loadLinear(dir, weightMap, prefix(layer, "self_attn.v_proj"),
                    format, backend, device, groupSize, tpSize, tpRank, true, outDtype);
            LinearOp wo = loadLinear(dir, weightMap, prefix(layer, "self_attn.o_proj"),
                    format, backend, device, groupSize, tpSize, tpRank, false, outDtype);
            gqa.replaceProjections(wq, wk, wv, wo);

            LinearOp w1 = loadLinear(dir, weightMap, prefix(layer, "mlp.gate_proj"),
                    format, backend, device, groupSize, tpSize, tpRank, true, outDtype);
            LinearOp w3 = loadLinear(dir, weightMap, prefix(layer, "mlp.up_proj"),
                    format, backend, device, groupSize, tpSize, tpRank, true, outDtype);
            LinearOp w2 = loadLinear(dir, weightMap, prefix(layer, "mlp.down_proj"),
                    format, backend, device, groupSize, tpSize, tpRank, false, outDtype);
            block.feedForward().replaceLinears(w1, w2, w3);
        }
        logger.info("Quantized Llama linears installed");
    }

    private static String prefix(int layer, String suffix) {
        return "model.layers." + layer + "." + suffix;
    }

    private static LinearOp loadLinear(Path dir, Map<String, String> weightMap, String base,
                                       QuantFormat format, WeightGemmBackend backend,
                                       Device device, int groupSize, int tpSize, int tpRank,
                                       boolean columnParallel, ScalarType outDtype)
            throws IOException {
        return switch (backend) {
            case FP8 -> loadFp8(dir, weightMap, base, device, tpSize, tpRank, columnParallel, outDtype);
            case NVFP4 -> loadNvfp4(dir, weightMap, base, device, tpSize, tpRank, columnParallel);
            case MARLIN -> loadMarlin(dir, weightMap, base, format, device, groupSize,
                    tpSize, tpRank, columnParallel);
            case DENSE -> throw new IllegalStateException("unexpected DENSE in quantized loader");
        };
    }

    private static LinearOp loadFp8(Path dir, Map<String, String> weightMap, String base,
                                    Device device, int tpSize, int tpRank,
                                    boolean columnParallel, ScalarType outDtype)
            throws IOException {
        Tensor weight = readTensor(dir, weightMap, base + ".weight", device);
        Tensor scale = readOptionalScale(dir, weightMap, base, device);
        try {
            Tensor local = columnParallel
                    ? QuantTpSharding.shardColumn(weight, tpSize, tpRank)
                    : QuantTpSharding.shardRow(weight, tpSize, tpRank);
            Tensor localScale = scale != null ? scale.copy() : Tensor.of(new float[]{1.0f}).to(device);
            return QuantLinearFactory.fp8(local, localScale, null, outDtype);
        } finally {
            weight.close();
            if (scale != null) {
                scale.close();
            }
        }
    }

    private static LinearOp loadNvfp4(Path dir, Map<String, String> weightMap, String base,
                                      Device device, int tpSize, int tpRank,
                                      boolean columnParallel) throws IOException {
        Tensor weight = readTensor(dir, weightMap, base + ".weight", device);
        Tensor scale = readOptionalScale(dir, weightMap, base, device);
        try {
            Tensor local = columnParallel
                    ? QuantTpSharding.shardColumn(weight, tpSize, tpRank)
                    : QuantTpSharding.shardRow(weight, tpSize, tpRank);
            Tensor localScale = scale != null ? scale.copy() : Tensor.of(new float[]{1.0f}).to(device);
            return QuantLinearFactory.nvfp4(local, localScale, null);
        } finally {
            weight.close();
            if (scale != null) {
                scale.close();
            }
        }
    }

    private static LinearOp loadMarlin(Path dir, Map<String, String> weightMap, String base,
                                       QuantFormat format, Device device, int groupSize,
                                       int tpSize, int tpRank, boolean columnParallel)
            throws IOException {
        Tensor qweight = readTensor(dir, weightMap, base + ".qweight", Device.CPU());
        Tensor scales = readTensor(dir, weightMap, base + ".scales", Device.CPU());
        Tensor qzeros = tryReadTensor(dir, weightMap, base + ".qzeros", Device.CPU());
        Tensor gIdx = tryReadTensor(dir, weightMap, base + ".g_idx", Device.CPU());
        try {
            Tensor qLocal = columnParallel
                    ? QuantTpSharding.shardGptqQweightColumn(qweight, tpSize, tpRank)
                    : QuantTpSharding.shardGptqQweightRow(qweight, tpSize, tpRank);
            Tensor sLocal;
            if (columnParallel && tpSize > 1) {
                long[] ss = scales.shape();
                // scales typically [groups, out] — shard out
                if (ss.length == 2 && ss[1] % tpSize == 0) {
                    sLocal = QuantTpSharding.shardColumn(
                            scales.reshape(ss[0], ss[1]), tpSize, tpRank);
                } else {
                    sLocal = scales.copy();
                }
            } else {
                sLocal = scales.copy();
            }
            Tensor zLocal = qzeros;
            if (format == QuantFormat.AWQ) {
                return QuantLinearFactory.marlinFromAwq(qLocal, sLocal, zLocal, groupSize, device);
            }
            return QuantLinearFactory.marlinFromGptq(qLocal, sLocal, zLocal, gIdx, groupSize, device);
        } finally {
            qweight.close();
            scales.close();
            if (qzeros != null) {
                qzeros.close();
            }
            if (gIdx != null) {
                gIdx.close();
            }
        }
    }

    private static Tensor readOptionalScale(Path dir, Map<String, String> weightMap, String base,
                                            Device device) throws IOException {
        for (String suffix : new String[]{".weight_scale", ".scale", ".weight_scale_inv"}) {
            Tensor t = tryReadTensor(dir, weightMap, base + suffix, device);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static Tensor readTensor(Path dir, Map<String, String> weightMap, String name,
                                     Device device) throws IOException {
        Tensor t = tryReadTensor(dir, weightMap, name, device);
        if (t == null) {
            throw new IOException("Missing quantized tensor: " + name);
        }
        return t;
    }

    private static Tensor tryReadTensor(Path dir, Map<String, String> weightMap, String name,
                                        Device device) throws IOException {
        String shard = weightMap.get(name);
        if (shard == null) {
            return null;
        }
        Path shardPath = dir.resolve(shard);
        SafeTensors st = SafeTensors.read(shardPath.toString(), device, java.util.List.of(name));
        try {
            Tensor src = st.tensors().get(name);
            if (src == null) {
                return null;
            }
            Tensor copy = src.copy();
            return copy;
        } finally {
            for (Tensor t : st.tensors().values()) {
                t.close();
            }
        }
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
        if (gs == null) {
            gs = qc.get("group_size");
        }
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
}
