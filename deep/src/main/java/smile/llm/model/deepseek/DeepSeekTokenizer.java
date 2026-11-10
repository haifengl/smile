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
package smile.llm.model.deepseek;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import smile.llm.tokenizer.HuggingFaceBpeVocab;
import smile.llm.tokenizer.Tiktoken;
import smile.util.Bytes;

/**
 * DeepSeek-V4.1 tokenizer: no normalizer; added tokens are matched first and stay atomic; the text between
 * them goes through the three "Isolated" splits of {@code tokenizer.json} (1-3 digit groups, CJK/kana runs, then
 * the word/punctuation/whitespace regex) before byte-level BPE.
 *
 * <p>Unlike tiktoken's {@code find()} loop, an "Isolated" split keeps the text <em>between</em> matches as pieces
 * of their own, which the following stage splits further, so {@link #tokenize} is overridden.
 *
 * @author Haifeng Li
 */
public class DeepSeekTokenizer extends Tiktoken {
    /** Beginning-of-sentence token. */
    public static final String BOS = "<｜begin▁of▁sentence｜>";
    /** End-of-sentence token. */
    public static final String EOS = "<｜end▁of▁sentence｜>";
    /** Padding token. */
    public static final String PAD = "<｜▁pad▁｜>";

    /** Stage 1: digit groups. */
    private static final Pattern DIGITS = Pattern.compile("\\p{N}{1,3}");
    /** Stage 2: CJK ideograph / hiragana / katakana runs. */
    private static final Pattern CJK = Pattern.compile("[一-龥぀-ゟ゠-ヿ]+");
    /** Stage 3: words, punctuation runs and whitespace. */
    private static final Pattern WORDS = Pattern.compile(
            "[!\"#$%&'()*+,\\-./:;<=>?@\\[\\\\\\]^_`{|}~][A-Za-z]+"
                    + "|[^\r\n\\p{L}\\p{P}\\p{S}]?[\\p{L}\\p{M}]+"
                    + "| ?[\\p{P}\\p{S}]+[\r\n]*"
                    + "|\\s*[\r\n]+"
                    + "|\\s+(?!\\S)"
                    + "|\\s+",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern[] STAGES = {DIGITS, CJK, WORDS};

    /** Role and turn markers hidden when decoding for display (the ones flagged special are hidden too). */
    private static final String[] MARKERS = {"<｜User｜>", "<｜Assistant｜>", "<｜System｜>", "<|EOT|>"};

    /** Alternation of every added token, longest first, so the leftmost-longest match wins. */
    private final Pattern addedPattern;
    private final Set<String> added;
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
     * @param ranks the token byte sequence to id map, including added tokens.
     * @param addedTokens the {@code added_tokens} of {@code tokenizer.json}.
     */
    public DeepSeekTokenizer(Map<Bytes, Integer> ranks, List<AddedToken> addedTokens) {
        super(WORDS, ranks, BOS, EOS, contents(ranks, addedTokens));
        this.added = new HashSet<>(Arrays.asList(contents(ranks, addedTokens)));
        String[] longestFirst = added.stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .toArray(String[]::new);
        StringBuilder regex = new StringBuilder();
        for (String s : longestFirst) {
            if (!regex.isEmpty()) {
                regex.append('|');
            }
            regex.append(Pattern.quote(s));
        }
        this.addedPattern = Pattern.compile(regex.toString());
        Set<Integer> h = new HashSet<>();
        for (AddedToken t : addedTokens) {
            Integer id = specialToken(t.content());
            if (id != null && t.special()) {
                h.add(id);
            }
        }
        for (String marker : MARKERS) {
            Integer id = specialToken(marker);
            if (id != null) {
                h.add(id);
            }
        }
        this.hidden = Set.copyOf(h);
        this.pad = specialToken(PAD);
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
     * @return the tokenizer.
     * @throws IOException if {@code tokenizer.json} is missing or malformed.
     */
    public static DeepSeekTokenizer of(String checkpointDir) throws IOException {
        Path json = Path.of(checkpointDir, "tokenizer.json");
        if (!Files.exists(json)) {
            throw new IOException("tokenizer.json not found in " + checkpointDir);
        }
        var list = new ArrayList<AddedToken>();
        for (var node : new tools.jackson.databind.ObjectMapper().readTree(json.toFile()).path("added_tokens")) {
            list.add(new AddedToken(node.path("content").asString(), node.path("special").asBoolean(false)));
        }
        return new DeepSeekTokenizer(HuggingFaceBpeVocab.loadTokenizerJson(json), list);
    }

    /**
     * Splits text into BPE words. With special tokens allowed, added tokens are cut out first (leftmost, longest)
     * and kept whole; everything else, including text between matches, goes through the three isolated splits.
     */
    @Override
    public String[] tokenize(String text) {
        List<String> out = new ArrayList<>();
        if (isSpecialTokenAllowed()) {
            Matcher m = addedPattern.matcher(text);
            int cursor = 0;
            while (m.find()) {
                if (m.start() > cursor) {
                    split(text.substring(cursor, m.start()), out);
                }
                out.add(m.group());
                cursor = m.end();
            }
            if (cursor < text.length()) {
                split(text.substring(cursor), out);
            }
        } else {
            split(text, out);
        }
        return out.toArray(String[]::new);
    }

    private static void split(String text, List<String> out) {
        List<String> pieces = List.of(text);
        for (Pattern stage : STAGES) {
            List<String> next = new ArrayList<>();
            for (String piece : pieces) {
                isolate(stage, piece, next);
            }
            pieces = next;
        }
        out.addAll(pieces);
    }

    /** HF "Isolated" split: matches become pieces, and so does the unmatched text around them. */
    private static void isolate(Pattern pattern, String text, List<String> out) {
        Matcher m = pattern.matcher(text);
        int cursor = 0;
        while (m.find()) {
            if (m.end() == m.start()) {
                continue;
            }
            if (m.start() > cursor) {
                out.add(text.substring(cursor, m.start()));
            }
            out.add(m.group());
            cursor = m.end();
        }
        if (cursor < text.length()) {
            out.add(text.substring(cursor));
        }
    }

    /**
     * Encodes template text in which markers such as {@code <｜User｜>} stand for single tokens.
     *
     * @param text a rendered chat prompt.
     * @return the token ids.
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
     * Decodes tokens; with {@code skipSpecial}, BOS/EOS/pad and the role markers are dropped while
     * {@code <think>}, {@code </think>} and {@code ｜DSML｜} stay so reasoning and tool calls can be parsed.
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
     * Stop token ids used during generation.
     *
     * @return the end-of-sentence token id.
     */
    public int[] stopTokens() {
        return new int[]{specialToken(EOS)};
    }
}
