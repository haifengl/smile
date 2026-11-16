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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import smile.shell.Serve;

/**
 * Manages the single SMILE Serve process shared by every model in a Studio
 * session.
 *
 * <p>Follows the same lifecycle as the Agent Hub: if a healthy engine already
 * answers at the configured {@code host:port} — launched by another Studio, by
 * {@code smile serve} in a terminal, in a container, or on a remote host — this
 * manager <em>attaches</em> to it instead of spawning a second JVM. Only when
 * nothing responds and the host is local does it launch
 * {@code $SMILE_HOME/serve/quarkus-run.jar}.
 *
 * <p>Attaching is deliberately unverified: serve is shared infrastructure, so
 * "an engine for this {@code smile.home}" is the wrong ownership test. The
 * consequence is that Stop and Studio exit only <em>detach</em> from an engine
 * this JVM did not spawn, and the pid file is a record rather than an
 * authorization to kill (see {@link ServePidFile}).
 *
 * <p>Once running, N models share one JVM: models are loaded into it through
 * {@code POST /api/v1/models/load} rather than by starting a process per model.
 *
 * <p><strong>Threading.</strong> No network call is ever made while holding the
 * manager monitor, because the Swing EDT reads state through
 * {@link #isRunning()} / {@link #isManageable()} and an unresponsive remote
 * engine must not freeze the UI. State transitions are committed by
 * {@link #apply} after the probe completes, and a {@code generation} counter
 * discards the result of a start that was superseded by a stop or a new target.
 *
 * @author Haifeng Li
 */
