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
                List.of("gpt-5.4", "claude-sonnet-5", "qwen3.8"),
                LlmServices.parseModelIds("gpt-5.4, claude-sonnet-5, qwen3.8"));
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
            prefs.put("openaiModel", "gpt-5.4-mini, gpt-5.4");
            prefs.put("anthropicApiKey", "anth-key");
            prefs.put("anthropicModel", "claude-sonnet-5");

            LlmServices services = new LlmServices();
            services.reload(prefs);

            assertEquals(3, services.availableModels().size());
            assertEquals("gpt-5.4-mini", services.defaultModel().model().id());
            assertNotNull(services.defaultClient());
            assertTrue(services.client("anthropic").isPresent());
            assertTrue(services.find("openai", "gpt-5.4").isPresent());
            assertTrue(services.find("anthropic", "gpt-5.4").isEmpty());
        } finally {
            prefs.removeNode();
        }
    }
}
