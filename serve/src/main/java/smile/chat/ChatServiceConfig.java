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
     * Maximum sequence length (context window) in tokens.
     * Defaults to {@code 4096}.
     */
    @WithDefault("4096")
    int maxSeqLen();

    /**
     * Maximum batch size for parallel generation.
     * Defaults to {@code 1}.
     */
    @WithDefault("1")
    int maxBatchSize();

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
}
