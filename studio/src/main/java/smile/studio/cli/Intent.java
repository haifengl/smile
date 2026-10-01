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

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
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
import static smile.studio.cli.IntentType.*;

/**
 * An intent is a multiline text input field, and its contents can be executed
 * by a variety of engines including LLM agents.
 *
 * @author Haifeng Li
 */
public class Intent extends JPanel {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Intent.class.getName(), Locale.getDefault());
    private static final Color borderColor = Palette.web("#8dd4e8");
    /** Emoji prefix (U+1F916 robot face) for status messages originating from agents. */
    static final String AGENT_STATUS_PREFIX = "\uD83E\uDD16 ";
    /** The scroll unit increment in pixels for the subagent output scrollbars. */
    private static final int SCROLL_UNIT_INCREMENT = 18;
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
    // Output pane. Subagent runs are tabs inside this pane.
    private final JPanel outputPane = new JPanel();
    private OutputArea output = createOutputArea();
    private JTabbedPane runs;
    private final Map<String, JPanel> runPanes = new LinkedHashMap<>();
    private final Map<String, OutputArea> runOutputs = new LinkedHashMap<>();
    private boolean selectMasterOnNextOutput;

    /**
     * Constructor.
     * @param cli the parent component.
     */
    public Intent(AgentCLI cli) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));

        effortComboBox = initEffortComboBox(cli);
        if (cli != null) {
            effortComboBox.setSelectedItem(cli.getReasoningEffort());
        }
        initModelComboBox(cli);
        initInputPane();
        if (cli != null) {
            initActionMap(cli);
        }

        outputPane.setLayout(new BoxLayout(outputPane, BoxLayout.Y_AXIS));
        outputPane.add(output);

        add(inputPane, BorderLayout.CENTER);
        add(outputPane, BorderLayout.SOUTH);
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
        progress.putClientProperty("JProgressBar.largeHeight", true);
        progressPane.setOpaque(false);
        progressPane.add(progress);
        progressPane.add(Box.createHorizontalStrut(10));
        progressPane.add(stopButton);

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

    /** Initializes the reasoning effort combo box. */
    private JComboBox<String> initEffortComboBox(AgentCLI cli) {
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
    private void initModelComboBox(AgentCLI cli) {
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

    private void initActionMap(AgentCLI cli) {
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
     * Sets the stop action for the intent.
     * @param stop the lambda to stop execution.
     */
    public <T> void setStopAction(Callable<T> stop) {
        stopButton.setVisible(true);
        progressPane.revalidate();
        footer.revalidate();
        footer.repaint();
        stopButton.addActionListener(e -> {
            try {
                stop.call();
                stopButton.setEnabled(false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        Intent.this,
                        ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    /** Creates an output area. */
    private OutputArea createOutputArea() {
        OutputArea output = new OutputArea();
        output.setFont(Monospaced.getFont());
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        return output;
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
        outputPane.remove(output);
        outputPane.add(html);
    }

    /**
     * Adds a question to the output pane.
     * @param question the question to add.
     */
    public void addQuestion(Question question) {
        JPanel pane = runs == null ? outputPane : runPanes.get("");
        addQuestionTo(pane == null ? outputPane : pane, null, question);
    }

    /**
     * Adds a question to the parent turn or to one subagent tab.
     * @param runId null for the parent turn.
     * @param question the question to add.
     */
    public void addQuestion(String runId, Question question) {
        if (runId == null || runs == null) {
            addQuestion(question);
            return;
        }
        JPanel pane = runPanes.get(runId);
        if (pane == null) {
            addQuestion(question);
            return;
        }
        addQuestionTo(pane, runId, question);
    }

    private void addQuestionTo(JPanel pane, String runId, Question question) {
        OutputArea area = runId == null ? output : runOutputs.getOrDefault(runId, output);
        if (area.getText().isBlank()) {
            pane.remove(area);
            pane.add(question.createGUI());
            pane.add(area);
        } else {
            pane.add(question.createGUI());
            OutputArea next = createOutputArea();
            pane.add(next);
            if (runId == null) {
                output = next;
                if (runs != null) {
                    runOutputs.put("", next);
                }
            } else {
                runOutputs.put(runId, next);
            }
        }
        pane.revalidate();
    }

    /**
     * Opens a tab for a subagent run. The first run wraps the parent output
     * in a tab and selects the new run.
     * @param runId the subagent run id.
     * @param label the tab title.
     * @param parentTitle the title of the parent tab, added with the first run.
     */
    public void beginRun(String runId, String label, String parentTitle) {
        if (runs == null) {
            runs = new JTabbedPane();
            outputPane.remove(output);
            JPanel parent = new JPanel();
            parent.setLayout(new BoxLayout(parent, BoxLayout.Y_AXIS));
            parent.add(output);
            runPanes.put("", parent);
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

        OutputArea area = createOutputArea();
        area.setRows(5);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        // Wraps a subagent output pane in a scroll pane.
        // To prevent the scroll pane from growing past a certain point
        // unless the parent container itself expands to give it more space,
        // wrap the JScrollPane in a panel utilizing GridBagLayout.
        JScrollPane scrollPane = new JScrollPane(area);
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
     * Appends text to the parent output or to one subagent tab.
     * @param runId null for the parent turn.
     * @param chunk the text to append.
     */
    public void appendRun(String runId, String chunk) {
        if (selectMasterOnNextOutput && (runId == null || runId.isEmpty())) {
            selectMasterOnNextOutput = false;
            selectMasterRun();
        }
        OutputArea area = runId == null || runs == null ? output : runOutputs.get(runId);
        if (area == null) {
            area = output;
        }
        area.append(chunk);
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
     */
    public void setProgress(boolean on) {
        if (on) {
            progress.setIndeterminate(true);
            progress.setEnabled(true);
            footer.add(progressPane, BorderLayout.EAST);
        } else {
            progress.setIndeterminate(false);
            progress.setEnabled(false);
            footer.remove(progressPane);
        }
        footer.revalidate();
        footer.repaint();
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
    public OutputArea output() {
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
}
