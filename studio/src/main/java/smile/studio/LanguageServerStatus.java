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
 * A no-op implementation of the LSP4J client interface.
 *
 * <p>Language servers send notifications back to the client (e.g.
 * {@code window/logMessage}, {@code textDocument/publishDiagnostics}).
 * We log them at DEBUG / INFO level and otherwise ignore them, since
 * this client is used for read-only queries.
 *
 * @author Haifeng Li
 */
public class LanguageServerStatus implements LanguageClient {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LanguageServerStatus.class);
    private final StatusBar statusBar;

    /**
     * Constructor.
     * @param statusBar the status bar to display LSP status messages.
     */
    public LanguageServerStatus(StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Override
    public void telemetryEvent(Object object) {
        logger.debug("[LSP telemetry] {}", object);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        SwingUtilities.invokeLater(() -> statusBar.setStatus(String.format("[LSP diagnostics] %d issue(s) in %s",
                diagnostics.getDiagnostics().size(), diagnostics.getUri())));
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        SwingUtilities.invokeLater(() -> statusBar.setStatus(String.format("[LSP %s] %s",
                messageParams.getType(), messageParams.getMessage())));
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(
            ShowMessageRequestParams requestParams) {
        logger.debug("[LSP showMessageRequest] {}", requestParams.getMessage());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        logger.debug("[LSP log] [{}] {}", message.getType(), message.getMessage());
    }
}
