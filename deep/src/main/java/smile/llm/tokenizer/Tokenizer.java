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
     * @param path The SentencePiece model file path.
     * @return a SentencePiece tokenizer.
     * @throws IOException if fail to load the model.
     */
    static SentencePiece sentencePiece(String path) throws IOException {
        return new SentencePiece(path);
    }

    /**
     * Loads a tiktoken model with default BOS token (<s>) and EOS token (</s>).
     * @param path The tiktoken model file path.
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
     * @param bos beginning of sequence token.
     * @param eos end of sequence token.
     * @param specialTokens Optional special tokens.
     * @return a tiktoken tokenizer.
     * @throws IOException if fail to load the model.
     */
    static Tiktoken tiktoken(String path, Pattern pattern, String bos, String eos, String... specialTokens) throws IOException {
        Map<Bytes, Integer> encoder = Tiktoken.load(path);
        return new Tiktoken(pattern, encoder, bos, eos, specialTokens);
    }

    /**
     * Loads a llama3 tokenizer model.
     * @param path The llama3 model file path.
     * @return a llama3 tokenizer.
     * @throws IOException if fail to load the model.
     */
    static Llama llama(String path) throws IOException {
        Map<Bytes, Integer> encoder = Tiktoken.load(path);
        return new Llama(encoder);
    }
}
