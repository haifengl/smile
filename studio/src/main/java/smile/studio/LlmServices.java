/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 */
package smile.studio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import ioa.llm.Model;
import ioa.llm.client.LLM;
import ioa.llm.client.LlmConfig;
import photos.sluice.secrets.SecretHolding;
import photos.sluice.secrets.SecretId;
import photos.sluice.secrets.SecretStatus;
import photos.sluice.secrets.SecretStore;
import photos.sluice.secrets.SecretStoreException;

/**
 * Client pool and available-model list for Smile Studio, delegating to the
 * shared {@link ioa.llm.client.LlmServices} and persisting non-secret provider
 * and model configuration to {@code ${smile.home}/conf/llm.json} (fallback
 * {@code ~/.smile/llm.json}).
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

    private final ioa.llm.client.LlmServices delegate = new ioa.llm.client.LlmServices();
    private volatile Preferences prefs;

    /**
     * One selectable model bound to the client that serves it.
     * @param serviceKey service prefix such as {@code openai}.
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

        static AvailableModel from(ioa.llm.client.LlmServices.AvailableModel m) {
            return m == null ? null : new AvailableModel(m.serviceKey(), m.serviceLabel(), m.model(), m.client());
        }

        ioa.llm.client.LlmServices.AvailableModel toShared() {
            return new ioa.llm.client.LlmServices.AvailableModel(serviceKey, serviceLabel, model, client);
        }
    }

    /**
     * Returns the {@link SecretId} for a service key, or {@link Optional#empty()}
     * when the service does not store a secret API key (e.g., {@code googleEnterprise}
     * stores a GCP Project ID in {@code llm.json}).
     *
     * @param serviceKey service key such as {@code openai}.
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
        for (String[] entry : LlmConfig.SERVICES) {
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
     * Loads the shared {@link LlmConfig} from {@code ${smile.home}/conf/llm.json}
     * (or migrates from {@link Preferences} if the file does not exist or {@code prefs}
     * holds non-empty settings).
     *
     * @param prefs application preferences (may be null).
     * @return the effective {@link LlmConfig}.
     */
    public static LlmConfig loadOrMigrateConfig(Preferences prefs) {
        Path configPath = LlmConfig.resolvePath();
        Optional<LlmConfig> fromPrefs = LlmConfig.fromPreferences(prefs);
        if (Files.isRegularFile(configPath)) {
            LlmConfig fileConfig = LlmConfig.load(configPath);
            // If preferences has explicit settings (e.g. in unit tests or right after a SettingsDialog save),
            // honor preferences when fileConfig has no models or preferences was updated.
            if (fromPrefs.isPresent() && !hasAnyModels(fileConfig) && hasAnyModels(fromPrefs.get())) {
                fromPrefs.get().save(configPath);
                return fromPrefs.get();
            }
            if (fromPrefs.isPresent()) {
                // Keep Preferences and llm.json synchronized when Preferences is explicitly passed
                return mergePrefsIfExplicit(fileConfig, fromPrefs.get(), prefs);
            }
            return fileConfig;
        }
        if (fromPrefs.isPresent()) {
            LlmConfig migrated = fromPrefs.get();
            migrated.save(configPath);
            return migrated;
        }
        return LlmConfig.empty();
    }

    private static boolean hasAnyModels(LlmConfig cfg) {
        return cfg.services().values().stream().anyMatch(s -> !s.models().isEmpty());
    }

    private static LlmConfig mergePrefsIfExplicit(LlmConfig fileCfg, LlmConfig prefsCfg, Preferences prefs) {
        if (prefs != null && SmileStudio.preferences() != prefs) {
            // Custom test Preferences node: use prefsCfg directly
            return prefsCfg;
        }
        return hasAnyModels(fileCfg) ? fileCfg : prefsCfg;
    }

    /**
     * Syncs an {@link LlmConfig} back into {@link Preferences} for backward compatibility.
     *
     * @param cfg the configuration to sync.
     * @param prefs target preferences node.
     */
    public static void syncToPreferences(LlmConfig cfg, Preferences prefs) {
        if (cfg == null || prefs == null) {
            return;
        }
        prefs.put(SettingsDialog.AI_SERVICE_KEY, LlmConfig.labelForServiceKey(cfg.defaultService()));
        if (cfg.defaultModel().isEmpty()) {
            prefs.remove(SettingsDialog.DEFAULT_MODEL_KEY);
        } else {
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, cfg.defaultModel());
        }
        for (String[] entry : LlmConfig.SERVICES) {
            String key = entry[0];
            LlmConfig.ServiceEntry svc = cfg.service(key);
            if ("googleEnterprise".equals(key)) {
                prefs.put(key + "ApiKey", svc.project());
                prefs.put(key + "BaseUrl", svc.location());
            } else {
                prefs.put(key + "BaseUrl", svc.baseUrl());
            }
            prefs.put(key + "Model", svc.modelsCsv());
        }
    }

    /**
     * Rebuilds the client pool and available-model list from {@code llm.json} / preferences
     * and the default application secret store.
     * @param prefs application preferences.
     */
    public void reload(Preferences prefs) {
        reload(prefs, SmileStudio.secrets());
    }

    /**
     * Rebuilds the client pool and available-model list from {@code llm.json} / preferences
     * and the given secret store.
     * @param prefs application preferences.
     * @param secrets credential store for API keys.
     */
    public void reload(Preferences prefs, SecretStore secrets) {
        this.prefs = prefs;
        migratePreferencesSecrets(prefs, secrets);

        Path configPath = (prefs != null && SmileStudio.preferences() != prefs)
                ? null
                : LlmConfig.resolvePath();
        LlmConfig cfg = (prefs != null && SmileStudio.preferences() != prefs)
                ? LlmConfig.fromPreferences(prefs).orElseGet(LlmConfig::empty)
                : loadOrMigrateConfig(prefs);

        if (configPath != null && !Files.exists(configPath) && hasAnyModels(cfg)) {
            cfg.save(configPath);
        }

        delegate.reload(
                cfg,
                configPath,
                serviceKey -> secretId(serviceKey)
                        .flatMap(id -> readSecret(secrets, id))
                        .or(() -> ioa.llm.client.LlmServices.defaultSecretResolver(serviceKey)),
                (label, msg) -> JOptionPane.showMessageDialog(
                        null,
                        MessageFormat.format(bundle.getString("InitError"), label + ": " + msg),
                        bundle.getString("Error"),
                        JOptionPane.ERROR_MESSAGE)
        );
    }

    /**
     * Returns the underlying shared {@link ioa.llm.client.LlmServices}.
     * @return the shared services instance.
     */
    public ioa.llm.client.LlmServices shared() {
        return delegate;
    }

    /**
     * Returns the current {@link LlmConfig}.
     * @return the current config.
     */
    public LlmConfig config() {
        return delegate.config();
    }

    /**
     * Returns the default service's client, or null when unconfigured.
     * @return the default client.
     */
    public LLM defaultClient() {
        return delegate.defaultClient();
    }

    /**
     * Looks up a client by service key.
     * @param serviceKey service prefix.
     * @return the client if present.
     */
    public Optional<LLM> client(String serviceKey) {
        return delegate.client(serviceKey);
    }

    /**
     * Returns all models from every configured service.
     * @return an immutable list.
     */
    public List<AvailableModel> availableModels() {
        return delegate.availableModels().stream()
                .map(AvailableModel::from)
                .toList();
    }

    /**
     * Returns the auto/default selection.
     * @return the default entry, or null when the pool is empty.
     */
    public AvailableModel defaultModel() {
        return AvailableModel.from(delegate.defaultModel());
    }

    /**
     * Sets the default model in {@code llm.json} and preferences.
     * @param model the default model, or null to remove the preference and
     *              fall back to the default service heuristic.
     */
    public void defaultModel(AvailableModel model) {
        String value = delegate.defaultModel(model == null ? null : model.toShared());
        Preferences p = prefs != null ? prefs : SmileStudio.preferences();
        if (p != null) {
            if (value == null || value.isEmpty()) {
                p.remove(SettingsDialog.DEFAULT_MODEL_KEY);
            } else {
                p.put(SettingsDialog.DEFAULT_MODEL_KEY, value);
            }
        }
    }

    /**
     * Finds a model by service and id.
     * @param serviceKey service prefix.
     * @param modelId API model id.
     * @return the entry if present.
     */
    public Optional<AvailableModel> find(String serviceKey, String modelId) {
        return delegate.find(serviceKey, modelId).map(AvailableModel::from);
    }

    /**
     * Splits a comma-separated model prefs value into trimmed ids.
     * @param value the raw prefs string.
     * @return non-blank model ids in order.
     */
    public static List<String> parseModelIds(String value) {
        return LlmConfig.parseModelIds(value);
    }

    static String serviceKeyForLabel(String label) {
        return LlmConfig.serviceKeyForLabel(label);
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
}
