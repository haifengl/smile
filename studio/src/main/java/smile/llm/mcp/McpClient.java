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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import smile.llm.tool.Tool;

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

    private final String name;
    private final McpServerConfig server;
    private final McpSyncClient client;
    private final List<McpToolSpec> tools;
    private final List<McpSchema.Resource> resources;
    private final List<McpSchema.Prompt> prompts;

    /**
     * Constructor.
     *
     * @param name the server name.
     * @param server the server configuration.
     * @param client a sync MCP client.
     */
    public McpClient(String name, McpServerConfig server, McpSyncClient client) {
        this.name = name;
        this.server = server;
        this.client = client;
        this.tools = client.listTools().tools().stream()
                .map(tool -> {
                    var schema = new Tool.JsonSchema(
                            tool.inputSchema().type(),
                            Optional.ofNullable(tool.inputSchema().properties()).orElse(Map.of()),
                            Optional.ofNullable(tool.inputSchema().required()).orElse(List.of()),
                            Optional.ofNullable(tool.inputSchema().additionalProperties()).orElse(false)
                    );
                    return new McpToolSpec(tool.name(), tool.description(), schema);
                })
                .toList();

        var capabilities = client.getServerCapabilities();
        this.resources = capabilities.resources() != null ?
            client.listResources().resources().stream().toList() : List.of();

        this.prompts = capabilities.resources() != null ?
                client.listPrompts().prompts() : List.of();
    }

    /**
     * Creates and initializes a new client for an MCP server.
     * @param name the MCP server name.
     * @param server the server configuration.
     * @return the initialized MCP client.
     */
    public static McpClient connect(String name, McpServerConfig server) {
        // Create a sync client with custom configuration
        McpSyncClient client = io.modelcontextprotocol.client.McpClient.sync(server.transport())
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        logger.info("Connecting to MCP server: {}", name);
        var result = client.initialize();
        return new McpClient(name, server, client);
    }

    /** Returns the server name. */
    public String name() {
        return name;
    }

    /** Returns the server configuration. */
    public McpServerConfig server() {
        return server;
    }

    /**
     * Calls a tool.
     * @param tool the name of the tool to call.
     * @param arguments the arguments to pass to the tool.
     */
    public String call(String tool, Map<String, Object> arguments) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                tool,
                arguments
        );
        var result = client.callTool(request);
        return result.content().stream().map(Object::toString).collect(Collectors.joining());
    }

    /** List available tools. */
    public List<McpToolSpec> tools() {
        return tools;
    }

    /** List available resources. */
    public List<McpSchema.Resource> resources() {
        return resources;
    }

    /**
     * Reads a resource.
     * @param uri the URI of the resource to read.
     * @return the result of the read resource request.
     */
    public List<McpSchema.ResourceContents> resource(String uri) {
        return client.readResource(new McpSchema.ReadResourceRequest(uri)).contents();
    }

    /** List available prompts. */
    public List<McpSchema.Prompt> prompts() {
        return prompts;
    }

    /**
     * Gets a prompt.
     * @param name the name of prompt template.
     * @param arguments the arguments to customize prompt.
     */
    public List<McpSchema.PromptMessage> prompt(String name, Map<String, Object> arguments) {
        return client.getPrompt(
                new McpSchema.GetPromptRequest(name, arguments)
        ).messages();
    }

    @Override
    public void close() {
        client.closeGracefully();
    }
}
