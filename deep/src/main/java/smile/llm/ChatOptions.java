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
package smile.llm;

import java.util.Arrays;
import java.util.List;

/**
 * Per-request chat options for tool calling.
 *
 * @param tools             available tools; {@code null} or empty means no tools.
 * @param toolChoice        tool selection policy; {@code null} defaults to {@link ToolChoice#AUTO}.
 * @param parallelToolCalls whether multiple tool calls in one turn are allowed.
 * @author Haifeng Li
 */
public record ChatOptions(
        ToolDefinition[] tools,
        ToolChoice toolChoice,
        boolean parallelToolCalls) {

    /** Empty options (no tools). */
    public static final ChatOptions NONE = new ChatOptions(null, ToolChoice.NONE, true);

    /**
     * Compact canonical constructor.
     */
    public ChatOptions {
        if (tools != null) {
            tools = Arrays.copyOf(tools, tools.length);
        }
        if (toolChoice == null) {
            toolChoice = ToolChoice.AUTO;
        }
    }

    /**
     * @return {@code true} when tools should be injected into the chat template.
     */
    public boolean hasTools() {
        return tools != null && tools.length > 0 && !(toolChoice instanceof ToolChoice.None);
    }

    /**
     * Tools filtered for template injection (named choice → single function).
     *
     * @return tools to expose, or empty list when none.
     */
    public List<ToolDefinition> toolsForTemplate() {
        if (!hasTools()) {
            return List.of();
        }
        if (toolChoice instanceof ToolChoice.Named named) {
            return Arrays.stream(tools)
                    .filter(t -> named.name().equals(t.function().name()))
                    .toList();
        }
        return List.of(tools);
    }

    /**
     * @return {@code true} when the tool-call output parser should run.
     */
    public boolean parseToolCalls() {
        return hasTools();
    }
}
