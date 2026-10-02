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
package smile.studio.cli;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
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
    void stopButton_hiddenUntilStopActionIsSet() {
        Intent intent = new Intent(null);
        assertFalse(intent.stopButton().isVisible());
    }

    @Test
    void setStopAction_showsAndEnablesStopButton() {
        Intent intent = new Intent(null);
        intent.setStopAction(() -> null);

        assertTrue(intent.stopButton().isVisible());
        assertTrue(intent.stopButton().isEnabled());
    }

    @Test
    void clickingStop_disablesButtonAndFreezesProgressBarAndRunsActionImmediately() {
        Intent intent = new Intent(null);
        int[] calls = {0};
        intent.setProgress(true);
        intent.setStopAction(() -> {
            calls[0]++;
            return null;
        });

        intent.onStopClicked();

        assertEquals(1, calls[0], "stop action should run on the first click");
        assertFalse(intent.stopButton().isEnabled(), "button must be disabled after a cancel");
        assertFalse(intent.progressBar().isIndeterminate(),
                "progress bar must stop animating as immediate feedback");
    }

    @Test
    void clickingStopTwice_runsActionOnlyOnce() {
        Intent intent = new Intent(null);
        int[] calls = {0};
        intent.setStopAction(() -> {
            calls[0]++;
            return null;
        });

        intent.onStopClicked();
        intent.onStopClicked();

        assertEquals(1, calls[0], "a second click while cancelling must be ignored");
    }

    @Test
    void newTurn_afterCancel_reArmsStopButton() {
        Intent intent = new Intent(null);
        intent.setProgress(true);
        intent.setStopAction(() -> null);
        intent.onStopClicked();
        assertFalse(intent.stopButton().isEnabled());

        // A new turn begins: cancel is reset.
        intent.setProgress(true);
        intent.setStopAction(() -> null);

        assertTrue(intent.stopButton().isEnabled(),
                "starting a new turn must re-enable a stop button disabled by a previous cancel");
        assertTrue(intent.progressBar().isIndeterminate(),
                "starting a new turn must resume progress animation");
    }

    @Test
    void clearingProgress_marksTurnIdle() {
        Intent intent = new Intent(null);
        intent.setProgress(true);
        intent.setStopAction(() -> null);

        intent.setProgress(false);

        assertFalse(intent.progressBar().isEnabled());
    }

    /** Every locale that ships an Intent bundle. */
    private static final List<Locale> QUEUE_LOCALES = List.of(
            Locale.US,
            Locale.SIMPLIFIED_CHINESE,
            Locale.JAPAN,
            Locale.FRANCE,
            Locale.of("es", "ES"));

    /** Keys the queue badge and controls read at runtime. */
    private static final List<String> QUEUE_KEYS = List.of(
            "Queued", "QueuedPosition", "CancelQueued", "EditQueued",
            "MoveQueuedUp", "MoveQueuedDown", "CancelledQueued", "QueueDepth");

    @Test
    void queueKeysExistInEveryLocale() {
        ResourceBundle base = ResourceBundle.getBundle(Intent.class.getName(), Locale.ROOT);
        for (String key : QUEUE_KEYS) {
            assertTrue(base.containsKey(key), "base Intent bundle is missing key: " + key);
        }
        for (Locale locale : QUEUE_LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(Intent.class.getName(), locale);
            for (String key : QUEUE_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " Intent bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " Intent bundle has a blank value for key: " + key);
            }
        }
    }

    @Test
    void showQueued_displaysBadgeAndHidesProgress() {
        Intent intent = new Intent(null);
        intent.setProgress(true);

        intent.showQueued(2, 3);

        assertTrue(intent.queuePane().isVisible(), "queue pane must show while waiting");
        assertTrue(intent.queueBadge().getText().contains("2"),
                "badge must show the 1-based position");
        assertTrue(intent.queueBadge().getText().contains("3"),
                "badge must show the queue depth");
        assertFalse(intent.progressBar().isEnabled(), "a waiting request is not running");
    }

    @Test
    void showQueued_statusReadsQueued_notThinking() {
        Intent intent = new Intent(null);
        // run() sets "Thinking..." at submit time, before the request is queued.
        intent.setStatus("Thinking...");

        intent.showQueued(1, 2);

        assertEquals("Queued", intent.status().getText(),
                "a waiting request must not claim it is thinking");
        assertNull(intent.status().getToolTipText());
    }

    @Test
    void setProgress_trueClearsTheQueueBadge() {
        Intent intent = new Intent(null);
        intent.showQueued(1, 1);
        assertTrue(intent.queuePane().isVisible());

        intent.setProgress(true);

        assertFalse(intent.queuePane().isVisible(),
                "starting a turn must remove the queue badge");
    }

    @Test
    void setQueueControls_hidesEditForPeerRequestsAndEnablesArrowsByPosition() {
        Intent intent = new Intent(null);
        intent.showQueued(2, 3);

        // Local prompt: edit offered.
        intent.setQueueControls(() -> {}, () -> {}, () -> {}, () -> {});
        intent.setQueueControlsEnabled(true, true);
        assertTrue(intent.editQueuedButton().isVisible());
        assertTrue(intent.moveUpButton().isEnabled());
        assertTrue(intent.moveDownButton().isEnabled());

        // Peer request: edit hidden.
        intent.setQueueControls(() -> {}, null, () -> {}, () -> {});
        assertFalse(intent.editQueuedButton().isVisible(),
                "edit must not be offered for a peer request");

        // At the head: move-up disabled.
        intent.setQueueControlsEnabled(false, true);
        assertFalse(intent.moveUpButton().isEnabled());
    }

    @Test
    void queueControlsInvokeTheirActions() {
        Intent intent = new Intent(null);
        intent.showQueued(1, 2);
        int[] hits = {0, 0, 0, 0};
        intent.setQueueControls(() -> hits[0]++, () -> hits[1]++,
                () -> hits[2]++, () -> hits[3]++);

        intent.cancelQueuedButton().doClick();
        intent.editQueuedButton().doClick();
        intent.moveUpButton().doClick();
        intent.moveDownButton().doClick();

        assertArrayEquals(new int[] {1, 1, 1, 1}, hits);
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
