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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import ioa.llm.Model;
import ioa.llm.client.Anthropic;
import ioa.llm.client.ChatCompletions;
import ioa.llm.client.GoogleGemini;
import ioa.llm.client.LLM;
import ioa.llm.client.OpenAI;
import photos.sluice.secrets.SecretHolding;
import photos.sluice.secrets.SecretId;
import photos.sluice.secrets.SecretStatus;
import photos.sluice.secrets.SecretStore;
import photos.sluice.secrets.SecretStoreException;
import smile.util.Strings;

/**
 * Client pool and available-model list for Smile Studio. One model-agnostic
 * {@link LLM} per configured AI service; each {@link AvailableModel} points at
 * the client that should serve that id.
 *
 * @author Haifeng Li
 */
public final class LlmServices {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LlmServices.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(
            SmileStudio.class.getName(), Locale.getDefault());

    /** Secret ID for OpenAI API key. */
    public static final SecretId OPENAI_SECRET = new SecretId("openai", "OPENAI_API_KEY");
    /** Secret ID for Azure OpenAI API key. */
    public static final SecretId AZURE_OPENAI_SECRET = new SecretId("azure-openai", "AZURE_OPENAI_API_KEY");
    /** Secret ID for Anthropic API key. */
    public static final SecretId ANTHROPIC_SECRET = new SecretId("anthropic", "ANTHROPIC_API_KEY");
    /** Secret ID for Google Gemini API key. */
    public static final SecretId GOOGLE_GEMINI_SECRET = new SecretId("google-gemini", "GOOGLE_API_KEY");
    /** Secret ID for Chat Completions compatible / Bedrock API key. */
    public static final SecretId CHAT_COMPLETIONS_SECRET = new SecretId("chat-completions", "AWS_BEARER_TOKEN_BEDROCK");

    /** Service key / display label pairs matching {@link SettingsDialog}. */
    private static final String[][] SERVICES = {
            {"openai", "OpenAI"},
            {"azureOpenAI", "Azure OpenAI"},
            {"anthropic", "Anthropic"},
            {"googleGemini", "Google Gemini"},
            {"googleEnterprise", "Google Gemini Enterprise"},
            {"chatCompletions", "Chat Completions Compatible"},
    };

    private volatile Map<String, LLM> clients = Map.of();
    private volatile List<AvailableModel> models = List.of();
    private volatile String defaultServiceKey = "";
    private volatile AvailableModel defaultModel;
    private volatile Preferences prefs;

    /**
     * One selectable model bound to the client that serves it.
     * @param serviceKey prefs prefix such as {@code openai}.
     * @param serviceLabel UI label such as {@code OpenAI}.
     * @param model catalog metadata.
     * @param client the inference client for this service.
     */
    public record AvailableModel(
            String serviceKey,
            String serviceLabel,
            Model model,
            LLM client
    ) {
        /**
         * Display label for combo boxes. Qualifies with the service when the
         * same model id appears on more than one service.
         * @param qualifyWithService true when the id is ambiguous.
         * @return a short label.
         */
        public String displayLabel(boolean qualifyWithService) {
            return qualifyWithService ? serviceLabel + ": " + model.id() : model.id();
        }
    }

    /**
     * Returns the {@link SecretId} for a service key, or {@link Optional#empty()}
     * when the service does not store a secret API key (e.g., {@code googleEnterprise}
     * stores a GCP Project ID in {@link Preferences}).
     *
     * @param serviceKey prefs prefix such as {@code openai}.
     * @return the corresponding {@link SecretId}, if any.
     */
    public static Optional<SecretId> secretId(String serviceKey) {
        if (serviceKey == null) {
            return Optional.empty();
        }
        return switch (serviceKey) {
            case "openai" -> Optional.of(OPENAI_SECRET);
            case "azureOpenAI" -> Optional.of(AZURE_OPENAI_SECRET);
            case "anthropic" -> Optional.of(ANTHROPIC_SECRET);
            case "googleGemini" -> Optional.of(GOOGLE_GEMINI_SECRET);
            case "chatCompletions" -> Optional.of(CHAT_COMPLETIONS_SECRET);
            default -> Optional.empty();
        };
    }

