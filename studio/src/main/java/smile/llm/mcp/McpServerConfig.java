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

import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;

/**
 * Sealed interface representing a single MCP server entry inside the
 * {@code mcpServers} map of an {@link McpConfig}.
 *
 * <p>Concrete implementations:
 * <ul>
 *   <li>{@link StdioMcpServerConfig} – {@code "type": "stdio"}</li>
 *   <li>{@link HttpMcpServerConfig}  – {@code "type": "sse"} or {@code "type": "http"}</li>
 * </ul>
 *
 * @author Haifeng Li
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StdioMcpServerConfig.class, name = "stdio"),
        @JsonSubTypes.Type(value = HttpMcpServerConfig.class,  name = "sse"),
        @JsonSubTypes.Type(value = HttpMcpServerConfig.class,  name = "http")
})
public sealed interface McpServerConfig permits StdioMcpServerConfig, HttpMcpServerConfig {
    /** The JSON mapper for MCP serialization/deserialization. */
    McpJsonMapper JSON_MAPPER = McpJsonDefaults.getMapper();

    /** Returns the transport type of this server. */
    ServerType type();

    /** Returns the input variable definitions, or {@code null} if none. */
    List<McpInput> inputs();

    /**
     * Returns {@code true} if this server is disabled.
     * A disabled server is not started or connected by the MCP client.
     */
    boolean disabled();

    /**
     * Returns the client transport configuration for this server.
     * @return the client transport configuration for this server.
     */
    McpClientTransport transport();

    /**
     * Creates and initializes a new client for this server.
     * @return the initialized MCP client connected to this server.
     */
    default McpClient client() {
        // Create a sync client with custom configuration
        McpSyncClient client = io.modelcontextprotocol.client.McpClient.sync(transport())
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        client.initialize();
        return new McpClient(client);
    }
}
