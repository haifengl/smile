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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.formdev.flatlaf.ui.FlatLineBorder;
import com.formdev.flatlaf.util.SystemInfo;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import smile.plot.swing.Palette;
import smile.studio.kernel.ShellRunner;
import smile.studio.model.CommandType;
import static smile.studio.model.CommandType.*;

/**
 * A command is a multiline text input field, and its contents can be executed
 * by a variety of engines including LLM agents.
 *
 * @author Haifeng Li
 */
public class Command extends JPanel {
    private static final Color inputColor = new Color(220, 248, 198);
    private static final Color borderColor = Palette.web("#8dd4e8");
    private final JPanel footer = new JPanel();
    private final JPanel inputPane = new JPanel(new BorderLayout());
    private final JLabel indicator = new JLabel(">", SwingConstants.CENTER);
    private final JComboBox<CommandType> commandType = new JComboBox<>(new CommandType[] {Raw, Magic, Shell, Python, Markdown, Instructions});
    private final CodeEditor editor = new CodeEditor(1, 80, SyntaxConstants.SYNTAX_STYLE_NONE);
    private final OutputArea output = new OutputArea();

    /**
     * Constructor.
     * @param analyst the parent analyst component.
     */
    public Command(Analyst analyst) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));

        initInputPane();
        initActionMap(analyst);
        output.setFont(Monospaced.getFont());
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);

        add(inputPane, BorderLayout.CENTER);
        add(output, BorderLayout.SOUTH);
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

        initCommandType();
        footer.setLayout(new BoxLayout(footer, BoxLayout.X_AXIS));
        footer.setOpaque(false);
        footer.add(Box.createHorizontalStrut(indicator.getPreferredSize().width));
        footer.add(commandType);

        inputPane.setBackground(inputColor);
        inputPane.setBorder(createRoundBorder());
        inputPane.add(sidebar, BorderLayout.WEST);
        inputPane.add(editor, BorderLayout.CENTER);
        inputPane.add(footer, BorderLayout.SOUTH);
    }

    private void initCommandType() {
        commandType.setSelectedItem(Instructions);
        commandType.setBorder(BorderFactory.createEmptyBorder());
        commandType.setBackground(inputColor);
        commandType.setForeground(Color.DARK_GRAY);
        if (commandType.getComponentCount() > 0 && commandType.getComponent(0) instanceof AbstractButton button) {
            button.setVisible(false);
        }
        commandType.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                var command = (CommandType) e.getItem();
                indicator.setText(command.legend());
                indicator.setToolTipText(command.toString());
                editor.requestFocusInWindow();

                switch (command) {
                    case Shell -> {
                        if (SystemInfo.isWindows) {
                            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
                        } else {
                            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
                        }
                    }
                    case Python ->
                            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
                    case Markdown ->
                            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
                    default ->
                            editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
                }
            }
        });
    }

    private void initActionMap(Analyst analyst) {
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
                            commandType.setSelectedItem(Magic);
                            editor.replaceRange("", 0, 1);
                        }
                        case '%' -> {
                            commandType.setSelectedItem(Shell);
                            editor.replaceRange("", 0, 1);

                        }
                        case '#' -> commandType.setSelectedItem(Markdown);
                    }
                } catch (BadLocationException ex) {
                    // ignore the exception
                }

                setEditable(false);
                switch ((CommandType) commandType.getSelectedItem()) {
                    case Raw -> runRaw();
                    case Magic -> runMagic();
                    case Shell, Python -> runShell();
                    case Markdown -> {
                        try {
                            var html = markdown(editor.getText());
                            remove(output);
                            add(html, BorderLayout.SOUTH);
                        } catch (Exception ex) {
                            output.setText("ERROR rendering Markdown: " + ex.getMessage());
                        }
                    }
                    case Instructions -> analyst.run(Command.this);
                }

                analyst.addCommand();
            }
        });
    }

    /**
     * Executes shell commands.
     */
    private void runShell() {
        List<String> command = new ArrayList<>();
        switch (getCommandType()) {
            case Python -> {
                command.add("python");
                command.add("-c");
                command.add(editor.getText());
            }
            case Shell -> {
                if (SystemInfo.isWindows) {
                    command.add("cmd.exe");
                    command.add("/c");
                } else {
                    command.add("bash");
                    command.add("-c");
                }
                command.add(editor.getText());
            }
            case Magic -> {
                var smile = System.getProperty("smile.home", ".") + "/bin/smile";
                if (SystemInfo.isWindows) smile += ".bat";
                command.add(smile);
                command.addAll(Arrays.asList(editor.getText().split("\\s+")));
            }
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                var shell = new ShellRunner();
                shell.setOutputArea(output);
                int ret = shell.exec(command);
                if (ret != 0) output.appendLine("\nCommand failed with error code " + ret);
                return null;
            }

            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    output.flush();
                });
            }
        };
        worker.execute();
    }

    /** Executes raw content. */
    private void runRaw() {
        // do nothing
    }

    /** Executes magic commands. */
    private void runMagic() {
        String instructions = editor.getText();
        String[] command = instructions.split("\\s+");
        switch (command[0]) {
            case "help" -> magicHelp(command);
            case "train", "predict", "serve" -> runShell();
            case "init" -> magicInit(instructions);
            case "load" -> magicLoad(command);
            case "analyze" -> magicAnalyze(command);
            default -> output.setText("ERROR: unknown magic - " + command[0]);
        }
    }

    private void magicHelp(String[] command) {
        output.setText("""
                The following magic commands are available:
                
                /init the project with your requirements
                /load data
                /analyze for exploratory data analysis
                /train to build a model
                /predict to run batch inference
                /serve to start an inference service""");
    }

    private void magicInit(String instructions) {

    }

    private void magicLoad(String[] command) {

    }

    private void magicAnalyze(String[] command) {

    }

    /**
     * Sets whether the input area should be editable.
     * @param editable the editable flag.
     */
    public void setEditable(boolean editable) {
        commandType.setEnabled(editable);
        editor.setEditable(editable);
        if (editable) {
            editor.setBackground(inputColor);
            inputPane.setBackground(inputColor);
            commandType.setBackground(inputColor);

        } else {
            editor.setBackground(getBackground());
            inputPane.setBackground(getBackground());
            footer.remove(commandType);
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
     * Returns the command type.
     * @return the command type.
     */
    public CommandType getCommandType() {
        return (CommandType) commandType.getSelectedItem();
    }

    /**
     * Sets the command type.
     * @param type the command type.
     */
    public void setCommandType(CommandType type) {
        commandType.setSelectedItem(type);
    }

    /**
     * Returns the indicator component.
     * @return the indicator component.
     */
    public JLabel indicator() {
        return indicator;
    }

    /**
     * Returns the command editor.
     * @return the command editor.
     */
    public CodeEditor editor() {
        return editor;
    }

    /**
     * Returns the command output.
     * @return the command output.
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

    /**
     * Returns an XHTML panel to display markdown content.
     * @param md the markdown content.
     * @return an XHTML panel to display markdown content.
     * @throws ParserConfigurationException if a DocumentBuilder cannot be created with default configuration.
     * @throws IOException if any IO errors occur.
     * @throws SAXException if any parse errors occur.
     */
    static XHTMLPanel markdown(String md) throws IOException, ParserConfigurationException, SAXException {
        // Parse Markdown to HTML
        Parser parser = Parser.builder().build();
        Node document = parser.parse(md);
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String content = renderer.render(document);

        String html = """
                        <html>
                        <body style="width: 95%; height: auto; margin: 0 auto;">
                        """ + content + "</body></html>";
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(html)));

        XHTMLPanel browser = new XHTMLPanel();
        browser.setInteractive(false);
        browser.setOpaque(false);
        browser.setDocument(doc, null); // The second argument is for base URI, can be null
        return browser;
    }
}
