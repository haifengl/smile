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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
 * @param servers Map of server name to its {@link McpServerConfig}.
 *
 * @author Haifeng Li
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpConfig(Map<String, McpServerConfig> servers) {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(McpConfig.class);

    /**
     * Parses an MCP configuration file.
     *
     * @param path the path to the JSON configuration file.
     * @return the parsed {@link McpConfig}.
     * @throws IOException if the file cannot be read or parsed.
     */
    public static McpConfig from(Path path) throws IOException {
        return MCP.MAPPER.readValue(path.toFile(), McpConfig.class);
    }

    /**
     * Connects to enabled servers.
     *
     * @return an unmodifiable map of enabled server name to its client.
     */
    public List<McpClient> connect() {
        List<McpClient> clients = new ArrayList<>();
        if (servers != null) {
            for (var entry : servers.entrySet()) {
                var name = entry.getKey();
                var server = entry.getValue();
                if (!server.disabled()) {
                    try {
                        clients.add(McpClient.connect(name, server));
                    } catch (Throwable ex) {
                        logger.error("Failed to connect to MCP server '{}'", name, ex);
                    }
                }
            }
        }
        return clients;
    }
}
