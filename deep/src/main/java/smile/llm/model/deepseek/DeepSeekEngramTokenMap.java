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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.tokenizer.Gpt2ByteMap;

/**
 * Maps every token id to a "compressed" id, so that tokens which normalize alike (" The", "the", "THE") share one.
 * The engram n-gram hashes are computed over these ids, and every hash multiplier derives from the compressed
 * vocabulary size, so the partition must match the training one exactly (the released model has 99092 classes).
 *
 * <p>Each id is decoded alone (byte-level BPE, lossy UTF-8). A token that decodes to a replacement character is
 * keyed by its raw {@code tokenizer.json} string; otherwise by the text after NFKC, NFD, removal of all marks
 * (Mn, Mc and Me: removing only Mn gives 99510 classes), lower-casing, collapsing {@code [ \t\r\n]+} to one space and
 * stripping whitespace (a lone space stays). Raw and normalized keys share one key space, as in the reference,
 * so a raw key can coincide with a normalized one.
 *
 * @author Haifeng Li
 */
public final class DeepSeekEngramTokenMap {
    private static final Pattern RUN = Pattern.compile("[ \\t\\r\\n]+");
    private static final char SENTINEL = '';

    private final int[] lookup;
    private final int size;

    private DeepSeekEngramTokenMap(int[] lookup, int size) {
        this.lookup = lookup;
        this.size = size;
    }

    /**
     * Maps a token id to its compressed id.
     *
     * @param token the token id.
     * @return the compressed id.
     */
    public int map(int token) {
        return lookup[token];
    }

    /**
     * The whole lookup table.
     *
     * @return a copy of the table, indexed by token id.
     */
    public int[] table() {
        return lookup.clone();
    }

    /**
     * Number of compressed ids.
     *
     * @return the compressed vocabulary size.
     */
    public int size() {
        return size;
    }

    /**
     * Builds the map from a {@code tokenizer.json}.
     *
     * @param tokenizerJson the file.
     * @param vocabSize     number of ids to map (the model's vocabulary size); ids without a token map to ""'s class.
     * @return the map.
     * @throws IOException if the file cannot be read.
     */
    public static DeepSeekEngramTokenMap load(Path tokenizerJson, int vocabSize) throws IOException {
        JsonNode root = new ObjectMapper().readTree(tokenizerJson.toFile());
        String[] tokens = new String[vocabSize];
        root.path("model").path("vocab").properties().forEach(e -> {
            if (e.getValue().asInt() < vocabSize) {
                tokens[e.getValue().asInt()] = e.getKey();
            }
        });
        for (JsonNode a : root.path("added_tokens")) {
            if (a.get("id").asInt() < vocabSize) {
                tokens[a.get("id").asInt()] = a.get("content").asString();
            }
        }
        Map<String, Integer> classes = new HashMap<>();
        int[] lookup = new int[vocabSize];
        for (int id = 0; id < vocabSize; id++) {
            String raw = tokens[id] == null ? "" : tokens[id];
            String text = raw.isEmpty() ? "" : decode(raw);
            String key;
            if (text.indexOf('�') >= 0) {
                key = raw;
            } else {
                String n = normalize(text);
                key = n.isEmpty() ? text : n;
            }
            Integer c = classes.get(key);
            if (c == null) {
                c = classes.size();
                classes.put(key, c);
            }
            lookup[id] = c;
        }
        return new DeepSeekEngramTokenMap(lookup, classes.size());
    }

    /** Decodes a vocabulary string: byte-level when every char is in the GPT-2 alphabet, else as is. */
    private static String decode(String token) {
        byte[] bytes = new byte[token.length()];
        for (int i = 0; i < token.length(); i++) {
            int b = Gpt2ByteMap.unicodeToByte(token.charAt(i));
            if (b < 0) {
                return token;
            }
            bytes[i] = (byte) b;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String normalize(String text) {
        String s = Normalizer.normalize(Normalizer.normalize(text, Normalizer.Form.NFKC), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(c -> {
            int t = Character.getType(c);
            if (t != Character.NON_SPACING_MARK && t != Character.COMBINING_SPACING_MARK && t != Character.ENCLOSING_MARK) {
                sb.appendCodePoint(Character.toLowerCase(c));
            }
        });
        s = RUN.matcher(sb).replaceAll(" ");
        if (s.equals(" ")) {
            s = String.valueOf(SENTINEL);
        }
        int start = 0;
        int end = s.length();
        while (start < end && isWhite(s.codePointAt(start))) {
            start += Character.charCount(s.codePointAt(start));
        }
        while (end > start && isWhite(s.codePointBefore(end))) {
            end -= Character.charCount(s.codePointBefore(end));
        }
        return s.substring(start, end).replace(SENTINEL, ' ');
    }

    /** Unicode White_Space (Rust's {@code char::is_whitespace}). */
    private static boolean isWhite(int c) {
        return Character.isWhitespace(c) || c == 0xA0 || c == 0x2007 || c == 0x202F || c == 0x85;
    }
}
