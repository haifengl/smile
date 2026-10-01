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
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

/**
 * An implementation of the LSP4J client interface for read-only
 * queries.
 *
 * <p>Language servers send notifications back to the client (e.g.
 * {@code window/logMessage}, {@code textDocument/publishDiagnostics}).
 * We display diagnostics and messages at the status bar and log other
 * notifications at DEBUG level.
 *
 * @author Haifeng Li
 */
public class LspServerNotificationHandler implements LanguageClient {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LspServerNotificationHandler.class);
    /** The name of the language server. */
    private final String server;
    /** The status bar to display LSP status messages. */
    private final StatusBar statusBar;

    /**
     * Constructor.
     * @param server the name of the language server.
     * @param statusBar the status bar to display LSP status messages.
     */
    public LspServerNotificationHandler(String server, StatusBar statusBar) {
        this.server = server;
        this.statusBar = statusBar;
    }

    @Override
    public void telemetryEvent(Object object) {
        logger.debug("[LSP telemetry] {}: {}", server, object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        SwingUtilities.invokeLater(() -> statusBar.setStatus(String.format("[LSP diagnostics] %s: %d issue(s) in %s",
                server, diagnostics.getDiagnostics().size(), diagnostics.getUri())));
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        SwingUtilities.invokeLater(() -> statusBar.setStatus(String.format("[LSP %s] %s: %s",
                messageParams.getType(), server, messageParams.getMessage())));
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(
            ShowMessageRequestParams requestParams) {
        logger.debug("[LSP request] {}: {}", server, requestParams.getMessage());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        SwingUtilities.invokeLater(() -> statusBar.setStatus(String.format("[LSP %s] %s: %s",
                message.getType(), server, message.getMessage())));
    }

    @JsonNotification("language/status")
    public void onLanguageStatus(Object report) {
        logger.debug("[LSP status] {}: {}", server, report);
    }

    @JsonNotification("language/eventNotification")
    public void onLanguageEventNotification(Object report) {
        logger.debug("[LSP event] {}: {}", server, report);
    }
}
