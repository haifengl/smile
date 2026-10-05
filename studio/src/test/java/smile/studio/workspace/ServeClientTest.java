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
package smile.studio.workspace;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
}
