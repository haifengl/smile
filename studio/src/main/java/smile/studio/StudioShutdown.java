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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates bounded, parallel teardown of SMILE Studio subsystems off the
 * Swing EDT and serves as the single JVM shutdown hook for external signals
 * ({@code SIGTERM}, {@code SIGINT}).
 *
 * <p>All registered tasks execute concurrently on daemon platform threads so
 * one slow or unresponsive subprocess cannot delay the teardown of unrelated
 * services. If every task completes within the deadline, the coordinator exits
 * cleanly via {@link System#exit(int)}; otherwise it logs the unfinished tasks
 * and invokes {@link Runtime#halt(int)} so the process cannot wedge.
 *
 * @author Haifeng Li
 */
public final class StudioShutdown {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StudioShutdown.class);

    /** Default hard deadline for all parallel teardown tasks. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private record Task(String name, Runnable action) {}

    /**
     * Outcome of a {@link #runOnce(Duration)} teardown pass.
     *
     * @param completedInTime {@code true} if every registered task finished within the timeout.
     * @param unfinishedTasks names of tasks still running when the timeout expired.
     */
    public record Result(boolean completedInTime, List<String> unfinishedTasks) {}

    private final List<Task> tasks = new CopyOnWriteArrayList<>();
    private final List<Thread> starters = new CopyOnWriteArrayList<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean exitStarted = new AtomicBoolean(false);
    private final AtomicBoolean hookInstalled = new AtomicBoolean(false);
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Runnable exitAction;
    private final Runnable haltAction;
    private volatile Result result;

    /** Creates a coordinator backed by {@link System#exit(int)} and {@link Runtime#halt(int)}. */
    public StudioShutdown() {
        this(() -> System.exit(0), () -> Runtime.getRuntime().halt(0));
    }

    /**
     * Package-private constructor for deterministic unit testing.
     *
     * @param exitAction invoked when teardown finishes within the timeout.
     * @param haltAction invoked when teardown exceeds the timeout.
     */
    StudioShutdown(Runnable exitAction, Runnable haltAction) {
        this.exitAction = Objects.requireNonNull(exitAction, "exitAction");
        this.haltAction = Objects.requireNonNull(haltAction, "haltAction");
    }

    /**
     * Registers a named teardown task to execute during shutdown.
     *
     * @param name   human-readable identifier used in logs and thread names.
     * @param action teardown action; any exception is logged and isolated.
     */
    public void register(String name, Runnable action) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        tasks.add(new Task(name, action));
    }

    /**
     * Tracks a background startup thread so it can be interrupted when shutdown begins.
     * If shutdown has already started, the thread is interrupted immediately.
     *
     * @param thread the background startup thread.
     * @return the same thread for fluent call sites.
     */
    public Thread trackStarter(Thread thread) {
        Objects.requireNonNull(thread, "thread");
        starters.add(thread);
        if (shuttingDown.get() && thread.isAlive()) {
            thread.interrupt();
        }
        return thread;
    }

    /**
     * Returns {@code true} once shutdown has been initiated.
     *
     * @return whether shutdown is in progress or complete.
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * Installs a single JVM shutdown hook that runs this coordinator on external
     * termination signals (e.g. {@code SIGTERM}). Safe to call more than once.
     *
     * @param timeout maximum duration to wait for teardown tasks.
     */
    public void installJvmHook(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (hookInstalled.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> runOnce(timeout), "studio-shutdown"));
        }
    }

    /**
     * Runs all registered teardown tasks in parallel at most once. Concurrent or
     * subsequent callers wait up to {@code timeout} for the in-flight pass to
     * finish and receive its {@link Result}.
     *
     * @param timeout maximum duration to wait for all tasks to complete.
     * @return the teardown {@link Result}.
     */
    public Result runOnce(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (!shuttingDown.compareAndSet(false, true)) {
            return awaitInFlight(timeout);
        }

        try {
            interruptStarters();
            Result outcome = executeTasks(timeout);
            result = outcome;
            return outcome;
        } finally {
            finished.countDown();
        }
    }

    /**
     * Initiates Phase 2 shutdown on a dedicated non-daemon {@code studio-exit}
     * thread so the Swing EDT is never blocked waiting on subprocesses or hooks.
     *
     * @param timeout maximum duration to wait before halting the JVM.
     */
    public void exitAsync(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (!exitStarted.compareAndSet(false, true)) {
            return;
        }
        Thread.ofPlatform().name("studio-exit").start(() -> {
            Result outcome = runOnce(timeout);
            if (outcome.completedInTime()) {
                exitAction.run();
            } else {
                logger.warn("Shutdown timed out after {} ms; unfinished tasks: {}. Halting JVM.",
                        timeout.toMillis(), outcome.unfinishedTasks());
                haltAction.run();
            }
        });
    }

    private void interruptStarters() {
        for (Thread starter : starters) {
            if (starter.isAlive()) {
                starter.interrupt();
            }
        }
        starters.clear();
    }

    private Result executeTasks(Duration timeout) {
        List<Task> snapshot = List.copyOf(tasks);
        if (snapshot.isEmpty()) {
            return new Result(true, List.of());
        }

        Set<String> pending = ConcurrentHashMap.newKeySet();
        for (Task task : snapshot) {
            pending.add(task.name());
        }
        CountDownLatch done = new CountDownLatch(snapshot.size());

        for (Task task : snapshot) {
            Thread.ofPlatform()
                    .name("studio-shutdown-" + task.name())
                    .daemon(true)
                    .start(() -> {
                        try {
                            task.action().run();
                        } catch (Throwable ex) {
                            logger.warn("Shutdown task '{}' failed: {}", task.name(), ex.getMessage(), ex);
                        } finally {
                            pending.remove(task.name());
                            done.countDown();
                        }
                    });
        }

        boolean completed;
        try {
            completed = done.await(Math.max(0L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            completed = false;
        }

        List<String> unfinished = List.copyOf(pending);
        return new Result(completed && unfinished.isEmpty(), unfinished);
    }

    private Result awaitInFlight(Duration timeout) {
        try {
            finished.await(Math.max(0L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        Result cached = result;
        return cached != null ? cached : new Result(false, List.of());
    }
}
