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
package smile.studio.bot;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import smile.studio.StudioConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HubManager} locking and shutdown behavior.
 *
 * @author Haifeng Li
 */
public class HubManagerTest {
    private HttpServer server;

    @AfterEach
    public void tearDown() {
        HubManager.getInstance().stopEventStream();
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    public void testStopDoesNotBlockOnConcurrentReadyProbe() throws Exception {
        // Given: a slow readiness endpoint that holds the HTTP request open.
        var probeEntered = new CountDownLatch(1);
        var releaseProbe = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/q/health", exchange -> {
            probeEntered.countDown();
            try {
                releaseProbe.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        var manager = HubManager.getInstance();
        manager.refreshClient(new StudioConfig.AgentHub(
                false, "127.0.0.1", port, "test-token", false));

        // When: a background thread is blocked inside isRunning() -> client.isReady()
        Thread probeThread = Thread.ofPlatform().daemon(true).start(manager::isRunning);
        assertTrue(probeEntered.await(2, TimeUnit.SECONDS));

        // Then: stop() does not wait for the in-flight HTTP probe to finish.
        long startNanos = System.nanoTime();
        try {
            manager.stop();
        } finally {
            releaseProbe.countDown();
            probeThread.join(2000);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        assertTrue(elapsedMs < 1500, "HubManager.stop() blocked for " + elapsedMs + " ms");
    }

    @Test
    public void testStopEventStreamIsIdempotent() {
        // Given
        var manager = HubManager.getInstance();

        // When / Then: calling stopEventStream() before or after start never throws.
        manager.stopEventStream();
        manager.ensureEventStreamRunning();
        manager.stopEventStream();
        manager.stopEventStream();
    }
}
