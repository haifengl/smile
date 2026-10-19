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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
}
