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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import ioa.agent.email.SharedSecrets;
import ioa.llm.client.LlmConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import photos.sluice.secrets.SecretId;
import photos.sluice.secrets.SecretStatus;
import photos.sluice.secrets.SecretStore;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LlmServices}.
 */
class LlmServicesTest {

    @TempDir
    Path tempDir;

    private SecretStore newTestSecretStore() {
        return SecretStore.forApplication("SmileStudioTest")
                .inNamespace("ai.smile.studio.test")
                .onOperatingSystem("none")
                .withCredentialFilesIn(tempDir.resolve("credentials"))
                .open();
    }

    @Test
    void parseModelIds_splitsCommaSeparatedList() {
        assertEquals(
                List.of("gpt-6-astra", "claude-sonnet-5", "qwen3.8"),
                LlmServices.parseModelIds("gpt-6-astra, claude-sonnet-5, qwen3.8"));
    }

    @Test
    void parseModelIds_trimsAndDropsEmpties() {
        assertEquals(List.of("a", "b"), LlmServices.parseModelIds("  a , , b  "));
        assertTrue(LlmServices.parseModelIds("").isEmpty());
        assertTrue(LlmServices.parseModelIds(null).isEmpty());
    }

    @Test
    void serviceKeyForLabel_mapsDisplayNames() {
        assertEquals("openai", LlmServices.serviceKeyForLabel("OpenAI"));
        assertEquals("anthropic", LlmServices.serviceKeyForLabel("Anthropic"));
        assertEquals("", LlmServices.serviceKeyForLabel(""));
    }

    @Test
    void secretId_mapsSecretBearingServicesAndExcludesGoogleEnterprise() {
        assertEquals(Optional.of(LlmServices.OPENAI_SECRET), LlmServices.secretId("openai"));
        assertEquals(Optional.of(LlmServices.AZURE_OPENAI_SECRET), LlmServices.secretId("azureOpenAI"));
        assertEquals(Optional.of(LlmServices.ANTHROPIC_SECRET), LlmServices.secretId("anthropic"));
        assertEquals(Optional.of(LlmServices.GOOGLE_GEMINI_SECRET), LlmServices.secretId("googleGemini"));
        assertEquals(Optional.of(LlmServices.CHAT_COMPLETIONS_SECRET), LlmServices.secretId("chatCompletions"));
        assertTrue(LlmServices.secretId("googleEnterprise").isEmpty());
        assertTrue(LlmServices.secretId(null).isEmpty());
    }

    @Test
    void reload_buildsModelsFromSecretStore() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        SecretStore secrets = newTestSecretStore();
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
            prefs.put("anthropicModel", "claude-sonnet-5");
            secrets.save(LlmServices.OPENAI_SECRET, "test-key");
            secrets.save(LlmServices.ANTHROPIC_SECRET, "anth-key");

            LlmServices services = new LlmServices();
            services.reload(prefs, secrets);

            assertEquals(3, services.availableModels().size());
            assertEquals("gpt-6-sol", services.defaultModel().model().id());
            assertNotNull(services.defaultClient());
            assertTrue(services.client("anthropic").isPresent());
            assertTrue(services.find("openai", "gpt-6-astra").isPresent());
            assertTrue(services.find("anthropic", "gpt-6-astra").isEmpty());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void reload_migratesLegacyPreferencesKeysToSecretStoreAndDeletesFromPreferences() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        SecretStore secrets = newTestSecretStore();
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiApiKey", "legacy-openai-key");
            prefs.put("openaiModel", "gpt-6-sol");
            prefs.put("anthropicApiKey", "legacy-anthropic-key");
            prefs.put("anthropicModel", "claude-sonnet-5");

            LlmServices services = new LlmServices();
            services.reload(prefs, secrets);

            // Secrets were migrated to SecretStore
            assertEquals(Optional.of("legacy-openai-key"), secrets.secret(LlmServices.OPENAI_SECRET));
            assertEquals(Optional.of("legacy-anthropic-key"), secrets.secret(LlmServices.ANTHROPIC_SECRET));
            assertInstanceOf(SecretStatus.InFile.class, secrets.status(LlmServices.OPENAI_SECRET));

            // Plaintext keys were deleted from Preferences
            assertNull(prefs.get("openaiApiKey", null));
            assertNull(prefs.get("anthropicApiKey", null));

