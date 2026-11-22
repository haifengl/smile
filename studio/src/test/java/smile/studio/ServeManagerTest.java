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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.swing.JPanel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.studio.bot.HubManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ServeManager}.
 *
 * <p>Process spawning is not exercised here; attach behaviour is covered against
 * a stub HTTP server, and the not-running state against a freshly stopped
 * manager.
 *
 * @author Haifeng Li
 */
public class ServeManagerTest {
    /** Stub endpoints keyed by path, mapped to {status, body}. */
    private final Map<String, Object[]> routes = new ConcurrentHashMap<>();
    private HttpServer server;

    @BeforeEach
    public void setUp() {
        routes.clear();
        ServeManager.getInstance().stop();
    }

    @AfterEach
    public void tearDown() throws Exception {
        ServeManager.getInstance().stop();
        stopServer();
    }

    private void startServer() throws IOException {
        startServer(0);
    }

    private void startServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", exchange -> {
            Object[] route = routes.get(exchange.getRequestURI().getPath());
            int status = route == null ? 404 : (Integer) route[0];
            String body = route == null ? "{}" : (String) route[1];
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void route(String path, int status, String body) {
        routes.put(path, new Object[]{status, body});
    }

    private int port() {
        return server.getAddress().getPort();
    }

    private void serveIsAlive(List<String> models) {
        route("/q/health/live", 200, "{\"status\":\"UP\"}");
        String ids = models.stream()
                .map(id -> "{\"id\":\"" + id + "\"}")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        route("/v1/models", 200, "{\"object\":\"list\",\"data\":[" + ids + "]}");
    }

    @Test
    public void testSingleton() {
        // Then
        assertSame(ServeManager.getInstance(), ServeManager.getInstance());
    }

    @Test
    public void testNotRunningInitially() {
        // Given: a fresh manager (stop clears any prior state).
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertFalse(manager.isRunning());
        assertNull(manager.baseUrl());
        // The client is built lazily from studio.json rather than being null, so the
        // configured target can be probed before any start.
        assertNotNull(manager.client());
    }

    @Test
    public void testLoadedModelIdsEmptyWhenNotRunning() {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertEquals(0, manager.loadedModelIds().size());
    }

    @Test
    public void testUnloadReturnsFalseWhenNotRunning() throws Exception {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertFalse(manager.unloadModel("iris-1"));
    }

    @Test
    public void testReloadReturnsFalseWhenNotRunning() throws Exception {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertFalse(manager.reloadModel("iris-1"));
    }

    @Test
    public void testLoadModelRejectsNullPath() {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then: a null path is rejected before any transport attempt.
        assertThrows(IllegalArgumentException.class, () -> manager.loadModel(null));
    }

    @Test
    public void testLoadModelRejectsBlankPath() {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertThrows(IllegalArgumentException.class, () -> manager.loadModel("   "));
    }

    @Test
    public void testLoadModelThrowsWhenNotRunning() {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then: a valid path with no service running is a transport error.
        assertThrows(IOException.class, () -> manager.loadModel("iris.sml"));
    }

    @Test
    public void testProcessPanelIsJPanelAndSharedByManagers() {
        // Given
        var servePanel = ServeManager.getInstance().panel();
        var hubPanel = HubManager.getInstance().panel();

        // Then
        assertNotNull(servePanel);
        assertNotNull(hubPanel);
        assertTrue(servePanel instanceof JPanel);
        assertTrue(hubPanel instanceof JPanel);
        assertSame(servePanel, ServeManager.getInstance().panel());
        assertSame(hubPanel, HubManager.getInstance().panel());
    }

    @Test
    public void testAttachToStringLiveEngine() throws Exception {
        // Given: an engine already answering, as another Studio or `smile serve` would leave it.
        startServer();
        serveIsAlive(List.of("iris-1"));

        // When
        boolean started = ServeManager.getInstance().start("127.0.0.1", port(), "");

        // Then: Studio attaches instead of spawning a second JVM.
        var manager = ServeManager.getInstance();
        assertTrue(started);
        assertTrue(manager.isRunning());
        assertTrue(manager.isAttached());
        assertEquals("http://127.0.0.1:" + port(), manager.baseUrl());
        assertEquals(List.of("iris-1"), manager.loadedModelIds());
    }

