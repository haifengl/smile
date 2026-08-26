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

import smile.llm.ChatCompletion;
import smile.llm.ChatOptions;
import smile.llm.FinishReason;

/**
 * Applies a {@link ToolCallParser} to a completed generation when options
 * request tool-call parsing.
 *
 * @author Haifeng Li
 */
public final class ToolCallPostProcessor {
    private static final ToolCallParser QWEN3_XML = new Qwen3XmlToolCallParser();

    private ToolCallPostProcessor() {}

    /**
     * Returns {@code completion} unchanged when tools are disabled; otherwise
     * rewrites content / finish reason / toolCalls from the parser.
     *
     * @param completion raw generation result.
     * @param options    chat options; may be {@code null}.
     * @return possibly rewritten completion.
     */
    public static ChatCompletion apply(ChatCompletion completion, ChatOptions options) {
        if (completion == null || options == null || !options.parseToolCalls()) {
            return completion;
        }
        boolean lengthLimited = completion.reason() == FinishReason.length;
        ParseResult parsed = QWEN3_XML.parse(completion.content(), lengthLimited);
        if (!parsed.hasToolCalls()) {
            if (lengthLimited) {
                return new ChatCompletion(
                        completion.model(),
                        completion.content(),
                        completion.promptTokens(),
                        completion.completionTokens(),
                        FinishReason.length,
                        completion.logprobs(),
                        null);
            }
            return completion;
        }
        return new ChatCompletion(
                completion.model(),
                parsed.content(),
                completion.promptTokens(),
                completion.completionTokens(),
                FinishReason.tool_calls,
                completion.logprobs(),
                parsed.toolCalls());
    }
}
