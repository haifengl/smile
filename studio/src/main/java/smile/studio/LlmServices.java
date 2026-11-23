/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 */
package smile.studio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import ioa.agent.email.SharedSecrets;
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
 * shared {@link ioa.llm.client.LlmServices} and persisting non-secret service
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
    public static final SecretId OPENAI_SECRET = SharedSecrets.OPENAI_SECRET;
    /** Secret ID for Azure OpenAI API key. */
    public static final SecretId AZURE_OPENAI_SECRET = SharedSecrets.AZURE_OPENAI_SECRET;
    /** Secret ID for Anthropic API key. */
    public static final SecretId ANTHROPIC_SECRET = SharedSecrets.ANTHROPIC_SECRET;
    /** Secret ID for Google Gemini API key. */
    public static final SecretId GOOGLE_GEMINI_SECRET = SharedSecrets.GOOGLE_GEMINI_SECRET;
    /** Secret ID for Chat Completions compatible / Bedrock API key. */
    public static final SecretId CHAT_COMPLETIONS_SECRET = SharedSecrets.CHAT_COMPLETIONS_SECRET;

    private final ioa.llm.client.LlmServices delegate = new ioa.llm.client.LlmServices();
    private volatile Preferences prefs;

    /**
     * One selectable model bound to the client that serves it.
     * @param serviceKey service connection name such as {@code openai} or {@code local-vllm}.
     * @param serviceLabel UI label such as {@code OpenAI} or {@code local-vllm}.
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
     * Returns the {@link SecretId} for a service connection name and provider,
     * or {@link Optional#empty()} when the service does not store a secret API key
     * (e.g., {@code googleEnterprise} stores a GCP Project ID in {@code llm.json}).
     *
     * <p>When {@code serviceName} is a canonical provider key or its standard
     * display label (such as {@code "Google Gemini"} for {@code "googleGemini"}),
     * returns the canonical provider {@link SecretId} so stored keys and standard
     * environment variables ({@code OPENAI_API_KEY}, {@code GOOGLE_API_KEY}, etc.)
     * remain shared between Studio and Agent Hub.
     *
     * @param serviceName service connection name such as {@code Google Gemini} or {@code azure-eastus}.
     * @param provider canonical provider key such as {@code openai} or {@code azureOpenAI}.
     * @return the corresponding {@link SecretId}, if any.
     */
    public static Optional<SecretId> secretId(String serviceName, String provider) {
        if (serviceName == null || serviceName.isBlank()) {
            return Optional.empty();
        }
        String effProvider = (provider == null || provider.isBlank()) ? serviceName.trim() : provider.trim();
        String canonicalName = LlmConfig.serviceKeyForLabel(serviceName);
        String canonicalProvider = LlmConfig.serviceKeyForLabel(effProvider);
        String resolvedProvider = canonicalProvider.isEmpty() ? effProvider : canonicalProvider;
        if (!canonicalName.isEmpty() && canonicalName.equalsIgnoreCase(resolvedProvider)) {
            return SharedSecrets.llmSecretId(canonicalName, canonicalName);
        }
        return SharedSecrets.llmSecretId(serviceName, provider);
    }

    /**
     * Returns the {@link SecretId} for a service key, or {@link Optional#empty()}
     * when the service does not store a secret API key (e.g., {@code googleEnterprise}).
     *
     * @param serviceKey service key such as {@code openai}.
     * @return the corresponding {@link SecretId}, if any.
     */
    public static Optional<SecretId> secretId(String serviceKey) {
        return secretId(serviceKey, serviceKey);
    }

    /**
     * Deletes any stored API key for the given service connection.
     *
     * @param secrets credential store (may be null).
     * @param serviceName service connection name.
     * @param provider canonical provider key.
     */
    static void deleteSecret(SecretStore secrets, String serviceName, String provider) {
        if (secrets == null) {
            return;
        }
        secretId(serviceName, provider).ifPresent(id -> {
            try {
                secrets.remove(id);
            } catch (RuntimeException ex) {
                logger.debug("Could not delete LLM secret for '{}': {}", serviceName, ex.getMessage());
            }
        });
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
            Optional<SecretId> idOpt = secretId(serviceKey, serviceKey);
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
     * Returns the default Studio {@link LlmConfig} with human-readable service
     * names matching {@code conf/llm.json}.
     *
     * @return the default configuration with empty model lists.
     */
    static LlmConfig defaultConfig() {
        return new LlmConfig(
                "OpenAI",
                "",
                List.of(
                        new LlmConfig.ServiceEntry("OpenAI", "openai", "https://api.openai.com/v1", null, null, List.of()),
                        new LlmConfig.ServiceEntry("Azure OpenAI", "azureOpenAI", "", null, null, List.of()),
                        new LlmConfig.ServiceEntry("Anthropic", "anthropic", "https://api.anthropic.com", null, null, List.of()),
                        new LlmConfig.ServiceEntry("Google Gemini", "googleGemini", "https://generativelanguage.googleapis.com", null, null, List.of()),
                        new LlmConfig.ServiceEntry("Google Enterprise", "googleEnterprise", "", "", "", List.of()),
                        new LlmConfig.ServiceEntry("SMILE", "chatCompletions", "http://localhost:8888/v1", null, null, List.of())
                )
        );
    }

    private static String displayNameForProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "";
        }
        return switch (provider.trim()) {
            case "openai" -> "OpenAI";
            case "azureOpenAI" -> "Azure OpenAI";
            case "anthropic" -> "Anthropic";
            case "googleGemini" -> "Google Gemini";
            case "googleEnterprise" -> "Google Enterprise";
            case "chatCompletions" -> "SMILE";
            default -> LlmConfig.labelForServiceKey(provider);
        };
    }

    /**
     * Loads the shared {@link LlmConfig} from {@code ${smile.home}/conf/llm.json}
     * (or migrates from {@link Preferences} if the file does not exist or has no
     * configured models yet, preserving service names from {@code llm.json}).
     *
     * @param prefs application preferences (may be null).
     * @return the effective {@link LlmConfig}.
     */
    public static LlmConfig loadOrMigrateConfig(Preferences prefs) {
        Path configPath = LlmConfig.resolvePath();
        Optional<LlmConfig> fromPrefs = LlmConfig.fromPreferences(prefs);
        boolean isCustomPrefs = prefs != null && SmileStudio.preferences() != prefs;
        if (Files.isRegularFile(configPath)) {
            LlmConfig fileConfig = LlmConfig.load(configPath);
            if (isCustomPrefs) {
                return fromPrefs.orElse(fileConfig);
            }
            if (fromPrefs.isPresent() && !hasAnyModels(fileConfig) && hasAnyModels(fromPrefs.get())) {
                LlmConfig merged = mergePreferencesIntoConfig(fileConfig, fromPrefs.get(), prefs);
                merged.save(configPath);
                return merged;
            }
            return fileConfig;
        }
        if (fromPrefs.isPresent()) {
            LlmConfig migrated = isCustomPrefs
                    ? fromPrefs.get()
                    : mergePreferencesIntoConfig(defaultConfig(), fromPrefs.get(), prefs);
            migrated.save(configPath);
            return migrated;
        }
        return defaultConfig();
    }

    /**
     * Merges {@link Preferences}-backed settings into {@code baseConfig} while
     * preserving the human-readable service connection names and ordering from
     * {@code baseConfig} (e.g., {@code "Google Gemini"}, {@code "SMILE"}) rather
     * than replacing them with internal provider keys ({@code "googleGemini"},
     * {@code "chatCompletions"}).
     */
    static LlmConfig mergePreferencesIntoConfig(
            LlmConfig baseConfig,
            LlmConfig prefsConfig,
            Preferences prefs
    ) {
        LlmConfig template = (baseConfig == null || baseConfig.services().isEmpty())
                ? defaultConfig()
                : baseConfig;
        if (prefsConfig == null) {
            return template;
        }

        List<LlmConfig.ServiceEntry> mergedServices = new ArrayList<>(template.services().size());
        Set<String> consumedProviders = new HashSet<>();

        for (LlmConfig.ServiceEntry baseSvc : template.services()) {
            String provider = baseSvc.provider().isEmpty() ? baseSvc.name() : baseSvc.provider();
            String providerKey = provider.toLowerCase(Locale.ROOT);
            Optional<LlmConfig.ServiceEntry> prefOpt = consumedProviders.add(providerKey)
                    ? prefsConfig.findService(provider)
                    : Optional.empty();
            if (prefOpt.isEmpty()) {
                mergedServices.add(baseSvc);
                continue;
            }
            LlmConfig.ServiceEntry prefSvc = prefOpt.get();
            List<String> models = !prefSvc.models().isEmpty() ? prefSvc.models() : baseSvc.models();
            if ("googleEnterprise".equals(provider)) {
                String project = !prefSvc.project().isEmpty() ? prefSvc.project() : baseSvc.project();
                String location = !prefSvc.location().isEmpty() ? prefSvc.location() : baseSvc.location();
                mergedServices.add(new LlmConfig.ServiceEntry(
                        baseSvc.name(), provider, "", project, location, models));
            } else {
                String explicitBaseUrl = prefs != null ? prefs.get(provider + "BaseUrl", "").trim() : "";
                String baseUrl = !explicitBaseUrl.isEmpty()
                        ? explicitBaseUrl
                        : (!baseSvc.baseUrl().isEmpty() ? baseSvc.baseUrl() : prefSvc.baseUrl());
                mergedServices.add(new LlmConfig.ServiceEntry(
                        baseSvc.name(), provider, baseUrl, null, null, models));
            }
        }

        for (LlmConfig.ServiceEntry prefSvc : prefsConfig.services()) {
            String provider = prefSvc.provider().isEmpty() ? prefSvc.name() : prefSvc.provider();
            String providerKey = provider.toLowerCase(Locale.ROOT);
            if (!consumedProviders.contains(providerKey)
                    && (!prefSvc.models().isEmpty()
                            || !prefSvc.project().isEmpty()
                            || !prefSvc.location().isEmpty())) {
                String name = displayNameForProvider(provider);
                mergedServices.add(new LlmConfig.ServiceEntry(
                        name.isEmpty() ? prefSvc.name() : name,
                        provider,
                        prefSvc.baseUrl(),
                        prefSvc.project(),
                        prefSvc.location(),
                        prefSvc.models()));
                consumedProviders.add(providerKey);
            }
        }

        String defaultService = resolveMergedServiceName(mergedServices, prefsConfig.defaultService())
                .or(() -> resolveMergedServiceName(mergedServices, template.defaultService()))
                .orElseGet(() -> mergedServices.isEmpty() ? "OpenAI" : mergedServices.getFirst().name());
        String defaultModel = resolveMergedDefaultModel(
                mergedServices, prefsConfig.defaultModel(), template.defaultModel());

        return new LlmConfig(defaultService, defaultModel, mergedServices);
    }

    private static Optional<String> resolveMergedServiceName(
            List<LlmConfig.ServiceEntry> services,
            String candidate
    ) {
        if (candidate == null || candidate.isBlank() || services.isEmpty()) {
            return Optional.empty();
        }
        String trimmed = candidate.trim();
        for (LlmConfig.ServiceEntry svc : services) {
            if (svc.name().equalsIgnoreCase(trimmed)) {
                return Optional.of(svc.name());
            }
        }
        String canonical = LlmConfig.serviceKeyForLabel(trimmed);
        String providerKey = canonical.isEmpty() ? trimmed : canonical;
        for (LlmConfig.ServiceEntry svc : services) {
            if (svc.provider().equalsIgnoreCase(providerKey)) {
                return Optional.of(svc.name());
            }
        }
        return Optional.empty();
    }

    private static String resolveMergedDefaultModel(
            List<LlmConfig.ServiceEntry> services,
            String prefDefaultModel,
            String baseDefaultModel
    ) {
        String model = (prefDefaultModel != null && !prefDefaultModel.isBlank())
                ? prefDefaultModel.trim()
                : (baseDefaultModel == null ? "" : baseDefaultModel.trim());
        int sep = model.indexOf(':');
        if (sep > 0) {
            String prefix = model.substring(0, sep).trim();
            Optional<String> resolved = resolveMergedServiceName(services, prefix);
            if (resolved.isPresent()) {
                return resolved.get() + ":" + model.substring(sep + 1).trim();
            }
        }
        return model;
    }

    private static boolean hasAnyModels(LlmConfig cfg) {
        return cfg.services().stream().anyMatch(s -> !s.models().isEmpty());
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
                serviceKey -> {
                    String provider = cfg.findService(serviceKey)
                            .map(LlmConfig.ServiceEntry::provider)
                            .orElse(serviceKey);
                    return secretId(serviceKey, provider)
                            .flatMap(id -> readSecret(secrets, id))
                            .or(() -> ioa.llm.client.LlmServices.defaultSecretResolver(serviceKey));
                },
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
     * Looks up a client by service connection name or provider key.
     * @param serviceKey service name or provider key.
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
     * Finds a model by service name (or provider key) and model id.
     * @param serviceKey service name or provider key.
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
