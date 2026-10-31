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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import ioa.agent.bot.ProjectsConfig;
import smile.shell.Hub;
import smile.shell.Serve;
import smile.studio.ProcessPanel;
import smile.studio.ServeManager;
import smile.studio.StudioConfig;

/**
 * Manages the Agent Hub &amp; Bot runtime service for a SMILE Studio session.
 *
 * <p>Mirrors {@link ServeManager}: if an Agent Hub is already running at the
 * configured {@code host:port} (either launched via {@code smile hub} in a terminal
 * or running remotely), {@link #start(String, int, String)} attaches to it without
 * spawning a second JVM. Otherwise, it launches {@code $SMILE_HOME/hub/quarkus-run.jar}
 * in a {@link ProcessPanel}, syncs tracked
 * Studio projects from {@code ${smile.home}/conf/projects.json}, and stops the child
 * process on Studio exit unless {@code keepAliveOnExit} is enabled in {@code studio.json}.
 *
 * @author Haifeng Li
 */
public final class HubManager {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HubManager.class);
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(90);
    private static final long READY_POLL_MS = 400L;

    private static final HubManager INSTANCE = new HubManager();

    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final ProcessPanel panel = new ProcessPanel(1000);
    private final List<Consumer<ProcessPanel>> startListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ProcessPanel>> stopListeners = new CopyOnWriteArrayList<>();

    private Process process;
    private String host;
    private int port;
    private String adminToken;
    private HubClient client;

    private HubManager() {
    }

    /** Returns the singleton manager. */
    public static HubManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns {@code true} when either the managed child process is alive or
     * a remote/attached Agent Hub responds ready at {@link #baseUrl()}.
     */
    public synchronized boolean isRunning() {
        if (process != null && process.isAlive()) {
            return true;
        }
        return client != null && client.isReady();
    }

    /** Returns the base URL of the connected Agent Hub, or {@code null} when not connected. */
    public synchronized String baseUrl() {
        return client == null ? null : client.baseUrl();
    }

    /**
     * Returns the {@link HubClient} (creating one with default {@link StudioConfig#agentHub()}
     * settings if none has been initialized yet, so offline config operations always work).
     */
    public synchronized HubClient client() {
        if (client == null) {
            var cfg = StudioConfig.agentHub();
            this.host = cfg.host();
            this.port = cfg.port();
            this.adminToken = cfg.adminToken();
            this.client = new HubClient(cfg.host(), cfg.port(), cfg.adminToken());
        }
        return client;
    }

    /**
     * Returns the console output panel for the local Agent Hub process.
     *
     * @return the process panel.
     */
    public ProcessPanel panel() {
        return panel;
    }

    /**
     * Registers a listener invoked on the Swing EDT when the local Agent Hub process starts.
     *
     * @param listener callback receiving the {@link ProcessPanel}.
     */
    public void addStartListener(Consumer<ProcessPanel> listener) {
        if (listener != null) {
            startListeners.add(listener);
        }
    }

    /**
     * Registers a listener invoked on the Swing EDT when the local Agent Hub process stops.
     *
     * @param listener callback receiving the {@link ProcessPanel}.
     */
    public void addStopListener(Consumer<ProcessPanel> listener) {
        if (listener != null) {
            stopListeners.add(listener);
        }
    }

    /**
     * Starts or attaches to the Agent Hub using the settings in {@code studio.json}.
     */
    public synchronized boolean start() {
        var cfg = StudioConfig.agentHub();
        return start(cfg.host(), cfg.port(), cfg.adminToken());
    }

    /**
     * Starts the local Agent Hub (or attaches if one is already running on {@code host:port}).
     *
     * @param host       the bind or target host.
     * @param port       the HTTP port.
     * @param adminToken the admin bearer token.
     * @return {@code true} when the service is running and ready.
     */
    public synchronized boolean start(String host, int port, String adminToken) {
        this.host = (host == null || host.isBlank()) ? StudioConfig.DEFAULT_HOST : host.trim();
        this.port = port > 0 ? port : StudioConfig.DEFAULT_HUB_PORT;
        this.adminToken = (adminToken == null || adminToken.isBlank())
                ? StudioConfig.DEFAULT_HUB_ADMIN_TOKEN
                : adminToken.trim();
        this.client = new HubClient(this.host, this.port, this.adminToken);

        // If an Agent Hub is already reachable on host:port (e.g., started via `smile hub`
        // or running on a remote host), attach to it directly.
        if (this.client.isReady()) {
            logger.info("Attached to running Agent Hub at {}", this.client.baseUrl());
            if (process != null && process.isAlive()) {
                notifyStarted();
            }
            syncTrackedProjects();
            return true;
        }

        if (process != null && process.isAlive()) {
            stop();
            this.client = new HubClient(this.host, this.port, this.adminToken);
        }

        String home = System.getProperty("smile.home", ".");
        Path homePath = Path.of(home).toAbsolutePath().normalize();
        Path jar = Hub.findQuarkusJar(home);
        if (!Files.isRegularFile(jar)) {
            logger.error("Agent Hub runner JAR not found at {}. Build it with ./gradlew :hub:copyHubToStudio", jar);
            return false;
        }

        Path botWorkspace = homePath.resolve("bot");
        try {
            Files.createDirectories(botWorkspace);
        } catch (Exception ex) {
            logger.warn("Could not create bot workspace {}: {}", botWorkspace, ex.getMessage());
        }

        panel.start(Serve.javaExecutable(),
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/java.nio=ALL-UNNAMED",
                "--enable-native-access", "ALL-UNNAMED",
                "-Dsmile.home=" + homePath,
                "-Dquarkus.profile=local",
                "-Dhub.agent.workspace=" + botWorkspace,
                "-Dhub.admin.token=" + this.adminToken,
                "-Dquarkus.http.host=" + this.host,
                "-Dquarkus.http.port=" + this.port,
                "-jar", jar.toString());
        process = panel.getProcess();
        notifyStarted();

        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!StudioConfig.agentHub().keepAliveOnExit()) {
                    stop();
                }
            }, "hub-manager-shutdown"));
        }

        boolean ready = awaitReady();
        if (ready) {
            syncTrackedProjects();
        }
        return ready;
    }

    /**
     * Syncs tracked Studio projects from {@code ${smile.home}/conf/projects.json}
     * to the running Agent Hub.
     */
    public void syncTrackedProjects() {
        HubClient c = client();
        if (c == null || !c.isReady()) {
            return;
        }
        try {
            c.syncProjects(ProjectsConfig.load().projects());
        } catch (Exception ex) {
            logger.debug("Could not sync tracked projects to Agent Hub: {}", ex.getMessage());
        }
    }

    private boolean awaitReady() {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (process == null || !process.isAlive()) {
                logger.error("Agent Hub exited before becoming ready");
                return false;
            }
            if (client.isReady()) {
                return true;
            }
            try {
                Thread.sleep(READY_POLL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        logger.error("Agent Hub did not become ready within {}", READY_TIMEOUT);
        return false;
    }

    /** Stops the managed local Agent Hub process. */
    public synchronized void stop() {
        boolean hadProcess = process != null || panel.getProcess() != null;
        panel.stop();
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        }
        process = null;
        if (hadProcess) {
            notifyStopped();
        }
    }

    private void notifyStarted() {
        SwingUtilities.invokeLater(() -> {
            for (Consumer<ProcessPanel> l : startListeners) {
                l.accept(panel);
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
}
