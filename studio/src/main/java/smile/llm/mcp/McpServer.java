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

import smile.util.OS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A live connection to a single MCP (Model Context Protocol) server.
 *
 * <p>Use {@link McpClient} to manage multiple servers from an {@link McpConfig}
 * configuration file, or call {@link #connect(String, ServerConfig)} directly
 * to open a single connection.
 *
 * <p>Stdio servers are started as a subprocess; the connection is closed by
 * destroying the subprocess. HTTP/SSE servers are stateless from the client
 * perspective — {@link #close()} is a no-op for those transport types.
 *
 * @author Haifeng Li
 */
public class McpServer implements AutoCloseable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(McpServer.class);

    /** The logical name identifying this server in the configuration. */
    private final String name;
    /** The resolved server configuration. */
    private final ServerConfig config;
    /** The subprocess handle for stdio servers; {@code null} for HTTP/SSE servers. */
    private final Process process;

    /**
     * Constructor for HTTP / SSE servers (no subprocess).
     *
     * @param name   the server name.
     * @param config the server configuration.
     */
    private McpServer(String name, HttpServerConfig config) {
        this.name  = name;
        this.config = config;
        this.process = null;
    }

    /**
     * Constructor for stdio servers.
     *
     * @param name    the server name.
     * @param config  the server configuration.
     * @param process the running subprocess.
     */
    private McpServer(String name, StdioServerConfig config, Process process) {
        this.name   = name;
        this.config = config;
        this.process = process;
    }

    /**
     * Opens a connection to the MCP server described by {@code config}.
     *
     * <ul>
     *   <li>For {@link StdioServerConfig}: launches a subprocess with the
     *       specified command and arguments, inheriting the current environment
     *       merged with any configured {@code env} overrides.</li>
     *   <li>For {@link HttpServerConfig} ({@code sse} or {@code http}): no
     *       persistent connection is established; the URL and headers are stored
     *       for use in per-request calls.</li>
     * </ul>
     *
     * @param name   the logical server name from the configuration.
     * @param config the server configuration.
     * @return a connected {@link McpServer} instance.
     * @throws IOException if a stdio subprocess cannot be started.
     * @throws IllegalArgumentException if the config type is unrecognized.
     */
    public static McpServer connect(String name, ServerConfig config) throws IOException {
        return switch (config) {
            case StdioServerConfig stdio -> connectStdio(name, stdio);
            case HttpServerConfig  http  -> new McpServer(name, http);
        };
    }

    /** Launches the subprocess for a stdio server. */
    private static McpServer connectStdio(String name, StdioServerConfig cfg) throws IOException {
        String command = cfg.command();
        List<String> args = cfg.args() != null ? cfg.args() : List.of();

        // Apply Windows-specific override when running on Windows.
        if (OS.isWindows() && cfg.windows() != null) {
            StdioServerConfig.WindowsOverride win = cfg.windows();
            command = win.command();
            args = win.args() != null ? win.args() : List.of();
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);

        // Merge environment overrides.
        if (cfg.env() != null) {
            pb.environment().putAll(cfg.env());
        }

        Process process = pb.start();
        logger.debug("Started stdio MCP server '{}': {}", name, cmd);
        return new McpServer(name, cfg, process);
    }

    /**
     * Returns the logical name of this server as declared in the configuration.
     *
     * @return the server name.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the server configuration.
     *
     * @return the {@link ServerConfig}.
     */
    public ServerConfig config() {
        return config;
    }

    /**
     * Returns the stdin stream of the underlying subprocess for stdio servers.
     *
     * @return the subprocess stdin, or {@code null} for HTTP/SSE servers.
     */
    public OutputStream stdin() {
        return process != null ? process.getOutputStream() : null;
    }

    /**
     * Returns the stdout stream of the underlying subprocess for stdio servers.
     *
     * @return the subprocess stdout, or {@code null} for HTTP/SSE servers.
     */
    public InputStream stdout() {
        return process != null ? process.getInputStream() : null;
    }

    /**
     * Returns the stderr stream of the underlying subprocess for stdio servers.
     *
     * @return the subprocess stderr, or {@code null} for HTTP/SSE servers.
     */
    public InputStream stderr() {
        return process != null ? process.getErrorStream() : null;
    }

    /**
     * Returns whether the underlying subprocess (stdio) is still alive.
     * Always returns {@code true} for HTTP/SSE servers.
     *
     * @return {@code true} if the server connection is active.
     */
    public boolean isAlive() {
        return process == null || process.isAlive();
    }

    /**
     * Closes this server connection.
     *
     * <p>For stdio servers, the subprocess is destroyed forcibly if it has
     * not already terminated. For HTTP/SSE servers this method is a no-op.
     */
    @Override
    public void close() {
        if (process != null && process.isAlive()) {
            process.destroy();
            logger.debug("Stopped stdio MCP server '{}'", name);
        }
    }
}
