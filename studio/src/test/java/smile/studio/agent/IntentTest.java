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
import javax.swing.ScrollPaneConstants;
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

    /** Keys the output Raw/Preview toggle bar reads at runtime. */
    private static final List<String> MODE_KEYS = List.of(
            "RawMode", "PreviewMode", "RawModeTooltip", "PreviewModeTooltip");

    @Test
    void modeKeysExistInEveryLocale() {
        ResourceBundle base = ResourceBundle.getBundle(Intent.class.getName(), Locale.ROOT);
        for (String key : MODE_KEYS) {
            assertTrue(base.containsKey(key), "base Intent bundle is missing key: " + key);
        }
        assertEquals("Text", base.getString("RawMode"));
        assertEquals("Markdown", base.getString("PreviewMode"));
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
    void newIntent_defaultsToRawModeAndHidesModeBarUntilTurnFinishesWithOutput() {
        Intent intent = new Intent(null);
        assertEquals(Intent.Mode.RAW, intent.getMode());
        assertFalse(intent.modeBar().isVisible(), "editable composer must not show modeBar");

        // Submitted turn with no output yet
        intent.setEditable(false);
        assertFalse(intent.modeBar().isVisible(), "read-only intent with blank output must hide modeBar");

        // Streaming turn in progress
        intent.setProgress(true);
        intent.appendRun(null, "| Col A | Col B |\n| --- | --- |\n| 1 | 2 |\n");
        assertFalse(intent.modeBar().isVisible(), "modeBar must stay hidden while turn is streaming");
        assertEquals(Intent.Mode.RAW, intent.getMode());

        // Turn completes
        intent.setProgress(false);
        assertTrue(intent.modeBar().isVisible(), "modeBar must appear at bottom-right once turn finishes with output");
        assertEquals(Intent.Mode.RAW, intent.getMode(), "agent output must remain in RAW mode by default");
        assertFalse(intent.modeButton().isSelected());
    }

    @Test
    void switchingBetweenPreviewAndRawMode_rendersMarkdownAndRestoresRawText() {
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
        assertEquals(Intent.Mode.RAW, intent.getMode());

        // Switch to Preview mode via the bottom-right toggle button
        ResourceBundle bundle = ResourceBundle.getBundle(Intent.class.getName(), Locale.getDefault());
        assertEquals(bundle.getString("PreviewMode"), intent.modeButton().getText());
        assertEquals(bundle.getString("PreviewMode"), intent.topModeButton().getText());
        intent.modeButton().doClick();
        assertEquals(Intent.Mode.PREVIEW, intent.getMode());
        assertTrue(intent.modeButton().isSelected());
        assertTrue(intent.topModeButton().isSelected());
        assertEquals(bundle.getString("RawMode"), intent.modeButton().getText());
        assertEquals(bundle.getString("RawMode"), intent.topModeButton().getText());
        assertNotNull(intent.markdownView());
        assertNotNull(intent.markdownView().contentPane());
        assertEquals(1, intent.markdownView().contentPane().getDocument().getElementsByTagName("table").getLength());
        assertEquals(1, intent.markdownView().contentPane().getDocument().getElementsByTagName("pre").getLength());
        assertTrue(intent.markdownView().getPreferredSize().height > 0,
                "embedded Markdown preview must compute a positive preferred height for BorderLayout.SOUTH");

        // Switch back to Raw mode via the top-right toggle button
        intent.topModeButton().doClick();
        assertEquals(Intent.Mode.RAW, intent.getMode());
        assertFalse(intent.modeButton().isSelected());
        assertFalse(intent.topModeButton().isSelected());
        assertEquals(bundle.getString("PreviewMode"), intent.modeButton().getText());
        assertEquals(bundle.getString("PreviewMode"), intent.topModeButton().getText());
        assertNotNull(intent.output().getParent(), "OutputArea must be restored to outputPane in RAW mode");
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

        // Switch back to RAW mode and verify both master and subagent OutputAreas are restored
        intent.setMode(Intent.Mode.RAW);
        assertEquals(Intent.Mode.RAW, intent.getMode());
        assertTrue(intent.output().getText().contains("Master Output"));
    }

    @Test
    void previewMode_fallsBackToRawModeOnMalformedXml() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.output().setText("# Broken\n\n<div>unclosed tag\n");

        intent.setMode(Intent.Mode.PREVIEW);
        assertEquals(Intent.Mode.RAW, intent.getMode(), "malformed XML fallback must keep mode as RAW");
        assertFalse(intent.modeButton().isSelected());
    }

    @Test
    void southPane_overlaysModeBarInLayeredPanePaletteLayer() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.output().setText("# Heading\n\nBody text.\n");

        assertInstanceOf(javax.swing.JLayeredPane.class, intent.southPane());
        var defaultComps = intent.southPane().getComponentsInLayer(javax.swing.JLayeredPane.DEFAULT_LAYER);
        var paletteComps = intent.southPane().getComponentsInLayer(javax.swing.JLayeredPane.PALETTE_LAYER);
        assertEquals(1, defaultComps.length);
        assertSame(intent.outputPane(), defaultComps[0]);
        assertEquals(2, paletteComps.length);
        assertTrue(List.of(paletteComps).contains(intent.modeBar()));
        assertTrue(List.of(paletteComps).contains(intent.topModeBar()));

        // Short output: only bottom-right modeBar is visible; topModeBar is hidden to avoid crowding
        intent.southPane().setSize(600, 60);
        intent.southPane().doLayout();
        assertTrue(intent.modeBar().isVisible(), "bottom-right modeBar must be visible on short output");
        assertFalse(intent.topModeBar().isVisible(), "top-right modeBar must be hidden on short output");
        assertEquals(60 - intent.modeBar().getPreferredSize().height, intent.modeBar().getY(),
                "modeBar must sit at the bottom edge");
        assertTrue(intent.modeBar().getX() > 0, "modeBar must be positioned near the right edge");
        assertEquals(60 - intent.modeBar().getPreferredSize().height, intent.outputPane().getHeight(),
                "the output must stop above the modeBar so the bar never covers text");

        // Tall output: both bottom-right modeBar and top-right topModeBar are visible
        intent.southPane().setSize(600, 200);
        intent.southPane().doLayout();
        assertEquals(600, intent.outputPane().getWidth());
        assertEquals(200 - intent.modeBar().getPreferredSize().height, intent.outputPane().getHeight(),
                "the output must stop above the modeBar");
        assertTrue(intent.modeBar().isVisible(), "bottom-right modeBar must be visible on tall output");
        assertTrue(intent.topModeBar().isVisible(), "top-right topModeBar must be visible on tall output");
        assertEquals(200 - intent.modeBar().getPreferredSize().height, intent.modeBar().getY(),
                "modeBar must be positioned at the bottom-right corner");
        assertEquals(2, intent.topModeBar().getY(),
                "topModeBar must be positioned at the top-right corner");
        assertTrue(intent.modeBar().getX() > 0, "modeBar must be positioned near the right edge");
        assertTrue(intent.topModeBar().getX() > 0, "topModeBar must be positioned near the right edge");
    }

    @Test
    void bottomModeBarSitsBelowTheLastLineOfText_soItNeverCoversOutput() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            text.append("text line ").append(i).append('\n');
        }
        intent.output().setText(text.toString());
        assertTrue(intent.modeBar().isVisible());

        intent.southPane().setSize(600, 200);
        intent.southPane().doLayout();

        int barTop = intent.modeBar().getY();
        int outputBottom = intent.outputPane().getY() + intent.outputPane().getHeight();
        assertEquals(barTop, outputBottom,
                "the output must end exactly where the modeBar starts, so the bar never covers text");
    }

    @Test
    void streamRouting_splitsLlmToolAndSystemStatusIntoSeparateWidgets_andPreviewsOnlyLlm() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.setProgress(true);

        // 1. Stream initial LLM reasoning
        intent.appendRun(null, "Let me inspect the files first.\n");
        // 2. Stream tool call status updates and TodoWrite checklist
        intent.appendRun(null, "  ✅ Read C:/code/smile/README.md\n");
        intent.appendRun(null, "  ✅ Grep pattern in studio/src\n");
        intent.appendRun(null, "📝 Current Tasks:\n⏳ Task one\n🚧 Task two\n");
        // 3. Stream a system/compaction status notice
        intent.appendSystemStatus(null, "[Context compacted. Continuing the task.]");
        // 4. Stream final LLM Markdown summary with table
        intent.appendRun(null, "## Results\n\n| File | Matches |\n| :--- | ---: |\n| README.md | 3 |\n");
        intent.setProgress(false);

        var comps = intent.outputPane().getComponents();
        assertEquals(4, comps.length, "Expected 4 separate widgets: LLM -> TOOL -> STATUS -> LLM");
        var llm1 = assertInstanceOf(AgentOutputArea.class, comps[0]);
        var toolArea = assertInstanceOf(AgentOutputArea.class, comps[1]);
        var statusArea = assertInstanceOf(AgentOutputArea.class, comps[2]);
        var llm2 = assertInstanceOf(AgentOutputArea.class, comps[3]);

        assertEquals(AgentOutputArea.Kind.LLM, llm1.kind());
        assertEquals(AgentOutputArea.Kind.TOOL, toolArea.kind());
        assertEquals(AgentOutputArea.Kind.STATUS, statusArea.kind());
        assertEquals(AgentOutputArea.Kind.LLM, llm2.kind());

        assertTrue(toolArea.getText().contains("  ✅ Read C:/code/smile/README.md\n  ✅ Grep pattern in studio/src"));
        assertTrue(toolArea.getText().contains("📝 Current Tasks:\n⏳ Task one\n🚧 Task two"));
        assertEquals("[Context compacted. Continuing the task.]", statusArea.getText());

        // Switch to PREVIEW mode: only LLM widgets become Markdown; TOOL and STATUS remain AgentOutputArea
        intent.setMode(Intent.Mode.PREVIEW);
        assertEquals(Intent.Mode.PREVIEW, intent.getMode());

        var previewComps = intent.outputPane().getComponents();
        assertEquals(4, previewComps.length);
        assertInstanceOf(smile.studio.text.Markdown.class, previewComps[0], "First LLM segment must be rendered as Markdown");
        assertSame(toolArea, previewComps[1], "TOOL widget must remain AgentOutputArea in PREVIEW mode");
        assertSame(statusArea, previewComps[2], "STATUS widget must remain AgentOutputArea in PREVIEW mode");
        var md2 = assertInstanceOf(smile.studio.text.Markdown.class, previewComps[3], "Second LLM segment must be rendered as Markdown");
        assertEquals(1, md2.contentPane().getDocument().getElementsByTagName("table").getLength());

        // Switch back to RAW mode: both LLM widgets are restored in place
        intent.setMode(Intent.Mode.RAW);
        var rawComps = intent.outputPane().getComponents();
        assertEquals(4, rawComps.length);
        assertSame(llm1, rawComps[0]);
        assertSame(toolArea, rawComps[1]);
        assertSame(statusArea, rawComps[2]);
        assertSame(llm2, rawComps[3]);
    }

    @Test
    void fencedCodeBlock_containingToolLikeLines_staysInLlmSegment() {
        String raw = """
                Here is an example output:
                ```text
                  ✅ Read foo.txt
                📝 Current Tasks:
                ⏳ Do not split inside code fence
                [status inside fence]
                ```
                Done.
                """;
        var segments = AgentOutputArea.splitSegments(raw);
        assertEquals(1, segments.size(), "Tool-like lines inside a fenced code block must remain in the LLM segment");
        assertEquals(AgentOutputArea.Kind.LLM, segments.getFirst().kind());
    }

    @Test
    void subagentTab_getsItsOwnAutoScroller_soStreamedOutputStaysInView() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        assertNull(intent.runScroller(null), "the master run scrolls the conversation pane, not a tab");

        intent.beginRun("run-1", "Explore", "James");
        AutoScroller scroller = intent.runScroller("run-1");
        assertNotNull(scroller, "a subagent tab must get an auto-scroller for its own output");
        assertTrue(scroller.isFollowing(), "a fresh tab follows its output");

        intent.appendRun("run-1", "line one\n");
        assertTrue(scroller.isFollowing(),
                "streaming into a tab must keep its view following the newest line");
    }

    @Test
    void subagentTab_hasNoHorizontalScrollBarAndOpensTenRowsTall() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.beginRun("run-1", "Explore", "James");

        var scrollPane = intent.runScrollPane("run-1");
        assertNotNull(scrollPane, "the subagent tab must hold a scroll pane");
        var content = intent.runContentPane("run-1");
        assertNotNull(content);
        var area = (AgentOutputArea) content.getComponent(0);

        // Then a fresh tab reserves the configured rows, and the pane is sized to fit
        // them. Measured before any output: wrapped text would inflate the area's own
        // preferred height and obscure the reservation.
        assertEquals(Intent.RUN_VIEW_ROWS, area.getRows(),
                "the tab's output must reserve the configured number of rows");
        int lineHeight = area.getPreferredSize().height / Intent.RUN_VIEW_ROWS;
        assertEquals(Intent.RUN_VIEW_ROWS, (int) Math.round(
                        (double) scrollPane.getPreferredSize().height / lineHeight),
                "the pane's preferred height must work out to the configured number of rows");

        // Given long lines that would otherwise overflow sideways
        intent.appendRun("run-1", "x".repeat(400) + "\n" + "y".repeat(400) + "\n");

        // Then the pane never offers a horizontal scrollbar: the output wraps instead
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                scrollPane.getHorizontalScrollBarPolicy(),
                "a subagent tab must not show a horizontal scrollbar");
    }

    @Test
    void subagentTab_preferredHeightStaysAtTenRowsAsOutputStreams() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.beginRun("run-1", "Explore", "James");
        var scrollPane = intent.runScrollPane("run-1");
        var content = intent.runContentPane("run-1");
        assertNotNull(scrollPane);
        assertNotNull(content);
        var area = (AgentOutputArea) content.getComponent(0);

        int empty = scrollPane.getPreferredSize().height;

        intent.appendRun("run-1", "line\n".repeat(200));

        // The output itself grows without bound; the pane stays the height of the
        // rows it reserves, which is what puts the overflow behind the scrollbar.
        assertTrue(area.getPreferredSize().height > empty * 10,
                "the output must have grown well past the visible rows");
        assertEquals(empty, scrollPane.getPreferredSize().height,
                "the pane's preferred height must not grow with the streamed output");
    }

    @Test
    void subagentTab_contentTracksTheViewportWidthSoWrappingNeedsNoHorizontalScroll() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.beginRun("run-1", "Explore", "James");

        var content = intent.runContentPane("run-1");
        assertNotNull(content);
        assertInstanceOf(javax.swing.Scrollable.class, content,
                "the tab content must be Scrollable for the viewport to control its width");
        assertTrue(((javax.swing.Scrollable) content).getScrollableTracksViewportWidth(),
                "tracking the viewport width is what keeps an over-long line from widening "
                        + "the view and forcing a horizontal scrollbar");
    }

    @Test
    void addQuestion_afterLongSubagentRun_selectsMasterTabAndPacksQuestionToPreferredHeight() {
        Intent intent = new Intent(null);
        intent.setEditable(false);
        intent.appendRun(null, "Let me explore the codebase.\n");

        // Subagent runs and produces many lines, making JTabbedPane tall
        intent.beginRun("run-1", "Explore", "James");
        StringBuilder longSubagentOutput = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            longSubagentOutput.append("Subagent line ").append(i).append("\n");
        }
        intent.appendRun("run-1", longSubagentOutput.toString());
        intent.finishRun("run-1", "Finished");
        assertEquals(1, intent.runs().getSelectedIndex(), "subagent tab is selected before master acts");

        // Master agent asks a question immediately after subagent finishes
        var question = new ioa.llm.tool.Question(
                "Approach",
                "Which approach do you prefer?",
                List.of("Option A", "Option B", "Other"),
                false);
        intent.addQuestion(null, question);

        // Master tab should be selected so the user sees the question
        assertEquals(0, intent.runs().getSelectedIndex(), "adding a master question must select the master tab");

        // Lay out the master tab inside a tall container (simulating the height of the subagent tab)
        var masterPane = intent.runContentPane("");
        assertNotNull(masterPane);
        masterPane.setSize(600, 1200);
        masterPane.doLayout();

        // Find the Question GUI component and verify it is not stretched to fill the 1200px height
        javax.swing.JComponent questionGui = null;
        for (var comp : masterPane.getComponents()) {
            if (!(comp instanceof AgentOutputArea) && comp instanceof javax.swing.JComponent jc) {
                questionGui = jc;
                break;
            }
        }
        assertNotNull(questionGui, "Question GUI must be added to master content pane");
        int prefHeight = questionGui.getPreferredSize().height;
        assertEquals(prefHeight, questionGui.getMaximumSize().height,
                "Question GUI maximum height must match its preferred height");
        assertEquals(prefHeight, questionGui.getHeight(),
                "Question GUI height after layout must equal its preferred height, not stretch to fill the tall tab");
        assertTrue(questionGui.getHeight() < 300,
                "Question GUI must stay compact rather than stretching to the subagent tab height");
    }
}
