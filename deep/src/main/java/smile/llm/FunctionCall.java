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
 * Function invocation payload inside a {@link ToolCall}.
 *
 * @param name      function name.
 * @param arguments JSON object string of arguments (OpenAI wire form).
 * @author Haifeng Li
 */
public record FunctionCall(String name, String arguments) {

    /**
     * Compact canonical constructor.
     */
    public FunctionCall {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("function call name must not be blank");
        }
        if (arguments == null) {
            arguments = "{}";
        }
    }
}
