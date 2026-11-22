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
package smile.llm.model.inkling;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.tokenizer.HuggingFaceBpeVocab;
import smile.llm.tokenizer.Tiktoken;
import smile.util.Bytes;

/**
 * Inkling-2 tokenizer: byte-level BPE (200k vocabulary, no normalizer) with the case-aware split regex of
 * {@code tokenizer.json}, plus 60 added tokens ({@code <|message_user|>}, {@code <|content_text|>},
 * {@code <|end_message|>}, ...) that are atomic wherever they appear in template text.
 *
 * <p>There is no beginning-of-text token in a prompt: the chat template starts with the first message header.
 * The model ends a reply with {@code <|content_model_end_sampling|>} (the EOS), and every reply starts with
 * {@code <|message_model|>}, which the model itself generates (the prompt ends after a closed message).
 *
 * @author Haifeng Li
 */
public class InklingTokenizer extends Tiktoken {
    /** Beginning-of-text token (unused by the chat template). */
    public static final String BOS = "<|begin_of_text|>";
    /** End of a reply: the token the model emits to stop. */
    public static final String EOS = "<|content_model_end_sampling|>";
    /** Padding token. */
    public static final String PAD = "<|endoftext|>";
    /** Header of a user message. */
    public static final String MESSAGE_USER = "<|message_user|>";
    /** Header of a model message. */
    public static final String MESSAGE_MODEL = "<|message_model|>";
    /** Header of a system message. */
    public static final String MESSAGE_SYSTEM = "<|message_system|>";
    /** Header of a tool message. */
    public static final String MESSAGE_TOOL = "<|message_tool|>";
    /** Content type: plain text. */
    public static final String CONTENT_TEXT = "<|content_text|>";
    /** Content type: image placeholder. */
    public static final String CONTENT_IMAGE = "<|content_image|>";
    /** Content type: reasoning. */
    public static final String CONTENT_THINKING = "<|content_thinking|>";
    /** Content type: XML payload (tool declarations). */
    public static final String CONTENT_XML = "<|content_xml|>";
    /** Content type: tool error. */
    public static final String CONTENT_TOOL_ERROR = "<|content_tool_error|>";
    /** Content type: JSON tool invocation. */
    public static final String CONTENT_INVOKE_TOOL_JSON = "<|content_invoke_tool_json|>";
    /** Content type: free-text tool invocation. */
    public static final String CONTENT_INVOKE_TOOL_TEXT = "<|content_invoke_tool_text|>";
    /** End of a message. */
    public static final String END_MESSAGE = "<|end_message|>";
    /** The image placeholder token that stands for one image's embeddings. */
    public static final String IMAGE_TOKEN = "<|unused_200054|>";

    /** Split regex of {@code tokenizer.json}; same as MiniMax-M3's. */
    private static final Pattern REGEX = Pattern.compile(
            "[^\\r\\n\\p{L}\\p{N}]?[\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{M}]*[\\p{Ll}\\p{Lm}\\p{Lo}\\p{M}]+(?i:'s|'t|'re|'ve|'m|'ll|'d)?"
                    + "|[^\\r\\n\\p{L}\\p{N}]?[\\p{Lu}\\p{Lt}\\p{Lm}\\p{Lo}\\p{M}]+[\\p{Ll}\\p{Lm}\\p{Lo}\\p{M}]*(?i:'s|'t|'re|'ve|'m|'ll|'d)?"
                    + "|\\p{N}{1,3}"
                    + "| ?[^\\s\\p{L}\\p{N}]+[\\r\\n/]*"
                    + "|\\s*[\\r\\n]+"
                    + "|\\s+(?!\\S)"
                    + "|\\s+",
            Pattern.UNICODE_CHARACTER_CLASS);

    private final Set<Integer> hidden;

    /**
     * Constructor.
     *
     * @param ranks token byte sequence to id map, including the added tokens.
     * @param added the added tokens (content) of {@code tokenizer.json}.
     * @throws IllegalArgumentException if a required control token is missing.
     */
    public InklingTokenizer(Map<Bytes, Integer> ranks, List<String> added) {
        super(REGEX, ranks, BOS, EOS, require(ranks, added));
        Set<Integer> h = new HashSet<>();
        for (String s : added) {
            h.add(specialToken(s));
        }
        this.hidden = Set.copyOf(h);
    }

