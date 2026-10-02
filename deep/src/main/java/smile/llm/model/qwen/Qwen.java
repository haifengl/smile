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
import smile.llm.cache.KvCacheLayout;
import smile.llm.cache.KvCachePool;
import smile.llm.checkpoint.SafeTensorsLoaderThreads;
import smile.llm.attention.AttentionBackend;
import smile.llm.attention.AttentionBackends;
import smile.llm.engine.DecodeCudaGraph;
import smile.llm.engine.VerifyCudaGraph;
import smile.llm.engine.DecodeForwardProfile;
import smile.llm.engine.DecodeStepTiming;
import smile.llm.engine.Sampling;
import smile.llm.engine.SpeculativeDecoding;
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

    /**
     * Kill switch for the checkpoint-replay verify-window fix: on a partial
     * MTP accept, restore a per-position DeltaNet checkpoint retained during
     * the primary verify forward instead of a second full-window forward.
     * Default on (validated 2026-10-01: removes the ~26 ms second forward per
     * partial accept, +17% tok/s over plain decode); set
     * {@code SMILE_MTP_VERIFY_CHECKPOINT_REPLAY=0} to restore the second-forward path.
     */
    private static final boolean MTP_VERIFY_CHECKPOINT_REPLAY =
            !"0".equals(System.getenv("SMILE_MTP_VERIFY_CHECKPOINT_REPLAY"));

    /**
     * Kill switch for the history-aware MTP draft head (default on). The
     * head's attention layer must see the request's whole prefix, exactly like
     * vLLM's Qwen3.5 MTP proposer (prompt positions are absorbed into a
     * persistent MTP KV cache during prefill; accepted verify rows are
     * absorbed on the next draft). {@code SMILE_MTP_HISTORY=0} restores the
     * legacy per-round, context-free draft head for A/B comparison.
     */
    private static final boolean MTP_HISTORY = !"0".equals(System.getenv("SMILE_MTP_HISTORY"));

    private static final Pattern HF_LAYER_WEIGHT = Pattern.compile(
            "^model\\.layers\\.(\\d+)\\.(self_attn|linear_attn|mlp|input_layernorm|post_attention_layernorm)\\.(.+)$");
    private static final Pattern HF_MTP_LAYER_WEIGHT = Pattern.compile(
            "^mtp\\.layers\\.(\\d+)\\.(self_attn|mlp|input_layernorm|post_attention_layernorm)\\.(.+)$");

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
     * When {@code true} and MTP weights are loaded, offline {@link #generate} and
     * engine {@link #speculateStep} use native MTP draft/verify.
     */
    private volatile boolean speculativeEnabled = true;
    /** Override draft depth; {@code <= 0} uses {@link QwenModelArgs#defaultNumSpeculativeTokens()}. */
    private volatile int numSpeculativeTokensOverride;
    /** Speculative rounds completed (for accept-rate metrics). */
    private final java.util.concurrent.atomic.AtomicLong speculativeRounds =
            new java.util.concurrent.atomic.AtomicLong();
    /** Draft tokens proposed across speculative rounds. */
    private final java.util.concurrent.atomic.AtomicLong speculativeDraftsProposed =
            new java.util.concurrent.atomic.AtomicLong();
    /** Draft tokens accepted across speculative rounds. */
    private final java.util.concurrent.atomic.AtomicLong speculativeDraftsAccepted =
            new java.util.concurrent.atomic.AtomicLong();
    /**
     * Target verify forwards across speculative rounds (window verify counts as 1;
     * DeltaNet restore/replay is excluded).
     */
    private final java.util.concurrent.atomic.AtomicLong speculativeTargetForwards =
            new java.util.concurrent.atomic.AtomicLong();
    /** Wall-clock nanos spent in {@link #draftGreedy} across online speculative rounds. */
    private final java.util.concurrent.atomic.AtomicLong speculativeDraftNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /** Wall-clock nanos spent in the primary verify forward + sampling. */
    private final java.util.concurrent.atomic.AtomicLong speculativeVerifyNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /** Wall-clock nanos spent in the DeltaNet-replay forward (partial accept only). */
    private final java.util.concurrent.atomic.AtomicLong speculativeReplayNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /**
     * Wall-clock nanos spent in per-round bookkeeping outside the forwards
     * themselves: {@code activatePools}, DeltaNet checkpoint save/restore,
     * {@code truncateKv} (FlashInfer CSR rebuild + decode-graph invalidate),
     * and MTP KV pool {@code beginRound}/{@code endRound} bind/unbind.
     */
    private final java.util.concurrent.atomic.AtomicLong speculativeBookkeepingNanos =
            new java.util.concurrent.atomic.AtomicLong();
    /**
     * Max-across-ranks phase breakdown for the most recent primary verify
     * forward ({@code SMILE_DECODE_PROFILE=1} only); {@code null} otherwise,
     * or when the round replayed a captured verify CUDA graph (replay bypasses
     * every instrumented Java call site, so no phase breakdown is available
     * for that round — only eager/capture rounds populate this).
     */
    private volatile DecodeForwardProfile.Snapshot lastVerifyProfile;

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
     * Returns whether a vision tower is loaded.
     *
     * @return {@code true} when a vision tower is loaded.
     */
    public boolean isMultimodal() {
        return visionArgs != null && model.visual() != null;
    }

    /**
     * Returns the VL processor.
     *
     * @return VL processor, or {@code null} for text-only.
     */
    public QwenVlProcessor vlProcessor() {
        return vlProcessor;
    }

    /**
     * Returns the vision args.
     *
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
            m.closeVerifyGraph();
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

    /**
     * Returns whether hybrid DeltaNet warm-prefix replay is enabled.
     *
     * @return whether hybrid DeltaNet warm-prefix replay is enabled.
     */
    public boolean isPrefixReplayEnabled() {
        return prefixReplayEnabled;
    }

    /**
     * Enables or disables native MTP speculative decoding when MTP weights are loaded.
     *
     * @param enabled {@code true} to use draft/verify (default when MTP is present).
     */
    public void setSpeculativeEnabled(boolean enabled) {
        this.speculativeEnabled = enabled;
        if (enabled) {
            ensureMtpHistoryPools();
        }
    }

    /**
     * Returns whether MTP speculation is enabled.
     *
     * @return whether MTP speculation is enabled.
     */
    public boolean isSpeculativeEnabled() {
        return speculativeEnabled && model.mtp() != null;
    }

    /**
     * Sets draft depth override ({@code <= 0} restores the HF/config default).
     *
     * @param n draft token count, capped at {@link QwenModelArgs#MAX_SPECULATIVE_TOKENS}.
     */
    public void setNumSpeculativeTokens(int n) {
        this.numSpeculativeTokensOverride = n;
    }

    /**
     * Returns the resolved draft depth when speculation is active.
     *
     * @return resolved draft depth when speculation is active, else {@code 0}.
     */
    public int numSpeculativeTokens() {
        if (!isSpeculativeEnabled()) {
            return 0;
        }
        return params.resolveNumSpeculativeTokens(numSpeculativeTokensOverride);
    }

    /**
     * Draft-token accept rate over completed speculative rounds
     * ({@code accepted / proposed}), or {@code 0} when none yet.
     *
     * @return speculative accept rate.
     */
    public double speculativeAcceptRate() {
        long proposed = speculativeDraftsProposed.get();
        return proposed == 0 ? 0.0
                : (double) speculativeDraftsAccepted.get() / (double) proposed;
    }

    /**
     * Mean accepted draft depth per speculative round ({@code 0} when none).
     *
     * @return mean accepted draft depth.
     */
    public double speculativeMeanAcceptedDepth() {
        long rounds = speculativeRounds.get();
        return rounds == 0 ? 0.0
                : (double) speculativeDraftsAccepted.get() / (double) rounds;
    }

    /**
     * Mean target verify forwards per speculative round (window verify → {@code 1}).
     *
     * @return average, or {@code 0} when no rounds yet.
     */
    public double speculativeMeanTargetForwardsPerRound() {
        long rounds = speculativeRounds.get();
        return rounds == 0 ? 0.0
                : (double) speculativeTargetForwards.get() / (double) rounds;
    }

    /**
     * Mean online-round wall-clock breakdown in milliseconds:
     * {@code {draftMs, verifyMs, replayMs, bookkeepingMs}}, or all zero
     * before any online round.
     *
     * @return mean per-round timing breakdown in milliseconds.
     */
    public double[] speculativeMeanRoundTimingMs() {
        long rounds = speculativeRounds.get();
        if (rounds == 0) {
            return new double[]{0.0, 0.0, 0.0, 0.0};
        }
        double toMs = 1.0 / (1_000_000.0 * rounds);
        return new double[]{
                speculativeDraftNanos.get() * toMs,
                speculativeVerifyNanos.get() * toMs,
                speculativeReplayNanos.get() * toMs,
                speculativeBookkeepingNanos.get() * toMs,
        };
    }

    /** Resets speculative accept-rate and round-timing counters. */
    public void resetSpeculativeMetrics() {
        speculativeRounds.set(0);
        speculativeDraftsProposed.set(0);
        speculativeDraftsAccepted.set(0);
        speculativeTargetForwards.set(0);
        speculativeDraftNanos.set(0);
        speculativeVerifyNanos.set(0);
        speculativeReplayNanos.set(0);
        speculativeBookkeepingNanos.set(0);
    }

    private void recordSpeculativeRound(int drafts, int acceptedDrafts) {
        speculativeRounds.incrementAndGet();
        speculativeDraftsProposed.addAndGet(drafts);
        speculativeDraftsAccepted.addAndGet(acceptedDrafts);
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
        return build(checkpointDir, maxBatchSize, maxSeqLen, deviceId, memFractionStatic, kvCacheDtype,
                pageSize, parallel, modelLoaderThreads, 0, 0);
    }

    /**
     * Like the overload above, additionally reserving DeltaNet checkpoint memory for batched MTP
     * speculation <em>before</em> the KV pool is sized, so the KV budget accounts for it and a large
     * speculating cohort never has to fall back to plain decode for lack of memory.
     *
     * @param speculativeRows  rows (concurrently speculating requests) to provision; {@code 0} reserves nothing.
     * @param speculativeDepth draft depth to provision for; {@code <= 0} uses the model default.
     * @throws IOException if the checkpoint cannot be read.
     * @return a loaded Qwen model.
     */
    public static Qwen build(String checkpointDir, int maxBatchSize, int maxSeqLen, byte deviceId,
                             double memFractionStatic, String kvCacheDtype, int pageSize,
                             ParallelConfig parallel, int modelLoaderThreads,
                             int speculativeRows, int speculativeDepth) throws IOException {
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
            finalizeRank(models[0], memFractionStatic, cacheDtype, pageSize, speculativeRows, speculativeDepth);
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(parallelConfig.tpSize());
            try {
                List<Future<?>> futures = new ArrayList<>(parallelConfig.tpSize());
                for (int r = 0; r < parallelConfig.tpSize(); r++) {
                    final int rank = r;
                    futures.add(pool.submit(() -> finalizeRank(
                            models[rank], memFractionStatic, cacheDtype, pageSize,
                            speculativeRows, speculativeDepth)));
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
                                     ScalarType cacheDtype, int pageSize,
                                     int speculativeRows, int speculativeDepth) {
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
            int depth = modelArgs.resolveNumSpeculativeTokens(speculativeDepth);
            if (speculativeRows > 0 && depth > 0 && model.mtp() != null) {
                // Reserved before the KV pool is sized so the static KV budget (total*fraction - used)
                // already excludes it.
                int rows = Math.min(speculativeRows, modelArgs.maxBatchSize());
                gpuState.ensureSpeculativeCheckpointRange(1, depth, rows);
                logger.info("tpRank={}: reserved {} MiB of DeltaNet checkpoints for {} speculating rows (depth {})",
                        rank, gpuState.speculativeCheckpointBytes(depth, rows) >> 20, rows, depth);
            }
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
        if (model.mtp() != null) {
            long t0 = System.currentTimeMillis();
            KvCacheLayout mtpLayout = model.mtp().kvCacheLayout();
            KvCachePool mtpPool = memFractionStatic > 0
                    ? KvCachePool.forMtp(mtpLayout, device, cacheDtype, pageSize)
                    : KvCachePool.forTesting(mtpLayout, device);
            model.mtp().setKvCachePool(mtpPool, false);
            logger.info("tpRank={}: MTP KvCachePool allocate in {} ms",
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
        if (hfName.startsWith("vision_")) {
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

        // Native MTP draft head (Qwen3.5/3.8).
        if (name.startsWith("mtp.")) {
            return remapMtpName(name);
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
     * Maps {@code mtp.*} HF names onto the registered MTP module tree.
     */
    static String remapMtpName(String name) {
        // name starts with mtp.
        if (name.equals("mtp.fc.weight")) {
            return "mtp.fc.weight";
        }
        if (name.equals("mtp.norm.weight")) {
            return "mtp.norm.weight";
        }
        if (name.equals("mtp.pre_fc_norm_hidden.weight")) {
            return "mtp.pre_fc_norm_hidden.weight";
        }
        if (name.equals("mtp.pre_fc_norm_embedding.weight")) {
            return "mtp.pre_fc_norm_embedding.weight";
        }
        Matcher m = HF_MTP_LAYER_WEIGHT.matcher(name);
        if (!m.matches()) {
            return null;
        }
        String layer = m.group(1);
        String component = m.group(2);
        String rest = m.group(3);
        String prefix = "mtp.layers." + layer + ".";
        return switch (component) {
            case "self_attn" -> prefix + "self_attn." + rest;
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
            int curPos = promptLen;
            while (curPos < totalLen) {
                throwIfCancelled(cancelRequested);

                int nSpec = numSpeculativeTokens();
                int maxDrafts = Math.min(nSpec, totalLen - curPos - 1);
                if (maxDrafts >= 1 && curPos > promptLen
                        && models[0].lastPreNormHidden() != null) {
                    AutoScope loopScope = new AutoScope();
                    Tensor.push(loopScope);
                    int written;
                    try {
                        written = speculateRoundOffline(
                                tokens, curPos, maxDrafts, temperature, topp, pool);
                    } finally {
                        Tensor.pop();
                    }
                    // EOS / streaming over the newly written span.
                    int end = Math.min(curPos + written, totalLen);
                    for (int p = curPos; p < end; p++) {
                        try (var col = Index.of(p);
                             Tensor cell = tokens[0].get(Index.Colon, col);
                             Tensor stop = cell.isin(stops[0]);
                             var text = masks[0].get(Index.Colon, col).not();
                             var textAndStop = text.and(stop)) {
                            eos[0].or_(textAndStop);
                        }
                    }
                    for (int r = 1; r < models.length; r++) {
                        try (Tensor e = eos[0].to(models[r].device())) {
                            smile.torch.Native.copy_(eos[r], e);
                        }
                    }
                    if (listener != null) {
                        listener.onGeneratedTokens(written);
                    }
                    prevPos = end - 1;
                    curPos = end;
                    boolean checkEos = true;
                    boolean done = eos[0].all() || curPos >= totalLen;
                    if (listener != null
                            && (curPos - chunkPos >= 20 || curPos >= totalLen || done)) {
                        int streamEnd = done ? Math.max(chunkPos, curPos - 1) : curPos;
                        if (streamEnd > chunkPos) {
                            long[] longArray;
                            try (var row = Index.of(0);
                                 var span = Index.slice(chunkPos, streamEnd);
                                 var chunkTokens = tokens[0].get(row, span);
                                 var cpuTokens = chunkTokens.to(Device.CPU())) {
                                longArray = cpuTokens.longArray();
                            }
                            var completion = Arrays.stream(longArray).mapToInt(x -> (int) x).toArray();
                            try {
                                var chunk = tokenizer.tryDecode(completion, true);
                                chunkPos = streamEnd;
                                if (!chunk.isEmpty()) {
                                    listener.onText(chunk);
                                }
                            } catch (java.nio.charset.CharacterCodingException ex) {
                                logger.debug("Cannot decode a chunk", ex);
                            }
                        }
                    }
                    if (done) {
                        break;
                    }
                    continue;
                }

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
                if (done) {
                    break;
                }
                curPos++;
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

    /**
     * Per-request state of the history-aware MTP draft head. Invariant while
     * {@link #valid}: MTP KV holds positions {@code [0, filled)}, and
     * {@code pending} holds backbone hidden rows for positions
     * {@code [filled, filled + rows)} (one {@code [rows, D]} tensor per TP
     * rank) whose "next token" inputs are {@code priorTokens} (all rows but
     * the last) plus the caller's {@code lastToken}.
     */
    private static final class MtpHistory {
        int filled;
        int[] priorTokens = new int[0];
        Tensor[] pending;
        boolean valid = true;

        int pendingRows() {
            return pending == null ? 0 : (int) pending[0].shape()[0];
        }

        void closePending() {
            if (pending != null) {
                for (Tensor t : pending) {
                    if (t != null) {
                        t.close();
                    }
                }
                pending = null;
            }
            priorTokens = new int[0];
        }

        void setPending(Tensor[] rows, int[] prior) {
            closePending();
            pending = rows;
            priorTokens = prior;
        }

        void invalidate() {
            invalidate("unspecified");
        }

        void invalidate(String reason) {
            if (valid) {
                logger.info("MTP history invalidated (request falls back to plain decode): {}", reason);
            }
            valid = false;
            closePending();
        }
    }

    /** Request id to MTP draft-head history (single-request speculation path). */
    private final Map<Integer, MtpHistory> mtpHistory = new ConcurrentHashMap<>();
    private boolean mtpHistoryPoolsReady;
    /** Per-instance switch (defaults to {@link #MTP_HISTORY}); tests of the legacy anchor path turn it off. */
    private volatile boolean mtpHistoryEnabled = MTP_HISTORY;

    /**
     * Test hook: selects the legacy shared-anchor MTP head for this instance. Must be called before
     * the first {@link #bind} (history pools and the anchor-capture switch are set up there).
     *
     * @param enabled {@code false} to use the legacy context-free head.
     */
    void setMtpHistoryEnabledForTesting(boolean enabled) {
        this.mtpHistoryEnabled = enabled;
    }

    /** Test hook: whether {@code requestId} still has a consistent MTP history. */
    boolean mtpHistoryValid(int requestId) {
        MtpHistory st = mtpHistory.get(requestId);
        return st != null && st.valid;
    }

    /** True when persistent MTP history pools exist on every rank. */
    private boolean mtpHistoryActive() {
        return mtpHistoryEnabled && mtpHistoryPoolsReady && isSpeculativeEnabled();
    }

    /**
     * Allocates the persistent MTP KV pools (once speculation is turned on).
     * Failure to allocate disables history (and therefore MTP speculation,
     * which is context-free garbage without it) rather than failing load.
     */
    private synchronized void ensureMtpHistoryPools() {
        if (!mtpHistoryEnabled || mtpHistoryPoolsReady) {
            return;
        }
        for (QwenModel m : models) {
            if (m.mtp() == null || m.kvCachePool() == null) {
                return;
            }
        }
        try {
            for (QwenModel m : models) {
                KvCachePool main = m.kvCachePool();
                KvCachePool pool = KvCachePool.forMtpHistory(
                        m.mtp().kvCacheLayout(), m.device(), main.keyCache().dtype(),
                        main.pageSize(), main.numSlots());
                m.mtp().setKvCachePool(pool, true);
                logger.info("tpRank={}: MTP history KvCachePool slots={}", m.tpRank(), pool.numSlots());
            }
            // History mode never reads the shared single-buffer anchor; stop capturing it (it is
            // a buffer CUDA graphs bake in and batched rounds would reallocate).
            for (QwenModel m : models) {
                m.setSkipAnchorCapture(true);
                m.invalidateVerifyCudaGraphs();
                m.invalidateDecodeCudaGraphs();
            }
            mtpHistoryPoolsReady = true;
        } catch (RuntimeException e) {
            logger.warn("MTP history KV pool allocation failed; speculation falls back to plain decode: {}",
                    e.getMessage());
        }
    }

    /** Binds the same request id in every rank's MTP history pool (best effort). */
    private void bindMtpHistory(int id, int[] prompt, int totalCapacity) {
        if (isSpeculativeEnabled()) {
            ensureMtpHistoryPools();
        }
        if (!mtpHistoryActive()) {
            return;
        }
        try {
            for (QwenModel m : models) {
                int local = m.mtp().kvCachePool().bindRequest(prompt, totalCapacity);
                if (local != id) {
                    throw new IllegalStateException("MTP request id mismatch: main=" + id + " mtp=" + local);
                }
            }
        } catch (RuntimeException e) {
            logger.warn("MTP history bind failed for request {} (no speculation): {}", id, e.getMessage());
            for (QwenModel m : models) {
                if (m.mtp() != null && m.mtp().kvCachePool() != null) {
                    m.mtp().kvCachePool().unbindRequest(id);
                }
            }
        }
    }

    private void releaseMtpHistory(int requestId) {
        MtpHistory st = mtpHistory.remove(requestId);
        if (st != null) {
            st.invalidate();
        }
        if (mtpHistoryEnabled && mtpHistoryPoolsReady) {
            for (QwenModel m : models) {
                if (m.mtp() != null && m.mtp().kvCachePool() != null) {
                    m.mtp().kvCachePool().unbindRequest(requestId);
                }
            }
        }
    }

    /** Runs {@code task} on every TP rank (own thread/ParallelState per rank when TP). */
    private Tensor[] runOnRanks(java.util.function.IntFunction<Tensor> task) {
        Tensor[] out = new Tensor[models.length];
        if (models.length == 1) {
            try (var guard = Tensor.noGradGuard()) {
                out[0] = task.apply(0);
            }
            return out;
        }
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int rank = 0; rank < models.length; rank++) {
            final int r = rank;
            futures.add(tpExecutor.submit(() -> {
                ParallelState.setCurrent(tpGroup.state(r));
                try (var guard = Tensor.noGradGuard()) {
                    return task.apply(r);
                } finally {
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < models.length; r++) {
                out[r] = futures.get(r).get();
            }
        } catch (Exception e) {
            for (Tensor t : out) {
                if (t != null) {
                    t.close();
                }
            }
            throw new RuntimeException("TP MTP step failed", e);
        }
        return out;
    }

    /**
     * One history-aware MTP forward over {@code hidden} ({@code [1, m, D]} per
     * rank) at positions {@code [startPos, startPos+m)}; returns per-rank last-row
     * logits when {@code wantLogits}, else {@code null} entries.
     */
    private Tensor[] mtpAbsorb(int[] nextTokens, Tensor[] hidden, int startPos, boolean wantLogits) {
        int m = nextTokens.length;
        long[] tl = new long[m];
        for (int i = 0; i < m; i++) {
            tl[i] = nextTokens[i];
        }
        return runOnRanks(r -> {
            Tensor tokT = Tensor.of(tl).reshape(1, m);
            Tensor dev = tokT.to(models[r].device());
            try {
                return models[r].mtp().absorb(dev, hidden[r], startPos, wantLogits);
            } finally {
                if (dev != tokT) {
                    dev.close();
                }
                tokT.close();
            }
        });
    }

    private void activateMtpPools(int... requestIds) {
        for (QwenModel m : models) {
            m.mtp().kvCachePool().activateStep(requestIds);
        }
    }

    /** Views per-rank {@code [rows, D]} tensors as {@code [1, rows, D]}. */
    private static Tensor[] asBatchOne(Tensor[] rows) {
        Tensor[] out = new Tensor[rows.length];
        for (int r = 0; r < rows.length; r++) {
            long[] sh = rows[r].shape();
            out[r] = rows[r].reshape(1, sh[0], sh[1]);
        }
        return out;
    }

    private static void closeAll(Tensor[] ts) {
        for (Tensor t : ts) {
            if (t != null) {
                t.close();
            }
        }
    }

    /**
     * Absorbs one prefill chunk {@code [from, to)} into the MTP history: every
     * position whose "next token" is already known gets its MTP K/V written;
     * at most one trailing row (waiting for the first generated token) stays
     * pending. Called right after the chunk's backbone forward (which retained
     * the chunk's hidden rows via {@code captureWindowHidden}).
     */
    private void mtpAbsorbPrefill(int requestId, int[] prompt, int from, int to) {
        MtpHistory st;
        if (from == 0) {
            st = new MtpHistory();
            MtpHistory old = mtpHistory.put(requestId, st);
            if (old != null) {
                old.invalidate("replaced by a new prefill of the same request id");
            }
        } else {
            st = mtpHistory.get(requestId);
        }
        if (st == null || !st.valid) {
            return;
        }
        try {
            int oldRows = st.pendingRows();
            int newRows = to - from;
            if (st.filled + oldRows != from || models[0].windowHidden() == null
                    || !models[0].mtp().kvCachePool().isBound(requestId)) {
                st.invalidate("prefill chunk start does not match filled+pending, or no window hidden, or MTP KV not bound");
                return;
            }
            int total = oldRows + newRows;
            int process = to < prompt.length ? total : total - 1;
            activateMtpPools(requestId);
            int pos = st.filled;
            if (oldRows == 1 && process >= 1) {
                Tensor[] h = asBatchOne(st.pending);
                try {
                    mtpAbsorb(new int[]{prompt[pos + 1]}, h, pos, false);
                } finally {
                    closeAll(h);
                }
                pos++;
            }
            int chunkRows = process - oldRows;
            if (chunkRows > 0) {
                Tensor[] h = new Tensor[models.length];
                for (int r = 0; r < models.length; r++) {
                    try (var sl = Index.slice(0, chunkRows)) {
                        h[r] = models[r].windowHidden().get(Index.Colon, sl);
                    }
                }
                try {
                    mtpAbsorb(Arrays.copyOfRange(prompt, pos + 1, pos + 1 + chunkRows), h, pos, false);
                } finally {
                    closeAll(h);
                }
            }
            if (process < total) {
                Tensor[] rows = new Tensor[models.length];
                for (int r = 0; r < models.length; r++) {
                    rows[r] = models[r].copyWindowHiddenRows(newRows - 1, 1);
                }
                st.setPending(rows, new int[0]);
            } else {
                st.closePending();
            }
            st.filled += process;
        } catch (RuntimeException e) {
            st.invalidate("prefill absorb threw");
            throw e;
        }
    }

    /**
     * History-aware greedy drafts: step 0 absorbs every committed-but-pending
     * row (prefill tail or last round's accepted rows) and predicts the next
     * token; later steps chain the MTP hidden. Returns {@code null} when the
     * request has no consistent history (caller falls back to plain decode).
     */
    private int[] draftGreedyHistory(int requestId, int lastToken, int lastPos, int n) {
        MtpHistory st = mtpHistory.get(requestId);
        if (st == null || !st.valid || st.pending == null) {
            return null;
        }
        int rows = st.pendingRows();
        if (st.filled + rows != lastPos || st.priorTokens.length != rows - 1
                || !models[0].mtp().kvCachePool().isBound(requestId)) {
            st.invalidate("draft precondition failed: filled+pending != lastPos or priorTokens/KV-bind mismatch");
            return null;
        }
        int[] tokens0 = Arrays.copyOf(st.priorTokens, rows);
        tokens0[rows - 1] = lastToken;
        int[] drafts = new int[n];
        try {
            activateMtpPools(requestId);
            Tensor[] h0 = asBatchOne(st.pending);
            Tensor[] logits;
            try {
                logits = mtpAbsorb(tokens0, h0, st.filled, true);
            } finally {
                closeAll(h0);
            }
            drafts[0] = smile.llm.engine.Sampling.sampleGreedyTokenId(logits[0]);
            closeAll(logits);
            st.filled = lastPos;
            st.closePending();
            for (int d = 1; d < n; d++) {
                Tensor[] hidden = new Tensor[models.length];
                for (int r = 0; r < models.length; r++) {
                    hidden[r] = models[r].mtp().lastDraftHidden();
                }
                logits = mtpAbsorb(new int[]{drafts[d - 1]}, hidden, lastPos - 1 + d, true);
                drafts[d] = smile.llm.engine.Sampling.sampleGreedyTokenId(logits[0]);
                closeAll(logits);
            }
        } catch (RuntimeException e) {
            st.invalidate("draft absorb threw");
            throw e;
        } finally {
            for (QwenModel m : models) {
                m.mtp().clearDraftHidden();
            }
        }
        return drafts;
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
            if (m.mtpAnchorPool() != null) {
                m.mtpAnchorPool().bindRequest(id);
            }
        }
        bindMtpHistory(id, prompt, totalCapacity);
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
                boolean history = mtpHistoryActive()
                        && models[0].mtp().kvCachePool().isBound(requestId);
                if (history) {
                    for (QwenModel m : models) {
                        m.setCaptureWindowHidden(true);
                    }
                }
                Tensor[] logits;
                try {
                    logits = forwardWindow(tokenShards, from, tpExecutor, false);
                } finally {
                    if (history) {
                        for (QwenModel m : models) {
                            m.setCaptureWindowHidden(false);
                        }
                    }
                }
                for (Tensor t : tokenShards) {
                    t.close();
                }
                for (QwenModel m : models) {
                    if (m.deltaNetStatePool() != null) {
                        m.deltaNetStatePool().scatterActive();
                    }
                }
                scatterMtpAnchor(new int[]{requestId});
                if (history) {
                    mtpAbsorbPrefill(requestId, prompt, from, to);
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
     * Runs {@link QwenModel#forwardVerifyGraph(Tensor, int)} on every TP rank
     * for the primary MTP window-verify forward (the {@code scatter == false}
     * / online-accept branch of {@link #forwardVerifyWindow} only — the
     * {@code scatter == true} DeltaNet-replay-only branch stays on the plain
     * eager {@link #forwardWindow} path; see
     * {@code VerifyCudaGraph}'s class javadoc for why).
     */
    private Tensor[] forwardWindowVerify(Tensor[] tokenShards, int startPos, ExecutorService pool) {
        Tensor[] logits = new Tensor[models.length];
        boolean profile = DecodeForwardProfile.enabled();
        DecodeForwardProfile.Snapshot merged = profile ? new DecodeForwardProfile.Snapshot() : null;
        if (models.length == 1) {
            logits[0] = models[0].forwardVerifyGraph(tokenShards[0], startPos);
            if (merged != null) {
                merged.maxWith(DecodeForwardProfile.snapshotAndReset());
            }
            lastVerifyProfile = merged;
            return logits;
        }
        DecodeForwardProfile.Snapshot[] rankProfiles =
                profile ? new DecodeForwardProfile.Snapshot[models.length] : null;
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int r = 0; r < models.length; r++) {
            final int rank = r;
            futures.add(pool.submit(() -> {
                ParallelState.setCurrent(tpGroup.state(rank));
                try (var guard = Tensor.noGradGuard()) {
                    Tensor out = models[rank].forwardVerifyGraph(tokenShards[rank], startPos);
                    if (rankProfiles != null) {
                        rankProfiles[rank] = DecodeForwardProfile.snapshotAndReset();
                    }
                    return out;
                } finally {
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < models.length; r++) {
                logits[r] = futures.get(r).get();
                if (merged != null) {
                    merged.maxWith(rankProfiles[r]);
                }
            }
        } catch (Exception e) {
            for (Tensor l : logits) {
                if (l != null) {
                    l.close();
                }
            }
            throw new RuntimeException("TP verify-graph forward failed", e);
        }
        lastVerifyProfile = merged;
        return logits;
    }

    /**
     * Runs {@link QwenModel#forwardBatchedVerify} on every TP rank for a
     * batched cohort of concurrent requests, possibly at different absolute
     * positions (never CUDA-graph captured — see
     * {@link QwenModel#forwardBatchedVerify}).
     */
    private Tensor[] forwardWindowVerifyBatch(Tensor[] tokenShards, int[] startPositions,
                                              int[] cacheLengths, ExecutorService pool) {
        // Goes through QwenModel.forwardVerifyGraph(tokens, int[]): replays the ragged batched verify
        // graph when enabled, otherwise the eager ragged path (forwardBatchedVerify).
        Tensor[] logits = new Tensor[models.length];
        if (models.length == 1) {
            logits[0] = models[0].forwardVerifyGraph(tokenShards[0], startPositions);
            return new Tensor[]{ownedVerifyWindowLogits(logits)};
        }
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int r = 0; r < models.length; r++) {
            final int rank = r;
            futures.add(pool.submit(() -> {
                ParallelState.setCurrent(tpGroup.state(rank));
                try (var guard = Tensor.noGradGuard()) {
                    return models[rank].forwardVerifyGraph(tokenShards[rank], startPositions);
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
                if (l != null && !VerifyCudaGraph.persistentLogits()) {
                    l.close();
                }
            }
            throw new RuntimeException("TP batched verify forward failed", e);
        }
        return new Tensor[]{ownedVerifyWindowLogits(logits)};
    }

    /**
     * Runs {@link QwenModel#forward(Tensor, int[])} on every TP rank for a
     * decode batch that already holds shape {@code [B, 1]} tokens.
     */
    private Tensor[] forwardAllDecode(Tensor[] tokens, int[] cachePositions, int[] ropePositions,
                                      ExecutorService pool) {
        Tensor[] logits = new Tensor[models.length];
        long t0 = System.nanoTime();
        // While any request speculates, its rounds rebuild the shared per-step KV metadata and drop
        // decode graphs every tick; a plain-decode group sharing the engine would then re-capture
        // its decode graph on every tick (a ~4x throughput collapse measured at 32 concurrent
        // requests with 16 speculating). Run that group eagerly instead.
        boolean graph = DecodeCudaGraph.canGraphDecode(cachePositions) && !anySpeculatingRequest();
        boolean profile = DecodeForwardProfile.enabled();
        DecodeForwardProfile.Snapshot merged = profile ? new DecodeForwardProfile.Snapshot() : null;
        if (models.length == 1) {
            long rank0 = System.nanoTime();
            if (graph) {
                logits[0] = models[0].forwardDecodeGraph(tokens[0], cachePositions, ropePositions);
            } else {
                logits[0] = models[0].forward(tokens[0], cachePositions, ropePositions, false);
            }
            long rankNs = System.nanoTime() - rank0;
            if (merged != null) {
                merged.maxWith(DecodeForwardProfile.snapshotAndReset());
            }
            recordForwardTiming(System.nanoTime() - t0, rankNs, merged);
            return logits;
        }
        long[] rankNs = new long[models.length];
        DecodeForwardProfile.Snapshot[] rankProfiles =
                profile ? new DecodeForwardProfile.Snapshot[models.length] : null;
        List<Future<Tensor>> futures = new ArrayList<>(models.length);
        for (int r = 0; r < models.length; r++) {
            final int rank = r;
            futures.add(pool.submit(() -> {
                long rankStart = System.nanoTime();
                ParallelState.setCurrent(tpGroup.state(rank));
                try (var guard = Tensor.noGradGuard()) {
                    Tensor out;
                    if (graph) {
                        out = models[rank].forwardDecodeGraph(
                                tokens[rank], cachePositions, ropePositions);
                    } else {
                        out = models[rank].forward(
                                tokens[rank], cachePositions, ropePositions, false);
                    }
                    if (rankProfiles != null) {
                        rankProfiles[rank] = DecodeForwardProfile.snapshotAndReset();
                    }
                    return out;
                } finally {
                    rankNs[rank] = System.nanoTime() - rankStart;
                    ParallelState.clearCurrent();
                }
            }));
        }
        try {
            for (int r = 0; r < models.length; r++) {
                logits[r] = futures.get(r).get();
                if (merged != null) {
                    merged.maxWith(rankProfiles[r]);
                }
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
        recordForwardTiming(System.nanoTime() - t0, slowest, merged);
        return logits;
    }

    private static void recordForwardTiming(long wallNs, long slowestRankNs,
                                            DecodeForwardProfile.Snapshot profile) {
        DecodeStepTiming timing = DecodeStepTiming.current();
        if (timing != null) {
            timing.forwardNs = wallNs;
            timing.slowestRankNs = slowestRankNs;
            timing.tpBarrierNs = Math.max(0L, wallNs - slowestRankNs);
            timing.profile = profile;
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

    /**
     * One exclusive-generate MTP round: draft {@code numDrafts} tokens, verify with
     * one target window forward, write accepted tokens (drafts + bonus) into
     * {@code tokens} starting at {@code writePos}.
     *
     * @return number of tokens written ({@code >= 1}).
     */
    int speculateRoundOffline(Tensor[] tokens, int writePos, int numDrafts,
                              double temperature, double topp, ExecutorService pool) {
        if (numDrafts < 1) {
            throw new IllegalArgumentException("numDrafts must be >= 1");
        }
        QwenMtp mtp = models[0].mtp();
        if (mtp == null) {
            throw new IllegalStateException("MTP head not loaded");
        }
        Tensor anchor = models[0].lastPreNormHidden();
        if (anchor == null) {
            throw new IllegalStateException("MTP anchor hidden missing; run a target forward first");
        }

        int lastPos = writePos - 1;
        long lastTokLong;
        try (var row = Index.of(0);
             var col = Index.of(lastPos);
             Tensor cell = tokens[0].get(row, col);
             Tensor cpu = cell.to(Device.CPU())) {
            lastTokLong = cpu.longArray()[0];
        }
        int lastToken = (int) lastTokLong;

        for (QwenModel m : models) {
            if (m.mtp() != null) {
                m.mtp().beginRound(numDrafts);
            }
        }
        try {
            int[] drafts = draftGreedy(lastToken, lastPos, numDrafts);
            SpeculativeDecoding.AcceptResult accept = verifyWindowOffline(
                    tokens, writePos, lastToken, lastPos, drafts, temperature, topp, pool);
            // Drafts were written into the buffer during verify; overwrite the bonus slot.
            putToken(tokens, writePos + accept.numDraftAccepted(), accept.bonusToken());
            recordSpeculativeRound(numDrafts, accept.numDraftAccepted());
            logger.debug("MTP speculate: drafts={} accepted={} bonus={} acceptRate={} meanDepth={} targetFwd/round={}",
                    numDrafts, accept.numDraftAccepted(), accept.bonusToken(),
                    speculativeAcceptRate(), speculativeMeanAcceptedDepth(),
                    speculativeMeanTargetForwardsPerRound());
            return accept.numTokens();
        } finally {
            for (QwenModel m : models) {
                if (m.mtp() != null) {
                    m.mtp().endRound();
                }
            }
        }
    }

    private void putToken(Tensor[] tokens, int pos, int tokenId) {
        try (Tensor t = Tensor.of(new long[]{tokenId})) {
            for (int r = 0; r < models.length; r++) {
                Tensor local = t.to(models[r].device());
                try (var col = Index.of(pos)) {
                    tokens[r].put_(local, Index.Colon, col);
                }
                if (local != t) {
                    local.close();
                }
            }
        }
    }

    private int sampleTargetId(Tensor logitsRow, double temperature, double topp) {
        if (temperature <= 0) {
            return Sampling.sampleGreedyTokenId(logitsRow);
        }
        try (Tensor sampled = Sampling.sampleNext(logitsRow, temperature, topp);
             Tensor cpu = sampled.to(Device.CPU())) {
            return (int) cpu.longArray()[0];
        }
    }

    /**
     * Samples one target id per window position from logits {@code [1,S,V]} or {@code [S,V]}.
     */
    /**
     * Samples one token per logits row where request {@code i} owns rows
     * {@code [i*perRequest, (i+1)*perRequest)} and has its own temperature / top-p. Requests that
     * share parameters are sampled together; the common all-equal case is a single call.
     */
    private int[] sampleTargetRows(Tensor flatLogits, int perRequest, double[] temps, double[] topps) {
        int b = temps.length;
        boolean uniform = true;
        for (int i = 1; i < b; i++) {
            if (temps[i] != temps[0] || topps[i] != topps[0]) {
                uniform = false;
                break;
            }
        }
        if (uniform) {
            return Sampling.sampleTokenIds(flatLogits, temps[0], topps[0]);
        }
        int[] out = new int[b * perRequest];
        boolean[] done = new boolean[b];
        for (int i = 0; i < b; i++) {
            if (done[i]) {
                continue;
            }
            int[] ids = Sampling.sampleTokenIds(flatLogits, temps[i], topps[i]);
            for (int j = i; j < b; j++) {
                if (!done[j] && temps[j] == temps[i] && topps[j] == topps[i]) {
                    System.arraycopy(ids, j * perRequest, out, j * perRequest, perRequest);
                    done[j] = true;
                }
            }
        }
        return out;
    }

    private int[] sampleTargetWindow(Tensor logits, double temperature, double topp) {
        Tensor flat = logits;
        boolean closeFlat = false;
        if (logits.dim() == 3) {
            long[] sh = logits.shape();
            flat = logits.reshape(sh[0] * sh[1], sh[2]);
            closeFlat = true;
        } else if (logits.dim() != 2) {
            throw new IllegalArgumentException(
                    "verify window logits must be [1,S,V] or [S,V], got dim=" + logits.dim());
        }
        try {
            return Sampling.sampleTokenIds(flat, temperature, topp);
        } finally {
            if (closeFlat) {
                flat.close();
            }
        }
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions,
                                 int numDrafts, double[] temperatures, double[] topps) {
        if (!isSpeculativeEnabled()) {
            throw new UnsupportedOperationException("MTP speculation not available");
        }
        int b = requestIds.length;
        if (lastTokens.length != b || positions.length != b || temperatures.length != b
                || topps.length != b || b == 0) {
            throw new IllegalArgumentException("speculateStep batch sizes must match");
        }
        int n = params.resolveNumSpeculativeTokens(numDrafts);
        if (n < 1) {
            throw new IllegalArgumentException("numDrafts must be >= 1");
        }
        if (b > 1 && mtpHistoryActive()) {
            return speculateBatchHistory(requestIds, lastTokens, positions, n, temperatures, topps);
        }
        int[][] out = new int[b][];
        for (int i = 0; i < b; i++) {
            out[i] = speculateStep(new int[]{requestIds[i]}, new int[]{lastTokens[i]},
                    new int[]{positions[i]}, numDrafts, temperatures[i], topps[i])[0];
        }
        return out;
    }

    @Override
    public int[][] speculateStep(int[] requestIds, int[] lastTokens, int[] positions,
                                 int numDrafts, double temperature, double topp) {
        if (!isSpeculativeEnabled()) {
            throw new UnsupportedOperationException("MTP speculation not available");
        }
        if (requestIds == null || lastTokens == null || positions == null) {
            throw new IllegalArgumentException("speculateStep args must not be null");
        }
        int b = requestIds.length;
        if (lastTokens.length != b || positions.length != b || b == 0) {
            throw new IllegalArgumentException("speculateStep batch sizes must match");
        }
        int n = params.resolveNumSpeculativeTokens(numDrafts);
        if (n < 1) {
            throw new IllegalArgumentException("numDrafts must be >= 1");
        }
        if (b > 1 && mtpHistoryActive()) {
            double[] temps = new double[b];
            double[] topps = new double[b];
            Arrays.fill(temps, temperature);
            Arrays.fill(topps, topp);
            return speculateBatchHistory(requestIds, lastTokens, positions, n, temps, topps);
        }
        if (b > 1 && allAnchorsReady(requestIds)) {
            return speculateBatch(requestIds, lastTokens, positions, n, temperature, topp);
        }
        int[][] out = new int[b][];
        for (int i = 0; i < b; i++) {
            out[i] = speculateOneRequest(requestIds[i], lastTokens[i], positions[i], n,
                    temperature, topp);
        }
        return out;
    }

    /**
     * True when every row's MTP anchor (see {@link MtpAnchorPool#hasRow}) is
     * already written, i.e. the whole cohort is eligible for
     * {@link #speculateBatch} in one call. A single missing row falls the
     * whole batch back to {@link #speculateOneRequest} per row rather than
     * splitting the batch, matching {@code InferenceEngine}'s own
     * uniform-or-fallback grouping philosophy elsewhere.
     */
    private boolean allAnchorsReady(int[] requestIds) {
        MtpAnchorPool anchorPool = models[0].mtpAnchorPool();
        if (models[0].mtp() == null || anchorPool == null) {
            return false;
        }
        for (int requestId : requestIds) {
            if (!anchorPool.hasRow(requestId)) {
                return false;
            }
        }
        return true;
    }

    private long batchRounds;
    private long batchRows;
    private long batchDraftNs;
    private long batchVerifyNs;
    private long batchBookkeepingNs;
    private long batchRestoreNs;
    private long batchScatterNs;

    /** Per-component mean cost of a batched speculative round, logged every 25 rounds. */
    private void recordBatchRound(int rows, long draftNs, long verifyNs, long bookkeepingNs) {
        batchRounds++;
        batchRows += rows;
        batchDraftNs += draftNs;
        batchVerifyNs += verifyNs;
        batchBookkeepingNs += bookkeepingNs;
        if (batchRounds % 25 == 0 && logger.isInfoEnabled()) {
            logger.info("MTP batch: rounds={} meanRows={} draft={}ms verifyForward={}ms bookkeeping={}ms"
                            + " (of which checkpointRestore={}ms deltaScatter={}ms)",
                    batchRounds, String.format("%.1f", (double) batchRows / batchRounds),
                    String.format("%.1f", batchDraftNs / 1e6 / batchRounds),
                    String.format("%.1f", batchVerifyNs / 1e6 / batchRounds),
                    String.format("%.1f", batchBookkeepingNs / 1e6 / batchRounds),
                    String.format("%.1f", batchRestoreNs / 1e6 / batchRounds),
                    String.format("%.1f", batchScatterNs / 1e6 / batchRounds));
        }
    }

    private boolean checkpointWarned;

    /**
     * Whether the DeltaNet checkpoint buffers a batched round of {@code rows} rows needs can be
     * allocated. They cost about 0.15 GB per row per GPU for a 27B hybrid model (slots x layers x
     * recurrent+conv state), so a large cohort on a nearly fully reserved GPU can run out of memory;
     * failing every request in the cohort for that is far worse than one plain batched step.
     */
    private boolean checkpointsFit(int numDrafts, int rows) {
        // A failed check is sticky for a few seconds: the engine keeps offering the same cohort
        // every tick, and re-querying free memory (plus a cache flush) per tick would cost more
        // than the plain decode step the cohort falls back to.
        long now = System.nanoTime();
        if (now < checkpointRetryAfterNs && rows >= checkpointFailedRows) {
            return false;
        }
        boolean fits = checkpointsFitNow(numDrafts, rows);
        if (fits) {
            checkpointRetryAfterNs = 0L;
        } else {
            checkpointRetryAfterNs = now + 5_000_000_000L;
            checkpointFailedRows = rows;
        }
        return fits;
    }

    private long checkpointRetryAfterNs;
    private int checkpointFailedRows = Integer.MAX_VALUE;

    private boolean checkpointsFitNow(int numDrafts, int rows) {
        for (QwenModel m : models) {
            DeltaNetStatePool pool = m.deltaNetStatePool();
            if (pool == null) {
                continue;
            }
            long need = pool.speculativeCheckpointGrowthBytes(1, numDrafts, rows);
            if (need == 0L) {
                continue;
            }
            long margin = 512L << 20;
            long free = freeDeviceBytes(m);
            if (free >= 0 && free < need + margin) {
                m.device().emptyCache();
                free = freeDeviceBytes(m);
            }
            if (free >= 0 && free < need + margin) {
                if (!checkpointWarned) {
                    checkpointWarned = true;
                    logger.warn("Batched MTP speculation needs {} MiB more GPU memory for DeltaNet "
                                    + "checkpoints ({} rows, {} slots) but only {} MiB is free; using plain "
                                    + "batched decode for such cohorts. Lower smile.chat.mem-fraction-static "
                                    + "or smile.chat.speculative-max-concurrency.",
                            need >> 20, rows, numDrafts, free >> 20);
                }
                return false;
            }
        }
        return true;
    }

    private static long freeDeviceBytes(QwenModel m) {
        if (!m.device().isCUDA()) {
            return -1L;
        }
        try {
            return smile.torch.Native.cudaMemGetInfo(m.device().index())[0];
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    /** True while at least one live request has a valid MTP history (i.e. may be speculating). */
    private boolean anySpeculatingRequest() {
        if (mtpHistory.isEmpty()) {
            return false;
        }
        for (MtpHistory st : mtpHistory.values()) {
            if (st.valid) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code requestId} has a consistent, ready-to-draft MTP history at {@code lastPos}. */
    private boolean historyUsable(int requestId, int lastPos) {
        MtpHistory st = mtpHistory.get(requestId);
        if (st == null || !st.valid || st.pending == null) {
            return false;
        }
        int rows = st.pendingRows();
        return st.filled + rows == lastPos && st.priorTokens.length == rows - 1
                && models[0].mtp().kvCachePool().isBound(requestId);
    }

    /**
     * Keeps at most {@code keep} pending rows for a request by absorbing the oldest surplus
     * rows into its MTP KV on the single-request path (a request that sat out several rounds
     * can have more pending rows than the batched step's window is wide).
     */
    private void trimPending(int requestId, MtpHistory st, int keep) {
        int rows = st.pendingRows();
        if (rows <= keep) {
            return;
        }
        int excess = rows - keep;
        try {
            activateMtpPools(requestId);
            Tensor[] head = new Tensor[models.length];
            Tensor[] tail = new Tensor[models.length];
            for (int r = 0; r < models.length; r++) {
                try (var h = Index.slice(0, excess); var t = Index.slice(excess, rows)) {
                    head[r] = st.pending[r].get(h);
                    Tensor tv = st.pending[r].get(t);
                    tail[r] = tv.copy();
                    tail[r].detachFromScopes();
                    tv.close();
                }
            }
            Tensor[] h3 = asBatchOne(head);
            try {
                mtpAbsorb(Arrays.copyOf(st.priorTokens, excess), h3, st.filled, false);
            } finally {
                closeAll(h3);
                closeAll(head);
            }
            st.filled += excess;
            st.setPending(tail, Arrays.copyOfRange(st.priorTokens, excess, st.priorTokens.length));
        } catch (RuntimeException e) {
            st.invalidate("trimming surplus pending rows threw");
            throw e;
        }
    }

    /**
     * History-aware drafts for a cohort. Step 0 absorbs every row's pending committed positions
     * in one ragged forward (rows padded with dummy columns after their last real one) and
     * predicts each row's next token; steps 1..n-1 chain one token per row at
     * {@code lastPos-1+d}. All rows must satisfy {@link #historyUsable}.
     */
    private int[][] draftGreedyHistoryBatch(int[] requestIds, int[] lastTokens, int[] lastPos, int n) {
        int b = requestIds.length;
        int s = n + 1;
        MtpHistory[] sts = new MtpHistory[b];
        int[] startPos = new int[b];
        int[] lastReal = new int[b];
        long[] tokLongs = new long[b * s];
        for (int i = 0; i < b; i++) {
            MtpHistory st = mtpHistory.get(requestIds[i]);
            sts[i] = st;
            trimPending(requestIds[i], st, s);
            int m = st.pendingRows();
            startPos[i] = st.filled;
            lastReal[i] = m - 1;
            for (int j = 0; j < m - 1; j++) {
                tokLongs[i * s + j] = st.priorTokens[j];
            }
            tokLongs[i * s + m - 1] = lastTokens[i];
        }
        int[][] drafts = new int[b][n];
        try {
            activateMtpPools(requestIds);
            long dim = sts[0].pending[0].shape()[1];
            Tensor[] logits = runOnRanks(r -> {
                Tensor tokT = Tensor.of(tokLongs).reshape(b, s);
                Tensor dev = tokT.to(models[r].device());
                Tensor hid = Tensor.zeros(new Tensor.Options().device(dev.device())
                        .dtype(sts[0].pending[r].dtype()).requireGradients(false), b, s, dim);
                try {
                    for (int i = 0; i < b; i++) {
                        try (var bi = Index.of(i); var sl = Index.slice(0, sts[i].pendingRows())) {
                            hid.put_(sts[i].pending[r], bi, sl, Index.Colon);
                        }
                    }
                    return models[r].mtp().absorbBatch(dev, hid, startPos, lastReal);
                } finally {
                    hid.close();
                    if (dev != tokT) {
                        dev.close();
                    }
                    tokT.close();
                }
            });
            int[] d0 = smile.llm.engine.Sampling.sampleGreedyTokenIds(logits[0]);
            closeAll(logits);
            for (int i = 0; i < b; i++) {
                drafts[i][0] = d0[i];
                sts[i].filled = lastPos[i];
                sts[i].closePending();
            }
            for (int d = 1; d < n; d++) {
                final int step = d;
                long[] tl = new long[b];
                int[] pos = new int[b];
                for (int i = 0; i < b; i++) {
                    tl[i] = drafts[i][step - 1];
                    pos[i] = lastPos[i] - 1 + step;
                }
                int[] zeros = new int[b];
                Tensor[] lg = runOnRanks(r -> {
                    Tensor tokT = Tensor.of(tl).reshape(b, 1);
                    Tensor dev = tokT.to(models[r].device());
                    try {
                        return models[r].mtp().absorbBatch(dev, models[r].mtp().lastDraftHidden(), pos, zeros);
                    } finally {
                        if (dev != tokT) {
                            dev.close();
                        }
                        tokT.close();
                    }
                });
                int[] di = smile.llm.engine.Sampling.sampleGreedyTokenIds(lg[0]);
                closeAll(lg);
                for (int i = 0; i < b; i++) {
                    drafts[i][step] = di[i];
                }
            }
        } catch (RuntimeException e) {
            for (MtpHistory st : sts) {
                st.invalidate("batched draft threw");
            }
            throw e;
        } finally {
            for (QwenModel m : models) {
                m.mtp().clearDraftHidden();
            }
        }
        return drafts;
    }

    /**
     * Batched speculation for a cohort using each request's persistent MTP history. Rows whose
     * history is unusable (multimodal, KV-bind failure, an earlier plain-decode gap) take one
     * batched plain decode step instead; a lone usable row uses the single-request path (and its
     * verify CUDA graph); two or more usable rows draft and verify together.
     */
    private int[][] speculateBatchHistory(int[] requestIds, int[] lastTokens, int[] lastPositions,
                                          int n, double[] temps, double[] topps) {
        int b = requestIds.length;
        int[][] out = new int[b][];
        List<Integer> good = new ArrayList<>();
        List<Integer> bad = new ArrayList<>();
        boolean fits = b < 2 || checkpointsFit(n, b);
        for (int i = 0; i < b; i++) {
            (fits && historyUsable(requestIds[i], lastPositions[i]) ? good : bad).add(i);
        }
        if (!bad.isEmpty()) {
            int[] ids = new int[bad.size()];
            int[] toks = new int[bad.size()];
            int[] pos = new int[bad.size()];
            double[] bt = new double[bad.size()];
            double[] bp = new double[bad.size()];
            for (int k = 0; k < ids.length; k++) {
                int i = bad.get(k);
                ids[k] = requestIds[i];
                toks[k] = lastTokens[i];
                pos[k] = lastPositions[i];
                bt[k] = temps[i];
                bp[k] = topps[i];
            }
            try (Tensor logits = decodeStep(ids, toks, pos)) {
                int[] sampled = sampleTargetRows(logits, 1, bt, bp);
                for (int k = 0; k < ids.length; k++) {
                    out[bad.get(k)] = new int[]{sampled[k]};
                }
            }
        }
        if (good.size() == 1) {
            int i = good.get(0);
            out[i] = speculateOneRequest(requestIds[i], lastTokens[i], lastPositions[i], n,
                    temps[i], topps[i]);
        } else if (good.size() > 1) {
            int g = good.size();
            int[] ids = new int[g];
            int[] toks = new int[g];
            int[] pos = new int[g];
            double[] gt = new double[g];
            double[] gp = new double[g];
            for (int k = 0; k < g; k++) {
                int i = good.get(k);
                ids[k] = requestIds[i];
                toks[k] = lastTokens[i];
                pos[k] = lastPositions[i];
                gt[k] = temps[i];
                gp[k] = topps[i];
            }
            long tDraft = System.nanoTime();
            int[][] drafts = draftGreedyHistoryBatch(ids, toks, pos, n);
            long draftNs = System.nanoTime() - tDraft;
            speculativeDraftNanos.addAndGet(draftNs);
            long verify0 = speculativeVerifyNanos.get();
            long book0 = speculativeBookkeepingNanos.get();
            SpeculativeDecoding.AcceptResult[] accepts = verifyWindowOnlineBatch(
                    ids, toks, pos, drafts, gt, gp);
            recordBatchRound(g, draftNs, speculativeVerifyNanos.get() - verify0,
                    speculativeBookkeepingNanos.get() - book0);
            for (int k = 0; k < g; k++) {
                recordSpeculativeRound(n, accepts[k].numDraftAccepted());
                out[good.get(k)] = accepts[k].acceptedTokens();
            }
            logVerify(n, accepts[0].numDraftAccepted(), accepts[0].bonusToken());
        }
        return out;
    }

    private int[] speculateOneRequest(int requestId, int lastToken, int lastPos, int numDrafts,
                                      double temperature, double topp) {
        // Window verify: one target forward over [lastToken] + drafts (vLLM-style).
        int n = Math.max(1, numDrafts);
        if (!mtpHistoryActive()) {
            MtpAnchorPool anchorPool = models[0].mtpAnchorPool();
            if (models[0].mtp() == null || anchorPool == null || !anchorPool.hasRow(requestId)) {
                try (Tensor logits = decodeStep(new int[]{requestId}, new int[]{lastToken},
                        new int[]{lastPos})) {
                    int tok = sampleTargetId(logits, temperature, topp);
                    return new int[]{tok};
                }
            }
        }
        if (mtpHistoryActive()) {
            long tDraft = System.nanoTime();
            int[] drafts = draftGreedyHistory(requestId, lastToken, lastPos, n);
            if (drafts == null) {
                // No consistent MTP history for this request (multimodal, mid-stream
                // enable, plain-decode gap, KV-bind failure): plain decode is exact.
                try (Tensor logits = decodeStep(new int[]{requestId}, new int[]{lastToken},
                        new int[]{lastPos})) {
                    return new int[]{sampleTargetId(logits, temperature, topp)};
                }
            }
            speculativeDraftNanos.addAndGet(System.nanoTime() - tDraft);
            SpeculativeDecoding.AcceptResult accept = verifyWindowOnline(
                    requestId, lastToken, lastPos, drafts, temperature, topp);
            recordSpeculativeRound(n, accept.numDraftAccepted());
            logVerify(n, accept.numDraftAccepted(), accept.bonusToken());
            return accept.acceptedTokens();
        }
        long tBookkeeping = System.nanoTime();
        for (QwenModel m : models) {
            if (m.mtp() != null) {
                m.mtp().beginRound(n);
            }
        }
        speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tBookkeeping);
        try {
            long tDraft = System.nanoTime();
            int[] drafts = draftGreedy(requestId, lastToken, lastPos, n);
            speculativeDraftNanos.addAndGet(System.nanoTime() - tDraft);
            SpeculativeDecoding.AcceptResult accept = verifyWindowOnline(
                    requestId, lastToken, lastPos, drafts, temperature, topp);
            recordSpeculativeRound(n, accept.numDraftAccepted());
            logVerify(n, accept.numDraftAccepted(), accept.bonusToken());
            return accept.acceptedTokens();
        } finally {
            long tEndRound = System.nanoTime();
            for (QwenModel m : models) {
                if (m.mtp() != null) {
                    m.mtp().endRound();
                }
            }
            speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tEndRound);
        }
    }

    /**
     * Batched counterpart of {@link #speculateOneRequest} for a cohort of
     * concurrent requests sharing a uniform draft depth (matches how
     * {@code InferenceEngine.runSpeculateStep} groups its cohort). Every row
     * must already have a written MTP anchor (see {@link MtpAnchorPool#hasRow})
     * — the caller excludes rows without one before building the cohort,
     * mirroring {@link #speculateOneRequest}'s own per-request guard, just
     * enforced once for the whole batch instead of per row inside it.
     *
     * @param requestIds    cohort request ids (order = batch row).
     * @param lastTokens    last accepted token per row.
     * @param lastPositions absolute position of {@code lastTokens[i]} per row.
     * @param numDrafts     uniform draft depth for this cohort.
     * @return accepted tokens (drafts + bonus) per row.
     */
    int[][] speculateBatch(int[] requestIds, int[] lastTokens, int[] lastPositions,
                           int numDrafts, double temperature, double topp) {
        int b = requestIds.length;
        int n = Math.max(1, numDrafts);
        long tBookkeeping = System.nanoTime();
        for (QwenModel m : models) {
            if (m.mtp() != null) {
                m.mtp().beginRound(b, n);
            }
        }
        speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tBookkeeping);
        try {
            long tDraft = System.nanoTime();
            int[][] drafts = draftGreedyBatch(requestIds, lastTokens, lastPositions, n);
            speculativeDraftNanos.addAndGet(System.nanoTime() - tDraft);
            SpeculativeDecoding.AcceptResult[] accepts = verifyWindowOnlineBatch(
                    requestIds, lastTokens, lastPositions, drafts, temperature, topp);
            int[][] out = new int[b][];
            for (int i = 0; i < b; i++) {
                recordSpeculativeRound(n, accepts[i].numDraftAccepted());
                out[i] = accepts[i].acceptedTokens();
            }
            logVerify(n, accepts[0].numDraftAccepted(), accepts[0].bonusToken());
            return out;
        } finally {
            long tEndRound = System.nanoTime();
            for (QwenModel m : models) {
                if (m.mtp() != null) {
                    m.mtp().endRound();
                }
            }
            speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tEndRound);
        }
    }

    private void logVerify(int drafts, int accepted, int bonus) {
        if (logger.isDebugEnabled() || speculativeRounds.get() % 32 == 1) {
            double[] timing = speculativeMeanRoundTimingMs();
            var p = lastVerifyProfile;
            if (p != null) {
                logger.info("MTP verify: drafts={} accepted={} bonus={} acceptRate={} meanDepth={} targetFwd/round={} "
                                + "meanRoundMs(draft={} verify={} replay={} bookkeeping={}) "
                                + "verifyProfile(embed={} fullAttn={} linearAttn={} mlp={} nccl={} lmHead={}) "
                                + "delta(proj={} conv={} gate={} recurrent={} out={})",
                        drafts, accepted, bonus,
                        String.format("%.3f", speculativeAcceptRate()),
                        String.format("%.2f", speculativeMeanAcceptedDepth()),
                        String.format("%.2f", speculativeMeanTargetForwardsPerRound()),
                        String.format("%.2f", timing[0]), String.format("%.2f", timing[1]),
                        String.format("%.2f", timing[2]), String.format("%.2f", timing[3]),
                        p.embedMs(), p.fullAttnMs(), p.linearAttnMs(),
                        p.mlpMs(), p.ncclMs(), p.lmHeadMs(),
                        p.deltaProjMs(), p.deltaConvMs(), p.deltaGateMs(),
                        p.deltaRecurrentMs(), p.deltaOutMs());
            } else {
                logger.info("MTP verify: drafts={} accepted={} bonus={} acceptRate={} meanDepth={} targetFwd/round={} "
                                + "meanRoundMs(draft={} verify={} replay={} bookkeeping={})",
                        drafts, accepted, bonus,
                        String.format("%.3f", speculativeAcceptRate()),
                        String.format("%.2f", speculativeMeanAcceptedDepth()),
                        String.format("%.2f", speculativeMeanTargetForwardsPerRound()),
                        String.format("%.2f", timing[0]), String.format("%.2f", timing[1]),
                        String.format("%.2f", timing[2]), String.format("%.2f", timing[3]));
            }
        }
    }

    /**
     * Online window verify: one target forward over {@code [lastToken] + drafts},
     * greedy accept, KV truncate, DeltaNet restore+replay on partial accept.
     */
    private SpeculativeDecoding.AcceptResult verifyWindowOnline(
            int requestId, int lastToken, int lastPos, int[] drafts,
            double temperature, double topp) {
        int n = drafts.length;
        int[] window = new int[n + 1];
        window[0] = lastToken;
        System.arraycopy(drafts, 0, window, 1, n);

        long tBookkeeping = System.nanoTime();
        activatePools(requestId);
        saveDeltaNetCheckpoint(n);
        speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tBookkeeping);

        boolean checkpointReplay = MTP_VERIFY_CHECKPOINT_REPLAY;
        if (checkpointReplay) {
            setVerifyWindowActive(true);
        }
        // History-aware MTP: retain the window's post-norm hidden rows so the accepted
        // prefix can be absorbed into the draft head's KV on the next draft.
        MtpHistory hist = mtpHistory.get(requestId);
        boolean captureHidden = hist != null && hist.valid && hist.pending == null
                && hist.filled == lastPos;
        if (captureHidden) {
            for (QwenModel m : models) {
                m.setCaptureWindowHidden(true);
            }
        }
        long tVerify = System.nanoTime();
        Tensor logits = null;
        int[] targetSamples;
        try {
            logits = forwardVerifyWindow(requestId, window, lastPos, false);
            speculativeTargetForwards.incrementAndGet();
            targetSamples = sampleTargetWindow(logits, temperature, topp);
            speculativeVerifyNanos.addAndGet(System.nanoTime() - tVerify);
        } finally {
            if (checkpointReplay) {
                setVerifyWindowActive(false);
            }
            if (captureHidden) {
                for (QwenModel m : models) {
                    m.setCaptureWindowHidden(false);
                }
            }
        }
        try {
            // Outside the timed region and outside verifyWindowActive: this
            // diagnostic's own extra eager forward must never be counted as
            // real verify cost, and must not re-enter GatedDeltaNet's
            // per-position checkpoint-replay loop (it would overwrite the
            // real per-position checkpoints restoreDeltaNetCheckpointAtWindowPosition
            // depends on below with values from this redundant forward).
            debugDiffVerifyGraphVsEager(requestId, window, lastPos, logits, n);
        } finally {
            logits.close();
        }
        if (targetSamples.length != n + 1) {
            throw new IllegalStateException(
                    "window logits produced " + targetSamples.length + " samples, expected " + (n + 1));
        }

        SpeculativeDecoding.AcceptResult accept = SpeculativeDecoding.acceptGreedy(drafts, targetSamples);
        int r = accept.numDraftAccepted();
        if (captureHidden) {
            // Rows 0..r are the committed positions lastPos..lastPos+r; their next
            // tokens are drafts[0..r-1] and (supplied next round) the bonus token.
            Tensor[] rows = new Tensor[models.length];
            boolean ok = true;
            for (int i = 0; i < models.length; i++) {
                rows[i] = models[i].copyWindowHiddenRows(0, r + 1);
                ok &= rows[i] != null;
            }
            if (ok) {
                hist.setPending(rows, Arrays.copyOf(drafts, r));
            } else {
                closeAll(rows);
                hist.invalidate("no window hidden captured during verify");
            }
        }
        int writtenEnd = lastPos + n + 1;
        int sealedLen = lastPos + 1 + r;
        tBookkeeping = System.nanoTime();
        truncateKv(requestId, sealedLen, writtenEnd);
        speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tBookkeeping);

        if (r < n) {
            long tReplay = System.nanoTime();
            if (checkpointReplay) {
                // Working rows already hold the state retained after window
                // position r by the per-step verify loop — no second forward.
                restoreDeltaNetCheckpointAtWindowPosition(r);
                scatterDeltaNet();
            } else {
                // Working rows hold end-of-window state; home still pre-window (no scatter yet).
                restoreDeltaNetCheckpoint();
                scatterDeltaNet();
                int[] committed = Arrays.copyOf(window, r + 1);
                try (Tensor ignored = forwardVerifyWindow(requestId, committed, lastPos, true)) {
                    // Replay restores DeltaNet through the sealed prefix and scatters home rows.
                }
            }
            speculativeReplayNanos.addAndGet(System.nanoTime() - tReplay);
        } else {
            tBookkeeping = System.nanoTime();
            scatterDeltaNet();
            speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tBookkeeping);
        }
        scatterMtpAnchor(new int[]{requestId});
        return accept;
    }

    /**
     * Batched online window verify for a cohort of concurrent requests: one
     * target forward over the whole cohort's window ({@code [lastToken_i] +
     * drafts_i} for each row {@code i}, possibly at different absolute
     * positions), per-row greedy accept, per-row KV truncate, per-row
     * DeltaNet checkpoint restore on partial accept.
     *
     * <p>Unlike single-request {@link #verifyWindowOnline}, this always uses
     * the per-position checkpoint-replay mechanism (never a second forward)
     * regardless of the {@code SMILE_MTP_VERIFY_CHECKPOINT_REPLAY} kill
     * switch — batching a second whole-forward per rejected row would need
     * its own ragged-window plumbing for comparatively little benefit, given
     * checkpoint-replay is already validated correct on real hardware.
     *
     * @param requestIds    cohort request ids (order = batch row).
     * @param lastTokens    last accepted token per row.
     * @param lastPositions absolute position of {@code lastTokens[i]} per row.
     * @param drafts        draft tokens {@code [B][n]} (uniform width {@code n} across the cohort).
     * @param temperature   sampling temperature (applied uniformly to every row's target samples).
     * @param topp          nucleus threshold (applied uniformly to every row's target samples).
     * @return accept result per row.
     */
    private SpeculativeDecoding.AcceptResult[] verifyWindowOnlineBatch(
            int[] requestIds, int[] lastTokens, int[] lastPositions, int[][] drafts,
            double temperature, double topp) {
        double[] temps = new double[requestIds.length];
        double[] topps = new double[requestIds.length];
        Arrays.fill(temps, temperature);
        Arrays.fill(topps, topp);
        return verifyWindowOnlineBatch(requestIds, lastTokens, lastPositions, drafts, temps, topps);
    }

    private SpeculativeDecoding.AcceptResult[] verifyWindowOnlineBatch(
            int[] requestIds, int[] lastTokens, int[] lastPositions, int[][] drafts,
            double[] temps, double[] topps) {
        int b = requestIds.length;
        int n = drafts[0].length;
        int windowLen = n + 1;

        long tBookkeeping = System.nanoTime();
        activatePools(requestIds);
        ensureLeanDeltaNetCheckpoints(n);
        speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tBookkeeping);

        int[] startPositions = lastPositions.clone();
        int[] cacheLengths = new int[b];
        long[] toks = new long[b * windowLen];
        for (int i = 0; i < b; i++) {
            cacheLengths[i] = startPositions[i] + windowLen;
            toks[i * windowLen] = lastTokens[i];
            for (int d = 0; d < n; d++) {
                toks[i * windowLen + 1 + d] = drafts[i][d];
            }
        }

        // History-aware MTP: rows whose draft consumed their pending hidden rows keep the verify
        // window's hidden so the accepted prefix can be absorbed on their next draft.
        boolean historyMode = mtpHistoryActive();
        boolean[] hist = new boolean[b];
        for (int i = 0; i < b && historyMode; i++) {
            MtpHistory st = mtpHistory.get(requestIds[i]);
            hist[i] = st != null && st.valid && st.pending == null && st.filled == lastPositions[i];
        }
        setVerifyWindowActive(true);
        long tVerify = System.nanoTime();
        Tensor[] tokenShards = new Tensor[models.length];
        for (int r = 0; r < models.length; r++) {
            tokenShards[r] = Tensor.of(toks).reshape(b, windowLen).to(models[r].device());
        }
        int[][] targetSamples = new int[b][windowLen];
        try {
            Tensor[] logits = forwardWindowVerifyBatch(tokenShards, startPositions, cacheLengths, tpExecutor);
            speculativeTargetForwards.incrementAndGet();
            try (Tensor owned = logits[0]) { // already an owned copy
                long vocab = owned.shape()[owned.dim() - 1];
                try (Tensor flat = owned.reshape(b * windowLen, vocab)) {
                    int[] flatSamples = sampleTargetRows(flat, windowLen, temps, topps);
                    for (int i = 0; i < b; i++) {
                        System.arraycopy(flatSamples, i * windowLen, targetSamples[i], 0, windowLen);
                    }
                }
            }
            speculativeVerifyNanos.addAndGet(System.nanoTime() - tVerify);
        } finally {
            for (Tensor t : tokenShards) {
                if (t != null) {
                    t.close();
                }
            }
            setVerifyWindowActive(false);
        }

        SpeculativeDecoding.AcceptResult[] results = new SpeculativeDecoding.AcceptResult[b];
        int[] restoreSlots = new int[b];
        // 0-indexed window position (valid 0..n) — distinct from restoreSlots'
        // 1-indexed checkpoint slot (valid 1..n+1). Mirrors the single-request
        // path's own restoreDeltaNetCheckpointAtWindowPosition, which passes
        // r+1 to DeltaNetStatePool.restoreCheckpoint but r (not r+1) to
        // QwenModel.setMtpAnchorAtWindowPosition. Reusing restoreSlots for both
        // previously sent an out-of-range window position (n+1) for any
        // fully-accepted row (r == n) sharing a batch with a partial one.
        int[] anchorPositions = new int[b];
        boolean anyPartial = false;
        tBookkeeping = System.nanoTime();
        for (int i = 0; i < b; i++) {
            results[i] = SpeculativeDecoding.acceptGreedy(drafts[i], targetSamples[i]);
            int r = results[i].numDraftAccepted();
            if (hist[i]) {
                // Rows 0..r of this request's window are its newly committed positions.
                MtpHistory st = mtpHistory.get(requestIds[i]);
                Tensor[] rows = new Tensor[models.length];
                boolean ok = true;
                for (int m = 0; m < models.length; m++) {
                    rows[m] = models[m].copyWindowHiddenRows(i, 0, r + 1);
                    ok &= rows[m] != null;
                }
                if (ok) {
                    st.setPending(rows, Arrays.copyOf(drafts[i], r));
                } else {
                    closeAll(rows);
                    st.invalidate("no window hidden captured during batched verify");
                }
            }
            int writtenEnd = lastPositions[i] + windowLen;
            int sealedLen = lastPositions[i] + 1 + r;
            if (KvCachePool.zeroRejectedKv()) {
                truncateKv(requestIds[i], sealedLen, writtenEnd);
            }
            // A fully accepted row's working state already is the end-of-window state, and that
            // slot is not stored (lean checkpoints): -1 = leave the row as it is.
            restoreSlots[i] = r < n ? r + 1 : -1;
            anchorPositions[i] = r;
            if (r < n) {
                anyPartial = true;
            }
        }
        if (!KvCachePool.zeroRejectedKv()) {
            // No per-row KV work is needed any more (attention is bounded by the committed
            // length); the only thing a KV change must still do is drop decode CUDA graphs.
            for (QwenModel m : models) {
                m.invalidateDecodeCudaGraphs();
            }
        }
        long tRestore = System.nanoTime();
        if (anyPartial) {
            for (QwenModel m : models) {
                DeltaNetStatePool pool = m.deltaNetStatePool();
                if (pool != null && pool.boundBatch() > 0) {
                    pool.restoreCheckpointPerRow(restoreSlots);
                }
                if (!historyMode) {
                    m.setMtpAnchorAtWindowPositions(anchorPositions.clone());
                }
            }
        }
        long tScatter = System.nanoTime();
        batchRestoreNs += tScatter - tRestore;
        scatterDeltaNet();
        batchScatterNs += System.nanoTime() - tScatter;
        if (!historyMode) {
            scatterMtpAnchor(requestIds);
        }
        speculativeBookkeepingNanos.addAndGet(System.nanoTime() - tBookkeeping);
        return results;
    }

    /**
     * Offline window verify against the exclusive token buffer.
     */
    private SpeculativeDecoding.AcceptResult verifyWindowOffline(
            Tensor[] tokens, int writePos, int lastToken, int lastPos, int[] drafts,
            double temperature, double topp, ExecutorService pool) {
        int n = drafts.length;
        for (int i = 0; i < n; i++) {
            putToken(tokens, writePos + i, drafts[i]);
        }
        // lastToken already resides at writePos - 1 / lastPos.
        saveDeltaNetCheckpoint();

        int[] targetSamples;
        Tensor[] logitsArr = forwardAll(tokens, lastPos, lastPos + n + 1, pool, true);
        try {
            speculativeTargetForwards.incrementAndGet();
            targetSamples = sampleTargetWindow(logitsArr[0], temperature, topp);
        } finally {
            for (Tensor l : logitsArr) {
                if (l != null) {
                    l.close();
                }
            }
        }
        if (targetSamples.length != n + 1) {
            throw new IllegalStateException(
                    "window logits produced " + targetSamples.length + " samples, expected " + (n + 1));
        }

        SpeculativeDecoding.AcceptResult accept = SpeculativeDecoding.acceptGreedy(drafts, targetSamples);
        int r = accept.numDraftAccepted();
        int writtenEnd = lastPos + n + 1;
        int sealedLen = lastPos + 1 + r;
        truncateKvExclusive(sealedLen, writtenEnd);

        if (r < n) {
            restoreDeltaNetCheckpoint();
            Tensor[] replay = forwardAll(tokens, lastPos, lastPos + r + 1, pool, false);
            for (Tensor l : replay) {
                if (l != null) {
                    l.close();
                }
            }
        }
        return accept;
    }

    /**
     * Eager multi-token target forward for window verify.
     * Never uses decode CUDA graphs ({@code S > 1}).
     *
     * @param scatter when {@code true}, write DeltaNet working rows back to home rows.
     *                {@code scatter} calls are DeltaNet-state-replay only (drafts
     *                already rejected past the sealed prefix): the caller never reads
     *                per-position logits, so {@code lm_head} runs on the last position
     *                only instead of the whole window.
     */
    Tensor forwardVerifyWindow(int requestId, int[] windowTokens, int startPos) {
        return forwardVerifyWindow(requestId, windowTokens, startPos, true);
    }

    private Tensor forwardVerifyWindow(int requestId, int[] windowTokens, int startPos,
                                       boolean scatter) {
        activatePools(requestId);
        long[] toks = new long[windowTokens.length];
        for (int i = 0; i < windowTokens.length; i++) {
            toks[i] = windowTokens[i];
        }
        Tensor[] shards = new Tensor[models.length];
        for (int r = 0; r < models.length; r++) {
            shards[r] = Tensor.of(toks).reshape(1, toks.length).to(models[r].device());
        }
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().setVerifyWindowKernel(true);
            }
        }
        try {
            // scatter=true (DeltaNet replay) discards logits; skip the vocab-sized
            // lm_head projection on every replayed position and only score the last.
            // scatter=false (the primary online-accept verify) is graph-eligible.
            if (scatter) {
                Tensor[] logits = forwardWindow(shards, startPos, tpExecutor, false);
                scatterDeltaNet();
                for (int r = 1; r < logits.length; r++) {
                    if (logits[r] != null) {
                        logits[r].close();
                    }
                }
                return logits[0];
            }
            Tensor[] logits = forwardWindowVerify(shards, startPos, tpExecutor);
            return ownedVerifyWindowLogits(logits);
        } finally {
            for (QwenModel m : models) {
                if (m.kvCachePool() != null) {
                    m.kvCachePool().setVerifyWindowKernel(false);
                }
            }
            for (Tensor t : shards) {
                if (t != null) {
                    t.close();
                }
            }
        }
    }

    /**
     * Returns an owned copy of rank 0's verify-window logits and closes every
     * rank's tensor — <em>except</em> when {@link VerifyCudaGraph#persistentLogits()}
     * reports the graph capture/replay path returned each {@code QwenModel}'s
     * own reused {@code verifyGraphLogitsBuf}, which must survive for the next
     * replay (closing it here would free the buffer a captured graph replay
     * still writes into — a real, reproducible use-after-free found on real
     * TP=4 hardware with both decode and verify CUDA graphs enabled: the
     * lighter single-GPU/TP=2 capture-correctness tests never hit it because
     * their low heap/allocator churn happened not to reuse the freed native
     * handle before the next replay read it back). Mirrors
     * {@code logitsRowFromDecodeOutput}'s identical decode-side pattern.
     */
    private static Tensor ownedVerifyWindowLogits(Tensor[] logits) {
        boolean persistent = VerifyCudaGraph.persistentLogits();
        try {
            Tensor out = logits[0].copy();
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
            VerifyCudaGraph.markPersistentLogits(false);
        }
    }

    private void scatterDeltaNet() {
        for (QwenModel m : models) {
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().scatterActive();
            }
        }
    }

    /**
     * Copies each model's freshly-written {@code lastPreNormHidden} batch
     * (decode/prefill/verify all write it, for whichever {@code requestIds}
     * that forward just processed) into the durable per-request
     * {@link MtpAnchorPool}, keyed by {@code requestIds[i]} for row {@code i}.
     * Call immediately after any forward that might feed a later MTP draft —
     * the engine loop is single-threaded, so nothing else can overwrite the
     * shared field between the forward returning and this running.
     *
     * @param requestIds requestId per row, in the same order used for that forward.
     */
    private void scatterMtpAnchor(int[] requestIds) {
        for (QwenModel m : models) {
            MtpAnchorPool pool = m.mtpAnchorPool();
            Tensor hidden = m.lastPreNormHidden();
            if (pool == null || hidden == null) {
                continue;
            }
            if (requestIds.length == 1) {
                pool.setRow(requestIds[0], hidden);
                continue;
            }
            for (int i = 0; i < requestIds.length; i++) {
                try (var idx = Index.of(i); Tensor row = hidden.get(idx)) {
                    pool.setRow(requestIds[i], row);
                }
            }
        }
    }

    private void saveDeltaNetCheckpoint() {
        saveDeltaNetCheckpointSlots(1);
    }

    /**
     * Sizes checkpoint slots for the checkpoint-replay path (kill-switch gated):
     * slot 0 is the existing pre-round snapshot; slots {@code 1..numDrafts+1}
     * retain per-position state (slot {@code i+1} = state after window
     * position {@code i}), written by {@link GatedDeltaNet}'s per-position
     * verify loop. Falls back to the plain 1-slot sizing when the checkpoint-
     * replay kill switch is off, so the old second-forward path is unaffected.
     *
     * <p>When {@link VerifyCudaGraph#debugDiff()} is also on, reserves one
     * extra slot beyond this (index {@code numDrafts + 2}, or {@code 1} when
     * checkpoint-replay is off) as {@link #debugDiffVerifyGraphVsEager}'s own
     * scratch slot — sized here, up front, since growing the slot count
     * elsewhere wipes every existing slot to zero.
     *
     * @param numDrafts number of draft tokens in this round ({@code n}).
     */
    private void saveDeltaNetCheckpoint(int numDrafts) {
        boolean debugDiff = VerifyCudaGraph.enabled() && VerifyCudaGraph.debugDiff();
        if (MTP_VERIFY_CHECKPOINT_REPLAY && !debugDiff) {
            ensureLeanDeltaNetCheckpoints(numDrafts);
            return;
        }
        int base = MTP_VERIFY_CHECKPOINT_REPLAY ? numDrafts + 2 : 1;
        saveDeltaNetCheckpointSlots(debugDiff ? base + 1 : base);
    }

    /**
     * Checkpoint-replay storage for a window of {@code numDrafts + 1} positions: only slots
     * {@code 1..numDrafts} are ever restored from (a partial accept at position {@code r < numDrafts}
     * restores slot {@code r+1}; a full accept keeps the working state, which already is the
     * end-of-window state; the pre-window slot 0 is never read), so only those are allocated
     * and no pre-window copy is made. Saves {@code 2/(numDrafts+2)} of the checkpoint memory and
     * one copy per layer per round.
     */
    private void ensureLeanDeltaNetCheckpoints(int numDrafts) {
        for (QwenModel m : models) {
            DeltaNetStatePool pool = m.deltaNetStatePool();
            if (pool != null && pool.boundBatch() > 0
                    && pool.ensureSpeculativeCheckpointRange(1, numDrafts)) {
                // Same hazard as saveDeltaNetCheckpointSlots: a captured verify graph references the
                // freed checkpoint tensors.
                m.invalidateVerifyCudaGraphs();
            }
        }
    }

    private void saveDeltaNetCheckpointSlots(int slots) {
        for (QwenModel m : models) {
            DeltaNetStatePool pool = m.deltaNetStatePool();
            if (pool != null && pool.boundBatch() > 0) {
                // A verify CUDA graph captured while checkpoint-replay's
                // per-position loop was writing into the *previous*,
                // now-freed speculative checkpoint tensors would replay
                // against stale memory (illegal access / silent corruption)
                // the next time this bucket's graph replays — mirrors the
                // existing truncateKv -> invalidateVerifyCudaGraphs()
                // convention for the same "buffer this graph references was
                // rebuilt out from under it" hazard.
                if (pool.ensureSpeculativeCheckpoints(slots)) {
                    m.invalidateVerifyCudaGraphs();
                }
                pool.saveCheckpoint(0);
            }
        }
    }

    private void restoreDeltaNetCheckpoint() {
        for (QwenModel m : models) {
            DeltaNetStatePool pool = m.deltaNetStatePool();
            if (pool != null && pool.boundBatch() > 0) {
                pool.restoreCheckpoint(0);
            }
        }
    }

    /**
     * Checkpoint-replay path: restores every model's DeltaNet working rows
     * from the per-position checkpoint retained after window position
     * {@code r} (slot {@code r+1}, written by {@link GatedDeltaNet}'s
     * per-position verify loop) and restores the MTP anchor from the same
     * position — replacing a second full-window forward on partial accept.
     *
     * @param r accepted window position (0-indexed, {@code < numDrafts}).
     */
    private void restoreDeltaNetCheckpointAtWindowPosition(int r) {
        for (QwenModel m : models) {
            DeltaNetStatePool pool = m.deltaNetStatePool();
            if (pool != null && pool.boundBatch() > 0) {
                pool.restoreCheckpoint(r + 1);
            }
            m.setMtpAnchorAtWindowPosition(r);
        }
    }

    /**
     * Diagnostic only ({@link VerifyCudaGraph#debugDiff()}): recomputes the
     * identical verify window via a direct, graph-bypassing eager
     * {@code model.forward(tokens, startPos, true)} call (never
     * {@code forwardVerifyGraph}) and logs the numeric gap against
     * {@code graphLogits} — the graph path's own output. Doubles verify cost
     * every round; stashes/restores DeltaNet state around the extra call
     * using a scratch checkpoint slot one past whatever
     * {@link #saveDeltaNetCheckpoint} sized for this round, so this never
     * perturbs the real checkpoint-replay state the graph path's own
     * trajectory depends on.
     *
     * <p>Re-added 2026-09-30 to chase a real-hardware, non-deterministic
     * verify CUDA graph corruption bug (see CLAUDE.md) — mirrors the
     * original mechanism (removed when checkpoint-replay landed) that first
     * surfaced verify-graph's numeric divergence from eager on real traffic.
     *
     * @param requestId   owning request (for log correlation).
     * @param window      the exact verify window tokens (lastToken + drafts).
     * @param startPos    absolute position of {@code window[0]}.
     * @param graphLogits the graph path's own output logits (not closed here).
     * @param numDrafts   draft count for this round, matching
     *                    {@link #saveDeltaNetCheckpoint}'s own sizing.
     */
    private void debugDiffVerifyGraphVsEager(int requestId, int[] window, int startPos,
                                              Tensor graphLogits, int numDrafts) {
        if (!VerifyCudaGraph.enabled() || !VerifyCudaGraph.debugDiff()) {
            return;
        }
        int scratchSlot = MTP_VERIFY_CHECKPOINT_REPLAY ? numDrafts + 2 : 1;
        // Slot count is sized once, up front, by saveDeltaNetCheckpoint's own
        // ensureSpeculativeCheckpoints call for this round — never here:
        // growing the slot count wipes every slot to zero, which would
        // destroy the real per-position checkpoints the caller depends on.
        for (QwenModel m : models) {
            DeltaNetStatePool pool = m.deltaNetStatePool();
            if (pool != null && pool.boundBatch() > 0) {
                pool.saveCheckpoint(scratchSlot);
                pool.restoreCheckpoint(0);
            }
        }
        long[] toks = new long[window.length];
        for (int i = 0; i < window.length; i++) {
            toks[i] = window[i];
        }
        Tensor[] shards = new Tensor[models.length];
        Tensor[] eager = new Tensor[models.length];
        // capturePreNormHidden (inside forward()) mutates each model's
        // lastPreNormHidden IN PLACE (same persistent buffer, copy-only) --
        // the next round's draftGreedy reads that same field, so the extra
        // eager forward below must not be allowed to leave its own value
        // there. Stash the real (graph-path) value now, restore it after.
        Tensor[] savedHidden = new Tensor[models.length];
        try {
            for (int r = 0; r < models.length; r++) {
                Tensor h = models[r].lastPreNormHidden();
                if (h != null) {
                    savedHidden[r] = h.copy();
                    savedHidden[r].detachFromScopes();
                }
            }
            for (int r = 0; r < models.length; r++) {
                shards[r] = Tensor.of(toks).reshape(1, toks.length).to(models[r].device());
            }
            if (models.length == 1) {
                eager[0] = models[0].forward(shards[0], startPos, true);
            } else {
                List<Future<Tensor>> futures = new ArrayList<>(models.length);
                for (int r = 0; r < models.length; r++) {
                    final int rank = r;
                    futures.add(tpExecutor.submit(() -> {
                        ParallelState.setCurrent(tpGroup.state(rank));
                        try (var guard = Tensor.noGradGuard()) {
                            return models[rank].forward(shards[rank], startPos, true);
                        } finally {
                            ParallelState.clearCurrent();
                        }
                    }));
                }
                for (int r = 0; r < models.length; r++) {
                    eager[r] = futures.get(r).get();
                }
            }
            // Second reference: the exact captured code run eagerly (same kernels, no graph).
            // Restore the pre-round DeltaNet state first (the generic reference above advanced it).
            for (QwenModel m : models) {
                DeltaNetStatePool pool = m.deltaNetStatePool();
                if (pool != null && pool.boundBatch() > 0) {
                    pool.restoreCheckpoint(0);
                }
            }
            Tensor[] same = new Tensor[models.length];
            // Same DeltaNet mode as the graph run (fused checkpoint-emitting window op), so the
            // two computations are op-for-op identical. It rewrites checkpoint slots 1..S with
            // values that must equal the graph run's own (diagnostic only).
            if (MTP_VERIFY_CHECKPOINT_REPLAY) {
                setVerifyWindowActive(true);
            }
            try {
                if (models.length == 1) {
                    same[0] = models[0].forwardVerifyGraphCodeEager(shards[0], startPos);
                } else {
                    List<Future<Tensor>> futures = new ArrayList<>(models.length);
                    for (int r = 0; r < models.length; r++) {
                        final int rank = r;
                        futures.add(tpExecutor.submit(() -> {
                            ParallelState.setCurrent(tpGroup.state(rank));
                            try (var guard = Tensor.noGradGuard()) {
                                return models[rank].forwardVerifyGraphCodeEager(shards[rank], startPos);
                            } finally {
                                ParallelState.clearCurrent();
                            }
                        }));
                    }
                    for (int r = 0; r < models.length; r++) {
                        same[r] = futures.get(r).get();
                    }
                }
                float[][] gRows = logitsRowsToFloat(graphLogits);
                float[][] sRows = logitsRowsToFloat(same[0]);
                float sameMax = 0f;
                int sameMismatch = 0;
                for (int i = 0; i < Math.min(gRows.length, sRows.length); i++) {
                    int ga = 0;
                    int sa = 0;
                    for (int j = 0; j < Math.min(gRows[i].length, sRows[i].length); j++) {
                        if (gRows[i][j] > gRows[i][ga]) {
                            ga = j;
                        }
                        if (sRows[i][j] > sRows[i][sa]) {
                            sa = j;
                        }
                        sameMax = Math.max(sameMax, Math.abs(gRows[i][j] - sRows[i][j]));
                    }
                    if (ga != sa) {
                        sameMismatch++;
                    }
                }
                logger.info("verify-graph debug diff (same-kernels eager): requestId={} startPos={} "
                        + "maxAbs={} argmaxMismatch={}/{}", requestId, startPos, sameMax,
                        sameMismatch, Math.min(gRows.length, sRows.length));
            } finally {
                if (MTP_VERIFY_CHECKPOINT_REPLAY) {
                    setVerifyWindowActive(false);
                }
                closeAll(same);
            }
            float[][] graphRows = logitsRowsToFloat(graphLogits);
            float[][] eagerRows = logitsRowsToFloat(eager[0]);
            float maxAbs = 0f;
            int mismatches = 0;
            int rows = Math.min(graphRows.length, eagerRows.length);
            for (int i = 0; i < rows; i++) {
                float[] g = graphRows[i];
                float[] e = eagerRows[i];
                int cols = Math.min(g.length, e.length);
                int gArg = 0;
                int eArg = 0;
                for (int j = 1; j < cols; j++) {
                    if (g[j] > g[gArg]) {
                        gArg = j;
                    }
                    if (e[j] > e[eArg]) {
                        eArg = j;
                    }
                }
                if (gArg != eArg) {
                    mismatches++;
                }
                for (int j = 0; j < cols; j++) {
                    maxAbs = Math.max(maxAbs, Math.abs(g[j] - e[j]));
                }
            }
            logger.info("verify-graph debug diff: requestId={} startPos={} maxAbs={} argmaxMismatch={}/{}",
                    requestId, startPos, maxAbs, mismatches, rows);
            if (mismatches > 0) {
                logger.warn("verify-graph debug diff: requestId={} startPos={} ARGMAX MISMATCH "
                        + "({}/{} rows) — graph replay disagrees with the eager reference",
                        requestId, startPos, mismatches, rows);
            }
        } catch (Exception e) {
            logger.warn("verify-graph debug diff failed: {}", e.getMessage());
        } finally {
            for (Tensor t : shards) {
                if (t != null) {
                    t.close();
                }
            }
            for (Tensor t : eager) {
                if (t != null) {
                    t.close();
                }
            }
            for (int r = 0; r < models.length; r++) {
                if (savedHidden[r] != null) {
                    Tensor h = models[r].lastPreNormHidden();
                    if (h != null) {
                        smile.torch.Native.copy_(h, savedHidden[r]);
                    }
                    savedHidden[r].close();
                }
            }
            for (QwenModel m : models) {
                DeltaNetStatePool pool = m.deltaNetStatePool();
                if (pool != null && pool.boundBatch() > 0) {
                    pool.restoreCheckpoint(scratchSlot);
                }
            }
        }
    }

    /**
     * Sets whether every model's DeltaNet pool is in the primary MTP
     * verify-window forward, gating {@link GatedDeltaNet}'s per-position
     * verify loop (see {@link DeltaNetStatePool#verifyWindowActive()}).
     */
    private void setVerifyWindowActive(boolean active) {
        for (QwenModel m : models) {
            DeltaNetStatePool pool = m.deltaNetStatePool();
            if (pool != null) {
                pool.setVerifyWindowActive(active);
            }
        }
    }

    /**
     * {@link KvCachePool#truncateTo} rebuilds the FlashInfer CSR on a genuine
     * page-boundary crossing (or bumps it in place within the same page,
     * since Stage 3 of the verify-CUDA-graph plan) — any CUDA graph captured
     * against the old CSR buffer would replay against freed/rebuilt memory
     * once a rebuild happens. Drop decode's graph here unconditionally, on
     * every round, rather than disabling capture for the process: plain
     * (non-speculative) decode steps recapture lazily and keep the graph fast
     * path once no further truncate invalidates it. The verify graph, by
     * contrast, is dropped only when {@code truncateTo} actually rebuilt
     * (returned {@code false}) — in steady state, once a page-stable bucket
     * is reached, this should essentially never fire mid-generation, which is
     * the whole point of Stage 3's bump generalization.
     */
    private void truncateKv(int requestId, int sealedLen, int writtenEnd) {
        for (QwenModel m : models) {
            KvCachePool pool = m.kvCachePool();
            if (pool != null) {
                pool.activateStep(requestId);
                // A CSR rebuild no longer invalidates the verify graph: it reads its own
                // fixed-address CSR (KvCachePool.verifyGraphMetadata).
                pool.truncateTo(sealedLen, writtenEnd);
                m.invalidateDecodeCudaGraphs();
            }
        }
    }

    private void truncateKvExclusive(int sealedLen, int writtenEnd) {
        for (QwenModel m : models) {
            KvCachePool pool = m.kvCachePool();
            if (pool != null) {
                // A CSR rebuild no longer invalidates the verify graph: it reads its own
                // fixed-address CSR (KvCachePool.verifyGraphMetadata).
                pool.truncateTo(sealedLen, writtenEnd);
                m.invalidateDecodeCudaGraphs();
            }
        }
    }

    /**
     * Package-visible hybrid parity helper: window vs sequential greedy argmax
     * and max abs logit difference at each position.
     *
     * @return {@code {windowArgmax[S], seqArgmax[S]}} plus side-channel via
     *         {@link #lastWindowVsSequentialMaxAbs}.
     */
    int[][] windowVsSequentialArgmax(int requestId, int[] windowTokens, int startPos) {
        if (windowTokens == null || windowTokens.length < 1) {
            throw new IllegalArgumentException("windowTokens required");
        }
        int s = windowTokens.length;
        activatePools(requestId);
        saveDeltaNetCheckpoint();

        float[][] windowLogits;
        int[] windowArgmax;
        try (Tensor logits = forwardVerifyWindow(requestId, windowTokens, startPos, false)) {
            windowLogits = logitsRowsToFloat(logits);
            windowArgmax = sampleTargetWindow(logits, 0.0, 1.0);
        }
        truncateKv(requestId, startPos, startPos + s);
        restoreDeltaNetCheckpoint();
        scatterDeltaNet();

        float[][] seqLogits = new float[s][];
        int[] seqArgmax = new int[s];
        for (int i = 0; i < s; i++) {
            try (Tensor logits = decodeStep(new int[]{requestId}, new int[]{windowTokens[i]},
                    new int[]{startPos + i})) {
                seqLogits[i] = logitsRow0ToFloat(logits);
                seqArgmax[i] = Sampling.sampleGreedyTokenId(logits);
            }
        }
        float maxAbs = 0f;
        for (int i = 0; i < s; i++) {
            float[] w = windowLogits[i];
            float[] q = seqLogits[i];
            int n = Math.min(w.length, q.length);
            for (int j = 0; j < n; j++) {
                maxAbs = Math.max(maxAbs, Math.abs(w[j] - q[j]));
            }
        }
        lastWindowVsSequentialMaxAbs = maxAbs;
        return new int[][]{windowArgmax, seqArgmax};
    }

    /**
     * Batched counterpart of {@link #windowVsSequentialArgmax}: proves that
     * running a cohort of concurrent requests' verify windows in <em>one</em>
     * forward (same absolute position — see {@code Qwen.verifyWindowOnlineBatch}'s
     * own javadoc for why) produces, for every row, the identical numeric
     * result each row would get processed alone. Deliberately compares raw
     * logit magnitude, not just argmax (see
     * {@code QwenVerifyGraphFullModelCaptureTest}'s own reasoning: a tiny,
     * legitimate floating-point difference can flip an argmax that is
     * essentially a coin flip on random/untrained weights without
     * indicating any real numerical divergence).
     *
     * @return {@code {windowArgmax[B][S], seqArgmax[B][S]}} plus side-channel
     *         via {@link #lastWindowVsSequentialMaxAbs}.
     */
    int[][][] windowVsSequentialArgmaxBatch(int[] requestIds, int[][] windowTokens, int[] startPositions) {
        int b = requestIds.length;
        int s = windowTokens[0].length;
        activatePools(requestIds);
        saveDeltaNetCheckpointSlots(1);

        long[] toks = new long[b * s];
        int[] cacheLengths = new int[b];
        for (int i = 0; i < b; i++) {
            cacheLengths[i] = startPositions[i] + s;
            for (int t = 0; t < s; t++) {
                toks[i * s + t] = windowTokens[i][t];
            }
        }

        float[][][] windowLogits = new float[b][][];
        int[][] windowArgmax = new int[b][];
        Tensor[] tokenShards = new Tensor[models.length];
        for (int r = 0; r < models.length; r++) {
            tokenShards[r] = Tensor.of(toks).reshape(b, s).to(models[r].device());
        }
        try {
            Tensor[] logits = forwardWindowVerifyBatch(tokenShards, startPositions, cacheLengths, tpExecutor);
            try (Tensor owned = logits[0]) { // already an owned copy
                long vocab = owned.shape()[owned.dim() - 1];
                try (Tensor flat = owned.reshape((long) b * s, vocab)) {
                    int[] flatArgmax = smile.llm.engine.Sampling.sampleGreedyTokenIds(flat);
                    float[][] flatLogits = logitsRowsToFloat(flat);
                    for (int i = 0; i < b; i++) {
                        windowLogits[i] = new float[s][];
                        windowArgmax[i] = new int[s];
                        for (int t = 0; t < s; t++) {
                            windowLogits[i][t] = flatLogits[i * s + t];
                            windowArgmax[i][t] = flatArgmax[i * s + t];
                        }
                    }
                }
            }
        } finally {
            for (Tensor t : tokenShards) {
                if (t != null) {
                    t.close();
                }
            }
        }

        for (int i = 0; i < b; i++) {
            truncateKv(requestIds[i], startPositions[i], startPositions[i] + s);
        }
        restoreDeltaNetCheckpoint();
        scatterDeltaNet();

        float[][][] seqLogits = new float[b][][];
        int[][] seqArgmax = new int[b][];
        for (int i = 0; i < b; i++) {
            seqLogits[i] = new float[s][];
            seqArgmax[i] = new int[s];
            for (int t = 0; t < s; t++) {
                try (Tensor logits = decodeStep(new int[]{requestIds[i]}, new int[]{windowTokens[i][t]},
                        new int[]{startPositions[i] + t})) {
                    seqLogits[i][t] = logitsRow0ToFloat(logits);
                    seqArgmax[i][t] = smile.llm.engine.Sampling.sampleGreedyTokenId(logits);
                }
            }
        }

        float maxAbs = 0f;
        for (int i = 0; i < b; i++) {
            for (int t = 0; t < s; t++) {
                float[] w = windowLogits[i][t];
                float[] q = seqLogits[i][t];
                int n = Math.min(w.length, q.length);
                for (int j = 0; j < n; j++) {
                    maxAbs = Math.max(maxAbs, Math.abs(w[j] - q[j]));
                }
            }
        }
        lastWindowVsSequentialMaxAbs = maxAbs;
        return new int[][][]{windowArgmax, seqArgmax};
    }

    /** Max abs logit delta from the last {@link #windowVsSequentialArgmax} call. */
    volatile float lastWindowVsSequentialMaxAbs;

    private static float[][] logitsRowsToFloat(Tensor logits) {
        Tensor flat = logits;
        boolean close = false;
        if (logits.dim() == 3) {
            long[] sh = logits.shape();
            flat = logits.reshape(sh[0] * sh[1], sh[2]);
            close = true;
        }
        try (Tensor cpu = flat.to(Device.CPU())) {
            long[] sh = cpu.shape();
            int rows = (int) sh[0];
            int cols = (int) sh[1];
            float[] all = cpu.floatArray();
            float[][] out = new float[rows][cols];
            for (int i = 0; i < rows; i++) {
                System.arraycopy(all, i * cols, out[i], 0, cols);
            }
            return out;
        } finally {
            if (close) {
                flat.close();
            }
        }
    }

    private static float[] logitsRow0ToFloat(Tensor logits) {
        try (Tensor cpu = logits.to(Device.CPU())) {
            float[] all = cpu.floatArray();
            if (logits.dim() == 1) {
                return all;
            }
            int cols = (int) logits.shape()[logits.dim() - 1];
            float[] row = new float[cols];
            System.arraycopy(all, 0, row, 0, cols);
            return row;
        }
    }

    /**
     * Package-visible helper for tests: online window verify + metric recording.
     *
     * @return accepted token count ({@code draftsAccepted + 1}).
     */
    int verifyWindowOnlineRecorded(int requestId, int lastToken, int lastPos, int[] drafts) {
        SpeculativeDecoding.AcceptResult accept =
                verifyWindowOnline(requestId, lastToken, lastPos, drafts, 0.0, 1.0);
        recordSpeculativeRound(drafts.length, accept.numDraftAccepted());
        return accept.numTokens();
    }

    /**
     * Package-visible helper for tests: batched online window verify with
     * explicit drafts (bypasses the MTP draft head), mirroring
     * {@link #verifyWindowOnlineRecorded}'s single-request counterpart.
     */
    SpeculativeDecoding.AcceptResult[] verifyWindowOnlineBatchRecorded(int[] requestIds,
            int[] lastTokens, int[] lastPositions, int[][] drafts) {
        return verifyWindowOnlineBatch(requestIds, lastTokens, lastPositions, drafts, 0.0, 1.0);
    }

    /**
     * One MTP draft step on all TP ranks; updates {@code hiddens} to the next
     * MTP hidden (caller owns / closes when {@code own[i]}).
     */
    private int draftOne(int token, int position, int draftStep,
                         Tensor[] hiddens, boolean[] own) {
        Tensor[] logits = new Tensor[models.length];
        if (models.length == 1) {
            try (Tensor tokT = Tensor.of(new long[]{token})) {
                Tensor deviceTok = tokT.to(models[0].device());
                logits[0] = models[0].mtp().draftStep(
                        deviceTok, hiddens[0], position, draftStep);
                deviceTok.close();
            }
        } else {
            List<Future<Tensor>> futures = new ArrayList<>(models.length);
            for (int rank = 0; rank < models.length; rank++) {
                final int r = rank;
                final Tensor hidden = hiddens[r];
                futures.add(tpExecutor.submit(() -> {
                    ParallelState.setCurrent(tpGroup.state(r));
                    try (var guard = Tensor.noGradGuard();
                         Tensor tokT = Tensor.of(new long[]{token})) {
                        Tensor deviceTok = tokT.to(models[r].device());
                        try {
                            return models[r].mtp().draftStep(
                                    deviceTok, hidden, position, draftStep);
                        } finally {
                            deviceTok.close();
                        }
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
                throw new RuntimeException("TP MTP draft failed", e);
            }
        }
        int draft = smile.llm.engine.Sampling.sampleGreedyTokenId(logits[0]);
        for (Tensor l : logits) {
            if (l != null) {
                l.close();
            }
        }
        for (int r = 0; r < models.length; r++) {
            if (own[r] && hiddens[r] != null) {
                hiddens[r].close();
            }
            Tensor next = models[r].mtp().lastDraftHidden();
            if (next == null) {
                throw new IllegalStateException(
                        "MTP draft hidden missing after step " + draftStep + " rank " + r);
            }
            hiddens[r] = next.detach();
            hiddens[r].detachFromScopes();
            own[r] = true;
        }
        return draft;
    }

    /**
     * One batched MTP draft step across a cohort of concurrent requests on
     * all TP ranks; updates {@code hiddens} to the next MTP hidden (caller
     * owns / closes when {@code own[i]}).
     *
     * @param tokens    last sampled / previous draft token per batch row.
     * @param positions absolute RoPE position per batch row.
     * @param draftStep zero-based index within the current draft window
     *                  (uniform across the batch).
     * @return draft token id per batch row.
     */
    private int[] draftOneBatch(int[] tokens, int[] positions, int draftStep,
                                Tensor[] hiddens, boolean[] own) {
        int b = tokens.length;
        long[] tokLongs = new long[b];
        for (int i = 0; i < b; i++) {
            tokLongs[i] = tokens[i];
        }
        Tensor[] logits = new Tensor[models.length];
        if (models.length == 1) {
            try (Tensor tokT = Tensor.of(tokLongs).reshape(b, 1)) {
                Tensor deviceTok = tokT.to(models[0].device());
                logits[0] = models[0].mtp().draftStep(deviceTok, hiddens[0], positions, draftStep);
                deviceTok.close();
            }
        } else {
            List<Future<Tensor>> futures = new ArrayList<>(models.length);
            for (int rank = 0; rank < models.length; rank++) {
                final int r = rank;
                final Tensor hidden = hiddens[r];
                futures.add(tpExecutor.submit(() -> {
                    ParallelState.setCurrent(tpGroup.state(r));
                    try (var guard = Tensor.noGradGuard();
                         Tensor tokT = Tensor.of(tokLongs).reshape(b, 1)) {
                        Tensor deviceTok = tokT.to(models[r].device());
                        try {
                            return models[r].mtp().draftStep(deviceTok, hidden, positions, draftStep);
                        } finally {
                            deviceTok.close();
                        }
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
                throw new RuntimeException("TP MTP draft failed", e);
            }
        }
        int[] drafts = smile.llm.engine.Sampling.sampleGreedyTokenIds(logits[0]);
        for (Tensor l : logits) {
            if (l != null) {
                l.close();
            }
        }
        for (int r = 0; r < models.length; r++) {
            if (own[r] && hiddens[r] != null) {
                hiddens[r].close();
            }
            Tensor next = models[r].mtp().lastDraftHidden();
            if (next == null) {
                throw new IllegalStateException(
                        "MTP draft hidden missing after step " + draftStep + " rank " + r);
            }
            hiddens[r] = next.detach();
            hiddens[r].detachFromScopes();
            own[r] = true;
        }
        return drafts;
    }

    /**
     * Greedy MTP drafts for a batched cohort of concurrent requests. Reads
     * each request's anchor from {@link MtpAnchorPool} (not the shared
     * {@code lastPreNormHidden} field) — see {@link #scatterMtpAnchor}.
     * Draft steps stay sequential (genuinely autoregressive — depth
     * {@code d+1}'s input is depth {@code d}'s own output — this cannot be
     * batched away), but each step is one batched TP dispatch for the whole
     * cohort instead of one dispatch per request.
     *
     * @param requestIds    cohort request ids (order = batch row).
     * @param lastTokens    last accepted/sampled token per row.
     * @param lastPositions absolute position of {@code lastTokens[i]} per row.
     * @param numDrafts     uniform draft depth for this cohort.
     * @return draft tokens {@code [B][numDrafts]}.
     */
    private int[][] draftGreedyBatch(int[] requestIds, int[] lastTokens, int[] lastPositions,
                                     int numDrafts) {
        int b = requestIds.length;
        Tensor[] hiddens = new Tensor[models.length];
        boolean[] own = new boolean[models.length];
        for (int r = 0; r < models.length; r++) {
            hiddens[r] = models[r].mtpAnchorPool().getRows(requestIds);
            own[r] = true;
        }
        int[][] drafts = new int[b][numDrafts];
        int[] tokens = lastTokens.clone();
        try {
            for (int d = 0; d < numDrafts; d++) {
                int[] positions = new int[b];
                for (int i = 0; i < b; i++) {
                    positions[i] = lastPositions[i] + 1 + d;
                }
                int[] stepDrafts = draftOneBatch(tokens, positions, d, hiddens, own);
                for (int i = 0; i < b; i++) {
                    drafts[i][d] = stepDrafts[i];
                }
                tokens = stepDrafts;
            }
        } finally {
            for (int r = 0; r < models.length; r++) {
                if (own[r] && hiddens[r] != null) {
                    hiddens[r].close();
                }
            }
        }
        return drafts;
    }

    /**
     * Greedy MTP drafts for the offline/exclusive-generate path (single
     * sequence, no request-pool binding — reads the shared
     * {@code lastPreNormHidden} field directly).
     */
    private int[] draftGreedy(int lastToken, int lastPos, int numDrafts) {
        Tensor[] hiddens = new Tensor[models.length];
        boolean[] own = new boolean[models.length];
        for (int r = 0; r < models.length; r++) {
            hiddens[r] = models[r].lastPreNormHidden();
            own[r] = false;
        }
        return draftGreedyCore(hiddens, own, lastToken, lastPos, numDrafts);
    }

    /**
     * Greedy MTP drafts for the request-pooled online serving path. Reads
     * the anchor from {@link MtpAnchorPool} (not the shared
     * {@code lastPreNormHidden} field) so a concurrent forward for a
     * different request can never clobber it — see {@link #scatterMtpAnchor}.
     *
     * @param requestId the anchor's owning request.
     */
    private int[] draftGreedy(int requestId, int lastToken, int lastPos, int numDrafts) {
        Tensor[] hiddens = new Tensor[models.length];
        boolean[] own = new boolean[models.length];
        for (int r = 0; r < models.length; r++) {
            hiddens[r] = models[r].mtpAnchorPool().getRow(requestId);
            own[r] = true;
        }
        return draftGreedyCore(hiddens, own, lastToken, lastPos, numDrafts);
    }

    private int[] draftGreedyCore(Tensor[] hiddens, boolean[] own,
                                  int lastToken, int lastPos, int numDrafts) {
        int[] drafts = new int[numDrafts];
        int token = lastToken;
        try {
            for (int d = 0; d < numDrafts; d++) {
                drafts[d] = draftOne(token, lastPos + 1 + d, d, hiddens, own);
                token = drafts[d];
            }
        } finally {
            for (int r = 0; r < models.length; r++) {
                if (own[r] && hiddens[r] != null) {
                    hiddens[r].close();
                }
            }
        }
        return drafts;
    }

    private void activatePools(int requestId) {
        activatePools(new int[]{requestId});
    }

    private void activatePools(int[] requestIds) {
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().activateStep(requestIds);
            }
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().activateStep(requestIds);
            }
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
        if (!mtpHistory.isEmpty()) {
            // A plain decode step advances the backbone without feeding the MTP
            // head, leaving a gap in its history; stop speculating this request.
            for (int id : requestIds) {
                MtpHistory st = mtpHistory.get(id);
                if (st != null) {
                    st.invalidate("plain decodeStep gap");
                }
            }
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
        DecodeCudaGraph.markPersistentLogits(false);
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
                scatterMtpAnchor(requestIds);
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
        releaseMtpHistory(requestId);
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().finishRequest(requestId, sequenceTokens);
            }
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().unbindRequest(requestId);
            }
            if (m.mtpAnchorPool() != null) {
                m.mtpAnchorPool().unbindRequest(requestId);
            }
        }
    }

    @Override
    public void evict(int requestId) {
        ropeDeltaByRequest.remove(requestId);
        releaseMtpHistory(requestId);
        for (QwenModel m : models) {
            if (m.kvCachePool() != null) {
                m.kvCachePool().unbindRequest(requestId);
            }
            if (m.deltaNetStatePool() != null) {
                m.deltaNetStatePool().unbindRequest(requestId);
            }
            if (m.mtpAnchorPool() != null) {
                m.mtpAnchorPool().unbindRequest(requestId);
            }
        }
    }

    @Override
    public void idleDecodeGraphPrefetch() {
        if (!DecodeCudaGraph.preCaptureEnabled()) {
            return;
        }
        KvCachePool pool = models[0].kvCachePool();
        if (pool == null || pool.boundRequestCount() == 0) {
            return;
        }
        for (QwenModel m : models) {
            m.idleAdvancePrefetch();
        }
    }
}
