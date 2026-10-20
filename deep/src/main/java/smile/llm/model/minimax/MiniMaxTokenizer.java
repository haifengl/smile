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
package smile.llm.model.minimax;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import smile.llm.tokenizer.HuggingFaceBpeVocab;
import smile.llm.tokenizer.Tiktoken;
import smile.util.Bytes;

/**
 * MiniMax-M3 tokenizer: NFC normalization, then byte-level BPE with the model's case-aware
 * split regex. Control tokens ({@code ]~b]} BOS, {@code [e~[} EOS, {@code ]!p~[} pad, ...)
 * are taken from {@code tokenizer.json}'s {@code added_tokens}; only specials actually present
 * are registered so no id can fall outside the embedding table.
 *
 * @author Haifeng Li
 */
public class MiniMaxTokenizer extends Tiktoken {
    /** Beginning-of-turn token. */
    public static final String BOS = "]~b]";
    /** End-of-turn token. */
    public static final String EOS = "[e~[";
    /** Padding token. */
    public static final String PAD = "]!p~[";

    /** Candidate specials; those missing from the vocabulary are dropped. */
    private static final String[] CANDIDATE_SPECIALS = {
            PAD, "]~!b[", BOS, EOS, "]<]image[>[", "]<]video[>[", "]<]minimax[>[",
            "<mm:think>", "</mm:think>"
    };

    /** Split regex from {@code tokenizer.json} (letters with case runs, 1-3 digit groups, punctuation, whitespace). */
    private static final Pattern REGEX = Pattern.compile(
            "[^\\r\\n\\p{L}\\p{N}]?[\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{M}]*[\\p{Ll}\\p{Lm}\\p{Lo}\\p{M}]+(?i:'s|'t|'re|'ve|'m|'ll|'d)?"
                    + "|[^\\r\\n\\p{L}\\p{N}]?[\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{M}]+[\\p{Ll}\\p{Lm}\\p{Lo}\\p{M}]*(?i:'s|'t|'re|'ve|'m|'ll|'d)?"
                    + "|\\p{N}{1,3}"
                    + "| ?[^\\s\\p{L}\\p{N}]+[\\r\\n/]*"
                    + "|\\s*[\\r\\n]+"
                    + "|\\s+(?!\\S)"
                    + "|\\s+");

    private final int pad;

    /**
     * Constructor.
     *
     * @param ranks token byte sequence to id map, including added tokens.
     * @throws IllegalArgumentException if the BOS, EOS or pad token is missing.
     */
    public MiniMaxTokenizer(Map<Bytes, Integer> ranks) {
        super(REGEX, ranks, BOS, EOS, presentSpecials(ranks));
        this.pad = specialToken(PAD);
    }

    private static String[] presentSpecials(Map<Bytes, Integer> ranks) {
        List<String> present = new ArrayList<>();
        for (String s : CANDIDATE_SPECIALS) {
            if (ranks.containsKey(new Bytes(s))) {
                present.add(s);
            }
        }
        for (String required : new String[]{BOS, EOS, PAD}) {
            if (!present.contains(required)) {
                throw new IllegalArgumentException("tokenizer is missing required special token " + required);
            }
        }
        return present.toArray(String[]::new);
    }

    /**
     * Loads the tokenizer from a checkpoint directory containing {@code tokenizer.json}.
     *
     * @param checkpointDir model directory.
     * @return tokenizer.
     * @throws IOException if {@code tokenizer.json} is missing or malformed.
     */
    public static MiniMaxTokenizer of(String checkpointDir) throws IOException {
        Path json = Path.of(checkpointDir, "tokenizer.json");
        if (!Files.exists(json)) {
            throw new IOException("tokenizer.json not found in " + checkpointDir);
        }
        return new MiniMaxTokenizer(HuggingFaceBpeVocab.loadTokenizerJson(json));
    }

    @Override
    public int[] encode(String text, boolean bos, boolean eos) {
        return super.encode(Normalizer.normalize(text, Normalizer.Form.NFC), bos, eos);
    }

    /**
     * Padding token id.
     * @return pad token id.
     */
    public int pad() {
        return pad;
    }

    /**
     * Stop token ids used during generation.
     * @return the end-of-turn token id.
     */
    public int[] stopTokens() {
        return new int[]{specialToken(EOS)};
    }
}
