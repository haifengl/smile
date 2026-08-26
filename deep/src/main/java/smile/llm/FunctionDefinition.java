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

import java.util.Map;

/**
 * OpenAI-style function definition inside a {@link ToolDefinition}.
 *
 * @param name        function name (required).
 * @param description human-readable description; may be {@code null}.
 * @param parameters  JSON Schema object as a map; may be {@code null}.
 * @author Haifeng Li
 */
public record FunctionDefinition(
        String name,
        String description,
        Map<String, Object> parameters) {

    /**
     * Compact canonical constructor.
     */
    public FunctionDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("function name must not be blank");
        }
        if (parameters != null) {
            parameters = Map.copyOf(parameters);
        }
    }
}
