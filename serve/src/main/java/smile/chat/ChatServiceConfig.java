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
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.chat")
public interface ChatServiceConfig {
    /** Path to the LLM model directory or file. */
    String model();

    /** Path to the SentencePiece tokenizer model file. */
    String tokenizer();

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
     * GPU device index to use for inference ({@code 0}-based).
     * Defaults to {@code 0} (first GPU).
     */
    @WithDefault("0")
    byte device();
}
