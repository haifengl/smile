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
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.deep.tensor.Device;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.qwen.GatedAttention;
import smile.llm.model.qwen.QwenBlock;
import smile.llm.model.qwen.QwenModel;
import smile.llm.model.qwen.QwenModelArgs;
import smile.llm.model.qwen.QwenWeightShard;
import smile.llm.parallel.TensorShardSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Installs native FP8 linears into a Qwen hybrid model from HuggingFace SafeTensors.
 *
 * <p>Official Qwen3.8-FP8 uses fine-grained {@code weight_block_size=[128,128]}
 * with {@code weight_scale_inv}. Those weights are installed as
 * {@link Fp8BlockLinear} (LibTorch {@code _scaled_mm_v2}). Tensor-scale FP8
 * checkpoints still use {@link Fp8Linear}.
 *
 * <p>DeltaNet {@code in_proj_a}/{@code in_proj_b}, norms, conv, {@code A_log},
 * {@code dt_bias}, embeddings, {@code lm_head}, and the vision tower stay on the
 * dense residual load path.
 *
 * @author Haifeng Li
 */
public final class QuantizedQwenFp8Loader {
    private static final Logger logger = LoggerFactory.getLogger(QuantizedQwenFp8Loader.class);

    private static final String[] HF_PREFIXES = {
            "model.language_model.layers.",
            "model.layers.",
            "language_model.model.layers.",
            "language_model.layers."
    };

    private QuantizedQwenFp8Loader() {}

    /**
     * Installs FP8 (or block-dequant dense) projections for every hybrid block
     * on one TP rank.
     *
     * @param model              target rank model (shells already on {@code device}).
     * @param dir                checkpoint directory.
     * @param device             rank device.
     * @param tpSize             tensor-parallel world size.
     * @param tpRank             this rank.
     * @param outDtype           compute dtype for dequant / Fp8Linear output.
     * @param modelLoaderThreads shard-read concurrency ({@code 0} = auto).
     */
    public static void install(QwenModel model, Path dir, Device device,
                               int tpSize, int tpRank, ScalarType outDtype,
                               int modelLoaderThreads) throws IOException {
        Map<String, String> weightMap = readWeightMap(dir);
        validateCheckpointPolicy(dir, weightMap, model.visionArgs() != null);

        List<LinearJob> jobs = buildJobs(model, weightMap);
        if (jobs.isEmpty()) {
            throw new IOException("No FP8 Qwen linear weights found under " + dir);
        }

        List<String> keys = new ArrayList<>();
        for (LinearJob job : jobs) {
            keys.add(job.hfBase + ".weight");
            for (String suffix : new String[]{".weight_scale_inv", ".weight_scale", ".scale"}) {
                String name = job.hfBase + suffix;
                if (weightMap.containsKey(name)) {
                    keys.add(name);
                }
            }
        }

        logger.info("Installing Qwen FP8 linears: jobs={} tp={}/{} outDtype={}",
                jobs.size(), tpRank, tpSize, outDtype);
        long t0 = System.currentTimeMillis();
        Map<String, Tensor> bank = QuantizedHfLoader.batchReadCpu(dir, weightMap, keys, modelLoaderThreads);
        int blockFp8 = 0;
        int tensorFp8 = 0;
        try {
            int fi = 0;
            for (QwenBlock block : model.layers()) {
                if (block.selfAttn() != null) {
                    LinearOp q = materialize(bank, jobs.get(fi++), model, device, outDtype);
                    LinearOp k = materialize(bank, jobs.get(fi++), model, device, outDtype);
                    LinearOp v = materialize(bank, jobs.get(fi++), model, device, outDtype);
                    LinearOp o = materialize(bank, jobs.get(fi++), model, device, outDtype);
                    blockFp8 += countBlockFp8(q, k, v, o);
                    tensorFp8 += countTensorFp8(q, k, v, o);
                    block.selfAttn().replaceProjections(q, k, v, o);
                } else if (block.linearAttn() != null) {
                    LinearOp qkv = materialize(bank, jobs.get(fi++), model, device, outDtype);
                    LinearOp z = materialize(bank, jobs.get(fi++), model, device, outDtype);
                    LinearOp out = materialize(bank, jobs.get(fi++), model, device, outDtype);
                    blockFp8 += countBlockFp8(qkv, z, out);
                    tensorFp8 += countTensorFp8(qkv, z, out);
                    block.linearAttn().replaceGemmProjections(qkv, z, out);
                }
                LinearOp w1 = materialize(bank, jobs.get(fi++), model, device, outDtype);
                LinearOp w3 = materialize(bank, jobs.get(fi++), model, device, outDtype);
                LinearOp w2 = materialize(bank, jobs.get(fi++), model, device, outDtype);
                blockFp8 += countBlockFp8(w1, w2, w3);
                tensorFp8 += countTensorFp8(w1, w2, w3);
                block.feedForward().replaceLinears(w1, w2, w3);
            }
            if (fi != jobs.size()) {
                throw new IllegalStateException(
                        "Qwen FP8 install job mismatch: consumed=" + fi + " jobs=" + jobs.size());
            }
        } finally {
            closeAll(bank);
        }
        logger.info("Qwen FP8 linears installed in {} ms (blockFp8={} tensorFp8={})",
                System.currentTimeMillis() - t0, blockFp8, tensorFp8);
    }