    /**
     * Migrates any legacy plaintext API keys from {@link Preferences} into the
     * {@link SecretStore} and removes the plaintext entries from {@link Preferences}.
     *
     * @param prefs application preferences.
     * @param secrets application secret store.
     */
    public static void migratePreferencesSecrets(Preferences prefs, SecretStore secrets) {
        if (prefs == null || secrets == null) {
            return;
        }
        boolean modified = false;
        for (String[] entry : SERVICES) {
            String serviceKey = entry[0];
            Optional<SecretId> idOpt = secretId(serviceKey);
            if (idOpt.isEmpty()) {
                continue;
            }
            String prefKey = serviceKey + "ApiKey";
            String legacyValue = prefs.get(prefKey, null);
            if (legacyValue == null) {
                continue;
            }
            String stripped = legacyValue.strip();
            if (stripped.isEmpty()) {
                prefs.remove(prefKey);
                modified = true;
                continue;
            }
            SecretId id = idOpt.get();
            try {
                boolean hasStoredSecret = secrets.holdings(id).stream()
                        .anyMatch(h -> h.location() instanceof SecretStatus.StoredLocation
                                && h.holding() == SecretHolding.Holding.HOLDS);
                if (!hasStoredSecret) {
                    if (stripped.length() <= SecretStore.maxSecret()
                            && secrets.whereASaveWouldStoreIt().isPresent()) {
                        secrets.save(id, stripped);
                    } else {
                        logger.warn("Cannot migrate legacy API key for '{}' to SecretStore", serviceKey);
                        continue;
                    }
                }
                prefs.remove(prefKey);
                modified = true;
            } catch (RuntimeException ex) {
                logger.warn("Failed to migrate API key for '{}' to SecretStore: {}", serviceKey, ex.getMessage());
            }
        }
        if (modified) {
            try {
                prefs.flush();
            } catch (Exception ex) {
                logger.debug("Failed to flush preferences after secret migration: {}", ex.getMessage());
            }
        }
    }

    /**
     * Rebuilds the client pool and available-model list from preferences and the
     * default application secret store.
     * @param prefs application preferences.
     */
    public void reload(Preferences prefs) {
        reload(prefs, SmileStudio.secrets());
    }

