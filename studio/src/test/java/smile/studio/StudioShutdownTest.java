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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StudioShutdown}.
 *
 * @author Haifeng Li
 */
public class StudioShutdownTest {

    @Test
    public void testRunsTasksInParallel() {
        // Given: three tasks that can only complete if all three run concurrently.
        var barrier = new CyclicBarrier(3);
        var completed = new AtomicInteger();
        var shutdown = new StudioShutdown(() -> {}, () -> {});
        for (int i = 0; i < 3; i++) {
            shutdown.register("task-" + i, () -> {
                try {
                    barrier.await(2, TimeUnit.SECONDS);
                    completed.incrementAndGet();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }

        // When
        var result = shutdown.runOnce(Duration.ofSeconds(3));

        // Then
        assertTrue(result.completedInTime());
        assertEquals(List.of(), result.unfinishedTasks());
        assertEquals(3, completed.get());
        assertTrue(shutdown.isShuttingDown());
    }

    @Test
    public void testRunOnceIsIdempotent() {
        // Given
        var runs = new AtomicInteger();
        var shutdown = new StudioShutdown(() -> {}, () -> {});
        shutdown.register("once", runs::incrementAndGet);

        // When
        var first = shutdown.runOnce(Duration.ofSeconds(2));
        var second = shutdown.runOnce(Duration.ofSeconds(2));

        // Then
        assertTrue(first.completedInTime());
        assertTrue(second.completedInTime());
        assertEquals(1, runs.get());
    }

    @Test
    public void testIsolatesTaskExceptions() {
        // Given
        var healthyRan = new AtomicBoolean();
        var shutdown = new StudioShutdown(() -> {}, () -> {});
        shutdown.register("failing", () -> {
            throw new IllegalStateException("boom");
        });
        shutdown.register("healthy", () -> healthyRan.set(true));

        // When
        var result = shutdown.runOnce(Duration.ofSeconds(2));

        // Then
        assertTrue(result.completedInTime());
        assertTrue(healthyRan.get());
    }

    @Test
    public void testInterruptsTrackedStarters() throws Exception {
        // Given
        var interrupted = new CountDownLatch(1);
        var shutdown = new StudioShutdown(() -> {}, () -> {});
        Thread starter = Thread.ofPlatform().daemon(true).start(() -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException ex) {
                interrupted.countDown();
            }
        });
        shutdown.trackStarter(starter);

        // When
        shutdown.runOnce(Duration.ofSeconds(2));

        // Then
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testExitAsyncCallsExitActionWhenCompletedInTime() throws Exception {
        // Given
        var exitCalled = new CountDownLatch(1);
        var haltCalled = new AtomicBoolean();
        var shutdown = new StudioShutdown(exitCalled::countDown, () -> haltCalled.set(true));
        shutdown.register("fast", () -> {});

        // When
        shutdown.exitAsync(Duration.ofSeconds(2));

        // Then
        assertTrue(exitCalled.await(2, TimeUnit.SECONDS));
        assertFalse(haltCalled.get());
    }

    @Test
    public void testExitAsyncCallsHaltActionAndReportsStragglerOnTimeout() throws Exception {
        // Given
        var releaseSlow = new CountDownLatch(1);
        var exitCalled = new AtomicBoolean();
        var haltCalled = new CountDownLatch(1);
        var shutdown = new StudioShutdown(() -> exitCalled.set(true), haltCalled::countDown);
        shutdown.register("fast", () -> {});
        shutdown.register("stuck-mcp", () -> {
            try {
                releaseSlow.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            // When
            shutdown.exitAsync(Duration.ofMillis(150));

            // Then
            assertTrue(haltCalled.await(2, TimeUnit.SECONDS));
            assertFalse(exitCalled.get());
            var cached = shutdown.runOnce(Duration.ofMillis(50));
            assertFalse(cached.completedInTime());
            assertEquals(List.of("stuck-mcp"), cached.unfinishedTasks());
        } finally {
            releaseSlow.countDown();
        }
    }
}