    @Test
    public void testAttachDoesNotRequireServeJar() throws Exception {
        // Given: a live engine on the configured port.
        startServer();
        serveIsAlive(List.of());

        // When: the attach path must not consult the local runner JAR at all.
        boolean started = ServeManager.getInstance().start("127.0.0.1", port(), "");

        // Then
        assertTrue(started);
        assertTrue(ServeManager.getInstance().isAttached());
    }

    @Test
    public void testStopDetachesWithoutKillingForeignEngine() throws Exception {
        // Given: attached to an engine this JVM did not spawn.
        startServer();
        serveIsAlive(List.of());
        var manager = ServeManager.getInstance();
        assertTrue(manager.start("127.0.0.1", port(), ""));

        // When
        manager.stop();

        // Then: Studio lets go of it, but the engine keeps running.
        assertFalse(manager.isRunning());
        assertFalse(manager.isAttached());
        try (var probe = java.net.http.HttpClient.newHttpClient()) {
            var response = probe.send(
                    java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create("http://127.0.0.1:" + port() + "/q/health/live")).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
    }

    @Test
    public void testRemoteHostNeverSpawns() {
        // Given: a non-loopback host with nothing listening.
        // When
        boolean started = ServeManager.getInstance().start("192.0.2.10", 18888, "");

        // Then: Studio refuses to launch a local JVM for a remote host.
        assertFalse(started);
        assertFalse(ServeManager.getInstance().isRunning());
    }

    @Test
    public void testLocalEngineIsManageable() throws Exception {
        // Given: a live loopback engine.
        startServer();
        serveIsAlive(List.of());
        var manager = ServeManager.getInstance();

        // When
        boolean started = manager.start("127.0.0.1", port(), "");

        // Then: management endpoints are reachable from localhost.
        assertTrue(started);
        assertTrue(manager.isManageable());
    }

    @Test
    public void testProbeDetachesWhenAttachedEngineDisappears() throws Exception {
        // Given: attached to a live engine.
        startServer();
        serveIsAlive(List.of());
        var manager = ServeManager.getInstance();
        assertTrue(manager.start("127.0.0.1", port(), ""));
        assertTrue(manager.isAttached());

        // When: the engine goes away.
        stopServer();
        manager.probe();

        // Then
        assertFalse(manager.isRunning());
        assertFalse(manager.isAttached());
    }

    @Test
    public void testStartRestartsOnDifferentTarget() throws Exception {
        // Given: attached to one engine.
        startServer();
        serveIsAlive(List.of());
        var manager = ServeManager.getInstance();
        int firstPort = port();
        assertTrue(manager.start("127.0.0.1", firstPort, ""));

        // When: pointed at a second engine.
        stopServer();
        startServer();
        serveIsAlive(List.of("phi-4"));
        assertTrue(manager.start("127.0.0.1", port(), ""));

        // Then
        assertEquals("http://127.0.0.1:" + port(), manager.baseUrl());
        assertEquals(List.of("phi-4"), manager.loadedModelIds());
    }

    @Test
    public void testDriftWarningMatchesLoadedModel() {
        // Given / When / Then
        assertTrue(ServeSupport.driftWarning(List.of("phi-4-mini"), "phi-4-mini").isEmpty());
        assertEquals(Optional.of("phi-4-mini"), ServeSupport.driftWarning(List.of("phi-4-mini-2"), "phi-4-mini"));
        assertTrue(ServeSupport.driftWarning(List.of("phi-4-mini"), "").isEmpty());
        assertTrue(ServeSupport.driftWarning(List.of("phi-4-mini"), null).isEmpty());
    }

    @Test
    public void testDriftWarningReportsMissingModel() {
        // Given / When
        Optional<String> warning = ServeSupport.driftWarning(List.of("phi-4-mini"), "Qwen3.8-27B");

        // Then
        assertEquals(Optional.of("Qwen3.8-27B"), warning);
    }

    @Test
    public void testDriftWarningReportsWhenCatalogIsEmpty() {
        // Given / When / Then: an empty but readable catalog means nothing is loaded.
        assertEquals(Optional.of("Qwen3.8-27B"), ServeSupport.driftWarning(List.of(), "Qwen3.8-27B"));
    }

    @Test
    public void testProbeDoesNotReattachAfterUserStop() throws Exception {
        // Given: attached to a live engine, then stopped by the user.
        startServer();
        serveIsAlive(List.of());
        var manager = ServeManager.getInstance();
        assertTrue(manager.start("127.0.0.1", port(), ""));
        manager.stop();
        assertFalse(manager.isRunning());

        // When: the periodic probe runs while the engine is still up.
        manager.probe();

        // Then: Stop actually disconnects instead of being undone by the next tick.
        assertFalse(manager.isRunning());
        assertFalse(manager.isAttached());
    }

    @Test
    public void testProbeReattachesAfterExplicitStart() throws Exception {
        // Given: stopped by the user, then explicitly started again.
        startServer();
        serveIsAlive(List.of());
        var manager = ServeManager.getInstance();
        assertTrue(manager.start("127.0.0.1", port(), ""));
        manager.stop();
        assertTrue(manager.start("127.0.0.1", port(), ""));

        // When / Then: start() clears the latch, so a probe keeps the connection.
        manager.probe();
        assertTrue(manager.isAttached());
    }

    @Test
    public void testBackgroundFlagIsCarriedPerEvent() throws Exception {
        // Given: Studio tried to connect before the engine existed, so no connection
        // is open and no stop has been requested.
        Assumptions.assumeFalse(
                java.nio.file.Files.isRegularFile(
                        smile.shell.Serve.findQuarkusJar(System.getProperty("smile.home", "."))),
                "a local serve JAR would spawn a real engine");

        int freePort;
        try (var socket = new java.net.ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        var manager = ServeManager.getInstance();
        // Drain notifications queued by earlier tests before registering, so the
        // listener only observes events raised by this test.
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        List<ServeManager.ServiceEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        Consumer<ServeManager.ServiceEvent> listener = events::add;
        manager.addStartListener(listener);
        try {
            assertFalse(manager.start("127.0.0.1", freePort, ""), "nothing is listening yet");

            // When: an engine appears on that target and the probe discovers it.
            startServer(freePort);
            serveIsAlive(List.of());
            manager.probe();
            javax.swing.SwingUtilities.invokeAndWait(() -> { });

            // Then: the probe-driven attach is flagged background so it cannot steal
            // focus, and the earlier failed attempt produced no start event.
            assertTrue(events.stream().anyMatch(ServeManager.ServiceEvent::background),
                    "the probe-driven attach must be flagged background");
            assertTrue(events.stream().allMatch(ServeManager.ServiceEvent::background),
                    "no foreground event should be raised by a failed start");
        } finally {
            manager.removeStartListener(listener);
        }
    }

    @Test
    public void testStopIsIdempotent() throws Exception {
        // Given: attached.
        startServer();
        serveIsAlive(List.of());
        var manager = ServeManager.getInstance();
        assertTrue(manager.start("127.0.0.1", port(), ""));

        // When: stopped twice.
        manager.stop();
        manager.stop();

        // Then
        assertFalse(manager.isRunning());
    }

    @Test
    public void testProbeIsCheapWhenNothingIsConfigured() {
        // Given: a stopped manager pointed at a port with no server.
        var manager = ServeManager.getInstance();
        manager.stop();

        // When: probe runs against the default target.
        manager.probe();

        // Then: no state change, no exception.
        assertFalse(manager.isRunning());
    }
}
