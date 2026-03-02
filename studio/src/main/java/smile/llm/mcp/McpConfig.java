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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Represents a parsed MCP (Model Context Protocol) configuration file.
 *
 * <p>The configuration file is a JSON document with the following top-level
 * structure, as defined by the
 * <a href="https://code.visualstudio.com/docs/copilot/reference/mcp-configuration">
 * VS Code MCP configuration reference</a>:
 *
 * <pre>{@code
 * {
 *   "inputs": [
 *     { "id": "api-key", "type": "promptString", "description": "API key", "password": true }
 *   ],
 *   "servers": {
 *     "filesystem": {
 *       "type": "stdio",
 *       "command": "npx",
 *       "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
 *     },
 *     "github": {
 *       "type": "http",
 *       "url": "https://api.githubcopilot.com/mcp/",
 *       "headers": { "Authorization": "Bearer ${input:api-key}" }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>The {@code servers} key maps each server name to a {@link McpServerConfig}:
 * <ul>
 *   <li>{@link StdioMcpServerConfig} for {@code "type": "stdio"}</li>
 *   <li>{@link HttpMcpServerConfig} for {@code "type": "sse"} or {@code "type": "http"}</li>
 * </ul>
 *
 * @param inputs  Top-level input variable definitions shared across all servers.
 * @param servers Map of server name to its {@link McpServerConfig}.
 *
 * @author Haifeng Li
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpConfig(Map<String, McpServerConfig> servers, List<McpInput> inputs) {

    /** Shared Jackson mapper with camelCase property names. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .build();

    /**
     * Parses an MCP configuration file.
     *
     * @param path the path to the JSON configuration file.
     * @return the parsed {@link McpConfig}.
     * @throws IOException if the file cannot be read or parsed.
     */
    public static McpConfig from(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), McpConfig.class);
    }

    /**
     * Connects to enabled servers.
     *
     * @return an unmodifiable map of enabled server name to its client.
     */
    public Map<String, McpClient> connect() {
        if (servers == null) return Map.of();
        return servers.entrySet().stream()
                .filter(e -> !e.getValue().disabled())
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().client()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
