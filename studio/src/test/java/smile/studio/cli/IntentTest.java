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

    @Test
    void subagentRun_selectsMasterTabWhenMasterOutputsTokensAgain() {
        Intent intent = new Intent(null);
        assertNull(intent.runs());

        // Master agent starts with no tabs initially
        intent.appendRun(null, "Thinking...");
        assertNull(intent.runs());

        // Master agent starts a subagent
        intent.beginRun("run-1", "Explore Code", "Guido");
        assertNotNull(intent.runs());
        assertEquals(2, intent.runs().getTabCount());
        // Subagent tab is selected
        assertEquals(1, intent.runs().getSelectedIndex());
        assertEquals("Explore Code", intent.runs().getTitleAt(1));
        assertEquals("Guido", intent.runs().getTitleAt(0));

        // Subagent streams tokens
        intent.appendRun("run-1", "Exploring...");
        assertEquals(1, intent.runs().getSelectedIndex());

        // Subagent finishes - master agent tab is not selected yet
        intent.finishRun("run-1", "Finished");
        assertEquals(1, intent.runs().getSelectedIndex());

        // Master agent starts outputting tokens again - master tab is selected
        intent.appendRun(null, "Based on my findings...");
        assertEquals(0, intent.runs().getSelectedIndex());

        // User switches to subagent tab while master is outputting tokens
        intent.runs().setSelectedIndex(1);
        assertEquals(1, intent.runs().getSelectedIndex());

        // Subsequent master agent tokens do not forcibly snap back
        intent.appendRun(null, " here are more details.");
        assertEquals(1, intent.runs().getSelectedIndex());

        // Another subagent starts
        intent.beginRun("run-2", "Run Tests", "Guido");
        assertEquals(3, intent.runs().getTabCount());
        assertEquals(2, intent.runs().getSelectedIndex());

        // Subagent finishes
        intent.finishRun("run-2", "Finished");
        assertEquals(2, intent.runs().getSelectedIndex());

        // Master outputs tokens again -> selects master tab
        intent.appendRun(null, "Tests completed.");
        assertEquals(0, intent.runs().getSelectedIndex());
    }
}
