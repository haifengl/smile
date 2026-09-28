/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 */
package smile.studio;

import java.util.List;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LlmServices}.
 */
class LlmServicesTest {

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
    void reload_buildsModelsForConfiguredServices() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiApiKey", "test-key");
            prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
            prefs.put("anthropicApiKey", "anth-key");
            prefs.put("anthropicModel", "claude-sonnet-5");

            LlmServices services = new LlmServices();
            services.reload(prefs);

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
    void defaultModel_withPreference_returnsConfiguredModel() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiApiKey", "test-key");
            prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
            prefs.put("anthropicApiKey", "anth-key");
            prefs.put("anthropicModel", "claude-sonnet-5");
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, "claude-sonnet-5");

            LlmServices services = new LlmServices();
            services.reload(prefs);

            assertEquals("claude-sonnet-5", services.defaultModel().model().id());
            assertEquals("anthropic", services.defaultModel().serviceKey());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void defaultModel_withServicePrefix_returnsConfiguredModel() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiApiKey", "test-key");
            prefs.put("openaiModel", "gpt-6-sol");
            prefs.put("anthropicApiKey", "anth-key");
            prefs.put("anthropicModel", "claude-sonnet-5");
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, "anthropic:claude-sonnet-5");

            LlmServices services = new LlmServices();
            services.reload(prefs);

            assertEquals("claude-sonnet-5", services.defaultModel().model().id());
            assertEquals("anthropic", services.defaultModel().serviceKey());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void defaultModel_withInvalidPreference_fallsBackToHeuristic() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiApiKey", "test-key");
            prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
            prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, "non-existent-model");

            LlmServices services = new LlmServices();
            services.reload(prefs);

            assertEquals("gpt-6-sol", services.defaultModel().model().id());
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void defaultModel_setter_updatesPreferenceAndRemovesOnNull() throws Exception {
        Preferences prefs = Preferences.userRoot().node("smile/studio/LlmServicesTest");
        try {
            prefs.clear();
            prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
            prefs.put("openaiApiKey", "test-key");
            prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
            prefs.put("anthropicApiKey", "anth-key");
            prefs.put("anthropicModel", "claude-sonnet-5");

            LlmServices services = new LlmServices();
            services.reload(prefs);

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
}
