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
package smile.llm.llama;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import smile.llm.tokenizer.Tiktoken;
import smile.util.Bytes;
import smile.util.IntArrayList;

/**
 * Custom tokenizer for Llama 3 models.
 *
 * @author Haifeng Li
 */
public class Tokenizer extends Tiktoken {
    /**
     * Token splitting regex.
     */
    private static final Pattern regex = Pattern.compile("(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+");

    /**
     * Constructor with default BOS, EOS, and special tokens.
     *
     * @param ranks The token to rank map.
     */
    public Tokenizer(Map<Bytes, Integer> ranks) {
        this(ranks, "<|begin_of_text|>", "<|end_of_text|>", specialTokens());
    }

    /**
     * Constructor.
     *
     * @param ranks         The token to id map.
     * @param bos           beginning of sequence token.
     * @param eos           end of sequence token.
     * @param specialTokens Optional special tokens.
     */
    public Tokenizer(Map<Bytes, Integer> ranks, String bos, String eos, String... specialTokens) {
        super(regex, ranks, bos, eos, specialTokens);
    }

    /**
     * Returns the default special tokens.
     *
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
            specialTokens[base + i] = String.format("<|reserved_special_token_{%d}|>", i + 5);
        }

        return specialTokens;
    }

    /**
     * Encodes a message header.
     * @param message the message.
     * @param tokens the buffer to store tokens.
     */
    private void encodeHeader(Message message, IntArrayList tokens) {
        tokens.add(specialTokens.get("<|start_header_id|>"));
        tokens.add(encode(message.role().name(), false, false));
        tokens.add(specialTokens.get("<|end_header_id|>"));
        tokens.add(encode("\n\n", false, false));
    }

    /**
     * Encodes a message.
     * @param message the message.
     * @param tokens the buffer to store tokens.
     */
    private void encodeMessage(Message message, IntArrayList tokens) {
        encodeHeader(message, tokens);
        tokens.add(encode(message.content(), false, false));
        tokens.add(specialTokens.get("<|eot_id|>"));
    }

    /**
     * Encodes a message.
     * @param message the message.
     * @return the tokens.
     */
    public int[] encodeMessage(Message message) {
        IntArrayList tokens = new IntArrayList();
        encodeMessage(message, tokens);
        return tokens.toArray();
    }

    /**
     * Encodes the messages of a dialog.
     * @param dialog the messages.
     * @return the tokens.
     */
    public int[] encodeDialog(Message... dialog) {
        IntArrayList tokens = new IntArrayList();
        tokens.add(specialTokens.get("<|begin_of_text|>"));
        for (var message : dialog) {
            encodeMessage(message, tokens);
        }
        // Add the start of an assistant message for the model to complete.
        encodeHeader(new Message(Role.assistant, ""), tokens);
        return tokens.toArray();
    }

    /**
     * Loads a llama3 tokenizer model.
     * @param path The llama3 model file path.
     * @return a llama3 tokenizer.
     * @throws IOException if fail to load the model.
     */
    public static Tokenizer of(String path) throws IOException {
        Map<Bytes, Integer> encoder = Tiktoken.load(path);
        return new Tokenizer(encoder);
    }
}
