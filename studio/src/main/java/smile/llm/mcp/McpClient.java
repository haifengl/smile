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

import java.util.Map;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP (Model Context Protocol) client that manages connections to one or more
 * MCP servers declared in an {@link McpConfig} configuration file.
 *
 * <p>Example usage:
 * <pre>{@code
 * McpClient client = McpClient.of(Path.of("mcp.json"));
 * List<McpServer> servers = client.servers();
 * }</pre>
 *
 * @author Haifeng Li
 */
public class McpClient implements AutoCloseable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(McpClient.class);

    /** The sync MCP client. */
    private final McpSyncClient client;

    /**
     * Constructor.
     *
     * @param client a sync MCP client.
     */
    public McpClient(McpSyncClient client) {
        this.client = client;
    }

    /** List available tools. */
    public McpSchema.ListToolsResult tools() {
        return client.listTools();
    }

    /** List available resources. */
    public McpSchema.ListResourcesResult resources() {
        return client.listResources();
    }

    /**
     * Reads a resource.
     * @param uri the URI of the resource to read.
     * @return the result of the read resource request.
     */
    public McpSchema.ReadResourceResult resource(String uri) {
        return client.readResource(new McpSchema.ReadResourceRequest(uri));
    }

    /** List available prompts. */
    public McpSchema.ListPromptsResult prompts() {
        return client.listPrompts();
    }

    /**
     * Gets a prompt.
     * @param name the name of prompt template.
     * @param arguments the arguments to customize prompt.
     */
    public McpSchema.GetPromptResult prompt(String name, Map<String, Object> arguments) {
         return client.getPrompt(
                new McpSchema.GetPromptRequest(name, arguments)
        );
    }

    @Override
    public void close() {
        client.closeGracefully();
    }
}
