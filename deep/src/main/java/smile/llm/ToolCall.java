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

/**
 * OpenAI-compatible tool call emitted by the assistant.
 *
 * @param id       client-visible call id (e.g. {@code call_abc123}).
 * @param type     always {@code "function"} for v1.
 * @param function function name and JSON arguments.
 * @author Haifeng Li
 */
public record ToolCall(String id, String type, FunctionCall function) {

    /**
     * Compact canonical constructor.
     */
    public ToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("tool call id must not be blank");
        }
        if (type == null || type.isBlank()) {
            type = "function";
        }
        if (function == null) {
            throw new IllegalArgumentException("function must not be null");
        }
    }

    /**
     * Convenience factory for a function tool call.
     *
     * @param id       call id.
     * @param function function payload.
     * @return tool call with {@code type = "function"}.
     */
    public static ToolCall function(String id, FunctionCall function) {
        return new ToolCall(id, "function", function);
    }
}
