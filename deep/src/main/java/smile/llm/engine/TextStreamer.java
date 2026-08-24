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

import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.function.BiFunction;
import smile.llm.GenerationListener;

/**
 * Coalesces generated token ids into streamed UTF-8 text chunks.
 *
 * @author Haifeng Li
 */
public final class TextStreamer {
    /** Default coalesce length before attempting a decode flush. */
    public static final int DEFAULT_CHUNK_TOKENS = 20;

    private final BiFunction<int[], Boolean, String> tryDecode;
    private final int chunkTokens;
    private int chunkStart;
    private final int[] buffer;
    private int length;

    /**
     * @param maxTokens capacity of the completion token buffer.
     * @param tryDecode {@code (tokens, skipSpecial) -> text}; may throw
     *                  {@link CharacterCodingException} as unchecked via wrapper.
     */
    public TextStreamer(int maxTokens, BiFunction<int[], Boolean, String> tryDecode) {
        this(maxTokens, DEFAULT_CHUNK_TOKENS, tryDecode);
    }

    public TextStreamer(int maxTokens, int chunkTokens,
                        BiFunction<int[], Boolean, String> tryDecode) {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be >= 1");
        }
        this.buffer = new int[maxTokens];
        this.chunkTokens = Math.max(1, chunkTokens);
        this.tryDecode = tryDecode;
        this.chunkStart = 0;
        this.length = 0;
    }

    /** Appends one generated token id. */
    public void accept(int tokenId) {
        if (length >= buffer.length) {
            return;
        }
        buffer[length++] = tokenId;
    }

    /**
     * Flushes a text chunk when enough tokens accumulated or {@code force}.
     *
     * @param listener destination; may be {@code null}.
     * @param force    flush even if below {@link #chunkTokens}.
     */
    public void maybeEmit(GenerationListener listener, boolean force) {
        if (listener == null || length <= chunkStart) {
            return;
        }
        if (!force && length - chunkStart < chunkTokens) {
            return;
        }
        int[] slice = Arrays.copyOfRange(buffer, chunkStart, length);
        try {
            String chunk = tryDecode.apply(slice, true);
            chunkStart = length;
            if (chunk != null && !chunk.isEmpty()) {
                listener.onText(chunk);
            }
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof CharacterCodingException) {
                // Incomplete multibyte sequence — wait for more tokens.
                return;
            }
            throw ex;
        }
    }
}
