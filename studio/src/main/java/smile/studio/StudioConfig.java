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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import ioa.agent.email.SharedSecrets;
import photos.sluice.secrets.SecretHolding;
import photos.sluice.secrets.SecretId;
import photos.sluice.secrets.SecretStatus;
import photos.sluice.secrets.SecretStore;
import photos.sluice.secrets.SecretStoreException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
    private static final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    /** Default bind host: loopback, which is safe for a desktop app. */
    public static final String DEFAULT_HOST = "localhost";
    /** Default HTTP port for Inference Server. */
    public static final int DEFAULT_PORT = 8888;
    /** Default HTTP port for Agent Hub. */
    public static final int DEFAULT_HUB_PORT = 8890;
    /** Default admin bearer token for local Agent Hub. */
    public static final String DEFAULT_HUB_ADMIN_TOKEN = "dev-admin-token";
    /** SecretStore identifier for the Agent Hub admin bearer token. */
    public static final SecretId AGENT_HUB_ADMIN_TOKEN_SECRET =
            new SecretId("agent-hub", "SMILE_HUB_ADMIN_TOKEN");
    /**
     * Default model location, relative to the working directory.
     *
     * <p>Studio is normally launched from a project folder, which is also the
     * working directory the agents use to find {@code input/} and {@code output/}.
     * {@code ./model} is where a project keeps its trained models.
     */
    public static final String DEFAULT_MODEL_PATH = "./model";

    /** Default LLM chat model (empty = none). */
    public static final String DEFAULT_LLM = "";

    /** The inference server settings, with defaults applied. */
    public static final InferenceServer DEFAULT_INFERENCE_SERVER =
            new InferenceServer(false, DEFAULT_HOST, DEFAULT_PORT, DEFAULT_MODEL_PATH, DEFAULT_LLM);

    /** The Agent Hub settings, with defaults applied. */
    public static final AgentHub DEFAULT_AGENT_HUB =
            new AgentHub(true, DEFAULT_HOST, DEFAULT_HUB_PORT, DEFAULT_HUB_ADMIN_TOKEN, false);

    private StudioConfig() {
    }

    /**
     * Inference server settings from {@code studio.json}.
     *
     * @param autoStart whether to start the service when Studio starts
     *                  (automatically {@code true} when {@code llm} is non-blank).
     * @param host      the bind host (default {@value #DEFAULT_HOST}).
     * @param port      the HTTP port (default {@value #DEFAULT_PORT}).
     * @param modelPath a model file or directory to load at startup
     *                  (default {@value #DEFAULT_MODEL_PATH}).
     * @param llm       an optional Hugging Face model ID or directory for LLM chat
     *                  (corresponds to {@code smile serve --llm}, default {@value #DEFAULT_LLM}).
     */
    public record InferenceServer(boolean autoStart, String host, int port, String modelPath, String llm) {
        /** Normalizes fields and enables {@code autoStart} when {@code llm} is specified. */
        public InferenceServer {
            llm = llm == null ? DEFAULT_LLM : llm.trim();
            if (!llm.isEmpty()) {
                autoStart = true;
            }
        }

        /**
         * Backwards-compatible constructor without {@code llm}.
         *
         * @param autoStart whether to start the service when Studio starts.
         * @param host      the bind host.
         * @param port      the HTTP port.
         * @param modelPath a model file or directory to load at startup.
         */
        public InferenceServer(boolean autoStart, String host, int port, String modelPath) {
            this(autoStart, host, port, modelPath, DEFAULT_LLM);
        }
    }

    /**
     * Agent Hub settings from {@code studio.json} and {@link SecretStore}.
     *
     * @param autoStart       whether to start or attach to Agent Hub when Studio starts.
     * @param host            the bind/target host (default {@value #DEFAULT_HOST}).
     * @param port            the HTTP port (default {@value #DEFAULT_HUB_PORT}).
     * @param adminToken      the bearer token for {@code /v1/admin/*} endpoints.
     * @param keepAliveOnExit whether to leave the local Agent Hub running when Studio closes.
     */
    public record AgentHub(boolean autoStart, String host, int port, String adminToken, boolean keepAliveOnExit) {
    }

    /**
     * The plugin configuration from {@code studio.json}'s {@code plugins} object.
     *
     * @param marketplaces the marketplace sources the plugin panel registers on
     *                     first open. Empty means "use the built-in default", which
     *                     is the official Anthropic marketplace
     *                     ({@code anthropics/claude-plugins-official}). Because the
     *                     list comes from the user's own configuration, its entries
     *                     are trusted to be fetched without an explicit add.
     */
    public record Plugins(List<String> marketplaces) {
        /** Normalizes the list. */
        public Plugins {
            marketplaces = marketplaces == null ? List.of() : List.copyOf(marketplaces);
        }

        /**
         * Returns whether this source is one the panel may register implicitly.
         * @param source the source string.
         * @return true when the source is in the configured list.
         */
        public boolean allows(String source) {
            if (source == null) {
                return false;
            }
            String normalized = source.trim();
            return marketplaces.stream().anyMatch(s -> s.trim().equals(normalized));
        }
    }

    /**
     * The default plugin configuration: an empty marketplace list, meaning the
     * built-in default (the official Anthropic marketplace) is seeded.
     */
    public static final Plugins DEFAULT_PLUGINS = new Plugins(List.of());

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
     * Returns the plugin marketplace policy, or the defaults when no
     * {@code studio.json} is present or it has no {@code plugins} object.
     *
     * @return the plugin policy.
     */
    public static Plugins plugins() {
        Path path = resolve();
        if (path == null) {
            return DEFAULT_PLUGINS;
        }
        try {
            return parsePlugins(path);
        } catch (IOException ex) {
            logger.error("Failed to read {}: {}", path, ex.getMessage());
            return DEFAULT_PLUGINS;
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
     * Resolves the target {@code studio.json} file path for saving configuration changes:
     * <ol>
     *   <li>Existing file returned by {@link #resolve()}, if present.</li>
     *   <li>{@code $SMILE_HOME/conf/studio.json} when {@code smile.home} is set and {@code conf} exists.</li>
     *   <li>{@code ~/.smile/studio.json} fallback.</li>
     * </ol>
     *
     * @return the target path for writing {@code studio.json}.
     */
    public static Path resolveWritablePath() {
        Path existing = resolve();
        if (existing != null) {
            return existing.toAbsolutePath().normalize();
        }
        String home = System.getProperty("smile.home");
        if (home != null && !home.isBlank()) {
            Path homeConf = Path.of(home.trim(), "conf", "studio.json").toAbsolutePath().normalize();
            if (Files.exists(homeConf) || Files.isDirectory(homeConf.getParent())) {
                return homeConf;
            }
        }
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, ".smile", "studio.json").toAbsolutePath().normalize();
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
        String llm = node.has("llm") && !node.get("llm").isNull()
                ? node.get("llm").asString().trim() : DEFAULT_LLM;
        boolean autoStart = (node.has("autoStart") && node.get("autoStart").asBoolean())
                || !llm.isEmpty();
        String host = node.has("host") && !node.get("host").asString().isBlank()
                ? node.get("host").asString().trim() : DEFAULT_HOST;
        int port = node.has("port") ? node.get("port").asInt() : DEFAULT_PORT;
        String modelPath = node.has("modelPath") && !node.get("modelPath").asString().isBlank()
                ? node.get("modelPath").asString().trim() : DEFAULT_MODEL_PATH;
        return new InferenceServer(autoStart, host, port, modelPath, llm);
    }

    /**
     * Parses a {@code studio.json} file's {@code plugins} object.
     *
     * @param path the configuration file.
     * @return the plugin configuration.
     * @throws IOException if the file cannot be read or parsed.
     */
    static Plugins parsePlugins(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        JsonNode node = root.get("plugins");
        if (node == null || !node.isObject()) {
            return DEFAULT_PLUGINS;
        }
        var marketplaces = new java.util.ArrayList<String>();
        JsonNode list = node.get("marketplaces");
        if (list != null && list.isArray()) {
            for (JsonNode element : list) {
                if (element != null && element.isString() && !element.asString().isBlank()) {
                    marketplaces.add(element.asString().trim());
                }
            }
        }
        return new Plugins(marketplaces);
    }

    /**
     * Returns the Agent Hub settings, resolving {@code adminToken} from the shared
     * {@link SecretStore} (or migrating a legacy plaintext token from {@code studio.json},
     * falling back to {@link #DEFAULT_HUB_ADMIN_TOKEN} when absent).
     *
     * @return the Agent Hub settings.
     */
    public static AgentHub agentHub() {
        return agentHub(SharedSecrets.store());
    }

    /**
     * Returns the Agent Hub settings using the provided {@link SecretStore}.
     *
     * @param secrets the secret store to read {@link #AGENT_HUB_ADMIN_TOKEN_SECRET} from.
     * @return the Agent Hub settings.
     */
    public static AgentHub agentHub(SecretStore secrets) {
        Path path = resolve();
        String secretToken = readAdminTokenSecret(secrets).orElse("");
        if (path == null) {
            String effectiveToken = secretToken.isEmpty() ? DEFAULT_HUB_ADMIN_TOKEN : secretToken;
            return new AgentHub(
                    DEFAULT_AGENT_HUB.autoStart(),
                    DEFAULT_AGENT_HUB.host(),
                    DEFAULT_AGENT_HUB.port(),
                    effectiveToken,
                    DEFAULT_AGENT_HUB.keepAliveOnExit()
            );
        }
        try {
            return parseAgentHub(path, secrets);
        } catch (IOException ex) {
            logger.error("Failed to read {}: {}", path, ex.getMessage());
            String effectiveToken = secretToken.isEmpty() ? DEFAULT_HUB_ADMIN_TOKEN : secretToken;
            return new AgentHub(
                    DEFAULT_AGENT_HUB.autoStart(),
                    DEFAULT_AGENT_HUB.host(),
                    DEFAULT_AGENT_HUB.port(),
                    effectiveToken,
                    DEFAULT_AGENT_HUB.keepAliveOnExit()
            );
        }
    }

    /**
     * Parses the {@code agentHub} object from the given {@code studio.json} path.
     *
     * @param path the configuration file.
     * @return the parsed Agent Hub settings.
     * @throws IOException if the file cannot be read or parsed.
     */
    public static AgentHub parseAgentHub(Path path) throws IOException {
        return parseAgentHub(path, null);
    }

    /**
     * Parses the {@code agentHub} object from the given {@code studio.json} path and
     * resolves {@code adminToken} against {@code secrets} (migrating any legacy plaintext
     * {@code adminToken} found in JSON when {@code secrets} is provided).
     *
     * @param path    the configuration file.
     * @param secrets optional secret store for resolving/migrating {@code adminToken}.
     * @return the parsed Agent Hub settings.
     * @throws IOException if the file cannot be read or parsed.
     */
    public static AgentHub parseAgentHub(Path path, SecretStore secrets) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        JsonNode node = root.get("agentHub");
        Optional<String> fromSecretStore = secrets != null ? readAdminTokenSecret(secrets) : Optional.empty();
        if (node == null || node.isNull()) {
            String token = fromSecretStore.filter(s -> !s.isBlank()).orElse(DEFAULT_HUB_ADMIN_TOKEN);
            return new AgentHub(
                    DEFAULT_AGENT_HUB.autoStart(),
                    DEFAULT_AGENT_HUB.host(),
                    DEFAULT_AGENT_HUB.port(),
                    token,
                    DEFAULT_AGENT_HUB.keepAliveOnExit()
            );
        }
        boolean autoStart = !node.has("autoStart") || node.get("autoStart").asBoolean();
        String host = node.has("host") && !node.get("host").asString().isBlank()
                ? node.get("host").asString().trim() : DEFAULT_HOST;
        int port = node.has("port") ? node.get("port").asInt() : DEFAULT_HUB_PORT;
        boolean keepAliveOnExit = node.has("keepAliveOnExit") && node.get("keepAliveOnExit").asBoolean();

        String legacyJsonToken = node.has("adminToken") && !node.get("adminToken").asString().isBlank()
                ? node.get("adminToken").asString().trim()
                : "";
        if (fromSecretStore.isEmpty()
                && !legacyJsonToken.isEmpty()
                && !DEFAULT_HUB_ADMIN_TOKEN.equals(legacyJsonToken)
                && secrets != null) {
            saveAdminTokenSecret(secrets, legacyJsonToken);
            fromSecretStore = Optional.of(legacyJsonToken);
        }

        String effectiveToken = fromSecretStore.filter(s -> !s.isBlank())
                .orElse(!legacyJsonToken.isEmpty() ? legacyJsonToken : DEFAULT_HUB_ADMIN_TOKEN);
        return new AgentHub(autoStart, host, port, effectiveToken, keepAliveOnExit);
    }

    /**
     * Reads the stored Agent Hub admin token from {@link SecretStore} (or environment override),
     * returning {@link Optional#empty()} if not set.
     *
     * @param secrets the secret store to query.
     * @return the stored admin token, if present.
     */
    public static Optional<String> readAdminTokenSecret(SecretStore secrets) {
        if (secrets == null) {
            return Optional.empty();
        }
        try {
            return secrets.secret(AGENT_HUB_ADMIN_TOKEN_SECRET)
                    .map(String::strip)
                    .filter(s -> !s.isEmpty());
        } catch (SecretStoreException ex) {
            logger.debug("Could not read Agent Hub admin token from SecretStore: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Saves or clears the Agent Hub admin token in {@link SecretStore}.
     * If {@code token} is null, blank, or equal to {@link #DEFAULT_HUB_ADMIN_TOKEN},
     * any existing stored secret is removed so the default token is used.
     *
     * @param secrets the secret store.
     * @param token   the admin token to store, or blank/default to clear.
     */
    public static void saveAdminTokenSecret(SecretStore secrets, String token) {
        if (secrets == null) {
            return;
        }
        String stripped = token == null ? "" : token.strip();
        try {
            if (stripped.isEmpty() || DEFAULT_HUB_ADMIN_TOKEN.equals(stripped)) {
                boolean hasStored = secrets.holdings(AGENT_HUB_ADMIN_TOKEN_SECRET).stream()
                        .anyMatch(h -> h.location() instanceof SecretStatus.StoredLocation
                                && h.holding() == SecretHolding.Holding.HOLDS);
                if (hasStored) {
                    secrets.remove(AGENT_HUB_ADMIN_TOKEN_SECRET);
                }
            } else if (stripped.length() <= SecretStore.maxSecret()
                    && secrets.whereASaveWouldStoreIt().isPresent()) {
                secrets.save(AGENT_HUB_ADMIN_TOKEN_SECRET, stripped);
            }
        } catch (IllegalArgumentException | SecretStoreException ex) {
            logger.warn("Failed to save Agent Hub admin token in SecretStore: {}", ex.getMessage());
        }
    }

    /**
     * Saves the {@link InferenceServer} and {@link AgentHub} settings to
     * {@link #resolveWritablePath()} and stores {@code agentHub.adminToken()} in
     * {@link SecretStore} (never writing {@code adminToken} in plaintext to {@code studio.json}).
     * Preserves any other top-level sections in {@code studio.json} such as {@code plugins}.
     *
     * @param inferenceServer the Inference Server configuration to persist.
     * @param agentHub        the Agent Hub configuration to persist.
     * @param secrets         optional secret store for {@code adminToken}.
     */
    public static void save(InferenceServer inferenceServer, AgentHub agentHub, SecretStore secrets) {
        save(resolveWritablePath(), inferenceServer, agentHub, secrets);
    }

    /**
     * Saves the {@link InferenceServer} and {@link AgentHub} settings to the specified
     * {@code studio.json} file path and stores {@code agentHub.adminToken()} in
     * {@link SecretStore} (never writing {@code adminToken} in plaintext to {@code studio.json}).
     * Preserves any other top-level sections in {@code studio.json} such as {@code plugins}.
     *
     * @param path            target {@code studio.json} file path.
     * @param inferenceServer the Inference Server configuration to persist.
     * @param agentHub        the Agent Hub configuration to persist.
     * @param secrets         optional secret store for {@code adminToken}.
     */
    public static void save(
            Path path,
            InferenceServer inferenceServer,
            AgentHub agentHub,
            SecretStore secrets
    ) {
        if (path == null) {
            return;
        }
        try {
            ObjectNode root = null;
            if (Files.isRegularFile(path)) {
                try {
                    JsonNode existing = mapper.readTree(path.toFile());
                    if (existing instanceof ObjectNode obj) {
                        root = obj;
                    }
                } catch (Exception ex) {
                    logger.warn("Could not parse existing {} before saving, creating fresh object: {}",
                            path, ex.getMessage());
                }
            }
            if (root == null) {
                root = mapper.createObjectNode();
            }

            if (inferenceServer != null) {
                ObjectNode infNode = mapper.createObjectNode();
                String llm = inferenceServer.llm() == null ? DEFAULT_LLM : inferenceServer.llm().trim();
                infNode.put("autoStart", inferenceServer.autoStart() || !llm.isEmpty());
                infNode.put("host", inferenceServer.host() == null || inferenceServer.host().isBlank()
                        ? DEFAULT_HOST : inferenceServer.host().trim());
                infNode.put("port", inferenceServer.port() > 0 ? inferenceServer.port() : DEFAULT_PORT);
                infNode.put("modelPath", inferenceServer.modelPath() == null || inferenceServer.modelPath().isBlank()
                        ? DEFAULT_MODEL_PATH : inferenceServer.modelPath().trim());
                infNode.put("llm", llm);
                root.set("inferenceServer", infNode);
            }

            if (agentHub != null) {
                ObjectNode hubNode = mapper.createObjectNode();
                hubNode.put("autoStart", agentHub.autoStart());
                hubNode.put("host", agentHub.host() == null || agentHub.host().isBlank()
                        ? DEFAULT_HOST : agentHub.host().trim());
                hubNode.put("port", agentHub.port() > 0 ? agentHub.port() : DEFAULT_HUB_PORT);
                hubNode.put("keepAliveOnExit", agentHub.keepAliveOnExit());
                root.set("agentHub", hubNode);
                if (secrets != null) {
                    saveAdminTokenSecret(secrets, agentHub.adminToken());
                }
            }

            if (!root.has("plugins")) {
                ObjectNode pluginsNode = mapper.createObjectNode();
                var arr = mapper.createArrayNode();
                arr.add("anthropics/claude-plugins-official");
                pluginsNode.set("marketplaces", arr);
                root.set("plugins", pluginsNode);
            }

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = mapper.writeValueAsString(root);
            Files.writeString(path, json + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logger.error("Failed to save {}: {}", path, ex.getMessage());
        }
    }
}
