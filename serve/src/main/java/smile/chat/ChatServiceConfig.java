/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the LLM chat completion service.
 * All properties are prefixed with {@code smile.chat}.
 *
 * <p>The SentencePiece tokenizer is resolved next to the checkpoint
 * ({@code original/tokenizer.model} or {@code tokenizer.model}); there is no
 * separate tokenizer path property.
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.chat")
public interface ChatServiceConfig {
    /**
     * Local HF-layout checkpoint directory, or a Hugging Face repository id
     * ({@code owner/name}).
     */
    String model();

    /**
     * Maximum sequence length in tokens (prompt + output), analogous to vLLM
     * {@code --max-model-len} / SGLang {@code --context-length}.
     *
     * <p>{@code <= 0} (the default) means auto: at model load, replace with
     * {@code max_position_embeddings} from the model {@code config.json} before
     * any request is served. {@link smile.llm.LanguageModel#maxSeqLen()} always
     * returns that positive resolved value. Set an explicit positive value to
     * cap context below the model default (recommended for large-window models
     * such as Qwen3.5).
     */
    @WithDefault("0")
    int maxSeqLen();

    /**
     * Maximum batch size for parallel generation.
     * Defaults to {@code 1}.
     */
    @WithDefault("1")
    int maxBatchSize();

    /**
     * SGLang {@code --mem-fraction-static}: fraction {@code y} of <em>total</em>
     * GPU memory reserved for the static region (model weights, DeltaNet state
     * pools, and KV cache). The remainder {@code (1 − y) × total} stays free for
     * dynamic activations. KV slots are {@code staticBudget − used}, then capped
     * at {@code maxBatchSize × maxSeqLen}. Defaults to {@code 0.85}.
     *
     * <p>The KV pool is allocated once and never grows per request. When free
     * slots are insufficient for the requested prompt+generation length,
     * generation stops early and returns partial output with
     * {@code finish_reason=length}.
     */
    @WithDefault("0.85")
    double memFractionStatic();

    /**
     * CUDA device index or comma-separated TP device list
     * (e.g. {@code 0} or {@code 0,7}). A single value is the sole / base
     * device; multiple values define the tensor-parallel ranks in order.
     * Defaults to {@code 0}.
     */
    @WithDefault("0")
    String devices();

    /**
     * Tensor-parallel size. When greater than 1 and {@link #devices()} has a
     * single entry {@code d}, ranks use consecutive devices
     * {@code d .. d+tensorParallelSize-1}. When {@link #devices()} lists
     * multiple indices, that list length must equal this size (or this may
     * stay at {@code 1} and the list length defines the TP world size).
     * Defaults to {@code 1}.
     */
    @WithDefault("1")
    int tensorParallelSize();

    /**
     * Pipeline-parallel size. Must remain {@code 1} until multi-node PP lands.
     * Defaults to {@code 1}.
     */
    @WithDefault("1")
    int pipelineParallelSize();

    /**
     * Concurrent safetensors shard loader threads. Each worker reads one shard
     * file onto CPU then fans weights out to TP ranks (peak host RAM scales
     * roughly as {@code threads × shard size}). {@code 0} (default) means auto:
     * {@code min(8, availableProcessors)}, then capped by the number of shard
     * files.
     */
    @WithDefault("0")
    int modelLoaderThreads();

    /**
     * Attention kernel backend: {@code flashinfer} (paged CSR attention) or
     * {@code torch_native} (LibTorch SDPA). Defaults to {@code flashinfer};
     * if FlashInfer is unavailable in {@code libsmile_torch}, the service
     * falls back to {@code torch_native} at startup.
     */
    @WithDefault("flashinfer")
    String attentionBackend();
}