            // Models and clients were still initialized
            assertEquals(2, services.availableModels().size());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void defaultModel_withPreference_returnsConfiguredModel() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        SecretStore secrets = newTestSecretStore();
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
            prefs.put("anthropicModel", "claude-sonnet-5");
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, "claude-sonnet-5");
            secrets.save(LlmServices.OPENAI_SECRET, "test-key");
            secrets.save(LlmServices.ANTHROPIC_SECRET, "anth-key");

            LlmServices services = new LlmServices();
            services.reload(prefs, secrets);

            assertEquals("claude-sonnet-5", services.defaultModel().model().id());
            assertEquals("anthropic", services.defaultModel().serviceKey());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void defaultModel_withServicePrefix_returnsConfiguredModel() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        SecretStore secrets = newTestSecretStore();
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiModel", "gpt-6-sol");
            prefs.put("anthropicModel", "claude-sonnet-5");
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, "anthropic:claude-sonnet-5");
            secrets.save(LlmServices.OPENAI_SECRET, "test-key");
            secrets.save(LlmServices.ANTHROPIC_SECRET, "anth-key");

            LlmServices services = new LlmServices();
            services.reload(prefs, secrets);

            assertEquals("claude-sonnet-5", services.defaultModel().model().id());
            assertEquals("anthropic", services.defaultModel().serviceKey());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void defaultModel_withInvalidPreference_fallsBackToHeuristic() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        SecretStore secrets = newTestSecretStore();
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, "non-existent-model");
            secrets.save(LlmServices.OPENAI_SECRET, "test-key");

            LlmServices services = new LlmServices();
            services.reload(prefs, secrets);

            assertEquals("gpt-6-sol", services.defaultModel().model().id());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void defaultModel_setter_updatesPreferenceAndRemovesOnNull() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        SecretStore secrets = newTestSecretStore();
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
            prefs.put("anthropicModel", "claude-sonnet-5");
            secrets.save(LlmServices.OPENAI_SECRET, "test-key");
            secrets.save(LlmServices.ANTHROPIC_SECRET, "anth-key");

            LlmServices services = new LlmServices();
            services.reload(prefs, secrets);

            // Initially fallback heuristic
            assertEquals("gpt-6-sol", services.defaultModel().model().id());

            // Set default model
            var claude = services.find("anthropic", "claude-sonnet-5").orElseThrow();
            services.defaultModel(claude);
            assertEquals("claude-sonnet-5", prefs.get(SettingsDialog.DEFAULT_MODEL_KEY, null));
            assertEquals("claude-sonnet-5", services.defaultModel().model().id());

            // Remove default model preference
            services.defaultModel(null);
            assertNull(prefs.get(SettingsDialog.DEFAULT_MODEL_KEY, null));
            assertEquals("gpt-6-sol", services.defaultModel().model().id());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void formatSecretStatus_reportsCorrectTierStatus() {
        SecretStore secrets = newTestSecretStore();

        String absentStatus = SettingsDialog.formatSecretStatus(secrets, LlmServices.OPENAI_SECRET);
        assertNotNull(absentStatus);
        assertFalse(absentStatus.isBlank());

        secrets.save(LlmServices.OPENAI_SECRET, "sk-test");
        String fileStatus = SettingsDialog.formatSecretStatus(secrets, LlmServices.OPENAI_SECRET);
        assertNotNull(fileStatus);
        assertFalse(fileStatus.isBlank());
        assertNotEquals(absentStatus, fileStatus);
    }

    @Test
    void secretId_supportsCustomNamedServicesPerProvider() {
        Optional<SecretId> azureEast = LlmServices.secretId("azure-eastus", "azureOpenAI");
        Optional<SecretId> azureWest = LlmServices.secretId("azure-westus", "azureOpenAI");
        assertTrue(azureEast.isPresent());
        assertTrue(azureWest.isPresent());
        assertNotEquals(azureEast.get(), azureWest.get());
        assertEquals("llm-azure-eastus", azureEast.get().name());
        assertEquals("IOA_LLM_SECRET_AZURE_EASTUS", azureEast.get().environmentVariable());
        assertTrue(LlmServices.secretId("vertex-prod", "googleEnterprise").isEmpty());
    }

    @Test
    void llmConfig_supportsMultipleConnectionsPerProviderAndSerializesAsList() throws Exception {
        Path cfgPath = tempDir.resolve("llm.json");
        SecretStore secrets = newTestSecretStore();

        LlmConfig cfg = new LlmConfig(
                "vllm-prod",
                "ollama-local:qwen3.5",
                List.of(
                        new LlmConfig.ServiceEntry(
                                "vllm-prod",
                                "chatCompletions",
                                "http://127.0.0.1:8000/v1",
                                null,
                                null,
                                List.of("deepseek-r1", "qwen3.5")
                        ),
                        new LlmConfig.ServiceEntry(
                                "ollama-local",
                                "chatCompletions",
                                "http://127.0.0.1:11434/v1",
                                null,
                                null,
                                List.of("qwen3.5")
                        ),
                        new LlmConfig.ServiceEntry(
                                "azure-east",
                                "azureOpenAI",
                                "https://east.openai.azure.com/openai/v1",
                                null,
                                null,
                                List.of("gpt-6-sol")
                        ),
                        new LlmConfig.ServiceEntry(
                                "azure-west",
                                "azureOpenAI",
                                "https://west.openai.azure.com/openai/v1",
                                null,
                                null,
                                List.of("gpt-6-astra")
                        )
                )
        );
        cfg.save(cfgPath);

        String rawJson = Files.readString(cfgPath, StandardCharsets.UTF_8);
        assertTrue(rawJson.contains("\"services\" : ["));
        assertTrue(rawJson.contains("\"provider\" : \"chatCompletions\""));

        SharedSecrets.saveLlmSecret(secrets, "azure-east", "azureOpenAI", "east-key");
        SharedSecrets.saveLlmSecret(secrets, "azure-west", "azureOpenAI", "west-key");

        LlmConfig reloadedCfg = LlmConfig.load(cfgPath);
        assertEquals(4, reloadedCfg.services().size());
        assertEquals("vllm-prod", reloadedCfg.defaultService());

        ioa.llm.client.LlmServices shared = new ioa.llm.client.LlmServices();
        shared.reload(
                reloadedCfg,
                cfgPath,
                svcName -> {
                    String provider = reloadedCfg.findService(svcName)
                            .map(LlmConfig.ServiceEntry::provider)
                            .orElse(svcName);
                    return LlmServices.secretId(svcName, provider)
                            .flatMap(id -> LlmServices.readSecret(secrets, id));
                },
                null
        );

        assertEquals(5, shared.availableModels().size());
        assertTrue(shared.client("vllm-prod").isPresent());
        assertTrue(shared.client("ollama-local").isPresent());
        assertTrue(shared.client("azure-east").isPresent());
        assertTrue(shared.client("azure-west").isPresent());
        assertNotSame(shared.client("vllm-prod").get(), shared.client("ollama-local").get());
        assertNotSame(shared.client("azure-east").get(), shared.client("azure-west").get());

        var defModel = shared.defaultModel();
        assertNotNull(defModel);
        assertEquals("ollama-local", defModel.serviceKey());
        assertEquals("qwen3.5", defModel.model().id());
    }

    @Test
    void llmConfig_migratesLegacyMapServicesFormatAutomatically() {
        String legacyJson = """
                {
                  "defaultService": "openai",
                  "defaultModel": "gpt-6-sol",
                  "services": {
                    "openai": {
                      "baseUrl": "https://api.openai.com/v1",
                      "models": ["gpt-6-sol"]
                    },
                    "googleEnterprise": {
                      "project": "my-gcp-proj",
                      "location": "us-central1",
                      "models": ["gemini-3-pro"]
                    }
                  }
                }
                """;
        LlmConfig parsed = LlmConfig.fromJson(legacyJson);
        assertEquals(2, parsed.services().size());
        assertEquals("openai", parsed.service("openai").name());
        assertEquals("openai", parsed.service("openai").provider());
        assertEquals(List.of("gpt-6-sol"), parsed.service("openai").models());
        assertEquals("googleEnterprise", parsed.service("googleEnterprise").provider());
        assertEquals("my-gcp-proj", parsed.service("googleEnterprise").project());
        assertEquals("us-central1", parsed.service("googleEnterprise").location());
    }

    @Test
    void givenCanonicalDisplayNames_whenSecretId_thenResolvesCanonicalProviderSecrets() {
        // Given/When/Then
        assertEquals(Optional.of(LlmServices.OPENAI_SECRET), LlmServices.secretId("OpenAI", "openai"));
        assertEquals(Optional.of(LlmServices.AZURE_OPENAI_SECRET), LlmServices.secretId("Azure OpenAI", "azureOpenAI"));
        assertEquals(Optional.of(LlmServices.ANTHROPIC_SECRET), LlmServices.secretId("Anthropic", "anthropic"));
        assertEquals(Optional.of(LlmServices.GOOGLE_GEMINI_SECRET), LlmServices.secretId("Google Gemini", "googleGemini"));
        assertEquals(Optional.of(LlmServices.GOOGLE_GEMINI_SECRET), LlmServices.secretId("Google Gemini"));
        assertTrue(LlmServices.secretId("Google Enterprise", "googleEnterprise").isEmpty());
        assertTrue(LlmServices.secretId("Google Gemini Enterprise", "googleEnterprise").isEmpty());

        // Custom connection names still get service-specific SecretIds
        Optional<SecretId> smileSecret = LlmServices.secretId("SMILE", "chatCompletions");
        assertTrue(smileSecret.isPresent());
        assertEquals("llm-smile", smileSecret.get().name());
        assertEquals("IOA_LLM_SECRET_SMILE", smileSecret.get().environmentVariable());
    }

    @Test
    void givenDeployedLlmJsonAndPrefsWithModels_whenLoadOrMigrateConfig_thenPreservesDisplayNamesAndMergesPrefs() throws Exception {
        // Given: freshly deployed conf/llm.json with proper display names and empty models
        Path cfgPath = tempDir.resolve("llm.json");
        LlmServices.defaultConfig().save(cfgPath);

        String savedProp = System.getProperty("ioa.llm.config");
        Preferences prefs = SmileStudio.preferences();
        String savedAiService = prefs.get(SettingsDialog.AI_SERVICE_KEY, null);
        String savedDefaultModel = prefs.get(SettingsDialog.DEFAULT_MODEL_KEY, null);
        String savedOpenAiModel = prefs.get("openaiModel", null);
        String savedGeminiModel = prefs.get("googleGeminiModel", null);
        String savedAzureUrl = prefs.get("azureOpenAIBaseUrl", null);
        String savedAzureModel = prefs.get("azureOpenAIModel", null);
        String savedChatUrl = prefs.get("chatCompletionsBaseUrl", null);
        String savedChatModel = prefs.get("chatCompletionsModel", null);

        try {
            System.setProperty("ioa.llm.config", cfgPath.toString());
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "Google Gemini");
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, "googleGemini:gemini-4-argon");
            prefs.put("openaiModel", "gpt-6-sol");
            prefs.put("googleGeminiModel", "gemini-4-argon, gemini-4-neon");
            prefs.put("azureOpenAIBaseUrl", "https://example.openai.azure.com/openai/v1");
            prefs.put("azureOpenAIModel", "gpt-4.1-shared");
            prefs.remove("chatCompletionsBaseUrl");
            prefs.put("chatCompletionsModel", "deepseek-v4.1-flash");

            // When: Studio starts up or opens SettingsDialog after rebuild/redeploy
            LlmConfig loaded = LlmServices.loadOrMigrateConfig(prefs);

            // Then: proper display names are preserved in memory and on disk
            List<String> names = loaded.services().stream().map(LlmConfig.ServiceEntry::name).toList();
            assertEquals(
                    List.of("OpenAI", "Azure OpenAI", "Anthropic", "Google Gemini", "Google Enterprise", "SMILE"),
                    names);
            assertEquals("Google Gemini", loaded.defaultService());
            assertEquals("Google Gemini:gemini-4-argon", loaded.defaultModel());
            assertEquals(List.of("gemini-4-argon", "gemini-4-neon"), loaded.service("googleGemini").models());
            assertEquals("https://example.openai.azure.com/openai/v1", loaded.service("azureOpenAI").baseUrl());
            assertEquals(List.of("gpt-4.1-shared"), loaded.service("azureOpenAI").models());
            assertEquals("http://localhost:8888/v1", loaded.service("chatCompletions").baseUrl());
            assertEquals(List.of("deepseek-v4.1-flash"), loaded.service("chatCompletions").models());

            LlmConfig persisted = LlmConfig.load(cfgPath);
            assertEquals(names, persisted.services().stream().map(LlmConfig.ServiceEntry::name).toList());
            assertEquals("Google Gemini", persisted.defaultService());
        } finally {
            if (savedProp == null) {
                System.clearProperty("ioa.llm.config");
            } else {
                System.setProperty("ioa.llm.config", savedProp);
            }
            restorePref(prefs, SettingsDialog.AI_SERVICE_KEY, savedAiService);
            restorePref(prefs, SettingsDialog.DEFAULT_MODEL_KEY, savedDefaultModel);
            restorePref(prefs, "openaiModel", savedOpenAiModel);
            restorePref(prefs, "googleGeminiModel", savedGeminiModel);
            restorePref(prefs, "azureOpenAIBaseUrl", savedAzureUrl);
            restorePref(prefs, "azureOpenAIModel", savedAzureModel);
            restorePref(prefs, "chatCompletionsBaseUrl", savedChatUrl);
            restorePref(prefs, "chatCompletionsModel", savedChatModel);
        }
    }

    @Test
    void givenDeployedLlmJsonAndPrefsWithoutModels_whenLoadOrMigrateConfig_thenPreservesFileDisplayNames() throws Exception {
        // Given: freshly deployed conf/llm.json with no models, and Preferences populated without models
        Path cfgPath = tempDir.resolve("llm.json");
        LlmServices.defaultConfig().save(cfgPath);

        String savedProp = System.getProperty("ioa.llm.config");
        Preferences prefs = SmileStudio.preferences();
        String savedAiService = prefs.get(SettingsDialog.AI_SERVICE_KEY, null);
        String savedDefaultModel = prefs.get(SettingsDialog.DEFAULT_MODEL_KEY, null);
        String savedOpenAiModel = prefs.get("openaiModel", null);
        String savedAzureModel = prefs.get("azureOpenAIModel", null);
        String savedAnthropicModel = prefs.get("anthropicModel", null);
        String savedGeminiModel = prefs.get("googleGeminiModel", null);
        String savedEnterpriseModel = prefs.get("googleEnterpriseModel", null);
        String savedChatModel = prefs.get("chatCompletionsModel", null);

        try {
            System.setProperty("ioa.llm.config", cfgPath.toString());
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.remove(SettingsDialog.DEFAULT_MODEL_KEY);
            prefs.put("openaiModel", "");
            prefs.put("azureOpenAIModel", "");
            prefs.put("anthropicModel", "");
            prefs.put("googleGeminiModel", "");
            prefs.put("googleEnterpriseModel", "");
            prefs.put("chatCompletionsModel", "");

            // When
            LlmConfig loaded = LlmServices.loadOrMigrateConfig(prefs);

            // Then: file display names are preserved instead of falling back to raw provider keys
            assertEquals(
                    List.of("OpenAI", "Azure OpenAI", "Anthropic", "Google Gemini", "Google Enterprise", "SMILE"),
                    loaded.services().stream().map(LlmConfig.ServiceEntry::name).toList());
        } finally {
            if (savedProp == null) {
                System.clearProperty("ioa.llm.config");
            } else {
                System.setProperty("ioa.llm.config", savedProp);
            }
            restorePref(prefs, SettingsDialog.AI_SERVICE_KEY, savedAiService);
            restorePref(prefs, SettingsDialog.DEFAULT_MODEL_KEY, savedDefaultModel);
            restorePref(prefs, "openaiModel", savedOpenAiModel);
            restorePref(prefs, "azureOpenAIModel", savedAzureModel);
            restorePref(prefs, "anthropicModel", savedAnthropicModel);
            restorePref(prefs, "googleGeminiModel", savedGeminiModel);
            restorePref(prefs, "googleEnterpriseModel", savedEnterpriseModel);
            restorePref(prefs, "chatCompletionsModel", savedChatModel);
        }
    }

    @Test
    void givenOllamaTaggedDefaultModel_whenMergePreferencesIntoConfig_thenKeepsModelTagIntact() throws Exception {
        // Given
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTestMerge");
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "Chat Completions Compatible");
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, "qwen3:8b");
            prefs.put("chatCompletionsModel", "qwen3:8b");

            LlmConfig fromPrefs = LlmConfig.fromPreferences(prefs).orElseThrow();

            // When
            LlmConfig merged = LlmServices.mergePreferencesIntoConfig(LlmServices.defaultConfig(), fromPrefs, prefs);

            // Then
            assertEquals("SMILE", merged.defaultService());
            assertEquals("qwen3:8b", merged.defaultModel());
            assertEquals(List.of("qwen3:8b"), merged.service("chatCompletions").models());
        } finally {
            prefs.removeNode();
        }
    }

    private static void restorePref(Preferences prefs, String key, String savedValue) {
        if (savedValue == null) {
            prefs.remove(key);
        } else {
            prefs.put(key, savedValue);
        }
    }
}
