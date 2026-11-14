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
package smile.studio.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import photos.sluice.secrets.SecretStore;
import smile.studio.LlmServices;
import smile.studio.SettingsDialog;
import smile.studio.SmileStudio;
import static org.junit.jupiter.api.Assertions.*;

class IntentTest {
    @TempDir
    Path tempDir;

    private String savedLlmConfigProp;
    private String savedService;
    private String savedModelPref;
    private String savedOpenAiModel;
    private String savedAnthropicModel;

    @BeforeEach
    void setUp() {
        savedLlmConfigProp = System.getProperty("ioa.llm.config");
        System.setProperty("ioa.llm.config", tempDir.resolve("llm.json").toString());

        Preferences prefs = SmileStudio.preferences();
        savedService = prefs.get(SettingsDialog.AI_SERVICE_KEY, "");
        savedModelPref = prefs.get(SettingsDialog.DEFAULT_MODEL_KEY, "");
        savedOpenAiModel = prefs.get("openaiModel", "");
        savedAnthropicModel = prefs.get("anthropicModel", "");

        prefs.put(SettingsDialog.AI_SERVICE_KEY, "OpenAI");
        prefs.put("openaiModel", "gpt-6-sol, gpt-6-astra");
        prefs.put("anthropicModel", "claude-sonnet-5");
        prefs.remove(SettingsDialog.DEFAULT_MODEL_KEY);

        SecretStore testSecrets = SecretStore.forApplication("SmileStudioIntentTest")
                .inNamespace("ai.smile.studio.intent.test")
                .onOperatingSystem("none")
                .withCredentialFilesIn(tempDir.resolve("credentials"))
                .open();
        testSecrets.save(LlmServices.OPENAI_SECRET, "test-key");
        testSecrets.save(LlmServices.ANTHROPIC_SECRET, "anth-key");

        SmileStudio.llmServices().reload(prefs, testSecrets);
    }

