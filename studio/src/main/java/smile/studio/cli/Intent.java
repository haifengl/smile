/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.cli;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import com.formdev.flatlaf.ui.FlatLineBorder;
import com.formdev.flatlaf.util.SystemInfo;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import ioa.llm.tool.Question;
import smile.plot.swing.Palette;
import smile.studio.Editor;
import smile.studio.Markdown;
import smile.studio.Monospaced;
import smile.studio.OutputArea;
import static smile.studio.cli.IntentType.*;

/**
 * An intent is a multiline text input field, and its contents can be executed
 * by a variety of engines including LLM agents.
 *
 * @author Haifeng Li
 */
public class Intent extends JPanel {
    private static final Color inputColor = new Color(220, 248, 198);
    private static final Color borderColor = Palette.web("#8dd4e8");
    private final JPanel footer = new JPanel();
    private final JPanel inputPane = new JPanel(new BorderLayout());
    private final JLabel indicator = new JLabel(">", SwingConstants.CENTER);
    private final JComboBox<IntentType> intentTypeComboBox = new JComboBox<>(IntentType.values());
    private final Editor editor = new Editor(1, 80, SyntaxConstants.SYNTAX_STYLE_NONE);
    private final JLabel status = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final JPanel outputPane = new JPanel();
    private OutputArea output = createOutputArea();

    /**
     * Constructor.
     * @param cli the parent component.
     */
    public Intent(AgentCLI cli) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));

        initInputPane();
        initActionMap(cli);

        var ac = new AutoCompletion(cli.hints());
        ac.setAutoActivationEnabled(true);
        ac.setAutoActivationDelay(500);
        ac.install(editor);

        outputPane.setLayout(new BoxLayout(outputPane, BoxLayout.Y_AXIS));
        outputPane.add(output);

        add(inputPane, BorderLayout.CENTER);
        add(outputPane, BorderLayout.SOUTH);
    }

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
        editor.setBackground(inputColor);

        status.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        progress.setMaximumSize(new Dimension(200, 12));

        initCommandType();
        footer.setLayout(new BoxLayout(footer, BoxLayout.X_AXIS));
        footer.setOpaque(false);
        footer.add(Box.createHorizontalStrut(indicator.getPreferredSize().width));
        footer.add(intentTypeComboBox);
        footer.add(status);
        footer.add(Box.createHorizontalGlue());

        inputPane.setBackground(inputColor);
        inputPane.setBorder(createRoundBorder());
        inputPane.add(sidebar, BorderLayout.WEST);
        inputPane.add(editor, BorderLayout.CENTER);
        inputPane.add(footer, BorderLayout.SOUTH);
    }

    private void initCommandType() {
        intentTypeComboBox.setSelectedItem(Instructions);
        intentTypeComboBox.setBorder(BorderFactory.createEmptyBorder());
        intentTypeComboBox.setBackground(inputColor);
        intentTypeComboBox.setForeground(Color.DARK_GRAY);
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

                switch (intentType) {
                    case Shell -> {
                        if (SystemInfo.isWindows) {
                            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
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
        inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "run");
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
        if (output.getText().isBlank()) {
            // keep the output area at the bottom for subsequent outputs
            outputPane.remove(output);
            outputPane.add(question.createGUI());
            outputPane.add(output);
        } else {
            outputPane.add(question.createGUI());
            // add a new output area for subsequent outputs
            output = createOutputArea();
            outputPane.add(output);
        }
    }

    /**
     * Sets whether the input area should be editable.
     * @param editable the editable flag.
     */
    public void setEditable(boolean editable) {
        intentTypeComboBox.setEnabled(editable);
        editor.setEditable(editable);
        if (editable) {
            editor.setBackground(inputColor);
            inputPane.setBackground(inputColor);
            intentTypeComboBox.setBackground(inputColor);

        } else {
            editor.setBackground(getBackground());
            inputPane.setBackground(getBackground());
            footer.remove(intentTypeComboBox);
        }
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
     * Sets the status label.
     */
    public void setStatus(String text) {
        status.setText(text);
    }

    /**
     * Turns on/off the progress bar.
     */
    public void setProgress(boolean on) {
        if (on) {
            progress.setIndeterminate(true);
            progress.setEnabled(true);
            footer.add(progress);
        } else {
            progress.setIndeterminate(false);
            progress.setEnabled(false);
            footer.remove(progress);
            // Repaint the footer to reflect the removal of the progress bar.
            // This is necessary as Swing is optimized for lazy evaluation.
            footer.repaint();
        }
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
    public Editor editor() {
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
