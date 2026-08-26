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

import java.util.List;
import smile.llm.FinishReason;
import smile.llm.ToolCall;

/**
 * Result of parsing assistant text for tool calls.
 *
 * @param content   text before the first tool call; may be {@code null}.
 * @param toolCalls parsed tool calls (possibly empty).
 * @param reason    finish reason after parsing.
 * @author Haifeng Li
 */
public record ParseResult(String content, List<ToolCall> toolCalls, FinishReason reason) {
    /**
     * Compact canonical constructor.
     */
    public ParseResult {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        if (toolCalls == null) {
            toolCalls = List.of();
        } else {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    /**
     * @return {@code true} when one or more tool calls were parsed.
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
