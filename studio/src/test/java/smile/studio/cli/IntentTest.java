/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 */
package smile.studio.cli;

import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.studio.SettingsDialog;
import smile.studio.SmileStudio;
import static org.junit.jupiter.api.Assertions.*;

class IntentTest {
    private String savedService;
    private String savedModelPref;
    private String savedOpenAiModel;
    private String savedAnthropicModel;

    @BeforeEach
    void setUp() {
        Preferences prefs = SmileStudio.preferences();
        savedService = prefs.get(SettingsDialog.AI_SERVICE_KEY, "");
        savedModelPref = prefs.get(SettingsDialog.DEFAULT_MODEL_KEY, "");
        savedOpenAiModel = prefs.get("openaiModel", "");
        savedAnthropicModel = prefs.get("anthropicModel", "");

        prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
        prefs.put("openaiApiKey", "test-key");
        prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
        prefs.put("anthropicApiKey", "anth-key");
        prefs.put("anthropicModel", "claude-sonnet-5");
        prefs.remove(SettingsDialog.DEFAULT_MODEL_KEY);

        SmileStudio.llmServices().reload(prefs);
    }

    @AfterEach
    void tearDown() {
        Preferences prefs = SmileStudio.preferences();
        if (savedService.isEmpty()) prefs.remove(SettingsDialog.AI_SERVICE_KEY);
        else prefs.put(SettingsDialog.AI_SERVICE_KEY, savedService);

        if (savedModelPref.isEmpty()) prefs.remove(SettingsDialog.DEFAULT_MODEL_KEY);
        else prefs.put(SettingsDialog.DEFAULT_MODEL_KEY, savedModelPref);

        if (savedOpenAiModel.isEmpty()) prefs.remove("openaiModel");
        else prefs.put("openaiModel", savedOpenAiModel);

        if (savedAnthropicModel.isEmpty()) prefs.remove("anthropicModel");
        else prefs.put("anthropicModel", savedAnthropicModel);

        SmileStudio.llmServices().reload(prefs);
    }

    @Test
    void initialIntent_usesFallbackHeuristicWhenNoPreference() {
        Intent intent = new Intent(null);
        assertNotNull(intent.resolveModel());
        assertEquals("gpt-6-sol", intent.resolveModel().model().id());
    }

    @Test
    void selectingModelInIntent_savesDefaultModelToPreferences_andFollowupIntentUsesIt() {
        Intent firstIntent = new Intent(null);
        // Initially on heuristic default
        assertEquals("gpt-6-sol", firstIntent.resolveModel().model().id());

        // Select claude-sonnet-5 in first intent
        var available = SmileStudio.llmServices().availableModels();
        boolean qualify = available.stream().map(m -> m.model().id()).distinct().count() < available.size();
        var claude = SmileStudio.llmServices().find("anthropic", "claude-sonnet-5").orElseThrow();

        // Simulate user selecting model via setter / preferences
        SmileStudio.llmServices().defaultModel(claude);
        assertEquals("claude-sonnet-5", SmileStudio.preferences().get(SettingsDialog.DEFAULT_MODEL_KEY, null));

        // Followup intent instance should use this default model
        Intent followupIntent = new Intent(null);
        assertNotNull(followupIntent.resolveModel());
        assertEquals("claude-sonnet-5", followupIntent.resolveModel().model().id());
        assertEquals("anthropic", followupIntent.resolveModel().serviceKey());
    }

    @Test
    void clearingDefaultModel_restoresFallbackHeuristicForFollowupIntents() {
        // Set default model to claude
        var claude = SmileStudio.llmServices().find("anthropic", "claude-sonnet-5").orElseThrow();
        SmileStudio.llmServices().defaultModel(claude);
        assertEquals("claude-sonnet-5", SmileStudio.preferences().get(SettingsDialog.DEFAULT_MODEL_KEY, null));

        // Followup intent uses it
        Intent intent1 = new Intent(null);
        assertEquals("claude-sonnet-5", intent1.resolveModel().model().id());

        // Clear default model preference (simulating selecting "default")
        SmileStudio.llmServices().defaultModel(null);
        assertNull(SmileStudio.preferences().get(SettingsDialog.DEFAULT_MODEL_KEY, null));

        // Followup intent falls back to heuristic (first model of default service: gpt-6-sol)
        Intent intent2 = new Intent(null);
        assertEquals("gpt-6-sol", intent2.resolveModel().model().id());
    }

    @Test
    void agentStatusPrefixIsRobotEmoji() {
        assertEquals(Character.toString(0x1F916) + " ", Intent.AGENT_STATUS_PREFIX);
    }

    @Test
    void setStatusUpdatesLabelAndTooltip() {
        Intent intent = new Intent(null);
        intent.setStatus("Thinking...");
        assertEquals("Thinking...", intent.status().getText());
        assertEquals("Thinking...", intent.status().getToolTipText());
    }

    @Test
    void setStatusNormalizesWhitespace() {
        Intent intent = new Intent(null);
        intent.setStatus("   Searching    codebase \t\n ");
        assertEquals("Searching codebase", intent.status().getText());
    }

    @Test
    void setStatusWithNullOrEmptyClearsLabel() {
        Intent intent = new Intent(null);
        intent.setStatus("Running");
        assertEquals("Running", intent.status().getText());

        intent.setStatus("");
        assertEquals("", intent.status().getText());
        assertNull(intent.status().getToolTipText());

        intent.setStatus(null);
        assertEquals("", intent.status().getText());
        assertNull(intent.status().getToolTipText());
    }
}
