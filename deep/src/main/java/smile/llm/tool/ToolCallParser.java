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

/**
 * Parses assistant completion text into structured tool calls.
 *
 * @author Haifeng Li
 */
public interface ToolCallParser {
    /**
     * Parses a complete assistant response.
     *
     * @param assistantText decoded generation text.
     * @return parse result with optional tool calls.
     */
    ParseResult parse(String assistantText);

    /**
     * Parses with a prior finish reason (e.g. length vs stop).
     *
     * @param assistantText decoded generation text.
     * @param lengthLimited {@code true} when generation hit the token limit.
     * @return parse result.
     */
    default ParseResult parse(String assistantText, boolean lengthLimited) {
        ParseResult result = parse(assistantText);
        if (lengthLimited && !result.hasToolCalls()) {
            return new ParseResult(result.content(), result.toolCalls(), smile.llm.FinishReason.length);
        }
        if (lengthLimited && result.hasToolCalls()) {
            // Prefer tool_calls when parse succeeded even if truncated mid-stream.
            return result;
        }
        return result;
    }
}
