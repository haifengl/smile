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
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;

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
}
