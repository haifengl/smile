/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import ioa.llm.Model;
import ioa.llm.client.Anthropic;
import ioa.llm.client.ChatCompletions;
import ioa.llm.client.GoogleGemini;
import ioa.llm.client.LLM;
import ioa.llm.client.OpenAI;
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
     * Rebuilds the client pool and available-model list from preferences.
     * @param prefs application preferences.
     */
    public void reload(Preferences prefs) {
        this.prefs = prefs;
        Map<String, LLM> nextClients = new LinkedHashMap<>();
        List<AvailableModel> nextModels = new ArrayList<>();

        setSystemPropertyFromPrefs(prefs, "openai.apiKey", "openaiApiKey");
        setSystemPropertyFromPrefs(prefs, "openai.baseUrl", "openaiBaseUrl");
        setSystemPropertyFromPrefs(prefs, "anthropic.apiKey", "anthropicApiKey");
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
                LLM client = createClient(key, prefs);
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

    private static LLM createClient(String serviceKey, Preferences prefs) {
        return switch (serviceKey) {
            case "openai" -> {
                var openai = new OpenAI();
                var apiKey = prefs.get("openaiApiKey", "");
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
                var apiKey = prefs.get("azureOpenAIApiKey", "");
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
                yield new Anthropic();
            }
            case "googleGemini" -> {
                var apiKey = prefs.get("googleGeminiApiKey", "");
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
                var apiKey = prefs.get("chatCompletionsApiKey", "");
                if (apiKey.isBlank()) {
                    apiKey = System.getenv("AWS_BEARER_TOKEN_BEDROCK");
                    if (Strings.isNullOrBlank(apiKey)) {
                        yield null;
                    }
                }
                yield new ChatCompletions(baseUrl, apiKey);
            }
            default -> null;
        };
    }
}
