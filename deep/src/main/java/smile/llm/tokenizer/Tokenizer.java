/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tokenizer;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.Map;
import java.util.regex.Pattern;
import smile.util.Bytes;

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
     * Note that a token may contain only partial bytes of a character.
     * This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.
     * @param tokens The list of token IDs to be decoded.
     * @return The decoded string.
     */
    String decode(int[] tokens);

    /**
     * Try to decode a list of token IDs into a string. This method throws
     * CharacterCodingException if the byte sequence is not legal UTF-8.
     * @param tokens The list of token IDs to be decoded.
     * @return The decoded string.
     * @exception CharacterCodingException If the byte sequence is not legal UTF-8.
     */
    default String tryDecode(int[] tokens) throws CharacterCodingException {
        return decode(tokens);
    }

    /**
     * Segments text into tokens.
     * @param text The input string to be tokenized.
     * @return The tokenized sequence.
     */
    String[] tokenize(String text);

    /**
     * Loads a tiktoken model with default BOS token (<s>) and EOS token (</s>).
     * @param path The tiktoken model file path.
     * @param pattern The regex pattern to split the input text into tokens.
     * @return a tiktoken tokenizer.
     * @throws IOException if fail to load the model.
     */
    static Tiktoken tiktoken(String path, Pattern pattern) throws IOException {
        String bos = "<s>";
        String eos = "</s>";
        return tiktoken(path, pattern, bos, eos, bos, eos);
    }

    /**
     * Loads a tiktoken model.
     * @param path The tiktoken model file path.
     * @param pattern The regex pattern to split the input text into tokens.
     * @param bos The beginning of sequence token.
     * @param eos The end of sequence token.
     * @param specialTokens Optional special tokens.
     * @return a tiktoken tokenizer.
     * @throws IOException if fail to load the model.
     */
    static Tiktoken tiktoken(String path, Pattern pattern, String bos, String eos, String... specialTokens) throws IOException {
        Map<Bytes, Integer> ranks = Tiktoken.load(path);
        return new Tiktoken(pattern, ranks, bos, eos, specialTokens);
    }
}
