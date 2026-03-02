/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.mcp;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An input variable definition used by MCP server configurations.
 * Input variables allow prompting the user for values (e.g., API keys)
 * when a server starts. The input variable value can be referenced in
 * environment variable values as {@code ${input:id}}.
 *
 * @param id          The unique identifier of the input variable.
 * @param type        The type of input: {@code promptString}, {@code pickString},
 *                    or {@code command}.
 * @param description A human-readable description shown in the input prompt.
 * @param password    If {@code true}, the input is treated as a password
 *                    (masked, not stored in history). Only applicable to
 *                    {@code promptString}.
 * @param options     The list of options to pick from. Only applicable to
 *                    {@code pickString}.
 * @param defaultValue The default value pre-filled in the input box.
 * @param command     The VS Code command to invoke to retrieve the value.
 *                    Only applicable to {@code command} type.
 *
 * @author Haifeng Li
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpInput(
        String id,
        InputType type,
        String description,
        boolean password,
        List<String> options,
        @JsonProperty("default") String defaultValue,
        String command) {
}
