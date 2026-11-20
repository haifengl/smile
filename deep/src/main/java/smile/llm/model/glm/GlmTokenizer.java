/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
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
package smile.llm.model.glm;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import smile.llm.tokenizer.HuggingFaceBpeVocab;
import smile.llm.tokenizer.Tiktoken;
import smile.util.Bytes;

/**
 * GLM-5.3-Flash tokenizer: no normalizer, one "Isolated" regex split (contractions, letter runs with an optional
 * leading symbol, 1-3 digit groups, punctuation runs, whitespace) and byte-level BPE with {@code ignore_merges}.
 * Every added token ({@code <|user|>}, {@code <think>}, {@code <tool_call>}, ...) is atomic in template text.
 * The regex matches every character, so the text between matches (which an "Isolated" split would keep) is
 * always empty and the plain tiktoken split loop is equivalent.
 *
 * @author Haifeng Li
 */
public class GlmTokenizer extends Tiktoken {
    /** First token of every prompt ({@code [gMASK]<sop>}). */
    public static final String BOS = "[gMASK]";
    /** Sentence-start marker following {@link #BOS}. */
    public static final String SOP = "<sop>";
    /** Padding and default end-of-text token. */
    public static final String EOS = "<|endoftext|>";

    /** Split regex from {@code tokenizer.json}. */
    private static final Pattern REGEX = Pattern.compile(
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}"
                    + "| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+",
            Pattern.UNICODE_CHARACTER_CLASS);

    /** Control tokens dropped when decoding for display (those flagged special). */
    private final Set<Integer> hidden;
    private final int pad;

    /**
     * One entry of {@code added_tokens} in {@code tokenizer.json}.
     *
     * @param content the token string.
     * @param special whether the token is flagged special.
     */
    public record AddedToken(String content, boolean special) { }

    /**
     * Constructor.
     *
     * @param ranks token byte sequence to id map, including added tokens.
     * @param added the {@code added_tokens} of {@code tokenizer.json}.
     * @throws IllegalArgumentException if a required special token is missing.
     */
    public GlmTokenizer(Map<Bytes, Integer> ranks, List<AddedToken> added) {
        super(REGEX, ranks, BOS, EOS, contents(ranks, added));
        Set<Integer> h = new HashSet<>();
        for (AddedToken t : added) {
            Integer id = specialToken(t.content());
            if (id != null && t.special()) {
                h.add(id);
            }
        }
        this.hidden = Set.copyOf(h);
        this.pad = specialToken(EOS);
    }

    private static String[] contents(Map<Bytes, Integer> ranks, List<AddedToken> added) {
        List<String> present = new ArrayList<>();
        for (AddedToken t : added) {
            if (ranks.containsKey(new Bytes(t.content()))) {
                present.add(t.content());
            }
        }
        for (String required : new String[]{BOS, SOP, EOS}) {
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
     * @return the tokenizer.
     * @throws IOException if {@code tokenizer.json} is missing or malformed.
     */
    public static GlmTokenizer of(String checkpointDir) throws IOException {
        Path json = Path.of(checkpointDir, "tokenizer.json");
        if (!Files.exists(json)) {
            throw new IOException("tokenizer.json not found in " + checkpointDir);
        }
        var added = new ArrayList<AddedToken>();
        for (var node : new tools.jackson.databind.ObjectMapper().readTree(json.toFile()).path("added_tokens")) {
            added.add(new AddedToken(node.path("content").asString(), node.path("special").asBoolean(false)));
        }
        return new GlmTokenizer(HuggingFaceBpeVocab.loadTokenizerJson(json), added);
    }

    /**
     * Encodes template text in which markers such as {@code <|user|>} and {@code <think>} stand for single
     * tokens, as the reference tokenizer does for text containing them.
     *
     * @param text a rendered chat prompt.
     * @return token ids.
     */
    public synchronized int[] encodeWithSpecials(String text) {
        allowSpecialTokens(true);
        try {
            return encode(text, false, false);
        } finally {
            allowSpecialTokens(false);
        }
    }

    /**
     * Decodes tokens. With {@code skipSpecial}, control tokens (role markers, {@code [gMASK]}, {@code <sop>},
     * end-of-text) are dropped; {@code <think>}, {@code </think>} and the tool-call markers are kept so clients
     * can style reasoning and parse tool calls.
     */
    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial) throws CharacterCodingException {
        if (!skipSpecial) {
            return super.tryDecode(tokens, false);
        }
        return super.tryDecode(Arrays.stream(tokens).filter(t -> !hidden.contains(t)).toArray(), false);
    }

    /**
     * Padding token id.
     *
     * @return the pad token id.
     */
    public int pad() {
        return pad;
    }

    /**
     * Stop token ids used during generation ({@code <|endoftext|>}, {@code <|user|>}, {@code <|observation|>}).
     *
     * @return the end-of-sequence ids of the checkpoint's {@code generation_config.json}.
     */
    public int[] stopTokens() {
        return new int[]{specialToken(EOS), specialToken("<|user|>"), specialToken("<|observation|>")};
    }
}
