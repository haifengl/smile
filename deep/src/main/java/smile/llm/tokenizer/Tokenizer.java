/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tokenizer;

import java.io.IOException;

/**
 * Tokenizing and encoding/decoding text.
 *
 * @author Haifeng Li
 */
public interface Tokenizer {
    /**
     * Encodes a string into a list of token IDs.
     * @param text The input string to be encoded.
     * @return A list of token IDs.
     */
    int[] encode(String text);

    /**
     * Encodes a string into a list of token IDs.
     * @param text The input string to be encoded.
     * @param bos Whether to prepend the beginning-of-sequence token.
     * @param eos Whether to append the end-of-sequence token.
     * @return A list of token IDs.
     */
    int[] encode(String text, boolean bos, boolean eos);

    /**
     * Decodes a list of token IDs into a string.
     * @param tokens The list of token IDs to be decoded.
     * @return The decoded string.
     */
    String decode(int[] tokens);

    /**
     * Segments text into tokens.
     * @param text The input string to be tokenized.
     * @return The tokenized sequence.
     */
    String[] tokenize(String text);

    /**
     * Loads a SentencePiece model.
     * @param path The path to the SentencePiece model file.
     * @return a SentencePiece tokenizer.
     * @throws IOException if fail to load the model.
     */
    static Tokenizer sentencePiece(String path) throws IOException {
        return new SentencePiece(path);
    }
}
