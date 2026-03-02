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
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /** The parsed MCP configuration. */
    private final McpConfig config;
    /** The active server connections. */
    private final List<McpServer> servers;

    /**
     * Creates an {@code McpClient} from the given configuration.
     *
     * @param config the MCP configuration.
     */
    public McpClient(McpConfig config) {
        this.config = config;
        this.servers = new ArrayList<>();
    }

    /**
     * Parses the given configuration file and returns a new {@code McpClient}.
     *
     * @param path path to the MCP JSON configuration file.
     * @return a new {@code McpClient} instance.
     * @throws IOException if the configuration file cannot be read or parsed.
     */
    public static McpClient of(Path path) throws IOException {
        return new McpClient(McpConfig.parse(path));
    }

    /**
     * Returns the parsed MCP configuration.
     *
     * @return the {@link McpConfig}.
     */
    public McpConfig config() {
        return config;
    }

    /**
     * Connects to all enabled servers declared in the configuration and
     * returns the list of active {@link McpServer} connections.
     *
     * @return an unmodifiable list of connected {@link McpServer} instances.
     * @throws IOException if any server fails to connect.
     */
    public List<McpServer> connect() throws IOException {
        servers.clear();
        for (Map.Entry<String, ServerConfig> entry : config.enabledServers().entrySet()) {
            String name = entry.getKey();
            ServerConfig cfg = entry.getValue();
            try {
                McpServer server = McpServer.connect(name, cfg);
                servers.add(server);
                logger.info("Connected to MCP server '{}'", name);
            } catch (IOException ex) {
                logger.error("Failed to connect to MCP server '{}'", name, ex);
                throw ex;
            }
        }
        return Collections.unmodifiableList(servers);
    }

    /**
     * Returns the currently active server connections. Call {@link #connect()}
     * first to populate this list.
     *
     * @return an unmodifiable list of active {@link McpServer} instances.
     */
    public List<McpServer> servers() {
        return Collections.unmodifiableList(servers);
    }

    @Override
    public void close() {
        for (McpServer server : servers) {
            try {
                server.close();
            } catch (Exception ex) {
                logger.warn("Error closing MCP server '{}'", server.name(), ex);
            }
        }
        servers.clear();
    }
}
