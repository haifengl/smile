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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.ResourceBundle;

import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.util.FontUtils;
import org.fife.ui.rtextarea.RTextScrollPane;
import smile.studio.model.RunBehavior;

/**
 * A cell is a multiline text input field, and its contents can be executed.
 * The execution behavior of a cell is determined by the cell's type.
 * There are three types of cells: code cells, markdown cells, and raw cells.
 *
 * @author Haifeng Li
 */
public class Cell extends JPanel {
    /** Editor font shared across all cells. */
    private static Font editorFont = FontUtils.getCompositeFont(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 14);
    /** Output font shared across all cells. */
    private static Font outputFont = FontUtils.getCompositeFont(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 12);
    /** The message resource bundle. */
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Cell.class.getName(), Locale.getDefault());
    /** The output buffer. StringBuffer is multi-thread safe while StringBuilder isn't. */
    final StringBuffer buffer = new StringBuffer();
    final JTextArea editor = new CodeEditor();
    final JTextArea output = new JTextArea();
    final TitledBorder border = BorderFactory.createTitledBorder("[ ]");
    final JButton runBtn = new JButton("▶");
    final JButton upBtn = new JButton("↑");
    final JButton downBtn = new JButton("↓");
    final JButton clearBtn = new JButton("⌦");
    final JButton deleteBtn = new JButton("🗑");

    /** Constructor. */
    public Cell(Notebook notebook) {
        super(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(8,8,8,8));

        // Header (cell controls)
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.add(runBtn);
        header.add(upBtn);
        header.add(downBtn);
        header.add(clearBtn);
        header.add(deleteBtn);

        runBtn.setToolTipText(bundle.getString("Run"));
        upBtn.setToolTipText(bundle.getString("MoveUp"));
        downBtn.setToolTipText(bundle.getString("MoveDown"));
        clearBtn.setToolTipText(bundle.getString("Clear"));
        deleteBtn.setToolTipText(bundle.getString("Delete"));

        runBtn.addActionListener(e -> notebook.runCell(this, RunBehavior.STAY));
        upBtn.addActionListener(e -> notebook.moveCellUp(this));
        downBtn.addActionListener(e -> notebook.moveCellDown(this));
        clearBtn.addActionListener(e -> output.setText(""));
        deleteBtn.addActionListener(e -> notebook.deleteCell(this));

        // Code area
        editor.setFont(editorFont);
        editor.setTabSize(4);
        editor.setLineWrap(false);
        editor.setWrapStyleWord(false);
        RTextScrollPane editorScroll = new RTextScrollPane(editor);
        editorScroll.setBorder(border);
        editorScroll.putClientProperty("JScrollBar.showButtons", true);

        // Key bindings (inspired by Jupyter)
        InputMap inputMap = editor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = editor.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "run-next");
        actionMap.put("run-next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                notebook.runCell(Cell.this, RunBehavior.NEXT_OR_NEW);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "run-stay");
        actionMap.put("run-stay", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                notebook.runCell(Cell.this, RunBehavior.STAY);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke("alt ENTER"), "run-insert");
        actionMap.put("run-insert", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                notebook.runCell(Cell.this, RunBehavior.INSERT_BELOW);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "increase-font-size");
        actionMap.put("increase-font-size", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                editorFont = editorFont.deriveFont(Math.min(32f, editorFont.getSize() + 1));
                editor.setFont(editorFont);

                outputFont = outputFont.deriveFont(Math.min(32f, outputFont.getSize() + 1));
                output.setFont(outputFont);
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "decrease-font-size");
        actionMap.put("decrease-font-size", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                editorFont = editorFont.deriveFont(Math.max(8f, editorFont.getSize() - 1));
                editor.setFont(editorFont);

                outputFont = outputFont.deriveFont(Math.max(8f, outputFont.getSize() - 1));
                output.setFont(outputFont);
            }
        });

        // Output area
        output.setEditable(false);
        output.setBackground(getBackground());
        output.setFont(outputFont);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);

        add(header, BorderLayout.NORTH);
        add(editorScroll, BorderLayout.CENTER);
        //add(output, BorderLayout.SOUTH);
    }

    /**
     * Sets the cell running state.
     * @param running the running state.
     */
    public void setRunning(boolean running) {
        runBtn.setEnabled(!running);
        upBtn.setEnabled(!running);
        downBtn.setEnabled(!running);
        deleteBtn.setEnabled(!running);
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
     * Sets the output.
     * @param text the output.
     */
    public void setOutput(String text) {
        output.setText(text);
        if (text.isEmpty()) {
            remove(output);
        } else {
            add(output, BorderLayout.SOUTH);
        }
    }

    /**
     * Returns the code editor.
     * @return the code editor.
     */
    public JTextArea editor() {
        return editor;
    }

    /**
     * Returns the output area.
     * @return the output area.
     */
    public JTextArea output() {
        return output;
    }

    /**
     * Returns the output buffer.
     * @return the output buffer.
     */
    public StringBuffer buffer() {
        return buffer;
    }
}