    private static int countBlockFp8(LinearOp... ops) {
        int n = 0;
        for (LinearOp op : ops) {
            if (op instanceof Fp8BlockLinear) {
                n++;
            }
        }
        return n;
    }

    private static int countTensorFp8(LinearOp... ops) {
        int n = 0;
        for (LinearOp op : ops) {
            if (op instanceof Fp8Linear) {
                n++;
            }
        }
        return n;
    }

    private static List<LinearJob> buildJobs(QwenModel model, Map<String, String> weightMap)
            throws IOException {
        List<LinearJob> jobs = new ArrayList<>();
        for (QwenBlock block : model.layers()) {
            int layer = block.layerId();
            if (block.selfAttn() != null) {
                jobs.add(job(weightMap, layer, "self_attn.q_proj",
                        "layers." + layer + ".self_attn.q_proj.weight"));
                jobs.add(job(weightMap, layer, "self_attn.k_proj",
                        "layers." + layer + ".self_attn.k_proj.weight"));
                jobs.add(job(weightMap, layer, "self_attn.v_proj",
                        "layers." + layer + ".self_attn.v_proj.weight"));
                jobs.add(job(weightMap, layer, "self_attn.o_proj",
                        "layers." + layer + ".self_attn.o_proj.weight"));
            } else if (block.linearAttn() != null) {
                jobs.add(job(weightMap, layer, "linear_attn.in_proj_qkv",
                        "layers." + layer + ".linear_attn.in_proj_qkv.weight"));
                jobs.add(job(weightMap, layer, "linear_attn.in_proj_z",
                        "layers." + layer + ".linear_attn.in_proj_z.weight"));
                jobs.add(job(weightMap, layer, "linear_attn.out_proj",
                        "layers." + layer + ".linear_attn.out_proj.weight"));
            }
            jobs.add(job(weightMap, layer, "mlp.gate_proj",
                    "layers." + layer + ".mlp.w1.weight"));
            jobs.add(job(weightMap, layer, "mlp.up_proj",
                    "layers." + layer + ".mlp.w3.weight"));
            jobs.add(job(weightMap, layer, "mlp.down_proj",
                    "layers." + layer + ".mlp.w2.weight"));
        }
        return jobs;
    }

    private static LinearJob job(Map<String, String> weightMap, int layer, String hfSuffix,
                                 String smileWeightName) throws IOException {
        String base = resolveHfBase(weightMap, layer, hfSuffix);
        return new LinearJob(layer, base, smileWeightName);
    }

    static String resolveHfBase(Map<String, String> weightMap, int layer, String suffix)
            throws IOException {
        for (String prefix : HF_PREFIXES) {
            String base = prefix + layer + "." + suffix;
            if (weightMap.containsKey(base + ".weight")) {
                return base;
            }
        }
        throw new IOException("Missing Qwen FP8 weight for layer " + layer + " suffix " + suffix);
    }

    private static LinearOp materialize(Map<String, Tensor> bank, LinearJob job, QwenModel model,
                                        Device device, ScalarType outDtype) {
        Tensor weight = require(bank, job.hfBase + ".weight");
        Tensor scale = optionalScale(bank, job.hfBase);
        QwenModelArgs args = model.params();
        TensorShardSpec shard = model.shard();

        if (scale == null) {
            throw new IllegalStateException(
                    "Qwen FP8 linear missing scale for " + job.hfBase
                            + " (expected weight_scale_inv / weight_scale / scale)");
        }

        if (Fp8BlockDequant.isBlockScale(weight, scale)) {
            Tensor localW = QwenWeightShard.shard(job.smileWeightName, weight, args, shard);
            boolean ownedW = localW != weight;
            Tensor localS = QwenWeightShard.shardScaleInv(job.smileWeightName, scale, args, shard);
            boolean ownedS = localS != scale;
            try {
                Tensor wDev = localW.device().equals(device) ? localW.copy() : localW.to(device);
                Tensor sDev = localS.to(ScalarType.Float);
                if (!sDev.device().equals(device)) {
                    Tensor moved = sDev.to(device);
                    sDev.close();
                    sDev = moved;
                }
                if (!Fp8BlockDequant.isBlockScale(wDev, sDev)) {
                    throw new IllegalStateException(
                            "TP shard broke block scale layout for " + job.hfBase);
                }
                return QuantLinearFactory.fp8Block(wDev, sDev, null, outDtype);
            } finally {
                if (ownedW) {
                    localW.close();
                }
                if (ownedS) {
                    localS.close();
                }
            }
        }

        if (!Fp8BlockDequant.isTensorScale(scale)) {
            throw new IllegalStateException(
                    "Unsupported FP8 scale layout for " + job.hfBase
                            + ": weight=" + java.util.Arrays.toString(weight.shape())
                            + " scale=" + java.util.Arrays.toString(scale.shape())
                            + " (supported: scalar tensor scale, or block-128 weight_scale_inv)");
        }

        Tensor localW = QwenWeightShard.shard(job.smileWeightName, weight, args, shard);
        boolean ownedW = localW != weight;
        Tensor wDev = localW.device().equals(device) ? localW.copy() : localW.to(device);
        if (ownedW) {
            localW.close();
        }
        Tensor scaleDev = scale.copy().to(device);
        return QuantLinearFactory.fp8(wDev, scaleDev, null, outDtype);
    }

