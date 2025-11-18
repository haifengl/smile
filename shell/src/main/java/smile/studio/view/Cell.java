/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
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
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import com.formdev.flatlaf.util.SystemInfo;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jdesktop.swingx.JXTextField;
import smile.studio.agent.LLM;
import smile.studio.agent.Prompt;
import smile.studio.kernel.PostRunNavigation;

/**
 * A cell is a multiline text input field, and its contents can be executed.
 * The execution behavior of a cell is determined by the cell's type.
 * There are three types of cells: code cells, markdown cells, and raw cells.
 *
 * @author Haifeng Li
 */
public class Cell extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Cell.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Cell.class.getName(), Locale.getDefault());
    private static final Optional<LLM> coder = LLM.getCoder();
    private final String placeholder = bundle.getString("Prompt");
    private final CodeEditor editor = new CodeEditor();
    private final OutputArea output = new OutputArea();
    private final JTextField prompt = new JXTextField(placeholder);
    private final TitledBorder border = BorderFactory.createTitledBorder("[ ]");
    private final JButton runButton = new JButton("▶");
    private final JButton runBelowButton = new JButton("⏭");
    private final JButton upButton = new JButton("↑");
    private final JButton downButton = new JButton("↓");
    // Windows doesn't show broom emoji properly
    private final JButton clearButton = new JButton(SystemInfo.isMacOS ? "🧹" : "⌫");
    private final JButton deleteButton = new JButton("⌦");
    /** Running code generation. */
    private volatile boolean isCoding = false;

    /**
     * Constructor.
     * @param notebook the parent notebook.
     */
    public Cell(Notebook notebook) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));

        // Header of action buttons and prompt field
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(Box.createHorizontalStrut(2));
        header.add(runButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(runBelowButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(upButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(downButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(clearButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(deleteButton);
        header.add(Box.createHorizontalStrut(20));
        header.add(prompt);

        prompt.addActionListener(e -> generateCode());
        prompt.putClientProperty("JComponent.roundRect", true);

        runButton.setToolTipText(bundle.getString("Run"));
        runBelowButton.setToolTipText(bundle.getString("RunBelow"));
        upButton.setToolTipText(bundle.getString("MoveUp"));
        downButton.setToolTipText(bundle.getString("MoveDown"));
        clearButton.setToolTipText(bundle.getString("Clear"));
        deleteButton.setToolTipText(bundle.getString("Delete"));

        runButton.addActionListener(e -> notebook.runCell(this, PostRunNavigation.STAY));
        runBelowButton.addActionListener(e -> notebook.runCellAndBelow(this));
        upButton.addActionListener(e -> notebook.moveCellUp(this));
        downButton.addActionListener(e -> notebook.moveCellDown(this));
        clearButton.addActionListener(e -> output.setText(""));
        deleteButton.addActionListener(e -> notebook.deleteCell(this));

        // Cell editor and output configuration
        Monospace.addListener((e) ->
            SwingUtilities.invokeLater(() -> {
                Font font = (Font) e.getNewValue();
                output.setFont(font);
                editor.setFont(font);
            })
        );

        output.setFont(Monospace.getFont());
        editor.setFont(Monospace.getFont());
        RTextScrollPane editorScroll = new RTextScrollPane(editor);
        editorScroll.setBorder(border);
        editorScroll.putClientProperty("JScrollBar.showButtons", true);

        // Key bindings (inspired by Jupyter)
        InputMap inputMap = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = editor.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("TAB"), "complete-code");
        actionMap.put("complete-code", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                completeCode();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "run-next");
        actionMap.put("run-next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                notebook.runCell(Cell.this, PostRunNavigation.NEXT_OR_NEW);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "run-stay");
        actionMap.put("run-stay", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                notebook.runCell(Cell.this, PostRunNavigation.STAY);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke("alt ENTER"), "run-insert");
        actionMap.put("run-insert", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                notebook.runCell(Cell.this, PostRunNavigation.INSERT_BELOW);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "increase-font-size");
        actionMap.put("increase-font-size", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                Monospace.adjustFontSize(1);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "decrease-font-size");
        actionMap.put("decrease-font-size", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                Monospace.adjustFontSize(-1);
            }
        });

        add(header, BorderLayout.NORTH);
        add(editorScroll, BorderLayout.CENTER);
        add(output, BorderLayout.SOUTH);
    }

    /**
     * Completes the current line of code.
     */
    private void completeCode() {
        if (coder.isEmpty() || isCoding) return;

        try {
            isCoding = true;
            int caretPosition = editor.getCaretPosition();
            int lineNum = editor.getLineOfOffset(caretPosition);
            // Skip code completion for first line.
            if (lineNum == 0) {
                isCoding = false;
                return;
            }

            int startOffset = editor.getLineStartOffset(lineNum);
            int endOffset = editor.getLineEndOffset(lineNum);
            String context = editor.getText(0, startOffset);
            String currentLine = editor.getText(startOffset, endOffset - startOffset);

            // Run code completion in a worker thread as join() blocks.
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    var input = Prompt.completeCode(context, currentLine);
                    coder.ifPresent(llm -> llm.complete(input).whenComplete((line, ex) -> {
                        if (ex != null) {
                            logger.warn("Code completion failed: {}", ex.getMessage());
                        }

                        if (line != null) {
                            SwingUtilities.invokeLater(() -> editor.replaceRange(line, startOffset, caretPosition));
                        }
                    }).join());
                    return null;
                }

                @Override
                protected void done() {
                    isCoding = false;
                }
            };
            worker.execute();
        } catch (BadLocationException ex) {
            isCoding = false;
            logger.trace("Code completion failed: {}", ex.getMessage());
        }
    }

    /**
     * Generates code based on prompt.
     */
    private void generateCode() {
        if (coder.isEmpty() || isCoding) return;

        String task = prompt.getText();
        if (!task.isBlank()) {
            isCoding = true;
            String context = editor.getText();
            editor.append(System.lineSeparator());
            for (String line : wrap(task, 80)) {
                editor.append("/// " + line + "\n");
                editor.setCaretPosition(editor.getDocument().getLength());
                editor.requestFocus();
            }

            // Run code completion in a worker thread as join() blocks.
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    var input = Prompt.generateCode(context, task);
                    coder.ifPresent(llm -> llm.generate(input,
                        chunk -> SwingUtilities.invokeLater(() -> editor.append(chunk)),
                        ex -> {
                            SwingUtilities.invokeLater(() -> editor.append("/// Code generation failed: " + ex.getMessage()));
                            return null;
                    }));
                    return null;
                }

                @Override
                protected void done() {
                    isCoding = false;
                }
            };
            worker.execute();
        }
    }

    /**
     * Breaks a string into lines with word wrapping up to a specified line width.
     * @param text the input text.
     * @param maxWidth the line width.
     * @return the lines.
     */
    private static List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = text.split("\\s+"); // Split by one or more spaces
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            if (line.isEmpty()) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxWidth) {
                line.append(" ").append(word);
            } else {
                lines.add(line.toString().trim());
                line = new StringBuilder(word);
            }
        }

        // Add the last line if it's not empty
        if (!line.isEmpty()) {
            lines.add(line.toString().trim());
        }

        return lines;
    }

    /**
     * Sets the cell running state.
     * @param running the running state.
     */
    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        upButton.setEnabled(!running);
        downButton.setEnabled(!running);
        deleteButton.setEnabled(!running);
        editor.setEnabled(!running);
        if (running) {
            border.setTitle("[*]");
            output.setText("");
        }
    }

    /**
     * Sets the cell title.
     * @param title the title.
     */
    public void setTitle(String title) {
        border.setTitle(title);
    }

    /**
     * Returns the code editor.
     * @return the code editor.
     */
    public CodeEditor editor() {
        return editor;
    }

    /**
     * Returns the output area.
     * @return the output area.
     */
    public OutputArea output() {
        return output;
    }
}
