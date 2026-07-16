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
package smile.studio;

import javax.swing.SwingUtilities;
import java.util.function.Consumer;
import io.modelcontextprotocol.spec.McpSchema;
import ioa.llm.mcp.NotificationHandler;

/**
 * An MCP server log message and notification handler.
 *
 * <p>MCP servers send log messages and notifications back to the client.
 * We display notifications and log messages at the status bar.
 *
 * @author Haifeng Li
 */
public class McpServerNotificationHandler implements NotificationHandler {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(McpServerNotificationHandler.class);
    /** The status bar to display LSP status messages. */
    private final StatusBar statusBar;

    /**
     * Constructor.
     * @param statusBar the status bar to display LSP status messages.
     */
    public McpServerNotificationHandler(StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Override
    public Consumer<McpSchema.LoggingMessageNotification> loggingConsumer(String server) {
        return message -> SwingUtilities.invokeLater(() ->
                        statusBar.setStatus(String.format("[MCP %s] %s: %s",
                                message.level(), server, message.data())));
    }

    @Override
    public Consumer<McpSchema.ProgressNotification> progressConsumer(String server) {
        return progress -> {
            // total is optional in MCP spec.
            if (progress.total() != null) {
                SwingUtilities.invokeLater(() ->
                        statusBar.setStatus(String.format("[MCP progress %.0f/%.0f] %s: %s",
                                progress.progress(), progress.total(), server, progress.message())));
            }
        };
    }
}
