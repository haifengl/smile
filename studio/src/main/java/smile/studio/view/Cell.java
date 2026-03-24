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
import ioa.agent.Coder;
import ioa.llm.client.StreamResponseHandler;
import smile.studio.kernel.PostRunNavigation;

/**
 * A cell is a multiline coding field, and its contents can be executed
 * by Java engine.
 *
 * @author Haifeng Li
 */
public class Cell extends JPanel implements StreamResponseHandler {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Cell.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Cell.class.getName(), Locale.getDefault());
    private final String placeholder = bundle.getString("Prompt");
    private final CellEditor editor;
    private final RTextScrollPane editorScroll;
    private final OutputArea output = new OutputArea();
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
    private boolean collapsed = false;
    /** Running code generation. */
    private volatile boolean isCoding = false;

    /**
     * Constructor.
     * @param notebook the parent notebook.
     */
    public Cell(Notebook notebook) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));

        coder = notebook.coder();
        editor = new CellEditor(1, 80, notebook.syntaxStyle());
        editorScroll = editor.scroll();
        JPanel header = createHeader(notebook);
        initActionMap(notebook);

        // Cell editor and output configuration
        output.setFont(Monospaced.getFont());
        editor.setFont(Monospaced.getFont());
        editorScroll.setBorder(border);
        add(header, BorderLayout.NORTH);
        add(editorScroll, BorderLayout.CENTER);
        add(output, BorderLayout.SOUTH);
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
        header.add(Box.createHorizontalStrut(30));
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
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    coder.complete(prefix, before, after).whenComplete((line, ex) -> {
                        if (ex != null) {
                            logger.warn("Code completion failed: {}", ex.getMessage());
                        }

                        if (line != null) {
                            SwingUtilities.invokeLater(() -> editor.insert(line, caretPosition));
                        }
                    }).join();
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
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                coder.generate(task, before, after, Cell.this);
                return null;
            }

            @Override
            protected void done() {
                isCoding = false;
            }
        };
        worker.execute();
    }

    @Override
    public void onNext(String chunk) {
        SwingUtilities.invokeLater(() -> editor.insert(chunk, editor.getCaretPosition()));
    }

    @Override
    public void onComplete(long totalTokens, long outputTokens, long inputTokens) {
        // Appending a new line at the end of generated code.
        SwingUtilities.invokeLater(() -> editor.insert("\n", editor.getCaretPosition()));
    }

    @Override
    public void onException(Throwable ex) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(Cell.this,
                "Code generation failed: " + ex.getMessage(),
                bundle.getString("AIService"),
                JOptionPane.ERROR_MESSAGE));
    }

    @Override
    public void onStatus(String status) {
        logger.info("Code generation status: {}", status);
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
}
