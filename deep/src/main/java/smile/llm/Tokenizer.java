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
package smile.llm;

import java.io.IOException;
import java.nio.file.Paths;
import ai.djl.sentencepiece.*;

/**
 * Tokenizing and encoding/decoding text using SentencePiece.
 *
 * @author Haifeng Li
 */
public class Tokenizer {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Tokenizer.class);
    /** SentencePiece tokenizer. */
    private final SpProcessor tokenizer;
    /** Unknown token (<unk>), default id 0. */
    private final int unk;
    /** BOS (beginning of sentence) token (<s>), default id 1. */
    private final int bos;
    /** EOS (end of sentence) token (</s>), default id 2. */
    private final int eos;

    /**
     * Initializes the Tokenizer with a SentencePiece model.
     * @param path The path to the SentencePiece model file.
     * @throws IOException if fail to load the model.
     */
    public Tokenizer(String path) throws IOException {
        SpTokenizer model = new SpTokenizer(Paths.get(path));
        SpVocabulary voc = SpVocabulary.from(model);
        logger.info("Load SentencePiece model from {}", path);
        tokenizer = model.getProcessor();
        unk = (int) voc.getIndex("<unk>");
        bos = (int) voc.getIndex("<s>");
        eos = (int) voc.getIndex("</s>");
        logger.info("UNK ID: {} | BOS ID: {} | EOS ID: {}", unk, bos, eos);
    }

    /**
     * Encodes a string into a list of token IDs.
     * @param text The input string to be encoded.
     * @return A list of token IDs.
     */
    public int[] encode(String text) {
        return encode(text, false, false);
    }

    /**
     * Encodes a string into a list of token IDs.
     * @param text The input string to be encoded.
     * @param bos Whether to prepend the beginning-of-sequence token.
     * @param eos Whether to append the end-of-sequence token.
     * @return A list of token IDs.
     */
    public int[] encode(String text, boolean bos, boolean eos) {
        int[] t = tokenizer.encode(text);

        int length = t.length;
        if (bos) ++length;
        if (eos) ++length;
        int[] tokens = length > t.length ? new int[length] : t;

        if (bos) {
            tokens[0] = this.bos;
            System.arraycopy(t, 0, tokens, 1, t.length);
        }

        if (eos) {
            tokens[length - 1] = this.eos;
        }

        return tokens;
    }

    /**
     * Decodes a list of token IDs into a string.
     * @param tokens The list of token IDs to be decoded.
     * @return The decoded string.
     */
    public String decode(int[] tokens) {
        return tokenizer.decode(tokens);
    }

    /**
     * Segments text into tokens.
     * @param text The input string to be tokenized.
     * @return The tokenized sequence.
     */
    public String[] tokenize(String text) {
        return tokenizer.tokenize(text);
    }
}
