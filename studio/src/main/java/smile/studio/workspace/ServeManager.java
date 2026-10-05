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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import smile.shell.Serve;
import smile.studio.ProcessFrame;

/**
 * Manages the single SMILE Serve process shared by every model in a Studio
 * session.
 *
 * <p>Previously Studio launched one serve JVM per saved model, each with its own
 * port and memory footprint. This manager starts at most one process and loads
 * additional models into it via {@code POST /api/v1/models/load}, so N models
 * share one JVM.
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

    private Process process;
    private ProcessFrame frame;
    private String host;
    private int port;
    private ServeClient client;

    private ServeManager() {
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
     * Returns {@code true} when the service process is running.
     *
     * @return {@code true} when running.
     */
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    /**
     * Returns the base URL of the running service.
     *
     * @return the base URL, or {@code null} when not running.
     */
    public synchronized String baseUrl() {
        return client == null ? null : client.baseUrl();
    }

    /**
     * Returns the HTTP client for the running service.
     *
     * @return the client, or {@code null} when not running.
     */
    public synchronized ServeClient client() {
        return client;
    }

    /**
     * Starts the service if it is not already running on the given host and port.
     * If it is running on a different host or port, it is stopped and restarted.
     *
     * @param host the bind host.
     * @param port the HTTP port.
     * @return {@code true} when the service is running and ready.
     */
    public synchronized boolean start(String host, int port) {
        if (isRunning() && host.equals(this.host) && port == this.port) {
            return true;
        }
        if (isRunning()) {
            stop();
        }

        String home = System.getProperty("smile.home", ".");
        Path jar = Serve.findQuarkusJar(home);
        if (!java.nio.file.Files.isRegularFile(jar)) {
            logger.error("SMILE Serve runner JAR not found at {}. Build it with ./gradlew :serve:build", jar);
            return false;
        }

        this.host = host;
        this.port = port;
        this.client = new ServeClient(port);

        frame = new ProcessFrame(1000);
        frame.setTitle("Inference Service");
        // No -Dsmile.serve.model: the service starts empty and models are loaded
        // on demand through the REST API. allow-empty is required, otherwise the
        // service exits at startup before the first load request arrives.
        frame.start(Serve.javaExecutable(),
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/java.nio=ALL-UNNAMED",
                "--enable-native-access", "ALL-UNNAMED",
                "-Dsmile.serve.allow-empty=true",
                "-Dquarkus.http.host=" + host,
                "-Dquarkus.http.port=" + port,
                "-jar", jar.toString());
        frame.setVisible(true);
        process = frame.getProcess();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "serve-manager-shutdown"));
        return awaitReady();
    }

    /**
     * Waits until the service reports ready.
     *
     * @return {@code true} when ready before the timeout.
     */
    private boolean awaitReady() {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (!isRunning()) {
                logger.error("Inference service exited before becoming ready");
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
        logger.error("Inference service did not become ready within {}", READY_TIMEOUT);
        return false;
    }

    /**
     * Loads a model into the running service, starting it first if necessary.
     *
     * @param modelPath the model file or directory path.
     * @return the loaded model id, or {@code null} when already loaded or unknown.
     * @throws IOException on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public String loadModel(String modelPath) throws IOException, InterruptedException {
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalArgumentException("modelPath must not be null or blank");
        }
        ServeClient c = client();
        if (c == null) {
            throw new IOException("Inference service is not running");
        }
        return c.loadModel(modelPath);
    }

    /**
     * Unloads a model from the running service.
     *
     * @param id the model id.
     * @return {@code true} when unloaded.
     * @throws IOException on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public boolean unloadModel(String id) throws IOException, InterruptedException {
        ServeClient c = client();
        return c != null && c.unloadModel(id);
    }

    /**
     * Reloads a model from disk in the running service.
     *
     * @param id the model id.
     * @return {@code true} when reloaded.
     * @throws IOException on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public boolean reloadModel(String id) throws IOException, InterruptedException {
        ServeClient c = client();
        return c != null && c.reloadModel(id);
    }

    /**
     * Stops the service and releases its resources.
     */
    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        process = null;
        client = null;
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    /**
     * Returns the ids of the models currently loaded in the service.
     *
     * @return the loaded model ids, or an empty list when not running.
     */
    public List<String> loadedModelIds() {
        ServeClient c = client();
        if (c == null) {
            return List.of();
        }
        try {
            var response = c.get("/api/v1/models");
            if (response.statusCode() != 200) {
                return List.of();
            }
            var node = new tools.jackson.databind.ObjectMapper().readTree(response.body());
            var data = node.get("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }
            List<String> ids = new java.util.ArrayList<>();
            for (var item : data) {
                if (item.has("id")) {
                    ids.add(item.get("id").asString());
                }
            }
            return ids;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }
}