    /**
     * HF keys that the FP8 installer owns (must not be force-fed into dense shells).
     */
    static void validateCheckpointPolicy(Path dir, Map<String, String> weightMap, boolean visionEnabled)
            throws IOException {
        for (String key : weightMap.keySet()) {
            if (isLmHead(key) && isScaleKey(key)) {
                throw new IOException(
                        "FP8 lm_head is not supported (" + key + "); expected BF16/FP16 lm_head");
            }
            if (isLmHeadWeight(key)) {
                // dtype checked lazily if needed; presence of sibling scale is the gate
                String base = key.substring(0, key.length() - ".weight".length());
                if (weightMap.containsKey(base + ".weight_scale_inv")
                        || weightMap.containsKey(base + ".weight_scale")
                        || weightMap.containsKey(base + ".scale")) {
                    throw new IOException(
                            "FP8 lm_head is not supported (" + key + "); expected BF16/FP16 lm_head");
                }
            }
            if (visionEnabled && isVisionKey(key) && isScaleKey(key)) {
                throw new IOException(
                        "FP8 vision-tower weights are not supported (" + key
                                + "). Use a checkpoint with dense vision weights, "
                                + "or load text-only.");
            }
        }
        // Warn if vision keys look FP8 even when VL disabled (text-only still OK).
        if (!visionEnabled) {
            for (String key : weightMap.keySet()) {
                if (isVisionKey(key) && isScaleKey(key)) {
                    logger.warn("Ignoring FP8 vision tensors in text-only load: {}", key);
                    break;
                }
            }
        }
        // Optional: read weight_block_size for logging
        Path config = dir.resolve("config.json");
        if (Files.isRegularFile(config)) {
            JsonNode root = new ObjectMapper().readTree(config.toFile());
            JsonNode qc = root.get("quantization_config");
            if (qc != null && qc.get("weight_block_size") != null) {
                logger.info("Qwen FP8 quantization_config.weight_block_size={}",
                        qc.get("weight_block_size"));
            }
        }
    }

    /**
     * HF keys that the FP8 installer owns (must not be force-fed into dense shells).
     */
    public static boolean isInstalledProjectionKey(String hfName) {
        if (hfName == null) {
            return false;
        }
        if (isScaleKey(hfName)) {
            // scales for installed linears only — residual never loads scales
            return hfName.contains(".self_attn.")
                    || hfName.contains(".linear_attn.")
                    || hfName.contains(".mlp.");
        }
        if (!hfName.endsWith(".weight")) {
            return false;
        }
        // Dense residual: in_proj_a / in_proj_b (no FP8 in official Qwen3.8-FP8)
        if (hfName.contains("linear_attn.in_proj_a.weight")
                || hfName.contains("linear_attn.in_proj_b.weight")
                || hfName.contains("linear_attn.in_proj_ba.weight")) {
            return false;
        }
        if (hfName.contains(".self_attn.q_proj.weight")
                || hfName.contains(".self_attn.k_proj.weight")
                || hfName.contains(".self_attn.v_proj.weight")
                || hfName.contains(".self_attn.o_proj.weight")) {
            return true;
        }
        if (hfName.contains(".linear_attn.in_proj_qkv.weight")
                || hfName.contains(".linear_attn.in_proj_z.weight")
                || hfName.contains(".linear_attn.out_proj.weight")) {
            return true;
        }
        return hfName.contains(".mlp.gate_proj.weight")
                || hfName.contains(".mlp.up_proj.weight")
                || hfName.contains(".mlp.down_proj.weight");
    }

    private static boolean isScaleKey(String name) {
        return name.contains("weight_scale") || name.endsWith(".scale") || name.contains(".scale.");
    }

    private static boolean isVisionKey(String name) {
        return name.contains("visual.") || name.contains(".visual.");
    }

    private static boolean isLmHead(String name) {
        return name.contains("lm_head");
    }

    private static boolean isLmHeadWeight(String name) {
        return name.equals("lm_head.weight")
                || name.equals("model.lm_head.weight")
                || name.endsWith(".lm_head.weight");
    }

    private static Tensor require(Map<String, Tensor> bank, String name) {
        Tensor t = bank.get(name);
        if (t == null) {
            throw new IllegalStateException("Missing tensor in bank: " + name);
        }
        return t;
    }

    private static Tensor optionalScale(Map<String, Tensor> bank, String base) {
        for (String suffix : new String[]{".weight_scale_inv", ".weight_scale", ".scale"}) {
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

    private record LinearJob(int layer, String hfBase, String smileWeightName) {}
}