    /**
     * Rebuilds the client pool and available-model list from preferences and the
     * given secret store.
     * @param prefs application preferences.
     * @param secrets credential store for API keys.
     */
    public void reload(Preferences prefs, SecretStore secrets) {
        this.prefs = prefs;
        migratePreferencesSecrets(prefs, secrets);

        Map<String, LLM> nextClients = new LinkedHashMap<>();
        List<AvailableModel> nextModels = new ArrayList<>();

        setSystemPropertyFromSecret(secrets, "openai.apiKey", OPENAI_SECRET);
        setSystemPropertyFromPrefs(prefs, "openai.baseUrl", "openaiBaseUrl");
        setSystemPropertyFromSecret(secrets, "anthropic.apiKey", ANTHROPIC_SECRET);
        setSystemPropertyFromPrefs(prefs, "anthropic.baseUrl", "anthropicBaseUrl");

        String defaultLabel = prefs.get(SettingsDialog.AI_SERVICE_KEY, "");
        defaultServiceKey = serviceKeyForLabel(defaultLabel);

        for (String[] entry : SERVICES) {
            String key = entry[0];
            String label = entry[1];
            List<String> modelIds = parseModelIds(prefs.get(key + "Model", ""));
            if (modelIds.isEmpty()) {
                continue;
            }
            try {
                LLM client = createClient(key, prefs, secrets);
                if (client == null) {
                    continue;
                }
                nextClients.put(key, client);
                for (String id : modelIds) {
                    nextModels.add(new AvailableModel(key, label, Model.of(id), client));
                }
            } catch (Throwable t) {
                var cause = t.getCause() != null ? t.getCause() : t;
                logger.error("Failed to initialize AI service {}: {}", label, cause.getMessage());
                JOptionPane.showMessageDialog(
                        null,
                        MessageFormat.format(bundle.getString("InitError"),
                                label + ": " + cause.getMessage()),
                        bundle.getString("Error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        clients = Map.copyOf(nextClients);
        models = List.copyOf(nextModels);
        defaultModel = computeDefaultModel();
    }

    /**
     * Returns the default service's client, or null when unconfigured.
     * @return the default client.
     */
    public LLM defaultClient() {
        AvailableModel def = defaultModel();
        return def != null ? def.client() : client(defaultServiceKey).orElse(null);
    }

    /**
     * Looks up a client by service key.
     * @param serviceKey prefs prefix.
     * @return the client if present.
     */
    public Optional<LLM> client(String serviceKey) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(clients.get(serviceKey));
    }

    /**
     * Returns all models from every configured service.
     * @return an immutable list.
     */
    public List<AvailableModel> availableModels() {
        return models;
    }

    /**
     * Returns the auto/default selection: the model configured in preferences
     * if present and available, or falling back to the first model of the
     * active/default AI service, or the first available model if that service
     * has none.
     * @return the default entry, or null when the pool is empty.
     */
    public AvailableModel defaultModel() {
        return defaultModel;
    }

    /**
     * Resolves the auto/default selection from preferences and the current
     * model pool. Called by {@link #reload(Preferences, SecretStore)} and
     * {@link #defaultModel(AvailableModel)} to keep the cached
     * {@link #defaultModel} field in sync.
     * @return the default entry, or null when the pool is empty.
     */
    private AvailableModel computeDefaultModel() {
        if (models.isEmpty()) {
            return null;
        }
        Preferences p = prefs != null ? prefs : SmileStudio.preferences();
        if (p != null) {
            String defaultModelId = p.get(SettingsDialog.DEFAULT_MODEL_KEY, "").trim();
            if (!defaultModelId.isEmpty()) {
                String service = null;
                String modelId = defaultModelId;
                int sep = defaultModelId.indexOf(':');
                if (sep < 0) {
                    sep = defaultModelId.indexOf('/');
                }
                if (sep > 0) {
                    service = defaultModelId.substring(0, sep).trim();
                    modelId = defaultModelId.substring(sep + 1).trim();
                }
                var found = find(service, modelId);
                if (found.isEmpty() && service == null && !defaultServiceKey.isBlank()) {
                    found = find(defaultServiceKey, modelId);
                }
                if (found.isEmpty()) {
                    found = find(null, modelId);
                }
                if (found.isPresent()) {
                    return found.get();
                }
            }
        }
        if (!defaultServiceKey.isBlank()) {
            for (AvailableModel m : models) {
                if (defaultServiceKey.equals(m.serviceKey())) {
                    return m;
                }
            }
        }
        return models.getFirst();
    }

    /**
     * Sets the default model in preferences.
     * @param model the default model, or null to remove the preference and
     *              fall back to the default service heuristic.
     */
    public void defaultModel(AvailableModel model) {
        Preferences p = prefs != null ? prefs : SmileStudio.preferences();
        if (p != null) {
            if (model == null) {
                p.remove(SettingsDialog.DEFAULT_MODEL_KEY);
            } else {
                boolean duplicate = models.stream()
                        .filter(m -> m.model().id().equals(model.model().id()))
                        .count() > 1;
                String value = duplicate
                        ? model.serviceKey() + ":" + model.model().id()
                        : model.model().id();
                p.put(SettingsDialog.DEFAULT_MODEL_KEY, value);
            }
        }
        defaultModel = computeDefaultModel();
    }

    /**
     * Finds a model by service and id.
     * @param serviceKey prefs prefix.
     * @param modelId API model id.
     * @return the entry if present.
     */
    public Optional<AvailableModel> find(String serviceKey, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        for (AvailableModel m : models) {
            if (m.model().id().equals(modelId)
                    && (serviceKey == null || serviceKey.isBlank() || serviceKey.equals(m.serviceKey()))) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    /**
     * Splits a comma-separated model prefs value into trimmed ids.
     * @param value the raw prefs string.
     * @return non-blank model ids in order.
     */
    public static List<String> parseModelIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    static String serviceKeyForLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        for (String[] entry : SERVICES) {
            if (entry[1].equals(label)) {
                return entry[0];
            }
        }
        return "";
    }

    private static void setSystemPropertyFromPrefs(Preferences prefs, String sysProp, String prefKey) {
        if (System.getProperty(sysProp, "").isBlank()) {
            String value = prefs.get(prefKey, "").trim();
            if (!value.isEmpty()) {
                System.setProperty(sysProp, value);
            }
        }
    }

    private static void setSystemPropertyFromSecret(SecretStore secrets, String sysProp, SecretId id) {
        if (System.getProperty(sysProp, "").isBlank()) {
            readSecret(secrets, id).ifPresent(value -> {
                if (!value.isBlank()) {
                    System.setProperty(sysProp, value);
                }
            });
        }
    }

    static Optional<String> readSecret(SecretStore secrets, SecretId id) {
        if (secrets == null || id == null) {
            return Optional.empty();
        }
        try {
            return secrets.secret(id);
        } catch (SecretStoreException ex) {
            logger.warn("Failed to read secret '{}': {}", id.name(), ex.getMessage());
            return Optional.empty();
        }
    }

    private static LLM createClient(String serviceKey, Preferences prefs, SecretStore secrets) {
        return switch (serviceKey) {
            case "openai" -> {
                var openai = new OpenAI();
                var apiKey = readSecret(secrets, OPENAI_SECRET).orElse("");
                if (!apiKey.isBlank()) {
                    openai.withApiKey(apiKey);
                }
                var baseUrl = prefs.get("openaiBaseUrl", "");
                if (!baseUrl.isBlank()) {
                    openai.withBaseUrl(baseUrl);
                }
                yield openai;
            }
            case "azureOpenAI" -> {
                var apiKey = readSecret(secrets, AZURE_OPENAI_SECRET).orElse("");
                var baseUrl = prefs.get("azureOpenAIBaseUrl", "");
                if (apiKey.isBlank() || baseUrl.isBlank()) {
                    yield null;
                }
                yield OpenAI.azure(apiKey, baseUrl);
            }
            case "anthropic" -> {
                if (System.getProperty(Anthropic.BASE_URL_PROPERTY_KEY, "").contains("bedrock")
                        && System.getProperty(Anthropic.API_KEY_PROPERTY_KEY) == null) {
                    var apiKey = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
                    if (!Strings.isNullOrBlank(apiKey)) {
                        System.setProperty(Anthropic.API_KEY_PROPERTY_KEY, apiKey);
                    }
                }
                var anthropic = new Anthropic();
                var apiKey = readSecret(secrets, ANTHROPIC_SECRET).orElse("");
                if (!apiKey.isBlank()) {
                    anthropic.withApiKey(apiKey);
                }
                yield anthropic;
            }
            case "googleGemini" -> {
                var apiKey = readSecret(secrets, GOOGLE_GEMINI_SECRET).orElse("");
                if (apiKey.isBlank()) {
                    yield null;
                }
                yield new GoogleGemini(apiKey);
            }
            case "googleEnterprise" -> {
                var project = prefs.get("googleEnterpriseApiKey", "");
                var location = prefs.get("googleEnterpriseBaseUrl", "");
                if (project.isBlank() || location.isBlank()) {
                    yield null;
                }
                yield GoogleGemini.enterprise(project, location);
            }
            case "chatCompletions" -> {
                var baseUrl = prefs.get("chatCompletionsBaseUrl", "");
                if (baseUrl.isBlank()) {
                    yield null;
                }
                var apiKey = readSecret(secrets, CHAT_COMPLETIONS_SECRET).orElse("");
                if (apiKey.isBlank()) {
                    yield null;
                }
                yield new ChatCompletions(baseUrl, apiKey);
            }
            default -> null;
        };
    }
}
