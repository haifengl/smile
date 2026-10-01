/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
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