public final class ServeManager {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ServeManager.class);
    /** How long to wait for the service to report ready after starting. */
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(120);
    /** Poll interval while waiting for readiness. */
    private static final long READY_POLL_MS = 500L;

    private static final ServeManager INSTANCE = new ServeManager();

    /** Guards one-time registration of the {@code serve-manager-shutdown} hook. */
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

    private final ProcessPanel panel = new ProcessPanel(1000);
    private final List<Consumer<ServiceEvent>> startListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ProcessPanel>> stopListeners = new CopyOnWriteArrayList<>();

    // ---- State guarded by `this`. Never perform I/O while holding it. ----
    private Process process;
    private String host;
    private int port;
    private String llm = "";
    private ServeClient client;
    /** True when connected to an engine this JVM did not spawn. */
    private boolean attached;
    /** Set by stop() so the periodic probe does not immediately re-attach. */
    private boolean stoppedByUser;
    /** Incremented by start() and stop() to invalidate in-flight work. */
    private long generation;

    private ServeManager() {
    }

    /**
     * A transition reported to start listeners.
     *
     * @param panel      the console panel.
     * @param background {@code true} when a periodic probe discovered the engine,
     *                   so the UI must not steal focus from the editor.
     * @param attached   {@code true} when Studio connected to an engine it did not spawn.
     */
    public record ServiceEvent(ProcessPanel panel, boolean background, boolean attached) {
    }

    /**
     * Returns the singleton manager.
     *
     * @return the manager.
     */
    public static ServeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns {@code true} when the engine is usable, whether this JVM owns it or
     * merely attached to it.
     *
     * <p>Holds the monitor only long enough to read the state fields; it never
     * performs I/O, so it is safe to call from the Swing EDT.
     *
     * @return {@code true} when running or attached.
     */
    public synchronized boolean isRunning() {
        return (process != null && process.isAlive()) || attached;
    }

    /**
     * Returns {@code true} when connected to an engine this JVM did not spawn.
     *
     * @return {@code true} when attached.
     */
    public synchronized boolean isAttached() {
        return attached;
    }

    /**
     * Returns {@code true} when model management is possible: the engine must be
     * local, because serve restricts those endpoints to localhost.
     *
     * <p>Safe to call from the Swing EDT: it reads state only.
     *
     * @return {@code true} when load/reload/unload can be issued.
     */
    public synchronized boolean isManageable() {
        return isRunning() && LocalHosts.isLocalHost(host);
    }

    /**
     * Returns the base URL of the connected engine.
     *
     * @return the base URL, or {@code null} when not connected.
     */
    public synchronized String baseUrl() {
        return client == null ? null : client.baseUrl();
    }

    /**
     * Returns the HTTP client for the configured engine, building it from
     * {@code studio.json} if no attempt has been made yet, so status and config
     * operations work before any start.
     *
     * @return the client, never {@code null}.
     */
    public synchronized ServeClient client() {
        if (client == null) {
            var cfg = StudioConfig.inferenceServer();
            this.host = ServeSupport.normalizeHost(cfg.host());
            this.port = ServeSupport.normalizePort(cfg.port());
            this.llm = ServeSupport.normalizeLlm(cfg.llm());
            this.client = new ServeClient(this.host, this.port);
        }
        return client;
    }

    /**
     * Returns the console output panel for the inference service.
     *
     * @return the process panel.
     */
    public ProcessPanel panel() {
        return panel;
    }

    /**
     * Registers a listener invoked on the Swing EDT when the engine becomes
     * available, whether started locally or attached to.
     *
     * @param listener callback receiving the {@link ServiceEvent}.
     */
    public void addStartListener(Consumer<ServiceEvent> listener) {
        if (listener != null) {
            startListeners.add(listener);
        }
    }

    /**
     * Removes a previously registered start listener.
     *
     * @param listener the listener to remove.
     */
    public void removeStartListener(Consumer<ServiceEvent> listener) {
        startListeners.remove(listener);
    }

    /**
     * Registers a listener invoked on the Swing EDT when the engine goes away.
     *
     * @param listener callback receiving the {@link ProcessPanel}.
     */
    public void addStopListener(Consumer<ProcessPanel> listener) {
        if (listener != null) {
            stopListeners.add(listener);
        }
    }

    /**
     * Connects to the configured engine using the settings in {@code studio.json},
     * starting one if none answers.
     *
     * @return {@code true} when the engine is running and ready.
     */
    public boolean start() {
        var cfg = StudioConfig.inferenceServer();
        return start(cfg.host(), cfg.port(), cfg.llm());
    }

    /**
     * Attaches to the engine at {@code host:port} if one answers, otherwise starts
     * a local one, using the configured LLM.
     *
     * @param host the bind or target host.
     * @param port the HTTP port.
     * @return {@code true} when the engine is running and ready.
     */
    public boolean start(String host, int port) {
        return start(host, port, StudioConfig.inferenceServer().llm());
    }

    /**
     * Attaches to the engine at {@code host:port} if one answers, otherwise starts
     * a local one.
     *
     * <p>Not {@code synchronized}: the target is snapshotted under the monitor,
     * then probing happens outside it. A concurrent {@link #stop()} or a second
     * {@code start} bumps {@link #generation}, and this call discards its result
     * rather than resurrecting a connection the user just closed.
     *
     * @param host the bind or target host.
     * @param port the HTTP port.
     * @param llm  optional Hugging Face model ID or directory for LLM chat.
     * @return {@code true} when the engine is running and ready.
     */
    public boolean start(String host, int port, String llm) {
        String normalizedHost = ServeSupport.normalizeHost(host);
        int normalizedPort = ServeSupport.normalizePort(port);
        String normalizedLlm = ServeSupport.normalizeLlm(llm);

        ServeClient probe;
        ServeClient reused = null;
        long ticket;
        synchronized (this) {
            boolean sameTarget = normalizedHost.equals(this.host)
                    && normalizedPort == this.port
                    && normalizedLlm.equals(this.llm);
            if (isRunning() && sameTarget) {
                // Verify liveness *outside* the monitor before reporting success.
                reused = client();
            } else {
                if (isRunning()) {
                    // Kills a child we own; only detaches from a foreign engine.
                    stop();
                }
                this.host = normalizedHost;
                this.port = normalizedPort;
                this.llm = normalizedLlm;
                this.stoppedByUser = false;
                this.client = new ServeClient(normalizedHost, normalizedPort);
                this.generation++;
            }
            ticket = this.generation;
            probe = this.client;
        }

        // ---- No I/O under the lock from here on. ----
        if (reused != null && reused.isAlive()) {
            notifyStarted(false);
            return true;
        }
        if (reused != null) {
            // The engine on an unchanged target is gone: re-establish from scratch.
            synchronized (this) {
                if (reused != client || ticket != generation) {
                    return false;
                }
                stop();
                this.stoppedByUser = false;
                this.client = new ServeClient(normalizedHost, normalizedPort);
                this.generation++;
                ticket = this.generation;
                probe = this.client;
            }
        }

        if (probe.isAlive()) {
            return commitAttach(ticket);
        }

        if (!LocalHosts.isLocalHost(normalizedHost)) {
            logger.error("No inference service answered at {}; a service on a remote host must be started there",
                    probe.baseUrl());
            return false;
        }

        String home = System.getProperty("smile.home", ".");
        Path jar = Serve.findQuarkusJar(home);
        if (!Files.isRegularFile(jar)) {
            logger.error("SMILE Serve runner JAR not found at {}. Build it with ./gradlew :serve:build", jar);
            return false;
        }

        Process child;
        synchronized (this) {
            if (ticket != generation) {
                return false;
            }
            child = spawn(jar, normalizedHost, normalizedPort, normalizedLlm);
        }
        if (awaitReady(child, probe)) {
            return true;
        }

        // A concurrent Studio may have bound the port while our child was starting;
        // that turns a failed spawn into a successful attach instead of an error.
        synchronized (this) {
            if (ticket == generation && child.isAlive()) {
                // The child survived but never became ready (slow load, transient
                // failure); we are abandoning it, so terminate rather than leak it.
                child.descendants().forEach(ProcessHandle::destroy);
                child.destroy();
                if (process == child) {
                    process = null;
                    ServePidFile.delete();
                }
            }
        }
        if (probe.isAlive()) {
            logger.info("Another process won the startup race at {}; attaching instead", probe.baseUrl());
            return commitAttach(ticket);
        }
        return false;
    }

    /**
     * Commits an attach transition, unless a stop or a new start superseded it.
     *
     * @param ticket the generation this attempt was issued under.
     * @return {@code true} when the attach was committed.
     */
    private boolean commitAttach(long ticket) {
        ServeClient committed;
        synchronized (this) {
            if (ticket != generation || stoppedByUser) {
                return false;
            }
            attached = true;
            process = null;
            committed = client;
        }
        populateAttachedPanel(committed);
        notifyStarted(false);
        return true;
    }

    /**
     * Launches the serve child process and records ownership.
     *
     * <p>Caller must hold the monitor; spawning is a local process operation, not
     * a network call, and the {@code process} field must be published atomically
     * with the arguments it was built from.
     *
     * @param jar  the Quarkus runner JAR.
     * @param host the bind host.
     * @param port the HTTP port.
     * @param llm  the configured LLM, possibly empty.
     * @return the spawned child process.
     */
    private Process spawn(Path jar, String host, int port, String llm) {
        List<String> cmd = ServeCommand.build(jar, host, port, llm);
        panel.start(cmd.toArray(String[]::new));
        Process child = panel.getProcess();
        process = child;
        attached = false;
        ServePidFile.write(child);
        notifyStarted(false);

        // Register the shutdown hook once for the singleton's lifetime. The hook
        // calls stop(), which is idempotent and reads the current process field,
        // so a single hook covers every start/restart cycle.
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "serve-manager-shutdown"));
        }
        return child;
    }

    /**
     * Populates {@link #panel} with a short status banner and the engine's current
     * model catalog, since an attached engine's stdout belongs to someone else.
     *
     * @param engine the client for the attached engine.
     */
    private void populateAttachedPanel(ServeClient engine) {
        List<String> models = engine.listModels();
        String panelText;
        synchronized (this) {
            panelText = ServeSupport.attachedPanelText(engine.baseUrl(), models, llm, host);
        }
        panel.setMessage(panelText);
    }

    /**
     * Waits until the spawned child reports ready.
     *
     * @param child the spawned child.
     * @param probe the client for the target.
     * @return {@code true} when ready before the timeout.
     */
    private boolean awaitReady(Process child, ServeClient probe) {
        boolean ready = ServeSupport.awaitReady(child, probe::isReady, READY_TIMEOUT, READY_POLL_MS);
        if (!ready && !child.isAlive()) {
            logger.error("Inference service exited before becoming ready");
        } else if (!ready) {
            logger.error("Inference service did not become ready within {}", READY_TIMEOUT);
        }
        return ready;
    }

    /**
     * Polls the configured engine and reconciles this manager's state with it.
     *
     * <p>Attaches silently when an engine appears, so an engine started elsewhere
     * is picked up without stealing focus, and detaches when an attached engine
     * disappears. A stop requested by the user suppresses re-attachment until the
     * next explicit start.
     */
    public void probe() {
        ServeClient engine;
        long ticket;
        boolean attachedNow;
        synchronized (this) {
            if (stoppedByUser) {
                return;
            }
            if (process != null) {
                if (!process.isAlive()) {
                    process = null;
                    ServePidFile.delete();
                    notifyStopped();
                }
                return;
            }
            attachedNow = attached;
            engine = client();
            ticket = generation;
        }

        // Liveness is a network call: never hold the monitor across it.
        boolean alive = engine.isAlive();

        if (attachedNow) {
            if (!alive) {
                synchronized (this) {
                    if (ticket == generation && attached) {
                        attached = false;
                        notifyStopped();
                    }
                }
            }
            return;
        }
        if (!alive) {
            return;
        }
        logger.info("Detected an inference service at {}", engine.baseUrl());
        synchronized (this) {
            if (ticket != generation || stoppedByUser || attached || process != null) {
                return;
            }
            attached = true;
        }
        populateAttachedPanel(engine);
        notifyStarted(true);
    }

    /**
     * Loads a model into the running service.
     *
     * @param modelPath the model file or directory path.
     * @return the loaded model id, or {@code null} when already loaded or unknown.
     * @throws ServeManagementForbiddenException when the engine is remote.
     * @throws IOException on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public String loadModel(String modelPath) throws IOException, InterruptedException {
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalArgumentException("modelPath must not be null or blank");
        }
        ServeClient c = client();
        if (!isRunning()) {
            throw new IOException("Inference service is not running");
        }
        return c.loadModel(modelPath);
    }

    /**
     * Unloads a model from the running service.
     *
     * @param id the model id.
     * @return {@code true} when unloaded.
     * @throws ServeManagementForbiddenException when the engine is remote.
     * @throws IOException on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public boolean unloadModel(String id) throws IOException, InterruptedException {
        ServeClient c = client();
        return isRunning() && c.unloadModel(id);
    }

    /**
     * Reloads a model from disk in the running service.
     *
     * @param id the model id.
     * @return {@code true} when reloaded.
     * @throws ServeManagementForbiddenException when the engine is remote.
     * @throws IOException on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public boolean reloadModel(String id) throws IOException, InterruptedException {
        ServeClient c = client();
        return isRunning() && c.reloadModel(id);
    }

    /**
     * Stops the engine if this JVM spawned it, otherwise detaches from it.
     *
     * <p>Only the child process this JVM launched is terminated. An engine that
     * was already running when Studio connected to it is left untouched; the user
     * stops it where it was started. The pid file is deliberately <em>not</em> used
     * to kill: it lives under a shared {@code smile.home}, so a second Studio that
     * attached to the first one's engine would otherwise be able to terminate it.
     *
     * <p>The {@code stoppedByUser} latch keeps {@link #probe()} from re-attaching
     * three seconds later, so Stop actually disconnects.
     */
    public synchronized void stop() {
        stoppedByUser = true;
        generation++;
        boolean wasRunning = process != null || attached;
        panel.stop();
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        }
        process = null;
        attached = false;
        ServePidFile.delete();
        client = null;
        if (wasRunning) {
            notifyStopped();
        }
    }

    private void notifyStarted(boolean background) {
        // The flag is captured here, on the calling thread, and closed over: reading
        // it inside the EDT task would race with a concurrent start()/probe().
        ServiceEvent event = new ServiceEvent(panel, background, attached);
        SwingUtilities.invokeLater(() -> {
            for (Consumer<ServiceEvent> l : startListeners) {
                l.accept(event);
            }
        });
    }

    private void notifyStopped() {
        SwingUtilities.invokeLater(() -> {
            for (Consumer<ProcessPanel> l : stopListeners) {
                l.accept(panel);
            }
        });
    }

    /**
     * Returns the ids of the models currently loaded in the service.
     *
     * @return the loaded model ids, or an empty list when not connected or unreachable.
     */
    public List<String> loadedModelIds() {
        if (!isRunning()) {
            return List.of();
        }
        return client().listModels();
    }
}
