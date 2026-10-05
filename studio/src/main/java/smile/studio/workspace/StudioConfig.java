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
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Studio's system-wide configuration, read from {@code studio.json}.
 *
 * <p>The file is looked up in two locations, in order:
 * <ol>
 *   <li>{@code $SMILE_HOME/conf/studio.json}</li>
 *   <li>{@code ~/.smile/studio.json}</li>
 * </ol>
 *
 * <p>Unlike {@code mcp.json}, the project-local {@code ./.smile/studio.json} is
 * deliberately <em>not</em> consulted: a server binding is machine-wide, not
 * project-scoped, and a project-local override could silently start a server on
 * a different port. (MCP servers, by contrast, are often project-scoped, which
 * is why {@code mcp.json} does check the working directory.)
 *
 * @author Haifeng Li
 */
public final class StudioConfig {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StudioConfig.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** Default bind host: loopback, which is safe for a desktop app. */
    public static final String DEFAULT_HOST = "localhost";
    /** Default HTTP port. */
    public static final int DEFAULT_PORT = 8888;
    /**
     * Default model location, relative to the working directory.
     *
     * <p>Studio is normally launched from a project folder, which is also the
     * working directory the agents use to find {@code input/} and {@code output/}.
     * {@code ./model} is where a project keeps its trained models.
     */
    public static final String DEFAULT_MODEL_PATH = "./model";

    /** The inference server settings, with defaults applied. */
    public static final InferenceServer DEFAULT_INFERENCE_SERVER =
            new InferenceServer(false, DEFAULT_HOST, DEFAULT_PORT, DEFAULT_MODEL_PATH);

    private StudioConfig() {
    }

    /**
     * Inference server settings from {@code studio.json}.
     *
     * @param autoStart whether to start the service when Studio starts.
     * @param host      the bind host (default {@value #DEFAULT_HOST}).
     * @param port      the HTTP port (default {@value #DEFAULT_PORT}).
     * @param modelPath a model file or directory to load at startup
     *                  (default {@value #DEFAULT_MODEL_PATH}).
     */
    public record InferenceServer(boolean autoStart, String host, int port, String modelPath) {
    }

    /**
     * Returns the inference server settings, or the defaults when no
     * {@code studio.json} is present or it has no {@code inferenceServer} object.
     *
     * @return the inference server settings.
     */
    public static InferenceServer inferenceServer() {
        Path path = resolve();
        if (path == null) {
            return DEFAULT_INFERENCE_SERVER;
        }
        try {
            return parse(path);
        } catch (IOException ex) {
            logger.error("Failed to read {}: {}", path, ex.getMessage());
            return DEFAULT_INFERENCE_SERVER;
        }
    }

    /**
     * Resolves the {@code studio.json} path across the system-wide locations.
     *
     * @return the first existing path, or {@code null} when none exists.
     */
    static Path resolve() {
        String home = System.getProperty("smile.home");
        if (home != null && !home.isBlank()) {
            Path path = Path.of(home, "conf", "studio.json");
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            Path path = Path.of(userHome, ".smile", "studio.json");
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Parses a {@code studio.json} file, applying defaults for absent fields.
     *
     * @param path the configuration file.
     * @return the inference server settings.
     * @throws IOException if the file cannot be read or parsed.
     */
    static InferenceServer parse(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        JsonNode node = root.get("inferenceServer");
        if (node == null || node.isNull()) {
            return DEFAULT_INFERENCE_SERVER;
        }
        boolean autoStart = node.has("autoStart") && node.get("autoStart").asBoolean();
        String host = node.has("host") && !node.get("host").asString().isBlank()
                ? node.get("host").asString() : DEFAULT_HOST;
        int port = node.has("port") ? node.get("port").asInt() : DEFAULT_PORT;
        String modelPath = node.has("modelPath") && !node.get("modelPath").asString().isBlank()
                ? node.get("modelPath").asString() : DEFAULT_MODEL_PATH;
        return new InferenceServer(autoStart, host, port, modelPath);
    }
}
