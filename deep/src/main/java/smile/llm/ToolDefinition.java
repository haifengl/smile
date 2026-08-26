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
 * OpenAI-compatible tool definition ({@code type: "function"}).
 *
 * @param type     tool type; always {@code "function"} for v1.
 * @param function function schema.
 * @author Haifeng Li
 */
public record ToolDefinition(String type, FunctionDefinition function) {

    /**
     * Compact canonical constructor.
     */
    public ToolDefinition {
        if (type == null || type.isBlank()) {
            type = "function";
        }
        if (function == null) {
            throw new IllegalArgumentException("function must not be null");
        }
    }

    /**
     * Convenience factory for a function tool.
     *
     * @param function function definition.
     * @return tool definition with {@code type = "function"}.
     */
    public static ToolDefinition function(FunctionDefinition function) {
        return new ToolDefinition("function", function);
    }
}
