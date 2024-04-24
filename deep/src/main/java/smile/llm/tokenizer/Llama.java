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

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import smile.util.Bytes;

/**
 * Custom tokenizer for Llama 3 models.
 *
 * @author Haifeng Li
 */
public class Llama extends Tiktoken {
    /** Token splitting regex. */
    private static final Pattern regex = Pattern.compile("(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+");

    /**
     * Constructor with default BOS, EOS, and special tokens.
     * @param encoder The token to id map.
     */
    public Llama(Map<Bytes, Integer> encoder) {
        this(encoder, "<|begin_of_text|>", "<|end_of_text|>", specialTokens());
    }

    /**
     * Constructor.
     * @param encoder The token to id map.
     * @param bos beginning of sequence token.
     * @param eos end of sequence token.
     * @param specialTokens Optional special tokens.
     */
    public Llama(Map<Bytes, Integer> encoder, String bos, String eos, String... specialTokens) {
        super(regex, encoder, bos, eos, specialTokens);
    }

    /**
     * Returns the default special tokens.
     * @return the default special tokens.
     */
    private static String[] specialTokens() {
        final int numReservedSpecialTokens = 256;
        String[] specialTokens = {
                "<|begin_of_text|>",
                "<|end_of_text|>",
                "<|reserved_special_token_0|>",
                "<|reserved_special_token_1|>",
                "<|reserved_special_token_2|>",
                "<|reserved_special_token_3|>",
                "<|start_header_id|>",
                "<|end_header_id|>",
                "<|reserved_special_token_4|>",
                "<|eot_id|>",  // end of turn
        };

        int base = specialTokens.length;
        specialTokens = Arrays.copyOf(specialTokens, specialTokens.length + numReservedSpecialTokens);
        for (int i = 0; i < numReservedSpecialTokens; i++) {
            specialTokens[base + i] = String.format("<|reserved_special_token_{%d}|>", i+5);
        }

        return specialTokens;
    }
}
