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
package smile.llm.tokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import smile.util.Bytes;

/**
 * GPT-2 / HuggingFace byte-level BPE mapping between raw bytes and the
 * printable Unicode characters used as {@code tokenizer.json} vocab keys.
 *
 * <p>HF stores each vocab entry as a string of these mapped characters.
 * Tiktoken-style BPE operates on raw UTF-8 bytes, so keys must be converted
 * with {@link #vocabTokenToBytes(String)} when loading an HF vocab.
 *
 * @author Haifeng Li
 * @see <a href="https://github.com/openai/gpt-2/blob/master/src/encoder.py">GPT-2 encoder</a>
 */
public final class Gpt2ByteMap {
    private static final char[] BYTE_TO_UNICODE = new char[256];
    private static final Map<Character, Integer> UNICODE_TO_BYTE = new HashMap<>(512);

    static {
        List<Integer> bs = new ArrayList<>(256);
        for (int i = '!'; i <= '~'; i++) {
            bs.add(i);
        }
        for (int i = '¡'; i <= '¬'; i++) {
            bs.add(i);
        }
        for (int i = '®'; i <= 'ÿ'; i++) {
            bs.add(i);
        }
        Set<Integer> present = new HashSet<>(bs);
        List<Integer> cs = new ArrayList<>(bs);
        int n = 0;
        for (int b = 0; b < 256; b++) {
            if (!present.contains(b)) {
                bs.add(b);
                cs.add(256 + n);
                n++;
            }
        }
        for (int i = 0; i < 256; i++) {
            int b = bs.get(i);
            char c = (char) cs.get(i).intValue();
            BYTE_TO_UNICODE[b] = c;
            UNICODE_TO_BYTE.put(c, b);
        }
    }

    private Gpt2ByteMap() {}

    /**
     * Converts an HF vocab token string (GPT-2 unicode-mapped) into the raw
     * byte sequence Tiktoken BPE expects. Falls back to UTF-8 of {@code token}
     * if any character is outside the GPT-2 map (e.g. unusual added tokens).
     *
     * @param token vocab key from {@code tokenizer.json} / {@code vocab.json}.
     * @return raw bytes for the rank map.
     */
    public static Bytes vocabTokenToBytes(String token) {
        byte[] out = new byte[token.length()];
        for (int i = 0; i < token.length(); i++) {
            Integer b = UNICODE_TO_BYTE.get(token.charAt(i));
            if (b == null) {
                return new Bytes(token);
            }
            out[i] = b.byteValue();
        }
        return new Bytes(out);
    }

    /**
     * Maps a raw byte to its GPT-2 vocab character (for tests / debugging).
     */
    static char byteToUnicode(int unsignedByte) {
        return BYTE_TO_UNICODE[unsignedByte & 0xff];
    }
}
