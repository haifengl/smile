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
                    + "|\\s+",
            // The reference regex engine is Unicode-aware: \s matches U+00A0 and other Unicode spaces.
            Pattern.UNICODE_CHARACTER_CLASS);

    private final int pad;
    /** Control tokens dropped when decoding for display. */
    private final java.util.Set<Integer> hidden;
    /** Ids of the reasoning tags, or -1 when the vocabulary lacks them. */
    private final int thinkOpen;
    private final int thinkClose;

    /**
     * One entry of {@code added_tokens} in {@code tokenizer.json}.
     *
     * @param content the token string.
     * @param special {@code true} if the token is a special control token.
     */
    public record AddedToken(String content, boolean special) { }

    /**
     * Constructor for a vocabulary whose added tokens are the built-in MiniMax set.
     *
     * @param ranks token byte sequence to id map, including added tokens.
     * @throws IllegalArgumentException if the BOS, EOS or pad token is missing.
     */
    public MiniMaxTokenizer(Map<Bytes, Integer> ranks) {
        this(ranks, defaultAdded(ranks));
    }

    /**
     * Constructor.
     *
     * <p>Every added token is atomic when encoding text, special or not (as in the reference tokenizer),
     * which is what makes {@code <tool_call>} and {@code ]<]minimax[>[} single tokens. Tokens flagged special
     * are hidden when decoding for display.
     *
     * @param ranks token byte sequence to id map, including added tokens.
     * @param added the {@code added_tokens} of {@code tokenizer.json}.
     * @throws IllegalArgumentException if the BOS, EOS or pad token is missing.
     */
    public MiniMaxTokenizer(Map<Bytes, Integer> ranks, List<AddedToken> added) {
        super(REGEX, ranks, BOS, EOS, contents(ranks, added));
        this.pad = specialToken(PAD);
        java.util.Set<Integer> h = new java.util.HashSet<>();
        for (AddedToken t : added) {
            Integer id = specialToken(t.content());
            if (id != null && t.special()) {
                h.add(id);
            }
        }
        for (String control : new String[]{PAD, BOS, EOS}) {
            h.add(specialToken(control));
        }
        this.hidden = java.util.Set.copyOf(h);
        Integer open = specialToken("<mm:think>");
        Integer close = specialToken("</mm:think>");
        this.thinkOpen = open == null ? -1 : open;
        this.thinkClose = close == null ? -1 : close;
    }

    private static List<AddedToken> defaultAdded(Map<Bytes, Integer> ranks) {
        List<AddedToken> list = new ArrayList<>();
        for (String s : CANDIDATE_SPECIALS) {
            if (ranks.containsKey(new Bytes(s))) {
                boolean control = !s.equals("]<]minimax[>[") && !s.endsWith("think>");
                list.add(new AddedToken(s, control));
            }
        }
        return list;
    }

    private static String[] contents(Map<Bytes, Integer> ranks, List<AddedToken> added) {
        List<String> present = new ArrayList<>();
        for (AddedToken t : added) {
            if (ranks.containsKey(new Bytes(t.content()))) {
                present.add(t.content());
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
        var added = new ArrayList<AddedToken>();
        for (var node : new tools.jackson.databind.ObjectMapper().readTree(json.toFile()).path("added_tokens")) {
            added.add(new AddedToken(node.path("content").asString(), node.path("special").asBoolean(false)));
        }
        return new MiniMaxTokenizer(HuggingFaceBpeVocab.loadTokenizerJson(json), added);
    }

    @Override
    public int[] encode(String text, boolean bos, boolean eos) {
        return super.encode(Normalizer.normalize(text, Normalizer.Form.NFC), bos, eos);
    }

    /**
     * Encodes template text in which markers such as {@code ]~b]}, {@code [e~[} and {@code ]<]minimax[>[}
     * stand for single special tokens, as the reference tokenizer does for text containing them.
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
     * Decodes tokens. With {@code skipSpecial}, control tokens ({@code ]~b]}, {@code [e~[}, padding, ...) are
     * dropped, the reasoning tags {@code <mm:think>} and {@code </mm:think>} are rendered as {@code <think>}
     * and {@code </think>} (the form chat clients style as reasoning), and the tool-call marker
     * {@code ]<]minimax[>[} stays so tool calls can be parsed from the text.
     */
    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial) throws java.nio.charset.CharacterCodingException {
        if (!skipSpecial) {
            return super.tryDecode(tokens, false);
        }
        StringBuilder out = new StringBuilder();
        int from = 0;
        for (int i = 0; i <= tokens.length; i++) {
            boolean boundary = i == tokens.length || tokens[i] == thinkOpen || tokens[i] == thinkClose;
            if (!boundary) {
                continue;
            }
            int[] segment = java.util.Arrays.stream(tokens, from, i).filter(t -> !hidden.contains(t)).toArray();
            if (segment.length > 0) {
                out.append(super.tryDecode(segment, false));
            }
            if (i < tokens.length) {
                out.append(tokens[i] == thinkOpen ? "<think>" : "</think>");
            }
            from = i + 1;
        }
        return out.toString();
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
