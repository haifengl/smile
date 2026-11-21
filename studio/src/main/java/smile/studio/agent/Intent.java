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

import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;
import com.formdev.flatlaf.util.SystemInfo;
import ioa.llm.client.LLM;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import ioa.llm.tool.Question;
import smile.plot.swing.Palette;
import smile.studio.LlmServices;
import smile.studio.SettingsDialog;
import smile.studio.SmileStudio;
import smile.studio.text.Markdown;
import smile.studio.text.Monospaced;
import smile.studio.text.OutputArea;
import smile.swing.ScrollablePanel;
import static smile.studio.agent.IntentType.*;

/**
 * An intent is a multiline text input field, and its contents can be executed
 * by a variety of engines including LLM agents.
 *
 * @author Haifeng Li
 */
public class Intent extends JPanel {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Intent.class.getName(), Locale.getDefault());
    private static final Color borderColor = Palette.web("#8dd4e8");
    /** Emoji prefix (U+1F4DC scroll) for status messages originating from agents. */
    static final String AGENT_STATUS_PREFIX = "\uD83D\uDCDC ";
    /** The scroll unit increment in pixels for the subagent output scrollbars. */
    private static final int SCROLL_UNIT_INCREMENT = 18;
    /**
     * The number of text rows a subagent output tab shows before its vertical
     * scrollbar takes over. This is the tab's preferred height, not a hard limit:
     * the GridBag wrapper lets the tab grow when the parent has room.
     */
    static final int RUN_VIEW_ROWS = 10;

    /**
     * Display mode for agent output.
     */
    public enum Mode {
        /** Raw Markdown text mode (default). */
        RAW,
        /** Rendered Markdown preview mode. */
        PREVIEW
    }
    // Input pane
    private Color inputPaneColor = UIManager.getColor("TextField.background");
    private final JPanel inputPane = new JPanel(new BorderLayout());
    private final JLabel indicator = new JLabel(">", SwingConstants.CENTER);
    private final IntentEditor editor = new IntentEditor(1, 80);
    // Footer for controls and status
    private final JPanel footer = new JPanel();
    // Left side for intent type, reasoning effort and status bar
    private final JPanel controlPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JComboBox<IntentType> intentTypeComboBox = new JComboBox<>(IntentType.values());
    private final JLabel modelLabel = new JLabel(bundle.getString("Model"));
    private final JComboBox<String> modelComboBox = new JComboBox<>();
    /** Null means auto/default; otherwise the explicit selection. */
    private LlmServices.AvailableModel selectedModel;
    private final Map<String, LlmServices.AvailableModel> modelByLabel = new LinkedHashMap<>();
    /** Flag to suppress ItemListener during programmatic combo updates. */
    private boolean updatingModelComboBox;
    private final JLabel reasoningLabel = new JLabel(bundle.getString("ReasoningEffort"));
    private final JComboBox<String> effortComboBox;
    private final JLabel status = new JLabel() {
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, super.getMinimumSize().height);
        }
    };
    // Right side for status and stop button.
    private final JPanel progressPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    private final JProgressBar progress = new JProgressBar();
    private final JButton stopButton = new JButton("❌");
    /** The action that interrupts the current turn, or null when idle. */
    private Callable<?> stopAction;
    // Right side for the queue state and controls while the request waits.
    private final JPanel queuePane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    private final JLabel queueBadge = new JLabel();
    private final JButton editQueuedButton = new JButton("✏️");
    private final JButton moveUpButton = new JButton("⬆️");
    private final JButton moveDownButton = new JButton("⬇️");
    private final JButton cancelQueuedButton = new JButton("❌");
    // Output pane. Subagent runs are tabs inside this pane.
    private final JLayeredPane southPane = new JLayeredPane() {
        @Override
        public void doLayout() {
            int w = getWidth();
            int h = getHeight();
            // The bottom toggle floats over the output, so the output must stop short of
            // it. Laying the output out at full height put the last two lines of text
            // directly under the button; reserving the bar's height carves out an empty
            // strip below the text for it to sit in.
            Dimension barPref = modeBar.isVisible() ? modeBar.getPreferredSize() : null;
            int reserved = barPref != null ? barPref.height : 0;
            outputPane.setBounds(0, 0, w, Math.max(0, h - reserved));
            if (barPref != null) {
                int scrollBarWidth = UIManager.getInt("ScrollBar.width");
                int rightMargin = (scrollBarWidth > 0 ? scrollBarWidth : 14) + 6;
                int x = Math.max(0, w - barPref.width - rightMargin);
                int bottomY = Math.max(0, h - barPref.height);
                modeBar.setBounds(x, bottomY, barPref.width, barPref.height);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            // Include the reserved strip so the enclosing layout gives the bar room
            // below the text instead of overlapping it.
            Dimension pref = outputPane.getPreferredSize();
            if (modeBar.isVisible()) {
                Dimension barPref = modeBar.getPreferredSize();
                return new Dimension(Math.max(pref.width, barPref.width), pref.height + barPref.height);
            }
            return pref;
        }
    };
    private final JPanel outputPane = new JPanel();
    private final JPanel topModeBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
    private final JToggleButton topModeButton = new JToggleButton(bundle.getString("PreviewMode"));
    private final JPanel modeBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
    private final JToggleButton modeButton = new JToggleButton(bundle.getString("PreviewMode"));
    private Mode mode = Mode.RAW;
    private Markdown markdownView;
    private final Map<Markdown, AgentOutputArea> previewedAreas = new LinkedHashMap<>();
    private AgentOutputArea output = createOutputArea(AgentOutputArea.Kind.LLM);
    private JTabbedPane runs;
    private final Map<String, JPanel> runPanes = new LinkedHashMap<>();
    private final Map<String, JPanel> runContentPanes = new LinkedHashMap<>();
    private final Map<String, AgentOutputArea> runOutputs = new LinkedHashMap<>();
    private final Map<String, StreamRouter> runRouters = new LinkedHashMap<>();
    /** Keeps each subagent tab's scroll pane pinned to its latest output while it streams. */
    private final Map<String, AutoScroller> runScrollers = new LinkedHashMap<>();
    /**
     * The conversation panel that hosts this intent, or null in tests and for
     * standalone intents. The master turn's output is scrolled by that panel, so
     * a question rendered in the master tab asks the panel to reveal it.
     */
    private final AgentPanel panel;
    private boolean selectMasterOnNextOutput;

    /**
     * Stateful per-run line router that routes streamed chunks to
     * {@link AgentOutputArea.Kind#LLM}, {@link AgentOutputArea.Kind#TOOL},
     * or {@link AgentOutputArea.Kind#STATUS} while respecting fenced code blocks.
     */
    private final class StreamRouter {
        private final String runId;
        private final StringBuilder lineBuffer = new StringBuilder();
        private boolean holdingLine;
        private boolean inFence;
        private String fenceMarker;
        private boolean inTodoBlock;

        StreamRouter(String runId) {
            this.runId = runId;
        }

        void accept(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }
            int i = 0;
            int len = chunk.length();
            while (i < len) {
                int nl = chunk.indexOf('\n', i);
                if (nl < 0) {
                    String tail = chunk.substring(i);
                    if (lineBuffer.isEmpty()) {
                        lineBuffer.append(tail);
                        if (mayBeNonLlmLine(lineBuffer.toString())) {
                            holdingLine = true;
                        } else {
                            holdingLine = false;
                            appendToKind(runId, AgentOutputArea.Kind.LLM, tail);
                        }
                    } else if (holdingLine) {
                        lineBuffer.append(tail);
                        if (!mayBeNonLlmLine(lineBuffer.toString())) {
                            holdingLine = false;
                            appendToKind(runId, AgentOutputArea.Kind.LLM, lineBuffer.toString());
                        }
                    } else {
                        lineBuffer.append(tail);
                        appendToKind(runId, AgentOutputArea.Kind.LLM, tail);
                    }
                    return;
                }

                String restOfLine = chunk.substring(i, nl + 1);
                boolean startedEmpty = lineBuffer.isEmpty();
                lineBuffer.append(restOfLine);
                String fullLine = lineBuffer.toString();
                lineBuffer.setLength(0);

                String content = fullLine;
                if (content.endsWith("\n")) {
                    content = content.substring(0, content.length() - 1);
                }
                if (content.endsWith("\r")) {
                    content = content.substring(0, content.length() - 1);
                }

                updateFenceState(content);

                AgentOutputArea.Kind kind;
                if (inFence) {
                    kind = AgentOutputArea.Kind.LLM;
                    inTodoBlock = false;
                } else if (content.isBlank()) {
                    inTodoBlock = false;
                    kind = null; // keep blank line on whichever widget is active (or LLM)
                } else {
                    kind = AgentOutputArea.classifyLine(content, inTodoBlock);
                    if (content.startsWith(AgentOutputArea.TODO_HEADER_PREFIX)
                            || (inTodoBlock && AgentOutputArea.isTodoItemLine(content))) {
                        inTodoBlock = true;
                    } else {
                        inTodoBlock = false;
                    }
                }

                if (startedEmpty || holdingLine) {
                    holdingLine = false;
                    if (kind == null) {
                        AgentOutputArea current = currentAreaFor(runId);
                        if (current != null && current.kind() == AgentOutputArea.Kind.LLM
                                && !current.getText().isEmpty()) {
                            appendToKind(runId, AgentOutputArea.Kind.LLM, fullLine);
                        }
                    } else if (kind == AgentOutputArea.Kind.LLM) {
                        appendToKind(runId, AgentOutputArea.Kind.LLM, fullLine);
                    } else {
                        appendLineToKind(runId, kind, content);
                    }
                } else {
                    // The prefix of this line was already streamed to the LLM widget, so
                    // the model stopped mid-line. Models are free to end a sentence
                    // without a newline, and a tool or status update can then follow in
                    // the next chunk; appending that remainder to the LLM widget would
                    // render the two as one run-on line. Route the remainder by its own
                    // kind instead, closing the model's line first only if it is still
                    // open -- so back-to-back tool calls do not pick up blank lines.
                    // A fenced code block is the exception: a line that merely looks like
                    // a tool call inside a fence is code, so it stays with the model text.
                    String tail = stripLineEnding(restOfLine);
                    AgentOutputArea.Kind tailKind = inFence || tail.isBlank()
                            ? AgentOutputArea.Kind.LLM
                            : AgentOutputArea.classifyLine(tail, inTodoBlock);
                    if (tailKind == AgentOutputArea.Kind.LLM) {
                        appendToKind(runId, AgentOutputArea.Kind.LLM, restOfLine);
                    } else {
                        appendLineToKind(runId, tailKind, tail);
                    }
                }

                i = nl + 1;
            }
        }

        /** Removes a trailing {@code \n} or {@code \r\n} from a complete line. */
        private static String stripLineEnding(String line) {
            String s = line;
            if (s.endsWith("\n")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith("\r")) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        }

        private boolean mayBeNonLlmLine(String prefix) {
            if (inFence || prefix.isEmpty()) {
                return false;
            }
            if ("  ✅ ".startsWith(prefix) || prefix.startsWith("  ✅ ")
                    || "  ❌ ".startsWith(prefix) || prefix.startsWith("  ❌ ")
                    || AgentOutputArea.TODO_HEADER_PREFIX.startsWith(prefix)
                    || prefix.startsWith(AgentOutputArea.TODO_HEADER_PREFIX)
                    || "⏳ ".startsWith(prefix) || prefix.startsWith("⏳ ")
                    || "🚧 ".startsWith(prefix) || prefix.startsWith("🚧 ")
                    || "✅ ".startsWith(prefix) || prefix.startsWith("✅ ")
                    || "❓ ".startsWith(prefix) || prefix.startsWith("❓ ")) {
                return true;
            }
            String stripped = prefix.stripLeading();
            return stripped.isEmpty() || stripped.startsWith("[");
        }

        private void updateFenceState(String content) {
            String stripped = content.stripLeading();
            int leading = content.length() - stripped.length();
            if (leading <= 3 && (stripped.startsWith("```") || stripped.startsWith("~~~"))) {
                char fc = stripped.charAt(0);
                int count = 0;
                while (count < stripped.length() && stripped.charAt(count) == fc) {
                    count++;
                }
                String marker = stripped.substring(0, count);
                if (!inFence) {
                    inFence = true;
                    fenceMarker = marker;
                } else if (stripped.startsWith(fenceMarker)
                        && stripped.substring(fenceMarker.length()).isBlank()) {
                    inFence = false;
                    fenceMarker = null;
                }
            }
        }

        void flush() {
            if (lineBuffer.isEmpty()) {
                return;
            }
            if (holdingLine) {
                holdingLine = false;
                String pending = lineBuffer.toString();
                lineBuffer.setLength(0);
                String content = pending;
                if (content.endsWith("\r")) {
                    content = content.substring(0, content.length() - 1);
                }
                AgentOutputArea.Kind kind = inFence
                        ? AgentOutputArea.Kind.LLM
                        : AgentOutputArea.classifyLine(content, inTodoBlock);
                if (kind == AgentOutputArea.Kind.LLM) {
                    appendToKind(runId, AgentOutputArea.Kind.LLM, pending);
                } else {
                    appendLineToKind(runId, kind, content);
                }
            } else {
                lineBuffer.setLength(0);
            }
        }
    }

    /**
     * Constructor.
     * @param cli the parent component.
     */
    public Intent(AgentPanel cli) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));
        this.panel = cli;

        effortComboBox = initEffortComboBox(cli);
        if (cli != null) {
            effortComboBox.setSelectedItem(cli.getReasoningEffort());
        }
        initModelComboBox(cli);
        initInputPane();
        initModeBar();
        if (cli != null) {
            initActionMap(cli);
        }

        outputPane.setLayout(new BoxLayout(outputPane, BoxLayout.Y_AXIS));
        outputPane.add(output);

        southPane.setOpaque(false);
        southPane.add(outputPane, JLayeredPane.DEFAULT_LAYER);
        southPane.add(modeBar, JLayeredPane.PALETTE_LAYER);

        add(inputPane, BorderLayout.CENTER);
        add(southPane, BorderLayout.SOUTH);
        if (cli != null && cli.hintWindow() != null) {
            cli.hintWindow().addEditor(editor);
        }

        // Listen for global Look and Feel changes
        UIManager.addPropertyChangeListener(evt -> {
            if ("lookAndFeel".equals(evt.getPropertyName())) {
                inputPaneColor = UIManager.getColor("TextField.background");
                if (editor.isEditable()) {
                    editor.setBackground(inputPaneColor);
                    controlPane.setBackground(inputPaneColor);
                    intentTypeComboBox.setBackground(inputPaneColor);
                    effortComboBox.setBackground(inputPaneColor);
                    modelComboBox.setBackground(inputPaneColor);
                    inputPane.setBackground(inputPaneColor);
                    inputPane.setBorder(createRoundBorder());
                }
            }
        });
    }

    /** Initializes the input pane. */
    private void initInputPane() {
        indicator.setFont(Monospaced.getFont());
        indicator.setToolTipText(Instructions.toString());

        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setOpaque(false);
        sidebar.add(Box.createVerticalStrut(3));
        sidebar.add(indicator);
        sidebar.add(Box.createVerticalGlue());

        editor.setFont(Monospaced.getFont());
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.setOpaque(false);
        editor.setHighlightCurrentLine(false);
        editor.setBackground(inputPaneColor);

        status.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        status.setHorizontalAlignment(SwingConstants.LEFT);
        stopButton.setVisible(false);
        stopButton.setToolTipText(bundle.getString("Stop"));
        stopButton.addActionListener(e -> onStopClicked());
        progress.putClientProperty("JProgressBar.largeHeight", true);
        progressPane.setOpaque(false);
        progressPane.add(progress);
        progressPane.add(Box.createHorizontalStrut(10));
        progressPane.add(stopButton);

        initQueuePane();
        initIntentTypeComboBox();
        footer.setLayout(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(0, indicator.getPreferredSize().width, 0, 0));

        controlPane.setBackground(inputPaneColor);
        controlPane.add(intentTypeComboBox);
        controlPane.add(Box.createHorizontalStrut(12));
        controlPane.add(modelLabel);
        controlPane.add(modelComboBox);
        controlPane.add(Box.createHorizontalStrut(12));
        controlPane.add(reasoningLabel);
        controlPane.add(effortComboBox);
        footer.add(controlPane, BorderLayout.WEST);
        footer.add(status, BorderLayout.CENTER);

        inputPane.setBackground(inputPaneColor);
        inputPane.setBorder(createRoundBorder());
        inputPane.add(sidebar, BorderLayout.WEST);
        inputPane.add(editor, BorderLayout.CENTER);
        inputPane.add(footer, BorderLayout.SOUTH);
    }

    /**
     * Initializes the bottom-right and input-footer toggle buttons for
     * switching between plain text and rendered Markdown view of agent output.
     */
    private void initModeBar() {
        configureModeBar(modeBar, modeButton);
        configureModeBar(topModeBar, topModeButton);
    }

    private void configureModeBar(JPanel bar, JToggleButton button) {
        button.setToolTipText(bundle.getString("PreviewModeTooltip"));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        button.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");
        button.setMargin(new Insets(1, 8, 1, 8));
        button.setFocusable(false);
        button.setSelected(false);
        button.addActionListener(e -> setMode(mode == Mode.RAW ? Mode.PREVIEW : Mode.RAW));

        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(2, 6, 2, 6));
        bar.add(button);
        bar.setVisible(false);
    }

    private void syncModeButton() {
        boolean preview = mode == Mode.PREVIEW;
        String text = bundle.getString(preview ? "RawMode" : "PreviewMode");
        String tooltip = bundle.getString(preview ? "RawModeTooltip" : "PreviewModeTooltip");
        for (JToggleButton button : new JToggleButton[] { modeButton, topModeButton }) {
            button.setSelected(preview);
            button.setText(text);
            button.setToolTipText(tooltip);
        }
    }

    /**
     * Initializes the queue pane shown in the footer while this request waits for its
     * turn. It is mutually exclusive with the progress pane: a waiting request is not
     * running, so it shows a queue badge and controls instead of the progress bar.
     */
    private void initQueuePane() {
        queuePane.setOpaque(false);
        queuePane.setVisible(false);
        queueBadge.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));

        for (JButton button : new JButton[] {
                editQueuedButton, moveUpButton, moveDownButton, cancelQueuedButton }) {
            button.setMargin(new Insets(0, 4, 0, 4));
            button.setFocusable(false);
            button.putClientProperty("JButton.buttonType", "toolBarButton");
        }
        editQueuedButton.setToolTipText(bundle.getString("EditQueued"));
        moveUpButton.setToolTipText(bundle.getString("MoveQueuedUp"));
        moveDownButton.setToolTipText(bundle.getString("MoveQueuedDown"));
        cancelQueuedButton.setToolTipText(bundle.getString("CancelQueued"));

        queuePane.add(queueBadge);
        queuePane.add(editQueuedButton);
        queuePane.add(moveUpButton);
        queuePane.add(moveDownButton);
        queuePane.add(cancelQueuedButton);
    }

    /**
     * Marks this intent as waiting in the queue and shows its controls.
     * <p>Shows the 1-based position and the queue depth in the footer, and hides the
     * progress pane. The caller wires the four controls after this call.
     * @param position the 1-based position in the waiting list.
     * @param size the number of waiting items.
     */
    public void showQueued(int position, int size) {
        queueBadge.setText(MessageFormat.format(bundle.getString("QueuedPosition"), position, size));
        // A waiting request has not started, so its status must not read "Thinking..."
        // (which run() sets at submit time). It becomes "Thinking..." again on STARTED.
        status.setText(bundle.getString("Queued"));
        status.setToolTipText(null);
        queuePane.setVisible(true);
        // A waiting request is not running: stop the progress animation and drop the pane.
        progress.setIndeterminate(false);
        progress.setEnabled(false);
        if (progressPane.getParent() == footer) {
            footer.remove(progressPane);
        }
        if (topModeBar.getParent() == footer) {
            footer.remove(topModeBar);
        }
        topModeBar.setVisible(false);
        if (queuePane.getParent() != footer) {
            footer.add(queuePane, BorderLayout.EAST);
        }
        updateModeBarVisibility();
        footer.revalidate();
        footer.repaint();
    }

    /**
     * Removes the queue badge and controls, returning the footer to its idle look.
     */
    public void clearQueued() {
        queuePane.setVisible(false);
        if (queuePane.getParent() == footer) {
            footer.remove(queuePane);
        }
        updateModeBarVisibility();
        footer.revalidate();
        footer.repaint();
    }

    /**
     * Wires the queue controls. A null action hides that control, so a peer request can
     * be cancel/reorder-only while a local prompt also offers edit.
     * @param cancel the cancel action, or null to hide it.
     * @param edit the edit action, or null to hide it.
     * @param moveUp the move-up action, or null to hide it.
     * @param moveDown the move-down action, or null to hide it.
     */
    public void setQueueControls(Runnable cancel, Runnable edit, Runnable moveUp, Runnable moveDown) {
        wire(editQueuedButton, edit);
        wire(moveUpButton, moveUp);
        wire(moveDownButton, moveDown);
        wire(cancelQueuedButton, cancel);
    }

    /**
     * Enables or disables the reorder controls based on the item's position.
     * @param canMoveUp true when the item is not already first.
     * @param canMoveDown true when the item is not already last.
     */
    public void setQueueControlsEnabled(boolean canMoveUp, boolean canMoveDown) {
        moveUpButton.setEnabled(canMoveUp);
        moveDownButton.setEnabled(canMoveDown);
    }

    /** Replaces a button's action, or hides the button when the action is null. */
    private static void wire(JButton button, Runnable action) {
        for (var listener : button.getActionListeners()) {
            button.removeActionListener(listener);
        }
        button.setVisible(action != null);
        if (action != null) {
            button.addActionListener(e -> action.run());
        }
    }

    /** Initializes the reasoning effort combo box. */
    private JComboBox<String> initEffortComboBox(AgentPanel cli) {
        var effortComboBox = new JComboBox<String>();
        effortComboBox.setBorder(BorderFactory.createEmptyBorder());
        effortComboBox.setBackground(inputPaneColor);
        if (effortComboBox.getComponentCount() > 0 &&
            effortComboBox.getComponent(0) instanceof AbstractButton button) {
            button.setVisible(false);
        }

        effortComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && cli != null) {
                cli.setReasoningEffort((String) effortComboBox.getSelectedItem());
            }
        });
        return effortComboBox;
    }

    /** Initializes the model combo box to the left of reasoning effort. */
    private void initModelComboBox(AgentPanel cli) {
        modelComboBox.setBorder(BorderFactory.createEmptyBorder());
        modelComboBox.setBackground(inputPaneColor);
        if (modelComboBox.getComponentCount() > 0 &&
            modelComboBox.getComponent(0) instanceof AbstractButton button) {
            button.setVisible(false);
        }
        modelComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (!(c instanceof JLabel label) || value == null) {
                    return c;
                }
                label.setText(modelItemText(value.toString(), index >= 0));
                return label;
            }
        });
        modelComboBox.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED || updatingModelComboBox) {
                return;
            }
            String label = (String) modelComboBox.getSelectedItem();
            if (label == null || bundle.getString("AutoModel").equals(label)) {
                selectedModel = null;
                SmileStudio.llmServices().defaultModel(null);
            } else {
                selectedModel = modelByLabel.get(label);
                SmileStudio.llmServices().defaultModel(selectedModel);
            }
            if (cli != null) {
                refillEffortLevels(cli.getReasoningEffort());
            }
            modelComboBox.repaint();
        });
        refreshModels();
    }

    /**
     * Renders a model combo item. Selected rows get a check mark; the
     * {@code default} row also shows the resolved model id so the user can see
     * which model auto-selection uses.
     */
    private String modelItemText(String item, boolean inPopup) {
        String auto = bundle.getString("AutoModel");
        boolean isAuto = auto.equals(item);
        boolean checked = isAuto ? selectedModel == null
                : selectedModel != null && selectedModel.equals(modelByLabel.get(item));

        String display = item;
        if (isAuto) {
            var resolved = SmileStudio.llmServices().defaultModel();
            if (resolved != null) {
                boolean qualify = modelByLabel.values().stream()
                        .map(m -> m.model().id()).distinct().count() < modelByLabel.size();
                display = auto + " · " + resolved.displayLabel(qualify);
            }
        }

        // Closed combo: show resolved id on default, no check mark.
        // Popup list: check mark on the active selection.
        if (!inPopup) {
            return isAuto ? display : item;
        }
        return (checked ? "✓ " : "   ") + display;
    }

    /**
     * Rebuilds the model list from {@link SmileStudio#llmServices()}.
     * Keeps the current selection when it is still available.
     */
    public void refreshModels() {
        updatingModelComboBox = true;
        try {
            String previous = selectedModel == null
                    ? null
                    : selectedModel.model().id();
            String previousService = selectedModel == null ? null : selectedModel.serviceKey();

            modelComboBox.removeAllItems();
            modelByLabel.clear();
            modelComboBox.addItem(bundle.getString("AutoModel"));

            var available = SmileStudio.llmServices().availableModels();
            boolean qualify = available.stream().map(m -> m.model().id()).distinct().count()
                    < available.size();
            for (var entry : available) {
                String label = entry.displayLabel(qualify);
                modelByLabel.put(label, entry);
                modelComboBox.addItem(label);
            }

            String restore = bundle.getString("AutoModel");
            if (previousService != null && previous != null) {
                for (var e : modelByLabel.entrySet()) {
                    if (e.getValue().serviceKey().equals(previousService)
                            && e.getValue().model().id().equals(previous)) {
                        restore = e.getKey();
                        break;
                    }
                }
            } else {
                String defaultModelPref = SmileStudio.preferences().get(SettingsDialog.DEFAULT_MODEL_KEY, "").trim();
                if (!defaultModelPref.isEmpty()) {
                    var def = SmileStudio.llmServices().defaultModel();
                    if (def != null) {
                        for (var e : modelByLabel.entrySet()) {
                            if (e.getValue().equals(def)) {
                                restore = e.getKey();
                                break;
                            }
                        }
                    }
                }
            }
            modelComboBox.setSelectedItem(restore);
            if (bundle.getString("AutoModel").equals(restore)) {
                selectedModel = null;
            } else {
                selectedModel = modelByLabel.get(restore);
            }
            refillEffortLevels(null);
        } finally {
            updatingModelComboBox = false;
        }
    }

    /**
     * Refills reasoning effort from the resolved model's catalog entry.
     * Always includes {@link LLM#DEFAULT_REASONING_EFFORT} first.
     */
    private void refillEffortLevels(String prefer) {
        String keep = prefer != null ? prefer : (String) effortComboBox.getSelectedItem();
        effortComboBox.removeAllItems();
        effortComboBox.addItem(LLM.DEFAULT_REASONING_EFFORT);
        var model = resolveModel();
        if (model != null) {
            for (String level : model.model().reasoningEffortLevels()) {
                effortComboBox.addItem(level);
            }
        }
        if (keep != null) {
            for (int i = 0; i < effortComboBox.getItemCount(); i++) {
                if (keep.equals(effortComboBox.getItemAt(i))) {
                    effortComboBox.setSelectedIndex(i);
                    return;
                }
            }
        }
        effortComboBox.setSelectedItem(LLM.DEFAULT_REASONING_EFFORT);
    }

    /**
     * Returns the model for this prompt: the explicit selection, or the
     * services default when the combo is on {@code default}.
     * @return the available model, or null when none are configured.
     */
    public LlmServices.AvailableModel resolveModel() {
        if (selectedModel != null) {
            return selectedModel;
        }
        return SmileStudio.llmServices().defaultModel();
    }

    /** Initializes the intent type combo box. */
    private void initIntentTypeComboBox() {
        intentTypeComboBox.setSelectedItem(Instructions);
        intentTypeComboBox.setBorder(BorderFactory.createEmptyBorder());
        intentTypeComboBox.setBackground(inputPaneColor);
        if (intentTypeComboBox.getComponentCount() > 0 &&
            intentTypeComboBox.getComponent(0) instanceof AbstractButton button) {
            button.setVisible(false);
        }

        intentTypeComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                var intentType = (IntentType) e.getItem();
                indicator.setText(intentType.legend());
                indicator.setToolTipText(intentType.toString());
                editor.requestFocusInWindow();

                if (effortComboBox != null) {
                    boolean show = intentType == Instructions || intentType == Command;
                    modelLabel.setVisible(show);
                    modelComboBox.setVisible(show);
                    reasoningLabel.setVisible(show);
                    effortComboBox.setVisible(show);
                }

                switch (intentType) {
                    case Shell -> {
                        if (SystemInfo.isWindows) {
                            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_POWERSHELL);
                        } else {
                            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
                        }
                    }
                    case Markdown ->
                        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
                    default ->
                        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
                }
            }
        });
    }

    private void initActionMap(AgentPanel cli) {
        InputMap inputMap = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = editor.getActionMap();
        // Map Shift+Enter to the default newline action (insert-break)
        inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), DefaultEditorKit.insertBreakAction);

        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "run");
        actionMap.put("run", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!editor.isEditable()) return;
                if (editor.getText().isBlank()) return;

                try {
                    char ch = editor.getText(0, 1).charAt(0);
                    switch (editor.getText(0, 1).charAt(0)) {
                        case '/' -> {
                            intentTypeComboBox.setSelectedItem(Command);
                            editor.replaceRange("", 0, 1);
                        }
                        case '!' -> {
                            intentTypeComboBox.setSelectedItem(Shell);
                            editor.replaceRange("", 0, 1);
                        }
                        case '#' -> intentTypeComboBox.setSelectedItem(Markdown);
                    }
                } catch (BadLocationException ex) {
                    // ignore the exception
                }

                setEditable(false);
                var intentType = (IntentType) intentTypeComboBox.getSelectedItem();
                if (intentType != null) {
                    switch (intentType) {
                        case Raw -> {} // do nothing
                        case Markdown -> renderMarkdown(editor.getText());
                        default -> cli.run(Intent.this, intentType);
                    }
                }

                // Append a new intent box for the next instructions
                cli.addIntent();
            }
        });
    }

    /**
     * Sets the stop action for the intent and arms the stop button for a new turn.
     * <p>The action is stored rather than wired to a fresh listener each turn, so
     * listeners do not accumulate and the button is re-enabled after a previous
     * cancel disabled it.
     * @param stop the lambda to stop execution.
     */
    public <T> void setStopAction(Callable<T> stop) {
        stopAction = stop;
        stopButton.setEnabled(true);
        stopButton.setVisible(true);
        stopButton.setText("❌");
        stopButton.setToolTipText(bundle.getString("Stop"));
        progressPane.revalidate();
        footer.revalidate();
        footer.repaint();
    }

    /**
     * Handles a click on the stop button. Gives immediate feedback by freezing
     * the progress bar and disabling the button before the stop action runs,
     * since the underlying cancellation is cooperative and may take a while.
     * <p>Package-private for testing.
     */
    void onStopClicked() {
        Callable<?> action = stopAction;
        // A disabled button means a cancel is already in flight; ignore the click.
        if (action == null || !stopButton.isEnabled()) {
            return;
        }
        // Immediate feedback: the progress bar stops animating and the button
        // is disabled so a second click cannot queue another cancel.
        progress.setIndeterminate(false);
        progress.setEnabled(false);
        stopButton.setEnabled(false);
        stopButton.setText("…");
        stopButton.setToolTipText(bundle.getString("Cancelling"));
        setStatus(bundle.getString("Cancelling"));
        try {
            action.call();
        } catch (Exception ex) {
            // The cancel could not be delivered; restore the button so the user
            // can retry instead of being left with a dead control.
            stopButton.setEnabled(true);
            stopButton.setText("❌");
            stopButton.setToolTipText(bundle.getString("Stop"));
            JOptionPane.showMessageDialog(
                    Intent.this,
                    ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /** Creates an output area of the specified kind. */
    private AgentOutputArea createOutputArea(AgentOutputArea.Kind kind) {
        AgentOutputArea area = new AgentOutputArea(kind);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateModeBarVisibility();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateModeBarVisibility();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        return area;
    }

    /** Returns the vertical BoxLayout container holding output widgets for the given run. */
    private JPanel containerFor(String runId) {
        if (runs == null) {
            return outputPane;
        }
        String key = runId == null ? "" : runId;
        JPanel content = runContentPanes.get(key);
        return content != null ? content : runContentPanes.get("");
    }

    /** Returns the active trailing output area for the given run. */
    private AgentOutputArea currentAreaFor(String runId) {
        if (runId == null || runId.isEmpty() || runs == null) {
            return output;
        }
        AgentOutputArea area = runOutputs.get(runId);
        return area != null ? area : output;
    }

    /** Sets the active trailing output area for the given run. */
    private void setCurrentAreaFor(String runId, AgentOutputArea area) {
        if (runId == null || runId.isEmpty()) {
            output = area;
            if (runs != null) {
                runOutputs.put("", area);
            }
        } else {
            runOutputs.put(runId, area);
        }
    }

    /** Returns or creates the trailing AgentOutputArea of the requested Kind in the run container. */
    private AgentOutputArea ensureAreaForKind(String runId, AgentOutputArea.Kind kind) {
        JPanel container = containerFor(runId);
        AgentOutputArea current = currentAreaFor(runId);
        if (current != null && current.getParent() == container) {
            if (current.kind() == kind) {
                return current;
            }
            if (current.getText().isEmpty()) {
                int idx = componentIndex(container, current);
                AgentOutputArea replacement = createOutputArea(kind);
                if (idx >= 0) {
                    container.remove(idx);
                    container.add(replacement, idx);
                } else {
                    container.add(replacement);
                }
                setCurrentAreaFor(runId, replacement);
                container.revalidate();
                return replacement;
            }
        }
        AgentOutputArea next = createOutputArea(kind);
        if (container != null) {
            container.add(next);
            container.revalidate();
        }
        setCurrentAreaFor(runId, next);
        return next;
    }

    private static int componentIndex(Container container, Component target) {
        Component[] comps = container.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private void appendToKind(String runId, AgentOutputArea.Kind kind, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        AgentOutputArea area = ensureAreaForKind(runId, kind);
        // Strip leading blank lines when starting a fresh LLM widget right after a TOOL/STATUS widget
        if (kind == AgentOutputArea.Kind.LLM && area.getText().isEmpty() && area.getParent() != null
                && area.getParent().getComponentCount() > 1) {
            int start = 0;
            while (start < text.length() && (text.charAt(start) == '\n' || text.charAt(start) == '\r')) {
                start++;
            }
            if (start == text.length()) {
                return;
            }
            text = text.substring(start);
        }
        area.append(text);
        follow(runId);
    }

    private void appendLineToKind(String runId, AgentOutputArea.Kind kind, String lineContent) {
        if (lineContent == null || lineContent.isBlank()) {
            return;
        }
        AgentOutputArea area = ensureAreaForKind(runId, kind);
        if (!area.getText().isEmpty()) {
            area.append("\n" + lineContent);
        } else {
            area.append(lineContent);
        }
        follow(runId);
    }

    /**
     * Pins a subagent tab to its latest output. The master turn scrolls the
     * conversation scroll pane in {@link AgentPanel}, but a subagent's output
     * lives in its own tab scroll pane, which nothing else moves. A no-op for
     * the master run, and while the user has scrolled up in that tab.
     *
     * @param runId {@code null} for the parent turn, or the subagent run id.
     */
    private void follow(String runId) {
        if (runId == null || runId.isEmpty()) {
            return;
        }
        AutoScroller scroller = runScrollers.get(runId);
        if (scroller != null) {
            scroller.scrollToBottom();
        }
    }

    /**
     * Appends model-generated Markdown text to an {@link AgentOutputArea.Kind#LLM}
     * widget for the specified run.
     *
     * @param runId {@code null} for the parent turn, or the subagent run id.
     * @param text the Markdown text to append.
     */
    public void appendLlmOutput(String runId, String text) {
        appendToKind(runId, AgentOutputArea.Kind.LLM, text);
    }

    /**
     * Appends tool-calling status output (e.g. {@code ✅ Read ...} or {@code TodoWrite}
     * checklist lines) to an {@link AgentOutputArea.Kind#TOOL} widget for the specified run.
     *
     * @param runId {@code null} for the parent turn, or the subagent run id.
     * @param text the tool status text to append.
     */
    public void appendToolStatus(String runId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String trimmed = AgentOutputArea.trimSurroundingBlankLines(text);
        if (!trimmed.isBlank()) {
            appendLineToKind(runId, AgentOutputArea.Kind.TOOL, trimmed);
        }
    }

    /**
     * Appends a subagent or system status notice (e.g. {@code [Searching...]} or
     * {@code [Context compacted...]}) to an {@link AgentOutputArea.Kind#STATUS}
     * widget for the specified run.
     *
     * @param runId {@code null} for the parent turn, or the subagent run id.
     * @param text the status notice to append.
     */
    public void appendSystemStatus(String runId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String trimmed = AgentOutputArea.trimSurroundingBlankLines(text);
        if (!trimmed.isBlank()) {
            appendLineToKind(runId, AgentOutputArea.Kind.STATUS, trimmed);
        }
    }

    /**
     * Returns the current display mode of the agent output.
     * @return the current {@link Mode}.
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Returns the currently rendered {@link Markdown} preview component, or {@code null}
     * if preview has not been rendered yet.
     * @return the {@link Markdown} preview component.
     */
    public Markdown markdownView() {
        return markdownView;
    }

    /**
     * Switches the agent output between {@link Mode#RAW} and {@link Mode#PREVIEW}.
     * Only {@link AgentOutputArea.Kind#LLM} widgets are rendered as HTML {@link Markdown}
     * in preview mode; {@link AgentOutputArea.Kind#TOOL} and {@link AgentOutputArea.Kind#STATUS}
     * widgets stay in place as raw text widgets so tool call lines never collapse into a
     * single paragraph.
     *
     * @param newMode the target mode.
     */
    public void setMode(Mode newMode) {
        if (newMode == null
                || editor.isEditable()
                || getIntentType() == IntentType.Markdown
                || getIntentType() == IntentType.Raw
                || !hasOutputText()) {
            newMode = Mode.RAW;
        }
        if (newMode == Mode.PREVIEW) {
            flushAllRouters();
            restoreRawMode();
            if (renderPreviewMode()) {
                this.mode = Mode.PREVIEW;
            } else {
                restoreRawMode();
                this.mode = Mode.RAW;
            }
        } else {
            restoreRawMode();
            this.mode = Mode.RAW;
        }
        syncModeButton();
        outputPane.revalidate();
        outputPane.repaint();
        southPane.revalidate();
        southPane.repaint();
        revalidate();
        repaint();
    }

    private boolean renderPreviewMode() {
        markdownView = null;
        boolean renderedAny = false;
        if (runs == null) {
            splitMixedLlmAreas(outputPane, null);
            Boolean ok = previewContainer(outputPane);
            if (ok == null) {
                return false;
            }
            renderedAny = ok;
        } else {
            for (var entry : runContentPanes.entrySet()) {
                String runId = entry.getKey();
                JPanel container = entry.getValue();
                if (container != null) {
                    splitMixedLlmAreas(container, runId.isEmpty() ? null : runId);
                    Boolean ok = previewContainer(container);
                    if (ok == null) {
                        return false;
                    }
                    renderedAny |= ok;
                }
            }
        }
        return renderedAny;
    }

    /**
     * If any {@link AgentOutputArea.Kind#LLM} widget had mixed LLM/tool/status text
     * assigned directly (e.g. via {@code output().setText(...)}), splits it into
     * typed {@link AgentOutputArea} widgets in place before rendering preview.
     */
    private void splitMixedLlmAreas(JPanel container, String runId) {
        Component[] comps = container.getComponents();
        boolean changed = false;
        for (Component comp : comps) {
            if (comp instanceof AgentOutputArea area
                    && area.kind() == AgentOutputArea.Kind.LLM
                    && !area.getText().isBlank()) {
                var segments = AgentOutputArea.splitSegments(area.getText());
                boolean hasNonLlm = segments.stream().anyMatch(s -> s.kind() != AgentOutputArea.Kind.LLM);
                if (hasNonLlm && segments.size() > 1) {
                    int idx = componentIndex(container, area);
                    if (idx >= 0) {
                        container.remove(idx);
                        AgentOutputArea lastCreated = null;
                        for (int s = 0; s < segments.size(); s++) {
                            var seg = segments.get(s);
                            AgentOutputArea segArea = createOutputArea(seg.kind());
                            segArea.setText(seg.text());
                            container.add(segArea, idx + s);
                            lastCreated = segArea;
                        }
                        if (area == currentAreaFor(runId) && lastCreated != null) {
                            setCurrentAreaFor(runId, lastCreated);
                        }
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            container.revalidate();
        }
    }

    /**
     * Replaces {@link AgentOutputArea.Kind#LLM} widgets in {@code container} with
     * {@link Markdown} views while leaving {@code TOOL} and {@code STATUS} widgets
     * untouched. Returns {@code null} if XML parsing fails on any LLM block.
     */
    private Boolean previewContainer(JPanel container) {
        boolean renderedAny = false;
        Component[] comps = container.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] instanceof AgentOutputArea area
                    && area.kind() == AgentOutputArea.Kind.LLM
                    && !area.getText().isBlank()) {
                Markdown md = area.createPreview(() -> setMode(Mode.RAW));
                if (md.contentPane() == null) {
                    return null;
                }
                container.remove(i);
                container.add(md, i);
                previewedAreas.put(md, area);
                if (markdownView == null) {
                    markdownView = md;
                }
                renderedAny = true;
            }
        }
        return renderedAny;
    }

    private void restoreRawMode() {
        for (var entry : previewedAreas.entrySet()) {
            Markdown md = entry.getKey();
            AgentOutputArea area = entry.getValue();
            Container parent = md.getParent();
            if (parent != null) {
                Component[] comps = parent.getComponents();
                for (int i = 0; i < comps.length; i++) {
                    if (comps[i] == md) {
                        parent.remove(i);
                        parent.add(area, i);
                        break;
                    }
                }
                parent.revalidate();
                parent.repaint();
            }
        }
        previewedAreas.clear();
    }

    private boolean hasOutputText() {
        if (output != null && !output.getText().isBlank()) {
            return true;
        }
        if (!previewedAreas.isEmpty()) {
            return true;
        }
        if (runs == null) {
            for (Component comp : outputPane.getComponents()) {
                if (comp instanceof OutputArea area && !area.getText().isBlank()) {
                    return true;
                }
            }
        } else {
            for (JPanel content : runContentPanes.values()) {
                if (content != null) {
                    for (Component comp : content.getComponents()) {
                        if (comp instanceof OutputArea area && !area.getText().isBlank()) {
                            return true;
                        }
                    }
                }
            }
        }
        for (OutputArea area : runOutputs.values()) {
            if (area != null && !area.getText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private void updateModeBarVisibility() {
        if (modeBar == null) {
            return;
        }
        IntentType type = getIntentType();
        boolean running = progressPane.getParent() == footer || progress.isIndeterminate();
        boolean show = !editor.isEditable()
                && !running
                && !queuePane.isVisible()
                && type != IntentType.Markdown
                && type != IntentType.Raw
                && hasOutputText();
        if (modeBar.isVisible() != show) {
            modeBar.setVisible(show);
            southPane.revalidate();
            southPane.repaint();
        }
        boolean footerChanged = false;
        if (show) {
            if (!topModeBar.isVisible()) {
                topModeBar.setVisible(true);
                footerChanged = true;
            }
            if (topModeBar.getParent() != footer) {
                footer.add(topModeBar, BorderLayout.EAST);
                footerChanged = true;
            }
        } else {
            if (topModeBar.isVisible()) {
                topModeBar.setVisible(false);
                footerChanged = true;
            }
            if (topModeBar.getParent() == footer) {
                footer.remove(topModeBar);
                footerChanged = true;
            }
        }
        if (footerChanged) {
            footer.revalidate();
            footer.repaint();
        }
    }

    /**
     * Returns the reasoning effort level.
     * @return the reasoning effort level.
     */
    public String getReasoningEffort() {
        return effortComboBox.getSelectedIndex() == 0 ? ""
                : (String) effortComboBox.getSelectedItem();
    }

    /**
     * Renders Markdown text in output area.
     * @param text the Markdown text.
     */
    public void renderMarkdown(String text) {
        var html = new Markdown(text);
        markdownView = html;
        outputPane.remove(output);
        outputPane.add(html);
        updateModeBarVisibility();
    }

    /**
     * Adds a question to the output pane.
     * @param question the question to add.
     */
    public void addQuestion(Question question) {
        addQuestion(null, question);
    }

    /**
     * Adds a question to the parent turn or to one subagent tab.
     * @param runId null for the parent turn.
     * @param question the question to add.
     */
    public void addQuestion(String runId, Question question) {
        if (runId == null || runs == null) {
            JPanel pane = runs == null ? outputPane : runContentPanes.get("");
            addQuestionTo(pane == null ? outputPane : pane, null, question);
            return;
        }
        JPanel pane = runContentPanes.get(runId);
        if (pane == null) {
            JPanel master = runContentPanes.get("");
            addQuestionTo(master == null ? outputPane : master, null, question);
            return;
        }
        addQuestionTo(pane, runId, question);
    }

    private void addQuestionTo(JPanel pane, String runId, Question question) {
        if (mode == Mode.PREVIEW) {
            setMode(Mode.RAW);
        }
        if (runId == null || runId.isEmpty()) {
            selectMasterRun();
        }
        flushRouter(runId);
        JComponent gui = question.createGUI();
        AgentOutputArea area = currentAreaFor(runId);
        if (area != null && area.getParent() == pane && area.getText().isBlank()) {
            pane.remove(area);
            pane.add(gui);
            pane.add(area);
        } else {
            pane.add(gui);
            AgentOutputArea next = createOutputArea(AgentOutputArea.Kind.LLM);
            pane.add(next);
            setCurrentAreaFor(runId, next);
        }
        pane.revalidate();
        pane.repaint();
        // A question the agent is waiting on is the newest thing in the tab, but a
        // fresh empty output area follows it, so following to the bottom would push
        // it above the fold. Reveal the box itself, keeping its header on screen.
        revealQuestion(runId, gui, question);
    }

    /**
     * Scrolls a just-added question box into view instead of the bottom of the
     * transcript, and re-arms following once it is answered.
     * <p>A subagent's box is inside its tab's scroll pane, while the tab itself
     * sits in the conversation pane owned by {@link AgentPanel}; both must move,
     * so both scrollers are asked to show the box. Intents built without a panel
     * (tests, standalone widgets) have no conversation pane and simply do
     * nothing there.
     *
     * @param runId {@code null} for the parent turn, or the subagent run id.
     * @param gui the question's GUI component.
     * @param question the question, whose answer ends the hold.
     */
    private void revealQuestion(String runId, JComponent gui, Question question) {
        boolean inTab = runId != null && !runId.isEmpty();
        AutoScroller tabScroller = inTab ? runScrollers.get(runId) : null;
        if (tabScroller != null) {
            tabScroller.reveal(gui);
        }
        // A master question's box is drawn directly in the conversation pane, so
        // reveal the box itself. A subagent's box lives in its own tab, whose inner
        // scroller above already reveals it; what the conversation pane must bring
        // into view is the tab that holds the box.
        JPanel tabPane = inTab ? runPanes.get(runId) : null;
        JComponent panelTarget = tabPane != null ? tabPane : gui;
        if (panel != null) {
            panel.reveal(panelTarget);
        }
        // A revealed box holds the view still so its own layout growth cannot pull it
        // away. Once the user answers, the turn's output is what they want to see.
        // The resume names what was revealed, so a later question that replaced it
        // keeps the view.
        question.ask().whenComplete((answer, error) -> SwingUtilities.invokeLater(() -> {
            if (tabScroller != null) {
                tabScroller.resumeFollowingAfter(gui);
            }
            if (panel != null) {
                panel.resumeFollowingAfter(panelTarget);
            }
        }));
    }

    /**
     * Opens a tab for a subagent run. The first run wraps the parent output
     * in a tab and selects the new run.
     * @param runId the subagent run id.
     * @param label the tab title.
     * @param parentTitle the title of the parent tab, added with the first run.
     */
    public void beginRun(String runId, String label, String parentTitle) {
        if (mode == Mode.PREVIEW) {
            setMode(Mode.RAW);
        }
        if (runs == null) {
            runs = new JTabbedPane();
            JPanel parent = new JPanel();
            parent.setLayout(new BoxLayout(parent, BoxLayout.Y_AXIS));
            parent.setOpaque(false);
            Component[] existingMaster = outputPane.getComponents();
            for (Component comp : existingMaster) {
                outputPane.remove(comp);
                parent.add(comp);
            }
            runPanes.put("", parent);
            runContentPanes.put("", parent);
            runOutputs.put("", output);
            runs.addTab(displayName(parentTitle), parent);
            outputPane.add(runs);
        }

        JPanel existing = runPanes.get(runId);
        if (existing != null) {
            int index = runTabIndex(existing);
            if (index >= 0) {
                runs.setSelectedIndex(index);
            }
            selectMasterOnNextOutput = true;
            return;
        }

        AgentOutputArea area = createOutputArea(AgentOutputArea.Kind.LLM);
        // An empty view reserves this many rows; the scroll pane below caps the
        // preferred height at the same count once the output exceeds it.
        area.setRows(RUN_VIEW_ROWS);

        // The content tracks the viewport width, so the output wraps at the visible
        // width instead of computing a wider preferred one. That is what lets the
        // horizontal scrollbar stay off: there is nothing to scroll sideways to.
        JPanel content = new ScrollablePanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(area);

        // Wraps a subagent output pane in a scroll pane. The pane's preferred height
        // is capped at RUN_VIEW_ROWS instead of following the output: a text area's
        // preferred height grows with its text, so an uncapped pane would ask its tab
        // to grow with every streamed line (see RowLimitedScrollPane).
        // To prevent the scroll pane from growing past that cap unless the parent
        // container itself expands to give it more space, wrap the JScrollPane in a
        // panel utilizing GridBagLayout.
        RowLimitedScrollPane scrollPane = new RowLimitedScrollPane(area, content, RUN_VIEW_ROWS);
        // The output wraps to the tab's width, so a horizontal scrollbar could only
        // ever show a clipped sliver of an over-long line; never show one.
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        runScrollers.put(runId, new AutoScroller(scrollPane));
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0; // Tells the component to absorb extra vertical space
        gbc.fill = GridBagConstraints.BOTH; // Expands component to fill that space

        // Add scroll pane to wrapper panel, then wrapper panel to tab
        pane.add(scrollPane, gbc);
        runOutputs.put(runId, area);
        runContentPanes.put(runId, content);
        runPanes.put(runId, pane);
        runs.addTab(label, pane);
        int index = runs.getTabCount() - 1;
        runs.setToolTipTextAt(index, "Running");
        runs.setSelectedIndex(index);
        selectMasterOnNextOutput = true;
        outputPane.revalidate();
        outputPane.repaint();
    }

    /** Returns the tab index of a run pane, or -1 if it is not shown. */
    private int runTabIndex(JPanel pane) {
        for (int i = 0; i < runs.getTabCount(); i++) {
            Component tab = runs.getComponentAt(i);
            if (tab == pane || (tab instanceof Container container && SwingUtilities.isDescendingFrom(pane, container))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Call-out names are stored in lower case. The parent tab shows the same
     * name with a capital initial. An AID is left unchanged.
     */
    static String displayName(String name) {
        if (name == null || name.isBlank() || name.indexOf(':') >= 0) {
            return name == null ? "" : name;
        }
        int end = name.offsetByCodePoints(0, 1);
        return name.substring(0, end).toUpperCase(Locale.ROOT) + name.substring(end);
    }

    /**
     * Returns the tabbed pane for subagent runs, or null if no subagent has run.
     * @return the tabbed pane for runs.
     */
    JTabbedPane runs() {
        return runs;
    }

    /**
     * Selects the master agent tab in the output pane if subagent tabs exist.
     */
    public void selectMasterRun() {
        if (runs != null && runs.getTabCount() > 0) {
            JPanel parent = runPanes.get("");
            int index = parent != null ? runTabIndex(parent) : 0;
            if (index >= 0 && index < runs.getTabCount() && runs.getSelectedIndex() != index) {
                runs.setSelectedIndex(index);
            }
        }
    }

    /**
     * Appends text to the parent output or to one subagent tab, routing LLM
     * Markdown chunks, tool-call status updates, and system status lines to
     * their corresponding {@link AgentOutputArea.Kind} widgets.
     * @param runId null for the parent turn.
     * @param chunk the text to append.
     */
    public void appendRun(String runId, String chunk) {
        if (selectMasterOnNextOutput && (runId == null || runId.isEmpty())) {
            selectMasterOnNextOutput = false;
            selectMasterRun();
        }
        String key = (runId == null || runs == null) ? "" : runId;
        StreamRouter router = runRouters.computeIfAbsent(
                key, k -> new StreamRouter(k.isEmpty() ? null : k));
        router.accept(chunk);
    }

    private void flushRouter(String runId) {
        String key = (runId == null || runs == null) ? "" : runId;
        StreamRouter router = runRouters.get(key);
        if (router != null) {
            router.flush();
        }
    }

    private void flushAllRouters() {
        for (StreamRouter router : runRouters.values()) {
            router.flush();
        }
    }

    /**
     * Marks a subagent tab as finished and leaves it in place.
     * @param runId the subagent run id.
     * @param tooltip the tab tooltip.
     */
    public void finishRun(String runId, String tooltip) {
        if (runs == null || runId == null) {
            return;
        }
        flushRouter(runId);
        JPanel pane = runPanes.get(runId);
        if (pane == null) {
            return;
        }
        int index = runTabIndex(pane);
        if (index >= 0) {
            runs.setToolTipTextAt(index, tooltip);
        }
        selectMasterOnNextOutput = true;
    }

    /**
     * Sets whether the input area should be editable.
     * @param editable the editable flag.
     */
    public void setEditable(boolean editable) {
        intentTypeComboBox.setEnabled(editable);
        editor.setEditable(editable);
        if (editable) {
            if (mode == Mode.PREVIEW) {
                setMode(Mode.RAW);
            }
            editor.setBackground(inputPaneColor);
            inputPane.setBackground(inputPaneColor);
            controlPane.setBackground(inputPaneColor);
            intentTypeComboBox.setBackground(inputPaneColor);
            effortComboBox.setBackground(inputPaneColor);
            footer.add(controlPane, BorderLayout.WEST);
        } else {
            editor.setBackground(getBackground());
            inputPane.setBackground(getBackground());
            footer.remove(controlPane);
        }
        updateModeBarVisibility();
        footer.revalidate();
        footer.repaint();
    }

    /**
     * Sets the text color for input and prompt.
     * @param color the foreground color.
     */
    public void setInputForeground(Color color) {
        indicator.setForeground(color);
        editor.setForeground(color);
    }

    /**
     * Sets the font for input and prompt.
     * @param font the font.
     */
    public void setInputFont(Font font) {
        indicator.setFont(font);
        editor.setFont(font);
        footer.setBorder(BorderFactory.createEmptyBorder(0, indicator.getPreferredSize().width, 0, 0));
    }

    /**
     * Returns the intent type.
     * @return the intent type.
     */
    public IntentType getIntentType() {
        return (IntentType) intentTypeComboBox.getSelectedItem();
    }

    /**
     * Sets the intent type.
     * @param type the intent type.
     */
    public void setIntentType(IntentType type) {
        intentTypeComboBox.setSelectedItem(type);
        updateModeBarVisibility();
    }

    /**
     * Sets the status label and updates the status bar in SmileStudio.
     *
     * @param text the status message text.
     */
    public void setStatus(String text) {
        String msg = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        status.setText(msg);
        status.setToolTipText(text != null && !text.isBlank() ? text : null);
        if (!msg.isEmpty()) {
            String statusText = msg.startsWith(AGENT_STATUS_PREFIX) ? msg : AGENT_STATUS_PREFIX + msg;
            SmileStudio.setStatus(this, statusText);
        } else {
            SmileStudio.setStatus(this, "");
        }
    }

    /**
     * Returns the status label.
     * @return the status label.
     */
    public JLabel status() {
        return status;
    }

    /**
     * Turns on/off the progress bar.
     * <p>Turning it on marks the start of a turn and resets the stop button, so a
     * button disabled by a previous cancel becomes usable again.
     */
    public void setProgress(boolean on) {
        if (on) {
            if (mode == Mode.PREVIEW) {
                setMode(Mode.RAW);
            }
            progress.setIndeterminate(true);
            progress.setEnabled(true);
            // A running turn is no longer waiting, so its queue badge and controls go away.
            clearQueued();
            stopButton.setText("❌");
            stopButton.setToolTipText(bundle.getString("Stop"));
            stopButton.setEnabled(true);
            if (topModeBar.getParent() == footer) {
                footer.remove(topModeBar);
            }
            topModeBar.setVisible(false);
            if (progressPane.getParent() != footer) {
                footer.add(progressPane, BorderLayout.EAST);
            }
        } else {
            flushAllRouters();
            progress.setIndeterminate(false);
            progress.setEnabled(false);
            footer.remove(progressPane);
            stopAction = null;
        }
        updateModeBarVisibility();
        footer.revalidate();
        footer.repaint();
    }

    /**
     * Returns the layered south pane that hosts {@link #outputPane()} and
     * overlays {@link #modeBar()}. Package-private for testing.
     * @return the south layered pane.
     */
    JLayeredPane southPane() {
        return southPane;
    }

    /**
     * Returns the output pane container. Package-private for testing.
     * @return the output pane container.
     */
    JPanel outputPane() {
        return outputPane;
    }

    /**
     * Returns the content panel for a run id ({@code ""} for master when subagents
     * exist). Package-private for testing.
     * @param runId the run id, or {@code ""} for master.
     * @return the vertical BoxLayout content panel.
     */
    JPanel runContentPane(String runId) {
        return runContentPanes.get(runId == null ? "" : runId);
    }

    /**
     * Returns the auto-scroller of a subagent tab, or null for the master run.
     * Package-private for testing.
     * @param runId the subagent run id.
     * @return the tab's auto-scroller, or null.
     */
    AutoScroller runScroller(String runId) {
        return runScrollers.get(runId);
    }

    /**
     * Returns the scroll pane that holds a subagent tab's output, or null when
     * the tab does not exist. Package-private for testing.
     * @param runId the subagent run id.
     * @return the tab's scroll pane, or null.
     */
    JScrollPane runScrollPane(String runId) {
        JPanel tab = runPanes.get(runId);
        if (tab == null) {
            return null;
        }
        for (Component child : tab.getComponents()) {
            if (child instanceof JScrollPane scrollPane) {
                return scrollPane;
            }
        }
        return null;
    }

    /**
     * Returns the bottom-right mode toggle bar. Package-private for testing.
     * @return the bottom-right mode toggle bar.
     */
    JPanel modeBar() {
        return modeBar;
    }

    /**
     * Returns the bottom-right mode toggle button. Package-private for testing.
     * @return the bottom-right mode toggle button.
     */
    JToggleButton modeButton() {
        return modeButton;
    }

    /**
     * Returns the input pane footer. Package-private for testing.
     * @return the input pane footer.
     */
    JPanel footer() {
        return footer;
    }

    /**
     * Returns the input-footer mode toggle bar. Package-private for testing.
     * @return the input-footer mode toggle bar.
     */
    JPanel topModeBar() {
        return topModeBar;
    }

    /**
     * Returns the input-footer mode toggle button. Package-private for testing.
     * @return the input-footer mode toggle button.
     */
    JToggleButton topModeButton() {
        return topModeButton;
    }

    /**
     * Returns the stop button. Package-private for testing.
     * @return the stop button.
     */
    JButton stopButton() {
        return stopButton;
    }

    /**
     * Returns the progress bar. Package-private for testing.
     * @return the progress bar.
     */
    JProgressBar progressBar() {
        return progress;
    }

    /**
     * Returns the queue badge label. Package-private for testing.
     * @return the queue badge label.
     */
    JLabel queueBadge() {
        return queueBadge;
    }

    /**
     * Returns the queue controls pane. Package-private for testing.
     * @return the queue controls pane.
     */
    JPanel queuePane() {
        return queuePane;
    }

    /**
     * Returns the edit-queued button. Package-private for testing.
     * @return the edit button.
     */
    JButton editQueuedButton() {
        return editQueuedButton;
    }

    /**
     * Returns the move-up button. Package-private for testing.
     * @return the move-up button.
     */
    JButton moveUpButton() {
        return moveUpButton;
    }

    /**
     * Returns the move-down button. Package-private for testing.
     * @return the move-down button.
     */
    JButton moveDownButton() {
        return moveDownButton;
    }

    /**
     * Returns the cancel-queued button. Package-private for testing.
     * @return the cancel button.
     */
    JButton cancelQueuedButton() {
        return cancelQueuedButton;
    }

    /**
     * Returns the indicator component.
     * @return the indicator component.
     */
    public JLabel indicator() {
        return indicator;
    }

    /**
     * Returns the intent editor.
     * @return the intent editor.
     */
    public IntentEditor editor() {
        return editor;
    }

    /**
     * Returns the intent execution output.
     * @return the intent execution output.
     */
    public AgentOutputArea output() {
        return output;
    }

    /**
     * Returns a border with round corners.
     * @return a border with round corner.
     */
    static FlatLineBorder createRoundBorder() {
        return new FlatLineBorder(new Insets(5, 5, 5, 5),
                borderColor, 1, 20);
    }

    /**
     * Returns a localized queue string from this widget's bundle, so callers outside
     * this class (the queue rendering in {@link AgentPanel}) share the same translations.
     * @param key the bundle key.
     * @param args optional {@link MessageFormat} arguments.
     * @return the localized message.
     */
    static String queuedMessage(String key, Object... args) {
        String value = bundle.getString(key);
        return args.length == 0 ? value : MessageFormat.format(value, args);
    }
}
