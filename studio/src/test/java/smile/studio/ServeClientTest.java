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
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ServeClient} against a stub HTTP server.
 *
 * @author Haifeng Li
 */
public class ServeClientTest {
    private HttpServer server;
    private ServeClient client;
    /** The last request body received by the stub. */
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new ServeClient(server.getAddress().getPort());
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    @Test
    public void testLoadModelReturnsId() throws Exception {
        // Given
        respond("/api/v1/models/load", 200, "{\"status\":\"loaded\",\"id\":\"iris-1\"}");

        // When
        String id = client.loadModel("/models/iris.sml");

        // Then
        assertEquals("iris-1", id);
        assertTrue(lastBody.get().contains("\"model\":\"/models/iris.sml\""));
        assertTrue(lastBody.get().contains("\"kind\":\"auto\""));
    }

    @Test
    public void testLoadModelConflictIsTreatedAsLoaded() throws Exception {
        // Given: the model id is already loaded.
        respond("/api/v1/models/load", 409, "{\"error\":\"already loaded\"}");

        // When
        String id = client.loadModel("/models/iris.sml");

        // Then: no exception, and no id is reported.
        assertNull(id);
    }

    @Test
    public void testLoadModelFailureThrows() {
        // Given
        respond("/api/v1/models/load", 400, "{\"error\":\"bad model\"}");

        // When / Then
        assertThrows(IOException.class, () -> client.loadModel("/models/bad.sml"));
    }

    @Test
    public void testUnloadFallsBackToOnnx() throws Exception {
        // Given: the SMILE endpoint 404s, the ONNX endpoint succeeds.
        respond("/api/v1/smile/squeezenet/unload", 404, "{}");
        respond("/api/v1/onnx/squeezenet/unload", 200, "{\"status\":\"unloaded\"}");

        // When
        boolean unloaded = client.unloadModel("squeezenet");

        // Then
        assertTrue(unloaded);
    }

    @Test
    public void testIsReady() {
        // Given
        respond("/q/health/ready", 200, "{\"status\":\"UP\",\"checks\":[]}");

        // When / Then
        assertTrue(client.isReady());
    }

    @Test
    public void testIsNotReadyWhenDown() {
        // Given
        respond("/q/health/ready", 503, "{\"status\":\"DOWN\"}");

        // When / Then
        assertFalse(client.isReady());
    }

    @Test
    public void testIsNotReadyWhenUnreachable() {
        // Given: a client pointed at a port with no server.
        var dead = new ServeClient(1);

        // When / Then
        assertFalse(dead.isReady());
    }

    @Test
    public void testIsAliveWhenHealthy() {
        // Given
        respond("/q/health/live", 200, "{\"status\":\"UP\",\"checks\":[]}");

        // When / Then
        assertTrue(client.isAlive());
    }

    @Test
    public void testIsAliveWhenDown() {
        // Given: a liveness probe that reports DOWN.
        respond("/q/health/live", 503, "{\"status\":\"DOWN\"}");

        // When / Then
        assertFalse(client.isAlive());
    }

    @Test
    public void testIsAliveWhenPathIsNotServe() {
        // Given: an unrelated HTTP server that serves a JSON 200 on /q/health/live.
        respond("/q/health/live", 200, "{\"status\":\"ok\"}");

        // When / Then: attaching requires the serve-shaped UP payload.
        assertFalse(client.isAlive());
    }

    @Test
    public void testIsAliveWhenUnreachable() {
        // Given
        var dead = new ServeClient(1);

        // When / Then
        assertFalse(dead.isAlive());
    }

    @Test
    public void testBaseUrlUsesConfiguredHost() {
        // Given / When
        var remote = new ServeClient("192.0.2.10", 8888);

        // Then
        assertEquals("http://192.0.2.10:8888", remote.baseUrl());
    }

    @Test
    public void testBaseUrlDefaultsToLoopback() {
        // Given / When
        var local = new ServeClient("  ", 8888);

        // Then
        assertEquals("http://127.0.0.1:8888", local.baseUrl());
    }

    @Test
    public void testBaseUrlBracketsIpv6Host() {
        // Given / When
        var v6 = new ServeClient("::1", 8888);

        // Then
        assertEquals("http://[::1]:8888", v6.baseUrl());
    }

    @Test
    public void testBaseUrlRedirectsWildcardToLoopback() {
        // Given: a wildcard bind address is not a valid destination.
        // When
        var wildcard = new ServeClient("0.0.0.0", 8888);

        // Then: the client connects to loopback instead of 0.0.0.0.
        assertEquals("http://127.0.0.1:8888", wildcard.baseUrl());
    }

    @Test
    public void testListModelsParsesCatalog() {
        // Given
        respond("/api/v1/models", 200,
                "{\"object\":\"list\",\"data\":[{\"id\":\"iris-1\"},{\"id\":\"squeezenet-1\"}]}");

        // When
        var ids = client.listModels();

        // Then
        assertEquals(List.of("iris-1", "squeezenet-1"), ids);
    }

    @Test
    public void testListModelsEmptyWhenNotOk() {
        // Given
        respond("/api/v1/models", 500, "{\"error\":\"boom\"}");

        // When / Then
        assertTrue(client.listModels().isEmpty());
    }

    @Test
    public void testParseModelIdsToleratesMalformedBody() {
        // Given / When / Then
        assertTrue(ServeClient.parseModelIds("not json").isEmpty());
        assertTrue(ServeClient.parseModelIds("{\"data\":{}}").isEmpty());
        assertTrue(ServeClient.parseModelIds("").isEmpty());
        assertTrue(ServeClient.parseModelIds(null).isEmpty());
    }

    @Test
    public void testLoadModelForbiddenIsDistinguishable() {
        // Given: the server refuses a non-loopback caller.
        respond("/api/v1/models/load", 403, "{\"error\":\"localhost only\"}");

        // When / Then
        assertThrows(ServeManagementForbiddenException.class,
                () -> client.loadModel("/models/iris.sml"));
    }

    @Test
    public void testUnloadForbiddenIsDistinguishable() {
        // Given
        respond("/api/v1/smile/iris-1/unload", 403, "{\"error\":\"localhost only\"}");

        // When / Then
        assertThrows(ServeManagementForbiddenException.class,
                () -> client.unloadModel("iris-1"));
    }

    @Test
    public void testReloadForbiddenIsDistinguishable() {
        // Given
        respond("/api/v1/smile/iris-1/reload", 403, "{\"error\":\"localhost only\"}");

        // When / Then
        assertThrows(ServeManagementForbiddenException.class,
                () -> client.reloadModel("iris-1"));
    }
}
