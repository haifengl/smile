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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
public class LspServerNotificationHandlerTest {
    private static class RecordingStatusBar extends StatusBar {
        final List<String> messages = new ArrayList<>();
        @Override
        public void setStatus(String message) { messages.add(message); }
    }
    private RecordingStatusBar statusBar;
    private LspServerNotificationHandler handler;
    @BeforeEach
    public void setUp() {
        statusBar = new RecordingStatusBar();
        handler   = new LspServerNotificationHandler("TestServer", statusBar);
    }
    @Test
    public void testTelemetryEventDoesNotThrow() {
        assertDoesNotThrow(() -> handler.telemetryEvent("data"));
        assertDoesNotThrow(() -> handler.telemetryEvent(null));
    }
    @Test
    public void testShowMessageUpdatesStatusBar() throws Exception {
        handler.showMessage(new MessageParams(MessageType.Info, "Server ready"));
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        assertFalse(statusBar.messages.isEmpty());
        assertTrue(statusBar.messages.getFirst().contains("Server ready"));
        assertTrue(statusBar.messages.getFirst().contains("TestServer"));
    }
    @Test
    public void testShowMessageIncludesType() throws Exception {
        handler.showMessage(new MessageParams(MessageType.Error, "Kaboom"));
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        assertTrue(statusBar.messages.getFirst().contains("Error"));
    }
    @Test
    public void testLogMessageUpdatesStatusBar() throws Exception {
        handler.logMessage(new MessageParams(MessageType.Warning, "Slow index"));
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        assertFalse(statusBar.messages.isEmpty());
        assertTrue(statusBar.messages.getFirst().contains("Slow index"));
    }
    @Test
    public void testPublishDiagnosticsUpdatesStatusBar() throws Exception {
        var params = new PublishDiagnosticsParams("file:///foo/Bar.java",
                List.of(new Diagnostic(new Range(), "Undefined variable")));
        handler.publishDiagnostics(params);
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        assertFalse(statusBar.messages.isEmpty());
        assertTrue(statusBar.messages.getFirst().contains("1"));
        assertTrue(statusBar.messages.getFirst().contains("TestServer"));
    }
    @Test
    public void testPublishDiagnosticsZeroIssues() throws Exception {
        handler.publishDiagnostics(new PublishDiagnosticsParams("file:///foo/Bar.java", List.of()));
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        assertTrue(statusBar.messages.getFirst().contains("0"));
    }
    @Test
    public void testShowMessageRequestReturnsCompletedFuture() {
        var p = new ShowMessageRequestParams();
        p.setMessage("Confirm?");
        p.setActions(List.of(new MessageActionItem("OK")));
        CompletableFuture<MessageActionItem> f = handler.showMessageRequest(p);
        assertNotNull(f);
        assertTrue(f.isDone());
        assertNull(f.join());
    }
}