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
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuration for an MCP server that uses the {@code stdio} transport.
 * The MCP client launches a local subprocess and communicates with it via
 * stdin/stdout using the JSON-RPC protocol.
 *
 * <p>Example JSON fragment:
 * <pre>{@code
 * "my-server": {
 *   "type": "stdio",
 *   "command": "npx",
 *   "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
 *   "env": { "MY_VAR": "value" },
 *   "envFile": "/path/to/.env",
 *   "windows": {
 *     "command": "cmd",
 *     "args": ["/c", "npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
 *   }
 * }
 * }</pre>
 *
 * @param type     The transport type; always {@link ServerType#STDIO} for this record.
 * @param command  The executable to launch.
 * @param args     Optional command-line arguments passed to the executable.
 * @param env      Optional environment variables to set for the subprocess.
 *                 Values may reference input variables as {@code ${input:id}}.
 * @param envFile  Optional path to a {@code .env} file whose variables are
 *                 merged into the subprocess environment.
 * @param windows  Optional Windows-specific override for {@code command} and
 *                 {@code args}, applied only when running on Windows.
 * @param inputs   Optional list of input variable definitions used to resolve
 *                 {@code ${input:id}} placeholders in {@code env} values.
 * @param disabled If {@code true}, this server is disabled and will not be started.
 *
 * @author Haifeng Li
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StdioServerConfig(
        ServerType type,
        String command,
        List<String> args,
        Map<String, String> env,
        String envFile,
        WindowsOverride windows,
        List<McpInput> inputs,
        boolean disabled) implements ServerConfig {

    /**
     * Windows-specific override for the command and arguments of a stdio
     * server. When running on Windows, these values replace the top-level
     * {@code command} and {@code args} fields.
     *
     * @param command The Windows-specific executable.
     * @param args    The Windows-specific command-line arguments.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WindowsOverride(String command, List<String> args) {}
}

