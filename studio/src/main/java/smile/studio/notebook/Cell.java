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
package smile.studio.notebook;

import java.awt.*;
import java.awt.event.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import com.formdev.flatlaf.util.SystemInfo;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jdesktop.swingx.JXTextField;
import ioa.agent.Coder;
import ioa.llm.client.StreamResponseHandler;
import smile.studio.Markdown;
import smile.studio.kernel.Kernel;
import smile.studio.kernel.PostRunNavigation;
import smile.studio.Monospaced;
import smile.studio.OutputArea;

/**
 * A cell is a multiline coding field, and its contents can be executed
 * by Java engine. If AI service is configured, it can also complete code
 * triggered by TAB and generate code based on natural language prompt.
 *
 * @author Haifeng Li
 */
public class Cell extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Cell.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Cell.class.getName(), Locale.getDefault());
    private static final DateTimeFormatter datetime = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String placeholder = bundle.getString("Prompt");
    private final CellEditor editor;
    private final RTextScrollPane editorScroll;
    private final OutputArea output = new OutputArea();
    private final JComboBox<CellType> cellTypeComboBox = new JComboBox<>(CellType.values());
    private final JTextField prompt = new JXTextField(placeholder);
    private final TitledBorder border = BorderFactory.createTitledBorder("[ ]");
    private final JButton runButton = new JButton("▶");
    private final JButton runBelowButton = new JButton("⏭");
    private final JButton collapseButton = new JButton("▾");
    private final JButton upButton = new JButton("↑");
    private final JButton downButton = new JButton("↓");
    // Windows doesn't show broom emoji properly
    private final JButton clearButton = new JButton(SystemInfo.isMacOS ? "🧹" : "⌫");
    private final JButton deleteButton = new JButton("⌦");
    private final Coder coder;
    private final String syntaxStyle;
    private CellType cellType = CellType.Code;
    private boolean collapsed = false;
    /** Running code generation. */
    private volatile boolean isCoding = false;
    // null means never executed, otherwise shows the prompt number in title.
    private Integer executionCount = null;

    /**
     * Constructor.
     * @param notebook the parent notebook.
     */
    public Cell(Notebook notebook) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));

        coder = notebook.coder();
        syntaxStyle = notebook.syntaxStyle();
        editor = new CellEditor(1, 80, syntaxStyle);
        editorScroll = editor.scroll();
        JPanel header = createHeader(notebook);
        initActionMap(notebook);

        // Cell editor and output configuration
        output.setFont(Monospaced.getFont());
        editor.setFont(Monospaced.getFont());
        editorScroll.setBorder(border);
        add(header, BorderLayout.NORTH);
        add(editorScroll, BorderLayout.CENTER);
    }

    /**
     * Returns the header of action buttons and prompt field.
     * @param notebook the parent notebook.
     * @return the header of action buttons and prompt field.
     */
    private JPanel createHeader(Notebook notebook) {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(Box.createHorizontalStrut(2));
        header.add(runButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(runBelowButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(collapseButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(upButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(downButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(clearButton);
        header.add(Box.createHorizontalStrut(6));
        header.add(deleteButton);

        initCellTypeComboBox();
        header.add(Box.createHorizontalStrut(20));
        header.add(cellTypeComboBox);

        header.add(Box.createHorizontalStrut(20));
        header.add(prompt);
        header.add(Box.createHorizontalStrut(2));

        prompt.addActionListener(e -> generateCode());
        prompt.putClientProperty("JComponent.roundRect", true);

        runButton.setToolTipText(bundle.getString("Run"));
        runBelowButton.setToolTipText(bundle.getString("RunBelow"));
        collapseButton.setToolTipText(bundle.getString("Collapse"));
        upButton.setToolTipText(bundle.getString("MoveUp"));
        downButton.setToolTipText(bundle.getString("MoveDown"));
        clearButton.setToolTipText(bundle.getString("Clear"));
        deleteButton.setToolTipText(bundle.getString("Delete"));

        runButton.addActionListener(e -> notebook.runCell(this, PostRunNavigation.STAY));
        runBelowButton.addActionListener(e -> notebook.runCellAndBelow(this));
        collapseButton.addActionListener(e -> setCollapsed(!collapsed));
        upButton.addActionListener(e -> notebook.moveCellUp(this));
        downButton.addActionListener(e -> notebook.moveCellDown(this));
        clearButton.addActionListener(e -> output.setText(""));
        deleteButton.addActionListener(e -> notebook.deleteCell(this));

        return header;
    }

    /** Initializes the cell type combo box. */
    private void initCellTypeComboBox() {
        cellTypeComboBox.setSelectedItem(CellType.Code);
        cellTypeComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                setType((CellType) e.getItem());
                editor.requestFocusInWindow();
            }
        });
    }

    /**
     * Sets up the action map of editor.
     * @param notebook the parent notebook.
     */
    private void initActionMap(Notebook notebook) {
        // Key bindings (inspired by Jupyter)
        InputMap inputMap = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = editor.getActionMap();

        if (coder != null && coder.llm() != null) {
            inputMap.put(KeyStroke.getKeyStroke("TAB"), "complete-code");
            actionMap.put("complete-code", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (isCoding) {
                        editor.insert("\t", editor.getCaretPosition());
                    } else {
                        completeCode();
                    }
                }
            });
        }
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

    }

    /**
     * Completes the current line of code.
     */
    private void completeCode() {
        if (coder == null || isCoding) return;

        try {
            int caretPosition = editor.getCaretPosition();
            int lineNum = editor.getLineOfOffset(caretPosition);
            // Skip code completion for first line.
            if (lineNum == 0) return;

            isCoding = true;
            int startOffset = editor.getLineStartOffset(lineNum);
            int endOffset = editor.getLineEndOffset(lineNum);
            String prefix = editor.getText(startOffset, endOffset - startOffset);
            String before = editor.getText(0, startOffset);
            String after = editor.getText(endOffset, editor.getDocument().getLength() - endOffset);

            // Run code completion in a worker thread as join() blocks.
            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    coder.complete(prefix, before, after).whenComplete((line, ex) -> {
                        if (ex != null) {
                            logger.warn("Code completion failed: {}", ex.getMessage());
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(Cell.this,
                                            "Code completion failed: " + ex.getMessage(),
                                            bundle.getString("AIService"),
                                            JOptionPane.ERROR_MESSAGE));
                        }

                        if (line != null) {
                            publish(line);
                        }
                    }).join();
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    // process and done are called in EDT, so we can safely update the UI here.
                    // Only one line is expected for code completion.
                    editor.insert(chunks.getFirst(), caretPosition);
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
        if (coder == null) {
            JOptionPane.showMessageDialog(this,
                    bundle.getString("NoAIServiceError"),
                    bundle.getString("AIService"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (isCoding) return;

        String task = prompt.getText();
        if (task.isBlank()) return;

        isCoding = true;
        editor.requestFocus();
        int position = editor.getSelectionEnd();
        // No selected text. Append at the end.
        if (position == 0) {
            editor.setCaretPosition(editor.getDocument().getLength());
        } else {
            try {
                int lineNumber = editor.getLineOfOffset(position);
                int lineEndOffset = editor.getLineEndOffset(lineNumber);
                editor.setCaretPosition(lineEndOffset);
            } catch (BadLocationException e) {
                editor.setCaretPosition(position);
            }
        }

        String before = getCodeGenerationBeforeContext();
        String after = getCodeGenerationAfterContext();
        editor.insert("\n", editor.getCaretPosition());
        for (String line : wrap(task, 80)) {
            editor.insert("/// " + line + "\n", editor.getCaretPosition());
        }

        // Run code completion in a worker thread as join() blocks.
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                coder.generate(task, before, after, new StreamResponseHandler() {
                    @Override
                    public void onNext(String chunk) {
                        publish(chunk);
                    }

                    @Override
                    public void onComplete(long totalTokens, long outputTokens, long inputTokens) {
                        logger.info("Code generation completed: totalTokens={}, outputTokens={}, inputTokens={}",
                                totalTokens, outputTokens, inputTokens);
                        // Appending a new line at the end of generated code.
                        publish("\n");
                    }

                    @Override
                    public void onException(Throwable ex) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(Cell.this,
                                        "Code generation failed: " + ex.getMessage(),
                                        bundle.getString("AIService"),
                                        JOptionPane.ERROR_MESSAGE));
                    }

                    @Override
                    public void onStatus(String status) {
                        logger.info("Code generation status: {}", status);
                    }

                });
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                // process and done are called in EDT, so we can safely update the UI here.
                String lines = String.join("", chunks);
                editor.insert(lines, editor.getCaretPosition());
            }

            @Override
            protected void done() {
                isCoding = false;
            }
        };
        worker.execute();
    }

    /**
     * Returns the before context for code generation.
     * @return the before context for code generation.
     */
    private String getCodeGenerationBeforeContext() {
        Cell prev = null; // previous cell before current cell
        Container parent = getParent(); // Get the notebook container
        if (parent != null) {
            Component[] siblings = parent.getComponents(); // Get all children
            for (Component comp : siblings) {
                if (comp instanceof Cell cell) {
                    if (comp == this) break;
                    prev = cell;
                }
            }
        }

        if (prev == null) return editor.getText();
        return prev.editor.getText() + "\n\n" + editor.getText();
    }

    /**
     * Returns the before context for code generation.
     * @return the before context for code generation.
     */
    private String getCodeGenerationAfterContext() {
        Cell next = null; // next cell after current cell
        Container parent = getParent(); // Get the notebook container
        if (parent != null) {
            boolean found = false;
            Component[] siblings = parent.getComponents(); // Get all children
            for (Component comp : siblings) {
                if (comp instanceof Cell cell) {
                    if (found) next = cell;
                    else if (comp == this) found = true;
                }
            }
        }

        if (next == null) return "";
        return next.editor.getText();
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
     * Runs the code in the cell using the given kernel,
     * and updates the output area with execution results.
     * @param kernel the kernel to execute code.
     * @param executionCount the execution count of cells.
     *                       It is also used for generating title after execution.
     * @return true if code runs successfully; false if any error occurs during execution.
     */
    public boolean run(Kernel<?> kernel, int executionCount) {
        if (cellType == CellType.Raw) return true;

        // Render markdown content and remove editor.
        if (cellType == CellType.Markdown) {
            String text = editor.getText();
            var markdown = new Markdown(text);
            var html = markdown.contentPane();
            if (html == null) return false;

            remove(editorScroll);
            add(html, BorderLayout.CENTER);
            html.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // Return to edit mode when double-click
                    if (e.getClickCount() == 2) {
                        remove(html);
                        add(editorScroll, BorderLayout.CENTER);
                        revalidate();
                        repaint();
                    }
                }
            });
            return true;
        }

        kernel.setOutputArea(output()); // Direct JShell prints
        SwingUtilities.invokeLater(() -> {
            setRunning(true);
            editor().setPreferredRows();
            add(output, BorderLayout.SOUTH);
        });

        try {
            output().clear();
            ZonedDateTime start = ZonedDateTime.now();
            output().println("⏵ " + datetime.format(start) + " started");

            List<Object> values = new ArrayList<>();
            var code = editor().getText();
            boolean success = kernel.eval(code, values);

            ZonedDateTime end = ZonedDateTime.now();
            Duration duration = Duration.between(start, end);
            output().println("⏹ " + datetime.format(end) + " finished (" + duration + ")");
            return success;
        } catch (Throwable t) {
            output().println("✖ ERROR during execution: " + t);
            logger.error("Error during execution: ", t);
            return false;
        } finally {
            kernel.removeOutputArea();;
            SwingUtilities.invokeLater(() -> {
                setRunning(false);
                setExecutionCount(executionCount);
                output().flush();
            });
        }
    }

    /**
     * Sets the cell running state.
     * @param running the running state.
     */
    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        runBelowButton.setEnabled(!running);
        collapseButton.setEnabled(!running);
        upButton.setEnabled(!running);
        downButton.setEnabled(!running);
        deleteButton.setEnabled(!running);
        editor.setEnabled(!running);
        if (running) {
            if (collapsed) setCollapsed(false);
            border.setTitle("[*]");
            output.setText("");
        }
    }

    /**
     * Sets collapsed/expanded state of the cell body.
     * @param collapsed true to collapse body; false to expand.
     */
    private void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        editorScroll.setVisible(!collapsed);
        output.setVisible(!collapsed);
        prompt.setEnabled(!collapsed);
        collapseButton.setText(collapsed ? "▸" : "▾");
        collapseButton.setToolTipText(bundle.getString(collapsed ? "Expand" : "Collapse"));
        updateCollapsedInfo();
        revalidate();
        repaint();
    }

    /**
     * Updates collapsed cell header with a short code preview.
     */
    private void updateCollapsedInfo() {
        if (!collapsed) {
            prompt.setText("");
            return;
        }

        String info = bundle.getString("CollapsedEmpty");
        String text = editor.getText();
        if (text != null && !text.isBlank()) {
            var lines = text.split("\\R", 2);
            info = lines[0].trim() + "  (" + Math.max(editor.getLineCount(), 1) + " lines)";
        }
        prompt.setText(info);
    }

    /**
     * Returns the cell type.
     * @return the cell type.
     */
    public CellType type() {
        return cellType;
    }

    /**
     * Sets the cell type.
     * @param type the cell type.
     */
    public void setType(CellType type) {
        cellType = type;
        cellTypeComboBox.setSelectedItem(type);
        if (type == CellType.Code) {
            editor.setLineWrap(false);
            editor.setWrapStyleWord(false);
        } else {
            editor.setLineWrap(true);
            editor.setWrapStyleWord(true);
        }

        switch (type) {
            case Code ->
                    editor.setSyntaxEditingStyle(syntaxStyle);
            case Markdown ->
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
            case Raw ->
                    editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
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
    public CellEditor editor() {
        return editor;
    }

    /**
     * Returns the output area.
     * @return the output area.
     */
    public OutputArea output() {
        return output;
    }

    /**
     * Returns the execution count of the cell, or null if never executed.
     * @return the execution count of the cell, or null if never executed.
     */
    public Integer getExecutionCount() {
        return executionCount;
    }

    /**
     * Sets the execution count of the cell and updates the title.
     * @param count the execution count of the cell.
     */
    private void setExecutionCount(int count) {
        executionCount = count;
        setTitle("[" + count + "]");
    }
}