    @AfterEach
    void tearDown() {
        if (savedLlmConfigProp == null) {
            System.clearProperty("ioa.llm.config");
        } else {
            System.setProperty("ioa.llm.config", savedLlmConfigProp);
        }

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
    void agentStatusPrefixIsScrollEmoji() {
        assertEquals(Character.toString(0x1F4DC) + " ", Intent.AGENT_STATUS_PREFIX);
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

    /** Keys the cancel path reads: the terminal status, the confirm dialog, and its title. */
    private static final List<String> CANCEL_KEYS = List.of(
            "Cancelled", "ConfirmDropQueue", "CancelConfirmTitle");

    @Test
    void cancelKeysExistInEveryLocale() {
        ResourceBundle base = ResourceBundle.getBundle(Intent.class.getName(), Locale.ROOT);
        for (String key : CANCEL_KEYS) {
            assertTrue(base.containsKey(key), "base Intent bundle is missing key: " + key);
        }
        for (Locale locale : QUEUE_LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(Intent.class.getName(), locale);
            for (String key : CANCEL_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " Intent bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " Intent bundle has a blank value for key: " + key);
            }
        }
    }

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

    /** Keys the output Text/Preview toggle bar reads at runtime. */
    private static final List<String> MODE_KEYS = List.of(
            "TextMode", "PreviewMode", "TextModeTooltip", "PreviewModeTooltip");

    @Test
    void modeKeysExistInEveryLocale() {
        ResourceBundle base = ResourceBundle.getBundle(Intent.class.getName(), Locale.ROOT);
        for (String key : MODE_KEYS) {
            assertTrue(base.containsKey(key), "base Intent bundle is missing key: " + key);
        }
        for (Locale locale : QUEUE_LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(Intent.class.getName(), locale);
            for (String key : MODE_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " Intent bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " Intent bundle has a blank value for key: " + key);
            }
        }
    }

    @Test
    void newIntent_defaultsToTextModeAndHidesModeBarUntilTurnFinishesWithOutput() {
        Intent intent = new Intent(null);
        assertEquals(Intent.Mode.TEXT, intent.getMode());
        assertFalse(intent.modeBar().isVisible(), "editable composer must not show modeBar");

        // Submitted turn with no output yet
        intent.setEditable(false);
        assertFalse(intent.modeBar().isVisible(), "read-only intent with blank output must hide modeBar");

        // Streaming turn in progress
        intent.setProgress(true);
        intent.appendRun(null, "| Col A | Col B |\n| --- | --- |\n| 1 | 2 |\n");
        assertFalse(intent.modeBar().isVisible(), "modeBar must stay hidden while turn is streaming");
        assertEquals(Intent.Mode.TEXT, intent.getMode());

        // Turn completes
        intent.setProgress(false);
        assertTrue(intent.modeBar().isVisible(), "modeBar must appear at bottom-right once turn finishes with output");
        assertEquals(Intent.Mode.TEXT, intent.getMode(), "agent output must remain in TEXT mode by default");
        assertFalse(intent.modeButton().isSelected());
    }

    @Test
    void switchingBetweenPreviewAndTextMode_rendersMarkdownAndRestoresPlainText() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        String md = """
                # Summary Report

                | Metric | Value |
                | :--- | ---: |
                | Latency | 1.8 ms |

                ```java
                int x = 42;
                ```
                """;
        intent.output().setText(md);
        assertTrue(intent.modeBar().isVisible());
        assertEquals(Intent.Mode.TEXT, intent.getMode());

        // Switch to Preview mode via the toggle button
        ResourceBundle bundle = ResourceBundle.getBundle(Intent.class.getName(), Locale.getDefault());
        assertEquals(bundle.getString("PreviewMode"), intent.modeButton().getText());
        intent.modeButton().doClick();
        assertEquals(Intent.Mode.PREVIEW, intent.getMode());
        assertTrue(intent.modeButton().isSelected());
        assertEquals(bundle.getString("TextMode"), intent.modeButton().getText());
        assertNotNull(intent.markdownView());
        assertNotNull(intent.markdownView().contentPane());
        assertEquals(1, intent.markdownView().contentPane().getDocument().getElementsByTagName("table").getLength());
        assertEquals(1, intent.markdownView().contentPane().getDocument().getElementsByTagName("pre").getLength());
        assertTrue(intent.markdownView().getPreferredSize().height > 0,
                "embedded Markdown preview must compute a positive preferred height for BorderLayout.SOUTH");

        // Switch back to Text mode via the toggle button
        intent.modeButton().doClick();
        assertEquals(Intent.Mode.TEXT, intent.getMode());
        assertFalse(intent.modeButton().isSelected());
        assertEquals(bundle.getString("PreviewMode"), intent.modeButton().getText());
        assertNotNull(intent.output().getParent(), "OutputArea must be restored to outputPane in TEXT mode");
        assertEquals(md, intent.output().getText());
    }

    @Test
    void previewMode_supportsSubagentTabsAndRestoresOutputAreas() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.appendRun(null, "# Master Output\n\n| A | B |\n| - | - |\n| 1 | 2 |\n");
        intent.beginRun("run-1", "Explore", "James");
        intent.appendRun("run-1", "```java\nSystem.out.println(\"subagent\");\n```\n");
        intent.finishRun("run-1", "Finished");
        intent.setProgress(false);

        assertTrue(intent.modeBar().isVisible());
        intent.setMode(Intent.Mode.PREVIEW);
        assertEquals(Intent.Mode.PREVIEW, intent.getMode());
        assertNotNull(intent.markdownView());

        // Switch back to TEXT mode and verify both master and subagent OutputAreas are restored
        intent.setMode(Intent.Mode.TEXT);
        assertEquals(Intent.Mode.TEXT, intent.getMode());
        assertTrue(intent.output().getText().contains("Master Output"));
    }

    @Test
    void previewMode_fallsBackToTextModeOnMalformedXml() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.output().setText("# Broken\n\n<div>unclosed tag\n");

        intent.setMode(Intent.Mode.PREVIEW);
        assertEquals(Intent.Mode.TEXT, intent.getMode(), "malformed XML fallback must keep mode as TEXT");
        assertFalse(intent.modeButton().isSelected());
    }
}
