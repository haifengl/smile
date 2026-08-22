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
package smile.llm.engine;

import smile.llm.LanguageModel;
import smile.llm.cache.KvCachePool;

/**
 * Low-level execution surface used by {@link InferenceEngine}.
 *
 * <p>Implementations wrap a concrete checkpoint (Llama, Qwen, …) and expose
 * the KV pool / stop tokens needed for continuous batching. Single-request
 * {@link LanguageModel#generate} remains available for offline use.
 *
 * @author Haifeng Li
 */
public interface ModelExecutor {
    /** Underlying language-model façade. */
    LanguageModel model();

    /**
     * Primary KV cache pool (rank 0 for TP). May be {@code null} only for
     * models without full-attention layers.
     */
    KvCachePool kvCachePool();

    /** Pad token id. */
    int padToken();

    /** Stop / EOS token ids. */
    int[] stopTokens();

    /** Decodes token ids to UTF-8 text. */
    String decode(int[] tokens);

    /**
     * Attempts a streaming decode; returns empty on incomplete UTF-8.
     *
     * @throws java.nio.charset.CharacterCodingException when the sequence is incomplete.
     */
    String tryDecode(int[] tokens, boolean skipSpecial)
            throws java.nio.charset.CharacterCodingException;
}
