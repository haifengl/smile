/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.chat;

import java.util.Optional;
import io.vertx.core.json.JsonObject;

/**
 * Creates dynamic wrappers around {@link ChatServiceConfig} and {@link KvCacheConfig}
 * that overlay JSON request overrides on top of existing defaults.
 *
 * @author Haifeng Li
 */
public final class DynamicChatConfig {

    private DynamicChatConfig() {}

    /**
     * Resolves a string parameter from a JsonObject checking multiple alias keys.
     */
    private static String optString(JsonObject json, JsonObject nested, String defaultValue, String... keys) {
        for (String k : keys) {
            if (nested != null && nested.containsKey(k)) {
                Object val = nested.getValue(k);
                if (val != null) return val.toString();
            }
            if (json != null && json.containsKey(k)) {
                Object val = json.getValue(k);
                if (val != null) return val.toString();
            }
        }
        return defaultValue;
    }

    private static int optInt(JsonObject json, JsonObject nested, int defaultValue, String... keys) {
        for (String k : keys) {
            if (nested != null && nested.containsKey(k)) {
                Object val = nested.getValue(k);
                if (val instanceof Number n) return n.intValue();
                if (val != null) {
                    try { return Integer.parseInt(val.toString().trim()); } catch (NumberFormatException ignored) {}
                }
            }
            if (json != null && json.containsKey(k)) {
                Object val = json.getValue(k);
                if (val instanceof Number n) return n.intValue();
                if (val != null) {
                    try { return Integer.parseInt(val.toString().trim()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return defaultValue;
    }

    private static double optDouble(JsonObject json, JsonObject nested, double defaultValue, String... keys) {
        for (String k : keys) {
            if (nested != null && nested.containsKey(k)) {
                Object val = nested.getValue(k);
                if (val instanceof Number n) return n.doubleValue();
                if (val != null) {
                    try { return Double.parseDouble(val.toString().trim()); } catch (NumberFormatException ignored) {}
                }
            }
            if (json != null && json.containsKey(k)) {
                Object val = json.getValue(k);
                if (val instanceof Number n) return n.doubleValue();
                if (val != null) {
                    try { return Double.parseDouble(val.toString().trim()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return defaultValue;
    }

    private static boolean optBoolean(JsonObject json, JsonObject nested, boolean defaultValue, String... keys) {
        for (String k : keys) {
            if (nested != null && nested.containsKey(k)) {
                Object val = nested.getValue(k);
                if (val instanceof Boolean b) return b;
                if (val != null) return Boolean.parseBoolean(val.toString().trim());
            }
            if (json != null && json.containsKey(k)) {
                Object val = json.getValue(k);
                if (val instanceof Boolean b) return b;
                if (val != null) return Boolean.parseBoolean(val.toString().trim());
            }
        }
        return defaultValue;
    }

    /**
     * Creates an overlay {@link ChatServiceConfig}.
     *
     * @param base      base configuration.
     * @param modelSpec model location or repo id.
     * @param overrides optional JSON overrides.
     * @return effective chat service configuration.
     */
    public static ChatServiceConfig overlay(ChatServiceConfig base, String modelSpec, JsonObject overrides) {
        JsonObject nested = (overrides != null && overrides.getValue("config") instanceof JsonObject n) ? n : null;

        int maxSeqLen = optInt(overrides, nested, base.maxSeqLen(), "max_seq_len", "maxSeqLen", "max-seq-len");
        int maxBatchSize = optInt(overrides, nested, base.maxBatchSize(), "max_batch_size", "maxBatchSize", "max-batch-size");
        int maxDecodeBatch = optInt(overrides, nested, base.maxDecodeBatch(), "max_decode_batch", "maxDecodeBatch", "max-decode-batch");
        int prefillTokenBudget = optInt(overrides, nested, base.prefillTokenBudget(), "prefill_token_budget", "prefillTokenBudget", "prefill-token-budget");
        long admissionTimeoutMs = (long) optDouble(overrides, nested, base.admissionTimeoutMs(), "admission_timeout_ms", "admissionTimeoutMs", "admission-timeout-ms");
        long admitCoalesceMs = (long) optDouble(overrides, nested, base.admitCoalesceMs(), "admit_coalesce_ms", "admitCoalesceMs", "admit-coalesce-ms");
        double memFractionStatic = optDouble(overrides, nested, base.memFractionStatic(), "mem_fraction_static", "memFractionStatic", "mem-fraction-static");
        String devices = optString(overrides, nested, base.devices(), "devices");
        int tensorParallelSize = optInt(overrides, nested, base.tensorParallelSize(), "tensor_parallel_size", "tensorParallelSize", "tensor-parallel-size");
        int pipelineParallelSize = optInt(overrides, nested, base.pipelineParallelSize(), "pipeline_parallel_size", "pipelineParallelSize", "pipeline-parallel-size");
        int modelLoaderThreads = optInt(overrides, nested, base.modelLoaderThreads(), "model_loader_threads", "modelLoaderThreads", "model-loader-threads");
        String attentionBackend = optString(overrides, nested, base.attentionBackend(), "attention_backend", "attentionBackend", "attention-backend", "attention");
        String quantization = optString(overrides, nested, base.quantization(), "quantization");
        boolean toolCallingEnabled = optBoolean(overrides, nested, base.toolCallingEnabled(), "tool_calling_enabled", "toolCallingEnabled");
        boolean speculative = optBoolean(overrides, nested, base.speculative(), "speculative");
        int speculativeTokens = optInt(overrides, nested, base.speculativeTokens(), "speculative_tokens", "speculativeTokens", "speculative-tokens");
        int speculativeMaxConcurrency = optInt(overrides, nested, base.speculativeMaxConcurrency(), "speculative_max_concurrency", "speculativeMaxConcurrency", "speculative-max-concurrency");

        return new ChatServiceConfig() {
            @Override public Optional<String> model() { return Optional.ofNullable(modelSpec); }
            @Override public int maxSeqLen() { return maxSeqLen; }
            @Override public int maxBatchSize() { return maxBatchSize; }
            @Override public int maxDecodeBatch() { return maxDecodeBatch; }
            @Override public int prefillTokenBudget() { return prefillTokenBudget; }
            @Override public long admissionTimeoutMs() { return admissionTimeoutMs; }
            @Override public long admitCoalesceMs() { return admitCoalesceMs; }
            @Override public double memFractionStatic() { return memFractionStatic; }
            @Override public String devices() { return devices; }
            @Override public int tensorParallelSize() { return tensorParallelSize; }
            @Override public int pipelineParallelSize() { return pipelineParallelSize; }
            @Override public int modelLoaderThreads() { return modelLoaderThreads; }
            @Override public String attentionBackend() { return attentionBackend; }
            @Override public Optional<String> flashinferAotDir() { return base.flashinferAotDir(); }
            @Override public Optional<String> flashinferCacheDir() { return base.flashinferCacheDir(); }
            @Override public boolean flashinferDownload() { return base.flashinferDownload(); }
            @Override public String flashinferCudaTag() { return base.flashinferCudaTag(); }
            @Override public boolean flashinferAllowTorchFallback() { return base.flashinferAllowTorchFallback(); }
            @Override public String quantization() { return quantization; }
            @Override public boolean toolCallingEnabled() { return toolCallingEnabled; }
            @Override public boolean speculative() { return speculative; }
            @Override public int speculativeTokens() { return speculativeTokens; }
            @Override public int speculativeMaxConcurrency() { return speculativeMaxConcurrency; }
        };
    }

    /**
     * Creates an overlay {@link KvCacheConfig}.
     *
     * @param base      base configuration.
     * @param overrides optional JSON overrides.
     * @return effective KV-cache configuration.
     */
    public static KvCacheConfig overlay(KvCacheConfig base, JsonObject overrides) {
        JsonObject nested = (overrides != null && overrides.getValue("config") instanceof JsonObject n) ? n : null;

        String dtype = optString(overrides, nested, base.dtype(), "kv_dtype", "dtype", "kvDtype");
        int pageSize = optInt(overrides, nested, base.pageSize(), "kv_page_size", "pageSize", "page_size");
        boolean prefixReuse = optBoolean(overrides, nested, base.prefixReuse(), "prefix_reuse", "prefixReuse");
        boolean hybridPrefixReplay = optBoolean(overrides, nested, base.hybridPrefixReplay(), "hybrid_prefix_replay", "hybridPrefixReplay");

        return new KvCacheConfig() {
            @Override public String dtype() { return dtype; }
            @Override public int pageSize() { return pageSize; }
            @Override public boolean prefixReuse() { return prefixReuse; }
            @Override public boolean hybridPrefixReplay() { return hybridPrefixReplay; }
        };
    }
}
