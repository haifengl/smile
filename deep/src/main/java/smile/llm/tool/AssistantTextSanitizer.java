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
package smile.llm.tool;

import java.util.regex.Pattern;

/**
 * Strips chat-template / tool / thinking markup from assistant text so API
 * and streaming clients never see role specials or raw tool XML mixed into
 * visible content.
 *
 * @author Haifeng Li
 */
public final class AssistantTextSanitizer {
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<think>.*?</think>", Pattern.DOTALL);
    private static final Pattern TOOL_CALL_BLOCK = Pattern.compile(
            "<tool_call>.*?</tool_call>", Pattern.DOTALL);
    private static final Pattern TOOL_RESPONSE_BLOCK = Pattern.compile(
            "<tool_response>.*?</tool_response>", Pattern.DOTALL);
    /** Chat specials and role headers that may appear if decode skips fail. */
    private static final Pattern CHAT_SPECIALS = Pattern.compile(
            "<\\|im_start\\|>|<\\|im_end\\|>|<\\|endoftext\\|>");
    private static final Pattern ROLE_HEADER = Pattern.compile(
            "(?m)^(?:system|user|assistant|tool)\\s*$\\n?");
    private static final Pattern ORPHAN_THINK = Pattern.compile(
            "</?think>");

    private AssistantTextSanitizer() {}

    /**
     * Cleans assistant-visible text for API / SSE content fields.
     *
     * <p>Removes thinking spans, tool-call / tool-response blocks, and chat
     * specials. When an open {@code <think>} was part of the prompt (so the
     * completion only has a closing tag), keeps the text after the last
     * {@code </think>}.
     *
     * @param text raw decoded generation; may be {@code null}.
     * @return cleaned text, or {@code null} when empty after cleaning.
     */
    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String out = text;
        // Prompt may leave an open <think>; keep only post-thinking answer.
        int close = out.lastIndexOf("</think>");
        int open = out.lastIndexOf("<think>");
        if (close >= 0 && (open < 0 || open < close)) {
            String after = out.substring(close + "</think>".length());
            if (!after.contains("<think>")) {
                out = after;
            } else {
                out = THINK_BLOCK.matcher(out).replaceAll("");
            }
        } else {
            out = THINK_BLOCK.matcher(out).replaceAll("");
        }
        out = TOOL_CALL_BLOCK.matcher(out).replaceAll("");
        out = TOOL_RESPONSE_BLOCK.matcher(out).replaceAll("");
        out = CHAT_SPECIALS.matcher(out).replaceAll("");
        out = ROLE_HEADER.matcher(out).replaceAll("");
        out = ORPHAN_THINK.matcher(out).replaceAll("");
        out = out.strip();
        return out.isEmpty() ? null : out;
    }

    /**
     * Removes tool-call XML from message content used when re-encoding
     * history that already has structured {@code tool_calls}.
     *
     * @param text message content; may be {@code null}.
     * @return content without tool-call blocks (may be empty).
     */
    public static String stripToolCalls(String text) {
        if (text == null) {
            return "";
        }
        return TOOL_CALL_BLOCK.matcher(text).replaceAll("").strip();
    }
}