    private static String[] require(Map<Bytes, Integer> ranks, List<String> added) {
        for (String s : new String[]{BOS, EOS, PAD, MESSAGE_MODEL, END_MESSAGE}) {
            if (!added.contains(s) || !ranks.containsKey(new Bytes(s))) {
                throw new IllegalArgumentException("tokenizer is missing required special token " + s);
            }
        }
        return added.stream().filter(s -> ranks.containsKey(new Bytes(s))).toArray(String[]::new);
    }

    /**
     * Loads the tokenizer from a checkpoint directory containing {@code tokenizer.json}.
     *
     * @param checkpointDir model directory.
     * @return the tokenizer.
     * @throws IOException if {@code tokenizer.json} is missing or malformed.
     */
    public static InklingTokenizer of(String checkpointDir) throws IOException {
        Path json = Path.of(checkpointDir, "tokenizer.json");
        if (!Files.exists(json)) {
            throw new IOException("tokenizer.json not found in " + checkpointDir);
        }
        List<String> added = new ArrayList<>();
        JsonNode root = new ObjectMapper().readTree(json.toFile());
        for (JsonNode node : root.path("added_tokens")) {
            added.add(node.path("content").asString());
        }
        return new InklingTokenizer(HuggingFaceBpeVocab.loadTokenizerJson(json), added);
    }

    /**
     * Encodes template text in which markers such as {@code <|message_user|>} stand for single special tokens.
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
     * Decodes tokens. With {@code skipSpecial}, a reply is rendered in the form the server's chat pipeline reads:
     * reasoning as {@code <think>...</think>}, tool calls as {@code <tool_call>{"name":..,"args":{..}}</tool_call>}
     * (the {@code <|message_model|>NAME} header of a call is dropped), text as is, every other control token removed.
     *
     * <p>The rendering is a scan over the given tokens only (the streamer decodes chunks), so a
     * {@code </think>} is written at the first text message of the chunk and a tool call cut by a chunk boundary
     * loses its closing tag; decoding a whole reply is exact (a leading {@code </think>} without reasoning is
     * dropped by the sanitizer). Use {@link smile.llm.tool.InklingResponseParser} for an exact split.
     */
    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial) throws CharacterCodingException {
        if (!skipSpecial) {
            return super.tryDecode(tokens, false);
        }
        final int header = specialToken(MESSAGE_MODEL);
        final int thinking = specialToken(CONTENT_THINKING);
        final int text = specialToken(CONTENT_TEXT);
        final int invoke = specialToken(CONTENT_INVOKE_TOOL_JSON);
        final int end = specialToken(END_MESSAGE);
        StringBuilder out = new StringBuilder();
        List<Integer> run = new ArrayList<>();
        int state = 0;                 // 0 unknown / between messages, 1 reasoning, 2 text, 3 tool call
        boolean sawText = false;
        for (int i = 0; i < tokens.length; i++) {
            int t = tokens[i];
            if (!hidden.contains(t)) {
                run.add(t);
                continue;
            }
            // a run of ordinary tokens ends at a control token
            String piece = run.isEmpty() ? "" : super.tryDecode(run.stream().mapToInt(Integer::intValue).toArray(), false);
            run.clear();
            if (t == invoke) {
                piece = "";            // the text just read was the tool name of the <|message_model|> header
            }
            out.append(piece);
            if (t == thinking) {
                out.append("<think>");
                state = 1;
            } else if (t == text) {
                if (state != 2 && !sawText) {
                    out.append("</think>");
                }
                sawText = true;
                state = 2;
            } else if (t == invoke) {
                out.append("<tool_call>");
                state = 3;
            } else if (t == end) {
                if (state == 1) {
                    out.append("</think>");
                    sawText = true;
                } else if (state == 3) {
                    out.append("</tool_call>");
                }
                state = 0;
            } else if (t == header) {
                state = 0;
            }
        }
        if (!run.isEmpty()) {
            out.append(super.tryDecode(run.stream().mapToInt(Integer::intValue).toArray(), false));
        }
        return out.toString();
    }

    /**
     * Padding token id.
     * @return the id of {@code <|endoftext|>}.
     */
    public int pad() {
        return specialToken(PAD);
    }

    /**
     * Stop token ids used during generation.
     * @return the end-of-sampling token id.
     */
    public int[] stopTokens() {
        return new int[]{specialToken(EOS)};
    }
}
