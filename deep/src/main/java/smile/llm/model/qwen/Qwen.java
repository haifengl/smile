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
package smile.llm.model.qwen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.deep.layer.ParameterInit;
import smile.deep.tensor.Device;
import smile.deep.tensor.Index;
import smile.deep.tensor.SafeTensors;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.GenerationListener;
import smile.llm.LanguageModel;
import smile.llm.Message;
import smile.llm.cache.KvCachePool;
import smile.llm.checkpoint.SafeTensorsLoaderThreads;
import smile.llm.attention.AttentionBackend;
import smile.llm.attention.AttentionBackends;
import smile.llm.engine.DecodeCudaGraph;
import smile.llm.engine.DecodeStepTiming;
import smile.llm.model.llama.Llama;
import smile.llm.parallel.ParallelConfig;
import smile.llm.parallel.ParallelState;
import smile.llm.parallel.TensorParallelGroup;
import smile.llm.parallel.TensorShardSpec;
import smile.torch.smile_torch_h;
import smile.util.AutoScope;

/**
 * Qwen3.5 hybrid text model (Gated DeltaNet + gated full attention).
 *
 * @author Haifeng Li
 */
public class Qwen implements LanguageModel, AutoCloseable, smile.llm.engine.ModelExecutor {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Qwen.class);
    /** Host-sync EOS check every N decode steps (always on last position). */
    private static final int EOS_CHECK_INTERVAL = 8;

    static final String family = "alibaba/qwen3.5";

    private static final Pattern HF_LAYER_WEIGHT = Pattern.compile(
            "^model\\.layers\\.(\\d+)\\.(self_attn|linear_attn|mlp|input_layernorm|post_attention_layernorm)\\.(.+)$");

    final String name;
    /** Rank-0 model (also {@code models[0]}). */
    final QwenModel model;
    /** One shard per TP rank; length 1 when tensor-parallel size is 1. */
    final QwenModel[] models;
    final TensorParallelGroup tpGroup;
    final Tokenizer tokenizer;
    final QwenModelArgs params;
    final QwenVisionArgs visionArgs;
    final QwenVlProcessor vlProcessor;
    /** Long-lived TP worker pool; null when {@code models.length == 1}. */
    private final ExecutorService tpExecutor;
    /**
     * When {@code true}, hybrid models may enable radix KV prefix reuse and
     * restore DeltaNet state via {@link #warmPrefix} on a hit. When {@code false}
     * (default until explicitly enabled), prefix reuse stays forced off for
     * hybrid safety.
     */
    private volatile boolean prefixReplayEnabled;
    /** Per-request mRoPE decode offset ({@code rope_delta}); cleared on finish/evict. */
    private final ConcurrentHashMap<Integer, Integer> ropeDeltaByRequest = new ConcurrentHashMap<>();
    /** Reused {@code [1,1]} token buffers per TP rank for batch-1 decode (outside scopes). */
    private Tensor[] decodeTokenBuf;

    /**
     * Constructor.
     *
     * @param name      model instance / checkpoint name.
     * @param model     decoder module (single-device).
     * @param tokenizer chat / completion tokenizer.
     * @param params    hyperparameters from the checkpoint.
     */
    public Qwen(String name, QwenModel model, Tokenizer tokenizer, QwenModelArgs params) {
        this(name, new QwenModel[]{model}, null, tokenizer, params, null, null);
    }

    /**
     * Tensor-parallel constructor.
     *
     * @param name      model instance / checkpoint name.
     * @param models    one decoder shard per TP rank.
     * @param tpGroup   tensor-parallel group, or {@code null} when {@code models.length == 1}.
     * @param tokenizer chat / completion tokenizer.
     * @param params    hyperparameters from the checkpoint.
     */
    public Qwen(String name, QwenModel[] models, TensorParallelGroup tpGroup,
                Tokenizer tokenizer, QwenModelArgs params) {
        this(name, models, tpGroup, tokenizer, params, null, null);
    }

    /**
     * Multimodal constructor.
     *
     * @param name        model instance / checkpoint name.
     * @param models      one decoder shard per TP rank.
     * @param tpGroup     tensor-parallel group, or {@code null}.
     * @param tokenizer   chat tokenizer.
     * @param params      text hyperparameters.
     * @param visionArgs  vision hyperparameters, or {@code null}.
     * @param vlProcessor multimodal processor, or {@code null}.
     */
    public Qwen(String name, QwenModel[] models, TensorParallelGroup tpGroup,
                Tokenizer tokenizer, QwenModelArgs params,
                QwenVisionArgs visionArgs, QwenVlProcessor vlProcessor) {
        if (models == null || models.length < 1) {
            throw new IllegalArgumentException("models required");
        }
        this.name = name;
        this.models = models;
        this.model = models[0];
        this.tpGroup = tpGroup;
        this.tokenizer = tokenizer;
        this.params = params;
        this.visionArgs = visionArgs;
        this.vlProcessor = vlProcessor;
        this.tpExecutor = models.length > 1
                ? Executors.newFixedThreadPool(models.length)
                : null;
    }

    /**
     * @return {@code true} when a vision tower is loaded.
     */
    public boolean isMultimodal() {
        return visionArgs != null && model.visual() != null;
    }

    /**
     * @return VL processor, or {@code null} for text-only.
     */
    public QwenVlProcessor vlProcessor() {
        return vlProcessor;
    }

    /**
     * @return vision args, or {@code null}.
     */
    public QwenVisionArgs visionArgs() {
        return visionArgs;
    }

    @Override
    public void close() {
        if (decodeTokenBuf != null) {
            for (Tensor t : decodeTokenBuf) {
                if (t != null) {
                    t.close();
                }
            }
            decodeTokenBuf = null;
        }
        for (QwenModel m : models) {
            m.closeDecodeGraph();
        }
        if (tpExecutor != null) {
            tpExecutor.shutdownNow();
        }
        if (tpGroup != null) {
            tpGroup.close();
        }
    }

    @Override
    public String toString() {
        return String.format("%s/%s", family, name);
    }

    @Override
    public String family() {
        return family;
    }

    /**
     * Enables Phase-1 hybrid prefix replay: radix KV hits are allowed and
     * {@link #warmPrefix} rebuilds DeltaNet state over the matched prefix.
     *
     * @param enabled {@code true} to allow safe hybrid prefix reuse.
     */
    public void setPrefixReplayEnabled(boolean enabled) {
        this.prefixReplayEnabled = enabled;
    }

    /** @return whether hybrid DeltaNet warm-prefix replay is enabled. */
    public boolean isPrefixReplayEnabled() {
        return prefixReplayEnabled;
    }

    /**
     * Enables or disables radix prefix reuse on every TP rank's KV pool.
     *
     * @param enabled {@code true} to match/insert prefixes across requests.
     */
    public void setPrefixReuseEnabled(boolean enabled) {
        // Hybrid DeltaNet + KV: reuse without restoring DeltaNet state is unsafe.
        // Phase 1: allow reuse when prefixReplayEnabled (warmPrefix restores state).
        if (enabled && model.deltaNetStatePool() != null && !prefixReplayEnabled) {
            logger.warn("Disabling radix prefix reuse for hybrid Qwen (DeltaNet state "
                    + "is not restored on prefix hit; enable smile.chat.kv-cache.hybrid-prefix-replay)");
            enabled = false;
        } else if (enabled && model.deltaNetStatePool() != null && prefixReplayEnabled) {
            logger.info("Hybrid Qwen prefix reuse enabled "
                    + "(KV pages shared; DeltaNet restored via warm-prefix replay)");
        }
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().setPrefixReuseEnabled(enabled);
            }
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int maxSeqLen() {
        return params.maxSeqLen();
    }

    @Override
    public int[] encodeChat(Message... dialog) {
        return encodeChat(dialog, null);
    }

    @Override
    public int[] encodeChat(Message[] dialog, smile.llm.ChatOptions options) {
        if (dialog != null) {
            for (Message m : dialog) {
                if (m != null && m.hasMedia()) {
                    if (vlProcessor == null) {
                        throw new IllegalStateException(
                                "Multimodal message requires a vision-capable Qwen checkpoint");
                    }
                    try {
                        return vlProcessor.process(dialog).inputIds();
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Failed to process multimodal dialog", e);
                    }
                }
            }
        }
        return tokenizer.encodeDialog(dialog, options);
    }

    /**
     * Processes a multimodal dialog (images/video) into tokens + vision tensors.
     *
     * @param dialog chat turns.
     * @return processed multimodal input.
     * @throws IOException if media cannot be loaded.
     */
    public QwenVlProcessor.ProcessedMultimodal processMultimodal(Message... dialog)
            throws IOException {
        if (vlProcessor == null) {
            throw new IllegalStateException("No VL processor (text-only checkpoint)");
        }
        return vlProcessor.process(dialog);
    }

    /**
     * Hyperparameters from the checkpoint.
     * @return model args.
     */
    public QwenModelArgs params() {
        return params;
    }

    /**
     * Builds a Qwen instance from a HuggingFace checkpoint directory.
     *
     * @param checkpointDir directory containing {@code config.json} and weights.
     * @param maxBatchSize  maximum batch size for inference.
     * @param maxSeqLen     maximum sequence length; {@code <= 0} uses the config value.
     * @param deviceId      CUDA device id, or negative for CPU.
     * @throws IOException if the checkpoint cannot be read.
     * @return a loaded Qwen model.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId)
            throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen, deviceId, 0, null,
                KvCachePool.DEFAULT_PAGE_SIZE, ParallelConfig.single(deviceId), 0);
    }

    /**
     * Builds a Qwen instance from a HuggingFace checkpoint directory.
     *
     * @param checkpointDir     directory containing {@code config.json} and weights.
     * @param maxBatchSize      maximum batch size for inference.
     * @param maxSeqLen         maximum sequence length; {@code <= 0} uses the config value.
     * @param deviceId          CUDA device id, or negative for CPU.
     * @param memFractionStatic static-region fraction of total GPU memory (SGLang-style);
     *                          {@code <=0} keeps test sizing.
     * @param kvCacheDtype      optional KV dtype override.
     * @throws IOException if the checkpoint cannot be read.
     * @return a loaded Qwen model.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                             double memFractionStatic, String kvCacheDtype) throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen, deviceId, memFractionStatic, kvCacheDtype,
                KvCachePool.DEFAULT_PAGE_SIZE, ParallelConfig.single(deviceId), 0);
    }

    /**
     * Builds a Qwen instance with optional tensor parallelism.
     *
     * @param checkpointDir     directory containing {@code config.json} and weights.
     * @param maxBatchSize      maximum batch size for inference.
     * @param maxSeqLen         maximum sequence length; {@code <= 0} uses the config value.
     * @param deviceId          CUDA device id, or negative for CPU.
     * @param memFractionStatic static-region fraction of total GPU memory (SGLang-style);
     *                          {@code <=0} keeps test sizing.
     * @param kvCacheDtype      optional KV dtype override.
     * @param parallel          {@link ParallelConfig#tensorParallel} for multi-GPU; {@code ppSize} must be 1.
     * @throws IOException if the checkpoint cannot be read.
     * @return a loaded Qwen model.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                             double memFractionStatic, String kvCacheDtype,
                             ParallelConfig parallel) throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen, deviceId, memFractionStatic, kvCacheDtype,
                KvCachePool.DEFAULT_PAGE_SIZE, parallel, 0);
    }

    /**
     * Builds a Qwen instance with optional tensor parallelism and KV page size.
     *
     * @param checkpointDir     directory containing {@code config.json} and weights.
     * @param maxBatchSize      maximum batch size for inference.
     * @param maxSeqLen         maximum sequence length; {@code <= 0} uses the config value.
     * @param deviceId          CUDA device id, or negative for CPU.
     * @param memFractionStatic static-region fraction of total GPU memory (SGLang-style);
     *                          {@code <=0} keeps test sizing.
     * @param kvCacheDtype      optional KV dtype override.
     * @param pageSize          tokens per radix / KV pool page ({@code >= 1}).
     * @param parallel          {@link ParallelConfig#tensorParallel} for multi-GPU; {@code ppSize} must be 1.
     * @throws IOException if the checkpoint cannot be read.
     * @return a loaded Qwen model.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                             double memFractionStatic, String kvCacheDtype, int pageSize,
                             ParallelConfig parallel) throws IOException {
        return build(checkpointDir, maxBatchSize, maxSeqLen, deviceId, memFractionStatic, kvCacheDtype,
                pageSize, parallel, 0);
    }

    /**
     * Builds a Qwen instance with optional tensor parallelism, KV page size, and
     * safetensors loader concurrency.
     *
     * @param checkpointDir      directory containing {@code config.json} and weights.
     * @param maxBatchSize       maximum batch size for inference.
     * @param maxSeqLen          maximum sequence length; {@code <= 0} uses the config value.
     * @param deviceId           CUDA device id, or negative for CPU.
     * @param memFractionStatic  static-region fraction of total GPU memory (SGLang-style);
     *                           {@code <=0} keeps test sizing.
     * @param kvCacheDtype       optional KV dtype override.
     * @param pageSize           tokens per radix / KV pool page ({@code >= 1}).
     * @param parallel           {@link ParallelConfig#tensorParallel} for multi-GPU; {@code ppSize} must be 1.
     * @param modelLoaderThreads safetensors loader threads; {@code 0} = auto
     *                           ({@link SafeTensorsLoaderThreads#resolve}).
     * @throws IOException if the checkpoint cannot be read.
     * @return a loaded Qwen model.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                             double memFractionStatic, String kvCacheDtype, int pageSize,
                             ParallelConfig parallel, int modelLoaderThreads) throws IOException {
        File dir = new File(checkpointDir);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Checkpoint directory not found: " + checkpointDir);
        }
        if (parallel == null) {
            parallel = ParallelConfig.single(deviceId);
        }
        final ParallelConfig parallelConfig = parallel;

        boolean cuda = deviceId >= 0 || (parallelConfig.isTensorParallel() && parallelConfig.devices()[0] >= 0);
        ScalarType computeDtype = ScalarType.Float;
        if (cuda) {
            var startTime = System.currentTimeMillis();
            Device.CUDA(parallelConfig.devices()[0]); // touch primary device
            computeDtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
            smile_torch_h.smile_set_default_dtype(computeDtype.code());
            var time = System.currentTimeMillis() - startTime;
            logger.info("Initialized CUDA (tpSize={}): {}.{} seconds",
                    parallelConfig.tpSize(), time / 1000, time % 1000);
        }

        var startTime = System.currentTimeMillis();
        Path configJson = Path.of(checkpointDir, "config.json");
        if (!Files.exists(configJson)) {
            throw new IllegalArgumentException("config.json not found in " + checkpointDir);
        }

        QwenModelArgs modelArgs = QwenModelArgs.fromHuggingFace(configJson.toString(), maxBatchSize, maxSeqLen);
        QwenVisionArgs visionArgs = QwenVisionArgs.fromHuggingFace(configJson.toString());
        if (visionArgs != null) {
            logger.info("Multimodal vision tower: depth={}, hidden={}, out={}, deepstack={}",
                    visionArgs.depth(), visionArgs.hiddenSize(), visionArgs.outHiddenSize(),
                    visionArgs.hasDeepStack());
            if (visionArgs.hasDeepStack()) {
                throw new IllegalArgumentException(
                        "DeepStack vision fusion is not supported; use Qwen3.8 (empty deepstack indexes)");
            }
        }
        if (maxSeqLen <= 0) {
            logger.info("max-seq-len auto-resolved to {} from model config (request override was {})",
                    modelArgs.maxSeqLen(), maxSeqLen);
        } else {
            logger.info("max-seq-len={} (explicit)", modelArgs.maxSeqLen());
        }
        ScalarType cacheDtype = resolveKvCacheDtype(kvCacheDtype, configJson, computeDtype);
        logger.info("KV cache dtype: {} (override={}, compute={})", cacheDtype, kvCacheDtype, computeDtype);

        Tokenizer tokenizer = Tokenizer.of(checkpointDir);
        tokenizer.requireChatSpecialsInVocab(modelArgs.vocabSize());
        // HF often pads embedding/lm_head (e.g. Qwen3.5: 248320 padded) above the
        // highest tokenizer id; that is expected. Warn only if the tokenizer can
        // emit ids the embedding table cannot hold.
        if (tokenizer.size() > modelArgs.vocabSize()) {
            logger.warn("Tokenizer size {} exceeds config vocab_size {}; embedding gather may OOB",
                    tokenizer.size(), modelArgs.vocabSize());
        } else if (tokenizer.size() > 0 && tokenizer.size() < modelArgs.vocabSize()) {
            logger.info("Tokenizer size {} < config vocab_size {} (HF padded embedding)",
                    tokenizer.size(), modelArgs.vocabSize());
        }

        TensorParallelGroup tpGroup = new TensorParallelGroup(parallelConfig);
        long tMap = System.currentTimeMillis();
        Map<String, String> weightMap = readWeightMap(dir);
        logger.info("Read weight map ({} tensors) in {} ms",
                weightMap.size(), System.currentTimeMillis() - tMap);

        // Phase A: construct empty shells on each rank's device (parallel by TP).
        QwenModel[] models = new QwenModel[parallelConfig.tpSize()];
        logger.info("Starting parallel TP rank construct (tpSize={})", parallelConfig.tpSize());
        long tConstruct = System.currentTimeMillis();
        if (parallelConfig.tpSize() == 1) {
            models[0] = constructRank(0, parallelConfig, modelArgs, visionArgs, cuda,
                    memFractionStatic, tpGroup);
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(parallelConfig.tpSize());
            try {
                List<Future<QwenModel>> futures = new ArrayList<>(parallelConfig.tpSize());
                for (int r = 0; r < parallelConfig.tpSize(); r++) {
                    final int rank = r;
                    futures.add(pool.submit(() -> constructRank(
                            rank, parallelConfig, modelArgs, visionArgs, cuda, memFractionStatic, tpGroup)));
                }
                for (int r = 0; r < parallelConfig.tpSize(); r++) {
                    models[r] = futures.get(r).get();
                }
            } catch (Exception e) {
                throw new IOException("Parallel TP rank construct failed", e);
            } finally {
                pool.shutdownNow();
            }
        }
        logger.info("Parallel TP rank construct finished in {} ms",
                System.currentTimeMillis() - tConstruct);

        // Phase B: each safetensors file once on CPU, fan-out to all ranks.
        long tLoad = System.currentTimeMillis();
        Device policyDevice = cuda
                ? Device.CUDA(parallelConfig.devices()[0])
                : Device.CPU();
        var quantPolicy = smile.llm.quant.QuantPolicy.resolve(
                Path.of(checkpointDir), policyDevice, null);
        if (quantPolicy.backend() == smile.llm.quant.WeightGemmBackend.FP8) {
            logger.info("Qwen FP8 weight install: format={} backend={} tpSize={}",
                    quantPolicy.format(), quantPolicy.backend(), parallelConfig.tpSize());
            Path ckpt = Path.of(checkpointDir);
            for (int r = 0; r < models.length; r++) {
                smile.llm.quant.QuantizedQwenFp8Loader.install(
                        models[r], ckpt, models[r].device(),
                        parallelConfig.tpSize(), r, computeDtype, modelLoaderThreads);
            }
            loadHuggingFaceWeightsShared(models, dir, weightMap, modelLoaderThreads,
                    /*skipInstalledFp8Linears=*/true);
        } else if (quantPolicy.backend() == smile.llm.quant.WeightGemmBackend.DENSE) {
            loadHuggingFaceWeightsShared(models, dir, weightMap, modelLoaderThreads, false);
        } else {
            throw new IllegalStateException(
                    "Quantized Qwen hybrid checkpoints only support native FP8 on Hopper+ "
                            + "(sm_90+); detected format=" + quantPolicy.format()
                            + " backend=" + quantPolicy.backend()
                            + ". GPTQ/AWQ/Marlin and NVFP4 are not supported for Qwen yet. "
                            + "Use a dense BF16/FP16 Qwen checkpoint, or Llama with "
                            + "native FP8 / GPTQ-AWQ (Marlin on Ampere).");
        }
        logger.info("Shared safetensors load finished in {} ms",
                System.currentTimeMillis() - tLoad);

        // Phase C: DeltaNet GPU swap + KV pool (after weights for mem-fraction).
        long tFinalize = System.currentTimeMillis();
        if (parallelConfig.tpSize() == 1) {
            finalizeRank(models[0], memFractionStatic, cacheDtype, pageSize);
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(parallelConfig.tpSize());
            try {
                List<Future<?>> futures = new ArrayList<>(parallelConfig.tpSize());
                for (int r = 0; r < parallelConfig.tpSize(); r++) {
                    final int rank = r;
                    futures.add(pool.submit(() -> finalizeRank(
                            models[rank], memFractionStatic, cacheDtype, pageSize)));
                }
                for (Future<?> f : futures) {
                    f.get();
                }
            } catch (Exception e) {
                throw new IOException("Parallel TP rank finalize failed", e);
            } finally {
                pool.shutdownNow();
            }
        }
        logger.info("TP rank finalize finished in {} ms",
                System.currentTimeMillis() - tFinalize);

        // Inference: drop requires_grad on all params so TP worker threads cannot
        // build autograd graphs if a NoGradGuard is missing (guard is thread-local).
        for (QwenModel m : models) {
            m.eval();
            m.setRequiresGrad(false);
        }

        var time = System.currentTimeMillis() - startTime;
        logger.info("Model {}: loaded in {}.{} seconds (tpSize={})",
                checkpointDir, time / 1000, time % 1000, parallelConfig.tpSize());
        QwenVlProcessor processor = null;
        if (visionArgs != null) {
            processor = QwenVlProcessor.fromCheckpoint(checkpointDir, visionArgs, tokenizer);
        }
        return new Qwen(dir.getName(), models, tpGroup, tokenizer, modelArgs, visionArgs, processor);
    }

    /**
     * Constructs one TP rank: empty module on the target device (no weight load / KV).
     */
    private static QwenModel constructRank(int rank, ParallelConfig parallel, QwenModelArgs modelArgs,
                                           QwenVisionArgs visionArgs, boolean cuda, double memFractionStatic,
                                           TensorParallelGroup tpGroup) {
        Device device = cuda ? Device.CUDA(parallel.devices()[rank]) : Device.CPU();
        TensorShardSpec shard = TensorShardSpec.forRank(
                parallel.tpSize(), rank,
                modelArgs.numHeads(), modelArgs.numKvHeads(), modelArgs.intermediateSize(),
                modelArgs.linearNumKeyHeads(), modelArgs.linearNumValueHeads());
        logger.info("tpRank={}: constructing on {} (layers={}, maxSeqLen={}, vision={})",
                rank, device, modelArgs.numLayers(), modelArgs.maxSeqLen(), visionArgs != null);

        DeltaNetStatePool statePool = null;
        if (modelArgs.numLinearAttentionLayers() > 0) {
            long t0 = System.currentTimeMillis();
            // Recurrent: float32 for fused CUDA kernel. Conv: compute dtype so
            // decode concat(convState, hidden) does not promote to float.
            ScalarType convDtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
            if (!cuda) {
                convDtype = ScalarType.Float;
            }
            statePool = new DeltaNetStatePool(
                    modelArgs.numLinearAttentionLayers(),
                    shard.linearNumValueHeads(),
                    modelArgs.linearKeyHeadDim(),
                    modelArgs.linearValueHeadDim(),
                    modelArgs.linearConvDim(shard),
                    modelArgs.linearConvKernelDim(),
                    modelArgs.maxBatchSize(),
                    memFractionStatic > 0 ? Device.CPU() : device,
                    ScalarType.Float,
                    convDtype);
            logger.info("tpRank={}: DeltaNetStatePool (staging) in {} ms",
                    rank, System.currentTimeMillis() - t0);
        }

        long tConstruct = System.currentTimeMillis();
        QwenModel model;
        try (var ignored = ParameterInit.uninitialized(device)) {
            model = new QwenModel(modelArgs, statePool, shard, tpGroup, visionArgs);
        }
        logger.info("tpRank={}: QwenModel construct in {} ms",
                rank, System.currentTimeMillis() - tConstruct);

        long tTo = System.currentTimeMillis();
        model.to(device);
        logger.info("tpRank={}: model.to({}) in {} ms",
                rank, device, System.currentTimeMillis() - tTo);
        model.eval();
        model.setRequiresGrad(false);
        return model;
    }

    /**
     * After weights: move DeltaNet state to GPU (when using mem-fraction) and allocate KV.
     */
    private static void finalizeRank(QwenModel model, double memFractionStatic,
                                     ScalarType cacheDtype, int pageSize) {
        int rank = model.shard() != null ? model.shard().tpRank() : 0;
        Device device = model.device();
        QwenModelArgs modelArgs = model.params();
        TensorShardSpec shard = model.shard();

        if (memFractionStatic > 0 && modelArgs.numLinearAttentionLayers() > 0) {
            long t0 = System.currentTimeMillis();
            // Recurrent stays float32 (in-place fused kernel). Conv matches model
            // compute dtype (bf16/fp16) so decode does not promote to float.
            ScalarType convDtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
            var gpuState = new DeltaNetStatePool(
                    modelArgs.numLinearAttentionLayers(),
                    shard.linearNumValueHeads(),
                    modelArgs.linearKeyHeadDim(),
                    modelArgs.linearValueHeadDim(),
                    modelArgs.linearConvDim(shard),
                    modelArgs.linearConvKernelDim(),
                    modelArgs.maxBatchSize(),
                    device,
                    ScalarType.Float,
                    convDtype);
            var previous = model.deltaNetStatePool;
            model.deltaNetStatePool = gpuState;
            for (var layer : model.layers) {
                if (layer.linearAttn != null) {
                    layer.linearAttn.setStatePool(gpuState);
                }
            }
            if (previous != null) previous.close();
            logger.info("tpRank={}: DeltaNetStatePool (GPU) in {} ms",
                    rank, System.currentTimeMillis() - t0);
        }
        if (modelArgs.numFullAttentionLayers() > 0) {
            long t0 = System.currentTimeMillis();
            device.emptyCache();
            KvCachePool pool = memFractionStatic > 0
                    ? KvCachePool.allocate(
                            modelArgs.kvCacheLayout(shard), device, cacheDtype, memFractionStatic,
                            pageSize)
                    : KvCachePool.forTesting(modelArgs.kvCacheLayout(shard), device);
            model.setKvCachePool(pool, false);
            logger.info("tpRank={}: KvCachePool allocate in {} ms",
                    rank, System.currentTimeMillis() - t0);
        }
    }

    static ScalarType resolveKvCacheDtype(String override, Path configJson, ScalarType fallback)
            throws IOException {
        if (override != null && !override.isBlank()) {
            return Llama.parseDtypeName(override);
        }
        if (Files.exists(configJson)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(configJson.toFile());
            JsonNode text = root.has("text_config") ? root.get("text_config") : root;
            if (text.has("torch_dtype") && !text.get("torch_dtype").asString().isBlank()) {
                return Llama.parseDtypeName(text.get("torch_dtype").asString());
            }
            if (root.has("torch_dtype") && !root.get("torch_dtype").asString().isBlank()) {
                return Llama.parseDtypeName(root.get("torch_dtype").asString());
            }
        }
        return fallback;
    }

    /**
     * Reads each safetensors shard once on CPU and fans weights out to every TP rank.
     * Loader concurrency is {@link SafeTensorsLoaderThreads#resolve}; per-rank
     * {@code loadStateDict} is serialized with a lock. Fan-out across ranks for one
     * shard runs in parallel with a deterministic stagger start index.
     *
     * @param skipInstalledFp8Linears when {@code true}, skip GEMM weights already
     *                                installed by {@link smile.llm.quant.QuantizedQwenFp8Loader}.
     */
    private static void loadHuggingFaceWeightsShared(QwenModel[] models, File dir,
                                                     Map<String, String> weightMap,
                                                     int modelLoaderThreads,
                                                     boolean skipInstalledFp8Linears)
            throws IOException {
        Map<String, List<String>> shardToKeys = new LinkedHashMap<>();
        for (var entry : weightMap.entrySet()) {
            if (skipInstalledFp8Linears
                    && smile.llm.quant.QuantizedQwenFp8Loader.isInstalledProjectionKey(entry.getKey())) {
                continue;
            }
            shardToKeys.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        List<String> shardFiles = new ArrayList<>(shardToKeys.keySet());
        Collections.sort(shardFiles);

        int tpSize = models.length;
        int threads = SafeTensorsLoaderThreads.resolve(modelLoaderThreads, shardFiles.size());
        logger.info("Safetensors loader threads={} (configured={}, shards={}, tpSize={}, skipFp8Linears={})",
                threads, modelLoaderThreads, shardFiles.size(), tpSize, skipInstalledFp8Linears);

        Object[] rankLocks = new Object[tpSize];
        for (int i = 0; i < tpSize; i++) {
            rankLocks[i] = new Object();
        }
        Set<String> loaded = ConcurrentHashMap.newKeySet();
        boolean needTiedLmHead = !weightMap.containsKey("lm_head.weight")
                && !weightMap.containsKey("model.lm_head.weight")
                && !weightMap.containsKey("language_model.lm_head.weight")
                && !weightMap.containsKey("model.language_model.lm_head.weight");

        Device loadDevice = Device.CPU();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
        try {
            List<Future<?>> futures = new ArrayList<>(shardFiles.size());
            for (int si = 0; si < shardFiles.size(); si++) {
                final int shardIndex = si;
                final String shardFile = shardFiles.get(si);
                final List<String> keys = shardToKeys.get(shardFile);
                futures.add(pool.submit(() -> {
                    try {
                        loadOneShardFanOut(models, dir, loadDevice, shardFile, keys, shardIndex,
                                rankLocks, loaded, needTiedLmHead);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    if (c instanceof RuntimeException re && re.getCause() instanceof IOException ioe) {
                        throw ioe;
                    }
                    throw new IOException("Safetensors shard load failed", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        logger.info("Loaded {} parameter names from HuggingFace safetensors (×{} ranks)",
                loaded.size(), tpSize);
        int layers = models[0].params().numLayers();
        int minExpected = skipInstalledFp8Linears
                ? Math.max(16, layers * 4)  // norms + DeltaNet residual + embeds
                : Math.max(32, layers * 8);
        if (loaded.size() < minExpected) {
            throw new IOException(String.format(
                    "Only loaded %d text parameters (expected at least %d for %d layers). "
                            + "Checkpoint keys are likely using an unsupported prefix; "
                            + "check remapHuggingFaceName for model.language_model.*",
                    loaded.size(), minExpected, layers));
        }
    }

    private static void loadOneShardFanOut(QwenModel[] models, File dir, Device loadDevice,
                                           String shardFile, List<String> keys, int shardIndex,
                                           Object[] rankLocks, Set<String> loaded,
                                           boolean needTiedLmHead)
            throws IOException {
        Path shardPath = Path.of(dir.getPath(), shardFile);
        logger.info("Loading safetensors shard: {} (index={})", shardFile, shardIndex);
        long tShard = System.currentTimeMillis();
        SafeTensors st = SafeTensors.read(shardPath.toString(), loadDevice, keys);
        long tRead = System.currentTimeMillis() - tShard;
        try {
            long tFan = System.currentTimeMillis();
            int tpSize = models.length;
            @SuppressWarnings("unchecked")
            CompletableFuture<Void>[] fanouts = new CompletableFuture[tpSize];
            for (int k = 0; k < tpSize; k++) {
                final int rank = (shardIndex + k) % tpSize;
                fanouts[k] = CompletableFuture.runAsync(() -> {
                    try {
                        applyShardToRank(models[rank], st, keys, rankLocks[rank], loaded,
                                needTiedLmHead);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            try {
                CompletableFuture.allOf(fanouts).join();
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                if (c instanceof RuntimeException re && re.getCause() instanceof IOException ioe) {
                    throw ioe;
                }
                throw new IOException("Fan-out failed for shard " + shardFile, e);
            }
            logger.info("Loaded safetensors shard: {} read={} ms fan-out={} ms",
                    shardFile, tRead, System.currentTimeMillis() - tFan);
        } finally {
            for (Tensor t : st.tensors().values()) {
                t.close();
            }
        }
    }

    private static void applyShardToRank(QwenModel model, SafeTensors st, List<String> keys,
                                         Object rankLock, Set<String> loaded,
                                         boolean needTiedLmHead)
            throws IOException {
        Device target = model.device();
        TensorShardSpec shard = model.shard();
        synchronized (rankLock) {
            Map<String, Tensor> stateDict = new HashMap<>();
            List<Tensor> owned = new ArrayList<>();
            try {
                for (String hfName : keys) {
                    Tensor src = st.tensors().get(hfName);
                    if (src == null) {
                        throw new IOException("Tensor '" + hfName + "' missing from safetensors");
                    }
                    String smileName = remapHuggingFaceName(hfName);
                    if (smileName == null) {
                        logger.debug("Skipping unrecognized HF weight: {}", hfName);
                        continue;
                    }
                    Tensor value = src;
                    if (smileName.contains("linear_attn.conv1d.weight") && src.dim() == 3) {
                        value = src.reshape(src.shape()[0], src.shape()[2]);
                        owned.add(value);
                    }
                    // Conv3d patch embed → linear: [O, C, T, P, P] → [O, C*T*P*P]
                    if (smileName.equals("visual.patch_embed.proj.weight") && src.dim() == 5) {
                        long out = src.shape()[0];
                        long flat = src.shape()[1] * src.shape()[2] * src.shape()[3] * src.shape()[4];
                        value = src.reshape(out, flat);
                        owned.add(value);
                    }
                    Tensor sliced = QwenWeightShard.shard(smileName, value, model.params(), shard);
                    if (sliced != value && sliced != src) {
                        owned.add(sliced);
                    }
                    Tensor onDevice = sliced.to(target);
                    if (onDevice != sliced) {
                        owned.add(onDevice);
                    }
                    Tensor contiguous = onDevice.contiguous();
                    if (contiguous != onDevice) {
                        owned.add(contiguous);
                    }
                    stateDict.put(smileName, contiguous);
                    loaded.add(smileName);
                }

                if (needTiedLmHead && !stateDict.containsKey("lm_head.weight")) {
                    for (String embKey : List.of(
                            "model.embed_tokens.weight",
                            "model.language_model.embed_tokens.weight",
                            "language_model.model.embed_tokens.weight")) {
                        if (st.tensors().containsKey(embKey)) {
                            Tensor onDevice = st.tensors().get(embKey).to(target);
                            owned.add(onDevice);
                            Tensor emb = onDevice.contiguous();
                            if (emb != onDevice) {
                                owned.add(emb);
                            }
                            stateDict.put("lm_head.weight", emb);
                            loaded.add("lm_head.weight");
                            break;
                        }
                    }
                }

                if (!stateDict.isEmpty()) {
                    model.loadStateDict(stateDict, false);
                }
            } finally {
                for (Tensor t : owned) {
                    t.close();
                }
            }
        }
    }

    static Map<String, String> readWeightMap(File dir) throws IOException {
        Path indexPath = Path.of(dir.getPath(), "model.safetensors.index.json");
        Map<String, String> map = new LinkedHashMap<>();
        if (Files.exists(indexPath)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(indexPath.toFile());
            JsonNode weightMap = root.get("weight_map");
            weightMap.properties().forEach(e -> map.put(e.getKey(), e.getValue().asString()));
            return map;
        }
        List<String> shards = getSafeTensorFiles(dir);
        if (shards.isEmpty()) {
            throw new IOException("No safetensors files found in " + dir);
        }
        for (String shard : shards) {
            for (String name : SafeTensors.listTensors(Path.of(dir.getPath(), shard).toString())) {
                map.put(name, shard);
            }
        }
        return map;
    }

    private static List<String> getSafeTensorFiles(File dir) {
        List<String> files = new ArrayList<>();
        File[] listed = dir.listFiles();
        if (listed == null) return files;
        for (var file : listed) {
            if (file.isFile() && file.getName().endsWith(".safetensors")) {
                files.add(file.getName());
            }
        }
        Collections.sort(files);
        return files;
    }

    /**
     * Maps a HuggingFace parameter name onto the registered SMILE module path.
     * Returns {@code null} for MTP / unrecognized tensors.
     *
     * <p>Normalizes common text-tower prefixes used by Qwen3.5 / Qwen3.8 checkpoints:
     * <ul>
     *   <li>{@code model.language_model.*} (multimodal {@code ForConditionalGeneration})</li>
     *   <li>{@code language_model.model.*} / {@code language_model.*}</li>
     *   <li>{@code model.*} (text-only)</li>
     *   <li>{@code model.visual.*} / {@code visual.*} (vision tower)</li>
     * </ul>
     */
    static String remapHuggingFaceName(String hfName) {
        if (hfName.startsWith("mtp.") || hfName.startsWith("vision_")) {
            return null;
        }

        String name = hfName;
        // Multimodal: vision tower under model.visual.*
        if (name.startsWith("model.visual.")) {
            name = "visual." + name.substring("model.visual.".length());
            return remapVisionName(name);
        }
        if (name.startsWith("visual.")) {
            return remapVisionName(name);
        }

        // Multimodal Qwen3.5/3.8: text weights live under model.language_model.*
        if (name.startsWith("model.language_model.")) {
            name = "model." + name.substring("model.language_model.".length());
        } else if (name.startsWith("language_model.")) {
            name = name.substring("language_model.".length());
        }

        if (name.equals("model.embed_tokens.weight")) {
            return "embed_tokens.weight";
        }
        if (name.equals("model.norm.weight")) {
            return "norm.weight";
        }
        if (name.equals("lm_head.weight") || name.equals("model.lm_head.weight")) {
            return "lm_head.weight";
        }

        Matcher m = HF_LAYER_WEIGHT.matcher(name);
        if (!m.matches()) {
            return null;
        }
        String layer = m.group(1);
        String component = m.group(2);
        String rest = m.group(3);
        String prefix = "layers." + layer + ".";

        return switch (component) {
            case "self_attn" -> prefix + "self_attn." + rest;
            case "linear_attn" -> {
                if (rest.equals("conv1d.weight")) {
                    yield prefix + "linear_attn.conv1d.weight";
                }
                yield prefix + "linear_attn." + rest;
            }
            case "mlp" -> switch (rest) {
                case "gate_proj.weight" -> prefix + "mlp.w1.weight";
                case "down_proj.weight" -> prefix + "mlp.w2.weight";
                case "up_proj.weight" -> prefix + "mlp.w3.weight";
                default -> null;
            };
            case "input_layernorm" -> prefix + "input_layernorm." + rest;
            case "post_attention_layernorm" -> prefix + "post_attention_layernorm." + rest;
            default -> null;
        };
    }

    /**
     * Maps {@code visual.*} HF names onto the registered vision module tree.
     * Conv3d {@code patch_embed.proj.weight} keeps that path; the loader reshapes
     * 5-D kernels to 2-D for the linear equivalent.
     */
    static String remapVisionName(String name) {
        // name starts with visual.
        if (name.startsWith("visual.patch_embed.proj.")) {
            return name; // visual.patch_embed.proj.{weight,bias}
        }
        if (name.equals("visual.pos_embed.weight")) {
            return "visual.pos_embed.weight";
        }
        if (name.startsWith("visual.merger.")) {
            return name;
        }
        // visual.blocks.N.{norm1,norm2,attn,mlp}.*
        if (name.startsWith("visual.blocks.")) {
            return name;
        }
        return null;
    }

    @Override
    public ChatCompletion generate(int[] prompt, int maxGenLen, double temperature,
                                   double topp, boolean logprobs, long seed,
                                   GenerationListener listener,
                                   java.util.function.BooleanSupplier cancelRequested) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }

        int promptLen = prompt.length;
        int vocabSize = params.vocabSize();
        for (int token : prompt) {
            if (token < 0 || token >= vocabSize) {
                throw new IllegalArgumentException(
                        "Prompt token id " + token + " out of range for vocab_size "
                                + vocabSize + " (im_start="
                                + tokenizer.specialToken("<|im_start|>")
                                + ", im_end=" + tokenizer.specialToken("<|im_end|>")
                                + "). This causes CUDA embedding gather OOB.");
            }
        }
        if (promptLen > params.maxSeqLen()) {
            throw new IllegalArgumentException("The prompt length is greater than max_seq_len");
        }
        // Cap prompt + max_tokens by max-seq-len.
        int maxAllowedGen = Math.max(0, params.maxSeqLen() - promptLen);
        if (maxGenLen > maxAllowedGen) {
            maxGenLen = maxAllowedGen;
        }
        if (maxGenLen < 0) {
            maxGenLen = 0;
        }

        if (seed != 0) {
            smile_torch_h.smile_manual_seed(seed);
        }

        try (var guard = Tensor.noGradGuard();
             var scope = new AutoScope()) {
            Tensor.push(scope);
            try {
            int desiredTotalLen = Math.min(params.maxSeqLen(), maxGenLen + promptLen);
            int prefixLen = 0;
            int totalLen = desiredTotalLen;
            final boolean usePrefix = model.kvCachePool() != null;
            if (usePrefix) {
                for (QwenModel m : models) {
                    if (m.kvCachePool() != null) {
                        prefixLen = m.kvCachePool().bindWithPrefix(prompt, desiredTotalLen);
                        totalLen = Math.min(totalLen, m.kvCachePool().requestCapacity());
                    }
                }
            }
            throwIfCancelled(cancelRequested);
            if (usePrefix && totalLen < promptLen) {
                throw new IllegalArgumentException(String.format(
                        "Prompt length %d exceeds free KV capacity %d",
                        promptLen, totalLen));
            }
            final int cachedPrefixTokens = prefixLen;
            if (prefixLen > 0 && promptLen < totalLen && promptLen > 0) {
                prefixLen = Math.min(prefixLen, promptLen - 1);
            }
            if (model.deltaNetStatePool() != null) {
                for (QwenModel m : models) {
                    if (m.deltaNetStatePool() != null) {
                        m.deltaNetStatePool().reset(1);
                    }
                }
            }
            if (listener != null) {
                listener.onInputTokens(promptLen);
                listener.onCachedInputTokens(usePrefix
                        ? Math.min(cachedPrefixTokens, promptLen)
                        : 0);
            }

            int pad = tokenizer.pad();
            var cpuOpts = new Tensor.Options()
                    .device(Device.CPU())
                    .dtype(ScalarType.Int64)
                    .requireGradients(false);
            Tensor tokensCpu = Tensor.zeros(cpuOpts, 1, totalLen).fill_(pad);
            try (var promptTensor = Tensor.of(prompt);
                 var row = Index.of(0);
                 var span = Index.slice(0, promptLen)) {
                tokensCpu.put_(promptTensor, row, span);
            }

            Tensor tokenLogprobs = null;
            if (logprobs) {
                var opts = new Tensor.Options().device(model.device()).requireGradients(false).dtype(ScalarType.Float);
                tokenLogprobs = Tensor.zeros(opts, 1, totalLen);
            }

            Tensor eosReached = Tensor.of(new boolean[1]);
            Tensor inputTextMask = tokensCpu.ne(pad);
            Tensor stopTokens = Tensor.of(tokenizer.stopTokens());

            Tensor[] tokens = new Tensor[models.length];
            Tensor[] eos = new Tensor[models.length];
            Tensor[] masks = new Tensor[models.length];
            Tensor[] stops = new Tensor[models.length];
            for (int r = 0; r < models.length; r++) {
                Device d = models[r].device();
                tokens[r] = tokensCpu.to(d);
                eos[r] = eosReached.to(d);
                masks[r] = inputTextMask.to(d);
                stops[r] = stopTokens.to(d);
            }
            tokensCpu.close();
            eosReached.close();
            inputTextMask.close();
            stopTokens.close();

            int prevPos = prefixLen;
            int chunkPos = promptLen;
            ExecutorService pool = tpExecutor;
            for (int curPos = promptLen; curPos < totalLen; curPos++) {
                throwIfCancelled(cancelRequested);
                AutoScope loopScope = new AutoScope();
                Tensor.push(loopScope);
                Tensor[] logits = null;
                try {
                    logits = forwardAll(tokens, prevPos, curPos, pool, logprobs);
                    for (Tensor l : logits) {
                        loopScope.add(l);
                    }

                    Tensor nextToken;
                    try (var last = Index.of(-1);
                         var tail = logits[0].get(Index.Colon, last)) {
                        nextToken = smile.llm.engine.Sampling.sampleNext(tail, temperature, topp);
                    }

                    try (var cur = Index.of(curPos);
                         var textMask = masks[0].get(Index.Colon, cur);
                         var currentTokens = tokens[0].get(Index.Colon, cur);
                         var merged = smile.llm.engine.Sampling.mergeWithPromptMask(
                                 textMask, currentTokens, nextToken)) {
                        nextToken.close();
                        nextToken = merged.detach();
                        for (int r = 0; r < models.length; r++) {
                            Tensor local = r == 0 ? nextToken : nextToken.to(models[r].device());
                            tokens[r].put_(local, Index.Colon, cur);
                            if (r != 0) local.close();
                        }
                    }

                    if (logprobs) {
                        try (var targetSpan = Index.slice(prevPos + 1, curPos + 1);
                             var targets = tokens[0].get(Index.Colon, targetSpan);
                             var transposed = logits[0].transpose(1, 2);
                             var entropy = Tensor.crossEntropy(transposed, targets, "none", pad).neg_();
                             var outSpan = Index.slice(prevPos + 1, curPos + 1)) {
                            tokenLogprobs.put_(entropy, Index.Colon, outSpan);
                        }
                    }

                    try (var cur = Index.of(curPos);
                         var text = masks[0].get(Index.Colon, cur).not();
                         var stop = nextToken.isin(stops[0]);
                         var textAndStop = text.and(stop)) {
                        eos[0].or_(textAndStop);
                        for (int r = 1; r < models.length; r++) {
                            try (Tensor e = eos[0].to(models[r].device())) {
                                smile.torch.Native.copy_(eos[r], e);
                            }
                        }
                    }

                    nextToken.close();
                    prevPos = curPos;
                    if (listener != null) {
                        listener.onGeneratedTokens(1);
                    }
                } finally {
                    Tensor.pop();
                }

                // Prefill is the activation peak; return cached blocks before decode.
                if (curPos == promptLen) {
                    for (QwenModel m : models) {
                        m.device().emptyCache();
                    }
                }

                // Defer GPU→CPU EOS sync: every N tokens and always on the last slot.
                boolean checkEos = (curPos - promptLen + 1) % EOS_CHECK_INTERVAL == 0
                        || curPos == totalLen - 1;
                boolean done = checkEos && eos[0].all();
                if (listener != null
                        && (curPos - chunkPos >= 20 || curPos == totalLen - 1 || done)) {
                    int end = done ? curPos : curPos + 1;
                    if (end > chunkPos) {
                        long[] longArray;
                        try (var row = Index.of(0);
                             var span = Index.slice(chunkPos, end);
                             var chunkTokens = tokens[0].get(row, span);
                             var cpuTokens = chunkTokens.to(Device.CPU())) {
                            longArray = cpuTokens.longArray();
                        }
                        var completion = Arrays.stream(longArray).mapToInt(x -> (int) x).toArray();
                        try {
                            var chunk = tokenizer.tryDecode(completion, true);
                            chunkPos = end;
                            if (!chunk.isEmpty()) {
                                listener.onText(chunk);
                            }
                        } catch (java.nio.charset.CharacterCodingException ex) {
                            logger.debug("Cannot decode a chunk", ex);
                        }
                    }
                }
                if (done) break;
            }

            long[] longArray;
            try (var cpuTokens = tokens[0].to(Device.CPU())) {
                longArray = cpuTokens.longArray();
            }
            float[] logprobArray = null;
            if (logprobs) {
                try (var cpuLogprobs = tokenLogprobs.to(Device.CPU())) {
                    logprobArray = cpuLogprobs.floatArray();
                }
            }

            int start = promptLen;
            var completion = Arrays.stream(longArray)
                    .skip(start)
                    .mapToInt(x -> (int) x)
                    .limit(maxGenLen)
                    .toArray();

            float[] probs = null;
            if (logprobs) {
                probs = Arrays.copyOfRange(logprobArray, start, start + maxGenLen);
            }

            boolean stop = false;
            for (var stopToken : tokenizer.stopTokens()) {
                for (int eosIdx = 0; eosIdx < completion.length; eosIdx++) {
                    if (completion[eosIdx] == stopToken) {
                        stop = true;
                        completion = Arrays.copyOf(completion, eosIdx);
                        if (logprobs) {
                            probs = Arrays.copyOf(probs, eosIdx);
                        }
                        break;
                    }
                }
            }
            var reason = stop ? FinishReason.stop : FinishReason.length;
            String decoded;
            try {
                decoded = tokenizer.tryDecode(completion, true);
            } catch (Exception e) {
                decoded = tokenizer.decode(completion);
            }
            ChatCompletion prediction = new ChatCompletion(name, decoded,
                    prompt, completion, reason, probs);

            if (usePrefix) {
                int[] sequenceToInsert = new int[promptLen + completion.length];
                System.arraycopy(prompt, 0, sequenceToInsert, 0, promptLen);
                System.arraycopy(completion, 0, sequenceToInsert, promptLen, completion.length);
                for (QwenModel m : models) {
                    if (m.kvCachePool() != null) {
                        m.kvCachePool().finishRequest(sequenceToInsert);
                    }
                }
            }
            return prediction;
            } finally {
                Tensor.pop();
            }
        } finally {
            int leakedScopes = Tensor.clearScopes();
            if (leakedScopes > 0) {
                logger.warn("Drained {} leftover Tensor AutoScope(s) after generate", leakedScopes);
            }
            for (QwenModel m : models) {
                if (m.kvCachePool() != null) {
                    m.kvCachePool().unbindRequests();
                }
                if (m.deltaNetStatePool() != null) {
                    m.deltaNetStatePool().unbind();
                }
                logCudaMemory(m, "before emptyCache");
                m.device().emptyCache();
                logCudaMemory(m, "after emptyCache");
            }
        }
    }

    /** Best-effort CUDA free/allocator log for leak diagnosis. */
    private static void logCudaMemory(QwenModel m, String when) {
        Device device = m.device();
        if (device == null || !device.isCUDA()) {
            return;
        }
        try {
            int idx = device.index();
            long[] mem = smile.torch.Native.cudaMemGetInfo(idx);
            long[] alloc = smile.torch.Native.cudaAllocatorStats(idx);
            logger.info("tpRank={}: {} freeMiB={} allocatedMiB={} reservedMiB={}",
                    m.tpRank(), when,
                    mem[0] / (1024 * 1024),
                    alloc[0] / (1024 * 1024),
                    alloc[1] / (1024 * 1024));
        } catch (RuntimeException e) {
            logger.debug("tpRank={}: cuda memory log failed at {}: {}",
                    m.tpRank(), when, e.toString());
        }
    }

    /**
     * Runs {@link QwenModel#forward} on every TP rank (in parallel when {@code tpSize > 1}).
     */
    private Tensor[] forwardAll(Tensor[] tokens, int prevPos, int curPos, ExecutorService pool) {
        return forwardAll(tokens, prevPos, curPos, pool, false);
    }

    /**
     * Runs {@link QwenModel#forward} on every TP rank (in parallel when {@code tpSize > 1}).
     *
     * @param allTokenLogits when {@code true}, score every position (needed for logprobs).
     */
    private Tensor[] forwardAll(Tensor[] tokens, int prevPos, int curPos, ExecutorService pool,
                                boolean allTokenLogits) {
        if (prevPos >= curPos) {
            throw new IllegalArgumentException(
                    "forwardAll requires prevPos < curPos, got " + prevPos + " >= " + curPos);
        }
        Tensor[] logits = new Tensor[models.length];
        if (models.length == 1) {
            try (var span = Index.slice(prevPos, curPos);
                 var window = tokens[0].get(Index.Colon, span)) {
                logits[0] = models[0].forward(window, prevPos, allTokenLogits);
            }
            return logits;
        }
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int r = 0; r < models.length; r++) {
            final int rank = r;
            futures.add(pool.submit(() -> {
                ParallelState.setCurrent(tpGroup.state(rank));
                // NoGradGuard is thread-local; the generate-thread guard does not
                // cover TP workers. Without this, requires_grad params build
                // autograd graphs (~1GiB+/request SavedVariable leak).
                try (var guard = Tensor.noGradGuard();
                     var span = Index.slice(prevPos, curPos);
                     var window = tokens[rank].get(Index.Colon, span)) {
                    return models[rank].forward(window, prevPos, allTokenLogits);
                } finally {
                    int depth = Tensor.scopeDepth();
                    if (depth > 0) {
                        logger.warn("tpRank={}: {} AutoScope(s) still pushed after forward "
                                        + "(possible activation leak)",
                                rank, depth);
                    }
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < models.length; r++) {
                logits[r] = futures.get(r).get();
            }
        } catch (Exception e) {
            for (Tensor l : logits) {
                if (l != null) {
                    l.close();
                }
            }
            throw new RuntimeException("TP forward failed", e);
        }
        return logits;
    }

    /**
     * Performs text completion for a prompt.
     *
     * @param prompt      text prompt.
     * @param maxGenLen   maximum number of new tokens to generate.
     * @param temperature temperature value for controlling randomness in sampling.
     * @param topp        top-p probability threshold for nucleus sampling.
     * @param logprobs    flag indicating whether to compute token log probabilities.
     * @param seed        optional RNG seed to sample deterministically.
     * @param listener    optional generation progress callback.
     * @return the generated text completion.
     */
    public ChatCompletion complete(String prompt, int maxGenLen, double temperature, double topp,
                                   boolean logprobs, long seed, GenerationListener listener) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        return generate(tokenizer.encode(prompt, false, false),
                maxGenLen, temperature, topp, logprobs, seed, listener);
    }

    @Override
    public ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature, double topp,
                               boolean logprobs, long seed, GenerationListener listener,
                               java.util.function.BooleanSupplier cancelRequested) {
        if (dialog == null) {
            throw new IllegalArgumentException("dialog must not be null");
        }
        return generate(tokenizer.encodeDialog(dialog),
                maxGenLen, temperature, topp, logprobs, seed, listener, cancelRequested);
    }

    private static void throwIfCancelled(java.util.function.BooleanSupplier cancelRequested) {
        if (cancelRequested != null && cancelRequested.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException("aborted");
        }
    }

    @Override
    public LanguageModel model() {
        return this;
    }

    @Override
    public smile.llm.cache.KvCachePool kvCachePool() {
        return model.kvCachePool();
    }

    @Override
    public int padToken() {
        return tokenizer.pad();
    }

    @Override
    public int[] stopTokens() {
        return tokenizer.stopTokens();
    }

    @Override
    public String decode(int[] tokens) {
        return tokenizer.decode(tokens);
    }

    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial)
            throws java.nio.charset.CharacterCodingException {
        return tokenizer.tryDecode(tokens, skipSpecial);
    }

    /**
     * Hybrid models must not reuse radix KV prefixes until DeltaNet state can
     * be restored; otherwise answers are numerically wrong. No-op when
     * {@link #prefixReplayEnabled} (Phase-1 warm-prefix path).
     */
    private void disablePrefixReuseForHybrid() {
        if (prefixReplayEnabled || model.deltaNetStatePool() == null) {
            return;
        }
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().setPrefixReuseEnabled(false);
            }
        }
    }

    @Override
    public int bind(int[] prompt, int totalCapacity) {
        disablePrefixReuseForHybrid();
        int id = -1;
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                int local = m.kvCachePool().bindRequest(prompt, totalCapacity);
                if (id < 0) {
                    id = local;
                } else if (local != id) {
                    throw new IllegalStateException(String.format(
                            "TP KV request id mismatch: rank0=%d other=%d (ranks must bind in lockstep)",
                            id, local));
                }
            }
        }
        if (id < 0) {
            throw new IllegalStateException("Qwen bind requires a KV cache pool");
        }
        for (QwenModel m : models) {
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().bindRequest(id);
            }
        }
        return id;
    }

    @Override
    public int prefixLen(int requestId) {
        KvCachePool pool = model.kvCachePool();
        return pool == null ? 0 : pool.matchedPrefixLen(requestId);
    }

    @Override
    public Tensor prefillMultimodal(int requestId,
                                    QwenVlProcessor.ProcessedMultimodal multimodal,
                                    int from, int to) {
        if (multimodal == null) {
            throw new IllegalArgumentException("multimodal required");
        }
        int[] prompt = multimodal.inputIds();
        if (from < 0 || to < from || to > prompt.length) {
            throw new IllegalArgumentException(
                    "invalid prefill range [" + from + ", " + to + ")");
        }
        if (from == to) {
            return null;
        }
        if (from != 0) {
            // Chunked multimodal with mid-prompt start would need partial mRoPE;
            // require full-window or start-at-zero for now.
            throw new UnsupportedOperationException(
                    "multimodal prefill must start at 0 (got from=" + from + ")");
        }
        if (visionArgs == null || model.visual() == null) {
            throw new IllegalStateException("vision tower not loaded");
        }
        ropeDeltaByRequest.put(requestId, multimodal.mrope().ropeDelta());

        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().activateStep(requestId);
            }
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().activateStep(requestId);
            }
        }

        logger.info("Multimodal prefill requestId={} range=[{}, {}) promptLen={} tpSize={} hasVision={}",
                requestId, from, to, prompt.length, models.length, multimodal.hasVision());

        try (var guard = Tensor.noGradGuard();
             var scope = new AutoScope()) {
            Tensor.push(scope);
            try {
                Device device = model.device();
                ScalarType dtype = Tensor.isBF16Supported() ? ScalarType.BFloat16 : ScalarType.Half;
                if (!device.isCUDA()) {
                    dtype = ScalarType.Float;
                }
                QwenVlProcessor.ProcessedMultimodal mm = multimodal.to(device, dtype);
                Tensor visionOut = null;
                if (mm.hasVision()) {
                    int[][] grids = concatGrids(mm.imageGridThw(), mm.videoGridThw());
                    long tVis = System.nanoTime();
                    logger.info("Vision tower forward requestId={} pixelRows={} media={}",
                            requestId, mm.pixelValues().shape()[0], grids.length);
                    visionOut = model.visual().forward(mm.pixelValues(), grids);
                    logger.info("Vision tower forward requestId={} done in {} ms tokens={}",
                            requestId, (System.nanoTime() - tVis) / 1_000_000L,
                            visionOut.shape()[0]);
                }
                try (Tensor tokenIds = Tensor.of(Arrays.copyOfRange(prompt, from, to))
                        .reshape(1, to - from).to(device);
                     Tensor textEmb = model.embedTokens(tokenIds)) {
                    Tensor embeds = textEmb;
                    if (visionOut != null) {
                        embeds = QwenModel.spliceVisionEmbeds(
                                textEmb, Arrays.copyOfRange(prompt, from, to), visionOut,
                                visionArgs.imageTokenId(), visionArgs.videoTokenId());
                        visionOut.close();
                    }
                    int[] posT = Arrays.copyOfRange(mm.mrope().t(), from, to);
                    int[] posH = Arrays.copyOfRange(mm.mrope().h(), from, to);
                    int[] posW = Arrays.copyOfRange(mm.mrope().w(), from, to);
                    try (PartialRotaryEncoding.CosSin mrope =
                                 InterleavedMRope.computeCosSin(
                                         params.rotaryDim(), params.ropeTheta(),
                                         visionArgs.mropeSection(), posT, posH, posW)) {
                        Tensor cos = mrope.cos().to(device);
                        Tensor sin = mrope.sin().to(device);
                        Tensor[] embedShards = new Tensor[models.length];
                        Tensor[] cosShards = new Tensor[models.length];
                        Tensor[] sinShards = new Tensor[models.length];
                        for (int r = 0; r < models.length; r++) {
                            Device dev = models[r].device();
                            embedShards[r] = r == 0 && embeds.device().equals(dev)
                                    ? embeds : embeds.to(dev);
                            cosShards[r] = r == 0 && cos.device().equals(dev) ? cos : cos.to(dev);
                            sinShards[r] = r == 0 && sin.device().equals(dev) ? sin : sin.to(dev);
                        }
                        long tLlm = System.nanoTime();
                        logger.info("LLM embed prefill requestId={} tokens={} tpSize={}",
                                requestId, to - from, models.length);
                        Tensor[] logitsArr = forwardEmbedsWindow(
                                embedShards, from, cosShards, sinShards, tpExecutor, false);
                        logger.info("LLM embed prefill requestId={} done in {} ms",
                                requestId, (System.nanoTime() - tLlm) / 1_000_000L);
                        for (int r = 0; r < models.length; r++) {
                            if (embedShards[r] != embeds) {
                                embedShards[r].close();
                            }
                            if (cosShards[r] != cos) {
                                cosShards[r].close();
                            }
                            if (sinShards[r] != sin) {
                                sinShards[r].close();
                            }
                        }
                        if (embeds != textEmb) {
                            embeds.close();
                        }
                        for (QwenModel m : models) {
                            if (m.deltaNetStatePool() != null) {
                                m.deltaNetStatePool().scatterActive();
                            }
                        }
                        if (to < prompt.length) {
                            for (Tensor l : logitsArr) {
                                if (l != null) {
                                    l.close();
                                }
                            }
                            return null;
                        }
                        try (var last = Index.of(-1);
                             Tensor selected = logitsArr[0].get(Index.Colon, last);
                             Tensor row = selected.reshape(1, -1)) {
                            Tensor out = row.copy();
                            out.promoteToParent();
                            for (Tensor l : logitsArr) {
                                if (l != null) {
                                    l.close();
                                }
                            }
                            return out;
                        }
                    }
                }
            } finally {
                Tensor.pop();
            }
        }
    }

    private static int[][] concatGrids(int[][] a, int[][] b) {
        int na = a == null ? 0 : a.length;
        int nb = b == null ? 0 : b.length;
        int[][] out = new int[na + nb][];
        int i = 0;
        if (a != null) {
            for (int[] g : a) {
                out[i++] = g;
            }
        }
        if (b != null) {
            for (int[] g : b) {
                out[i++] = g;
            }
        }
        return out;
    }

    @Override
    public Tensor prefill(int requestId, int[] prompt, int prefixLen) {
        return prefillChunk(requestId, prompt, prefixLen, prompt.length);
    }

    /**
     * Phase-1 hybrid prefix hit: run a full hybrid forward over
     * {@code prompt[0, prefixLen)} so DeltaNet recurrent/conv state matches a
     * cold prefill. Shared radix KV pages are rewritten with equivalent values.
     *
     * <p>Skipping full-attention residuals here would be incorrect — DeltaNet
     * inputs depend on the residual stream after attention blocks.
     */
    @Override
    public void warmPrefix(int requestId, int[] prompt, int prefixLen) {
        if (prefixLen <= 0 || prompt == null) {
            return;
        }
        if (model.deltaNetStatePool() == null) {
            return;
        }
        if (prefixLen > prompt.length) {
            throw new IllegalArgumentException(
                    "prefixLen " + prefixLen + " exceeds prompt length " + prompt.length);
        }
        logger.debug("warmPrefix requestId={} prefixLen={} (DeltaNet replay)", requestId, prefixLen);
        Tensor logits = prefillChunk(requestId, prompt, 0, prefixLen);
        if (logits != null) {
            logits.close();
        }
    }

    @Override
    public Tensor prefillChunk(int requestId, int[] prompt, int from, int to) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        if (from < 0 || to < from || to > prompt.length) {
            throw new IllegalArgumentException(
                    "invalid prefill range [" + from + ", " + to + ") for len " + prompt.length);
        }
        if (from == to) {
            return null;
        }
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().activateStep(requestId);
            }
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().activateStep(requestId);
            }
        }
        try (var guard = Tensor.noGradGuard();
             var scope = new AutoScope()) {
            Tensor.push(scope);
            try {
                int[] window = Arrays.copyOfRange(prompt, from, to);
                Tensor[] tokenShards = new Tensor[models.length];
                for (int r = 0; r < models.length; r++) {
                    tokenShards[r] = Tensor.of(window).reshape(1, window.length).to(models[r].device());
                }
                Tensor[] logits = forwardWindow(tokenShards, from, tpExecutor, false);
                for (Tensor t : tokenShards) {
                    t.close();
                }
                for (QwenModel m : models) {
                    if (m.deltaNetStatePool() != null) {
                        m.deltaNetStatePool().scatterActive();
                    }
                }
                if (to < prompt.length) {
                    for (Tensor l : logits) {
                        if (l != null) {
                            l.close();
                        }
                    }
                    return null;
                }
                try (var last = Index.of(-1);
                     Tensor selected = logits[0].get(Index.Colon, last);
                     Tensor row = selected.reshape(1, -1)) {
                    Tensor out = row.copy();
                    out.promoteToParent();
                    for (int r = 1; r < logits.length; r++) {
                        logits[r].close();
                    }
                    logits[0].close();
                    return out;
                }
            } finally {
                Tensor.pop();
            }
        }
    }

    /**
     * Runs {@link QwenModel#forwardEmbeds} on every TP rank for the same
     * spliced embedding window (replicated per device).
     */
    private Tensor[] forwardEmbedsWindow(Tensor[] embedShards, int startPos,
                                           Tensor[] cosShards, Tensor[] sinShards,
                                           ExecutorService pool, boolean allTokenLogits) {
        Tensor[] logits = new Tensor[models.length];
        if (models.length == 1) {
            logits[0] = models[0].forwardEmbeds(
                    embedShards[0], startPos, cosShards[0], sinShards[0], allTokenLogits);
            return logits;
        }
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int r = 0; r < models.length; r++) {
            final int rank = r;
            futures.add(pool.submit(() -> {
                ParallelState.setCurrent(tpGroup.state(rank));
                try (var guard = Tensor.noGradGuard()) {
                    return models[rank].forwardEmbeds(
                            embedShards[rank], startPos, cosShards[rank], sinShards[rank],
                            allTokenLogits);
                } finally {
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < models.length; r++) {
                logits[r] = futures.get(r).get();
            }
        } catch (Exception e) {
            for (Tensor l : logits) {
                if (l != null) {
                    l.close();
                }
            }
            throw new RuntimeException("TP multimodal embed prefill failed", e);
        }
        return logits;
    }

    /**
     * Runs {@link QwenModel#forward(Tensor, int, boolean)} on every TP rank for
     * a prefill window tensor that already holds shape {@code [1, chunkLen]}.
     *
     * <p>Unlike {@link #forwardAll}, does not re-slice by absolute prompt offsets
     * (the chunk tensor is already {@code prompt[from,to)}).
     */
    private Tensor[] forwardWindow(Tensor[] tokenShards, int startPos, ExecutorService pool,
                                   boolean allTokenLogits) {
        Tensor[] logits = new Tensor[models.length];
        if (models.length == 1) {
            logits[0] = models[0].forward(tokenShards[0], startPos, allTokenLogits);
            return logits;
        }
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int r = 0; r < models.length; r++) {
            final int rank = r;
            futures.add(pool.submit(() -> {
                ParallelState.setCurrent(tpGroup.state(rank));
                try (var guard = Tensor.noGradGuard()) {
                    return models[rank].forward(tokenShards[rank], startPos, allTokenLogits);
                } finally {
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < models.length; r++) {
                logits[r] = futures.get(r).get();
            }
        } catch (Exception e) {
            for (Tensor l : logits) {
                if (l != null) {
                    l.close();
                }
            }
            throw new RuntimeException("TP prefill forward failed", e);
        }
        return logits;
    }

    /**
     * Runs {@link QwenModel#forward(Tensor, int[])} on every TP rank for a
     * decode batch that already holds shape {@code [B, 1]} tokens.
     */
    private Tensor[] forwardAllDecode(Tensor[] tokens, int[] cachePositions, int[] ropePositions,
                                      ExecutorService pool) {
        Tensor[] logits = new Tensor[models.length];
        long t0 = System.nanoTime();
        boolean graph = DecodeCudaGraph.enabled() && cachePositions.length == 1;
        if (models.length == 1) {
            long rank0 = System.nanoTime();
            if (graph) {
                logits[0] = models[0].forwardDecodeGraph(tokens[0], cachePositions, ropePositions);
            } else {
                logits[0] = models[0].forward(tokens[0], cachePositions, ropePositions, false);
            }
            long rankNs = System.nanoTime() - rank0;
            recordForwardTiming(System.nanoTime() - t0, rankNs);
            return logits;
        }
        long[] rankNs = new long[models.length];
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int r = 0; r < models.length; r++) {
            final int rank = r;
            futures.add(pool.submit(() -> {
                long rankStart = System.nanoTime();
                ParallelState.setCurrent(tpGroup.state(rank));
                try (var guard = Tensor.noGradGuard()) {
                    if (graph) {
                        return models[rank].forwardDecodeGraph(
                                tokens[rank], cachePositions, ropePositions);
                    }
                    return models[rank].forward(tokens[rank], cachePositions, ropePositions, false);
                } finally {
                    rankNs[rank] = System.nanoTime() - rankStart;
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < models.length; r++) {
                logits[r] = futures.get(r).get();
            }
        } catch (Exception e) {
            for (Tensor l : logits) {
                if (l != null) {
                    l.close();
                }
            }
            throw new RuntimeException("TP ragged decode forward failed", e);
        }
        long slowest = 0L;
        for (long ns : rankNs) {
            slowest = Math.max(slowest, ns);
        }
        recordForwardTiming(System.nanoTime() - t0, slowest);
        return logits;
    }

    private static void recordForwardTiming(long wallNs, long slowestRankNs) {
        DecodeStepTiming timing = DecodeStepTiming.current();
        if (timing != null) {
            timing.forwardNs = wallNs;
            timing.slowestRankNs = slowestRankNs;
            timing.tpBarrierNs = Math.max(0L, wallNs - slowestRankNs);
        }
    }

    /**
     * Fills or allocates per-rank {@code [1,1]} int64 token tensors for batch-1 decode.
     */
    private Tensor[] decodeTokenShards(long token) {
        if (decodeTokenBuf == null) {
            decodeTokenBuf = new Tensor[models.length];
        }
        Tensor[] shards = new Tensor[models.length];
        for (int r = 0; r < models.length; r++) {
            Device device = models[r].device();
            if (decodeTokenBuf[r] == null) {
                var opts = new Tensor.Options().device(device).dtype(ScalarType.Int64);
                decodeTokenBuf[r] = Tensor.zeros(opts, 1, 1);
                decodeTokenBuf[r].detachFromScopes();
            }
            decodeTokenBuf[r].put_(token, 0, 0);
            shards[r] = decodeTokenBuf[r];
        }
        return shards;
    }

    /** Last-row logits {@code [B, V]}; copies so the result outlives closed views. */
    private static Tensor logitsRowFromDecodeOutput(Tensor[] logits, int batch) {
        boolean persistent = DecodeCudaGraph.persistentLogits();
        try (var last = Index.of(-1);
             Tensor selected = logits[0].get(Index.Colon, last);
             Tensor row = selected.reshape(batch, -1)) {
            Tensor out = row.copy();
            out.promoteToParent();
            if (!persistent) {
                for (Tensor l : logits) {
                    if (l != null) {
                        l.close();
                    }
                }
            }
            return out;
        } finally {
            DecodeCudaGraph.markPersistentLogits(false);
        }
    }

    @Override
    public Tensor decodeStep(int[] requestIds, int[] lastTokens, int[] positions) {
        if (requestIds == null || lastTokens == null || positions == null) {
            throw new IllegalArgumentException("decodeStep args must not be null");
        }
        int b = requestIds.length;
        if (lastTokens.length != b || positions.length != b || b == 0) {
            throw new IllegalArgumentException("decodeStep batch sizes must match and be non-empty");
        }
        int[] ropePos = Arrays.copyOf(positions, b);
        for (int i = 0; i < b; i++) {
            Integer delta = ropeDeltaByRequest.get(requestIds[i]);
            if (delta != null) {
                ropePos[i] += delta;
            }
        }

        // FlashInfer: one forward over mixed positions (ragged CSR + per-row RoPE).
        if (AttentionBackends.current() == AttentionBackend.FLASHINFER) {
            return runDecodeStep(requestIds, lastTokens, positions, ropePos);
        }

        // torch_native: cohort by cache position (existing path below uses forwardAllDecode with equal planes)
        // Fall through — read remaining original method...
        return decodeStepTorchNative(requestIds, lastTokens, positions, ropePos);
    }

    private Tensor runDecodeStep(int[] requestIds, int[] lastTokens, int[] positions,
                                   int[] ropePos) {
        int b = requestIds.length;
        long tPrep = System.nanoTime();
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().activateStep(requestIds);
            }
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().activateStep(requestIds);
            }
        }
        DecodeStepTiming timing = DecodeStepTiming.current();
        if (timing != null) {
            timing.prepNs = System.nanoTime() - tPrep;
        }
        try (var guard = Tensor.noGradGuard();
             var scope = new AutoScope()) {
            Tensor.push(scope);
            try {
                Tensor[] tokenShards;
                if (b == 1) {
                    tokenShards = decodeTokenShards(lastTokens[0]);
                } else {
                    long[] toks = new long[b];
                    for (int i = 0; i < b; i++) {
                        toks[i] = lastTokens[i];
                    }
                    tokenShards = new Tensor[models.length];
                    for (int r = 0; r < models.length; r++) {
                        tokenShards[r] = Tensor.of(toks).reshape(b, 1).to(models[r].device());
                    }
                }
                Tensor[] logits = forwardAllDecode(tokenShards, positions, ropePos, tpExecutor);
                if (b != 1) {
                    for (Tensor t : tokenShards) {
                        t.close();
                    }
                }
                for (QwenModel m : models) {
                    if (m.deltaNetStatePool() != null) {
                        m.deltaNetStatePool().scatterActive();
                    }
                }
                long tLogits = System.nanoTime();
                Tensor out = logitsRowFromDecodeOutput(logits, b);
                if (timing != null) {
                    timing.logitsNs = System.nanoTime() - tLogits;
                }
                return out;
            } finally {
                Tensor.pop();
            }
        }
    }

    private Tensor decodeStepTorchNative(int[] requestIds, int[] lastTokens, int[] positions,
                                         int[] ropePos) {
        return runDecodeStep(requestIds, lastTokens, positions, ropePos);
    }

    @Override
    public void finish(int requestId, int[] sequenceTokens) {
        ropeDeltaByRequest.remove(requestId);
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().finishRequest(requestId, sequenceTokens);
            }
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().unbindRequest(requestId);
            }
        }
    }

    @Override
    public void evict(int requestId) {
        ropeDeltaByRequest.remove(requestId);
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().unbindRequest(requestId);
            }
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().unbindRequest(requestId);
            }
        }
    }
}
