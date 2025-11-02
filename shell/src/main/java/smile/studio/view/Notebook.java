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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jdk.jshell.*;
import smile.studio.model.RunBehavior;
import smile.studio.model.Runner;

/**
 * Interactive environment to write and execute Java code combining code,
 * documentation, and visualizations. The notebook consists of a sequence
 * of cells.
 *
 * @author Haifeng Li
 */
public class Notebook extends JPanel implements DocumentListener {
    private final static String CELL_SEPARATOR = "//--- CELL ---";
    private final JPanel cells = new JPanel();
    private final JScrollPane scrollPane = new JScrollPane(cells);
    private final DateTimeFormatter datetime = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final Runner runner;
    private int runCount = 0;
    private File file;
    private boolean saved = false;

    /**
     * Constructor.
     */
    public Notebook(Runner runner) {
        super(new BorderLayout());
        this.runner = runner;
        cells.setLayout(new BoxLayout(cells, BoxLayout.Y_AXIS));
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        add(scrollPane, BorderLayout.CENTER);

        // Start with one cell
        addCell(null);
    }

    /**
     * Returns the notebook file.
     * @return the notebook file.
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the notebook file.
     * @param file the notebook file.
     */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Opens a notebook.
     * @param file the notebook file.
     * @throws IOException If an I/O error occurs.
     */
    public void open(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> snippets = parseCells(lines);
        cells.removeAll();
        for (String src : snippets) {
            Cell cell = new Cell(this);
            cell.editor.setText(src);
            cell.editor.getDocument().addDocumentListener(this);
            cells.add(cell);
        }
        if (snippets.isEmpty()) addCell(null);
        cells.revalidate();
        cells.repaint();
        this.file = file;
    }

    /**
     * Saves the notebook to file.
     * @throws IOException If an I/O error occurs.
     */
    public void save() throws IOException {
        if (file == null) throw new IOException("Notebook file is null");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < cells.getComponentCount(); i++) {
            Cell cell = getCell(i);
            lines.addAll(codeToLines(cell.editor.getText()));
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        saved = true;
    }

    /**
     * Returns true if the notebook is saved.
     * @return true if the notebook is saved.
     */
    public boolean isSaved() {
        return saved;
    }

    private static List<String> parseCells(List<String> lines) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.trim().equals(CELL_SEPARATOR)) {
                if (!current.isEmpty()) cells.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(line).append(System.lineSeparator());
            }
        }
        return cells;
    }

    /**
     * Splits code snippet into lines.
     * @param code the code snippet.
     * @return the code lines.
     * @throws IOException if
     */
    private static List<String> codeToLines(String code) throws IOException {
        String line = CELL_SEPARATOR;
        List<String> lines = new ArrayList<>();
        lines.add(line);
        try (BufferedReader br = new BufferedReader(new StringReader(code))) {
            while ((line = br.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        saved = false;
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        saved = false;
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // This method is typically not used for plain text changes
        // but for changes to attributes of styled text.
    }

    /**
     * Adds a new cell.
     * @param insertAfter adds the new cell after this one.
     * If null, add the cell at the end of notebook.
     */
    public Cell addCell(Cell insertAfter) {
        Cell cell = new Cell(this);
        cell.editor.getDocument().addDocumentListener(this);
        int idx = (insertAfter == null) ? cells.getComponentCount()
                                        : indexOf(insertAfter) + 1;
        cells.add(cell, idx);
        cells.revalidate();
        cells.repaint();

        SwingUtilities.invokeLater(() -> {
            cell.editor.requestFocusInWindow();
            scrollTo(cell);
        });
        return cell;
    }

    /**
     * Deletes a cell.
     * @param cell the cell to delete.
     */
    public void deleteCell(Cell cell) {
        if (cells.getComponentCount() == 1) {
            cell.editor.setText("");
            cell.output.setText("");
            return;
        }

        int idx = indexOf(cell);
        cells.remove(cell);
        cells.revalidate();
        cells.repaint();
        focusCell(Math.max(0, idx - 1));
    }

    /**
     * Moves up a cell.
     * @param cell the cell to move.
     */
    public void moveCellUp(Cell cell) {
        int idx = indexOf(cell);
        if (idx > 0) {
            cells.remove(cell);
            cells.add(cell, idx - 1);
            cells.revalidate();
            cells.repaint();
            scrollTo(cell);
        }
    }

    /**
     * Moves down a cell.
     * @param cell the cell to move.
     */
    public void moveCellDown(Cell cell) {
        int idx = indexOf(cell);
        if (idx < cells.getComponentCount() - 1) {
            cells.remove(cell);
            cells.add(cell, idx + 1);
            cells.revalidate();
            cells.repaint();
            scrollTo(cell);
        }
    }

    /**
     * Scroll the notebook to the given component.
     * @param c the component.
     */
    public void scrollTo(Component c) {
        Rectangle r = c.getBounds();
        scrollPane.getViewport().scrollRectToVisible(r);
    }

    /**
     * Returns the index of cell.
     * @param cell a cell in this notebook.
     * @return the index of cell or -1 if not found.
     */
    public int indexOf(Cell cell) {
        for (int i = 0; i < cells.getComponentCount(); i++) {
            if (cells.getComponent(i) == cell) return i;
        }
        return -1;
    }

    /**
     * Returns the cell at specific index.
     * @param index the cell index.
     * @return the cell.
     */
    public Cell getCell(int index) {
        return (Cell) cells.getComponent(index);
    }

    /**
     * Focus on the cell at given cell.
     * @param index the cell index.
     */
    public void focusCell(int index) {
        Cell c = getCell(index);
        c.editor.requestFocusInWindow();
    }

    public synchronized void runCell(Cell cell, RunBehavior behavior) {
        if (runner.isRunning()) {
            JOptionPane.showMessageDialog(this,
                    "A cell is currently running. Please wait.",
                    "Execution in progress",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String code = cell.editor.getText();
        if (code.trim().isEmpty()) {
            // Honor the navigation behavior even on empty run
            handlePostRunNav(cell, behavior);
            return;
        }

        runner.setRunning(true);
        cell.setRunning(true);
        cell.setOutput("");
        StringBuilder outBuff = new StringBuilder();
        String stamp = datetime.format(ZonedDateTime.now());
        appendLine(outBuff, "⏵ " + stamp + " started");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                try {
                    runCount++;
                    // Direct JShell prints for THIS thread into this buffer
                    runner.setOutBuffer(outBuff);

                    // Evaluate
                    List<SnippetEvent> events = runner.eval(code);

                    // Capture diagnostics, values, and exceptions in order
                    for (SnippetEvent ev : events) {
                        if (ev.status() == Snippet.Status.VALID) {
                            if (ev.value() != null) {
                                appendLine(outBuff, "⇒ " + ev.value());
                            }
                        } else if (ev.status() == Snippet.Status.REJECTED) {
                            appendLine(outBuff, "✖ Rejected snippet: " + ev.snippet().source());
                        } else if (ev.status() == Snippet.Status.RECOVERABLE_DEFINED ||
                                   ev.status() == Snippet.Status.RECOVERABLE_NOT_DEFINED) {
                            appendLine(outBuff, "⚠ Recoverable issue.");
                        }

                        runner.diagnostics(ev.snippet()).forEach(d -> {
                            String kind = d.isError() ? "ERROR" : "WARN";
                            appendLine(outBuff, String.format("%s: %s",
                                    kind, d.getMessage(Locale.getDefault())));
                        });

                        if (ev.exception() instanceof EvalException ex) {
                            appendLine(outBuff, "Exception: " + ex.getExceptionClassName());
                            if (ex.getMessage() != null) {
                                appendLine(outBuff, ex.getMessage());
                            }
                            // JShell exception stack trace is often concise
                            for (StackTraceElement ste : ex.getStackTrace()) {
                                appendLine(outBuff, "  at " + ste.toString());
                            }
                        }
                    }
                } catch (Throwable t) {
                    appendLine(outBuff, "✖ Error during execution: " + t);
                } finally {
                    runner.removeOutBuffer();
                }
                return null;
            }

            @Override protected void done() {
                String stamp = datetime.format(ZonedDateTime.now());
                appendLine(outBuff, "⏹ " + stamp + " finished");
                cell.setOutput(outBuff.toString());
                cell.output.setCaretPosition(0);
                cell.setRunning(false);
                cell.setTitle("[" + runCount + "]");
                runner.setRunning(false);

                // Post-run navigation
                handlePostRunNav(cell, behavior);
            }
        };
        worker.execute();
    }

    private void handlePostRunNav(Cell cell, RunBehavior behavior) {
        switch (behavior) {
            case STAY -> SwingUtilities.invokeLater(cell.editor::requestFocusInWindow);
            case NEXT_OR_NEW -> {
                int idx = indexOf(cell);
                if (idx < cells.getComponentCount() - 1) {
                    Cell next = getCell(idx + 1);
                    SwingUtilities.invokeLater(next.editor::requestFocusInWindow);
                    scrollTo(next);
                } else {
                    addCell(cell);
                }
            }
            case INSERT_BELOW -> {
                addCell(cell);
            }
        }
    }

    public void runAllCells() {
        if (runner.isRunning()) {
            JOptionPane.showMessageDialog(this,
                    "A cell is currently running. Please wait.",
                    "Execution in progress",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Sequentially run all non-empty cells
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < this.cells.getComponentCount(); i++) {
            cells.add(getCell(i));
        }
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                runner.setRunning(true);
                for (Cell cell : cells) {
                    if (cell.editor.getText().trim().isEmpty()) continue;
                    runCount++;
                    SwingUtilities.invokeLater(() -> cell.setRunning(true));
                    StringBuilder outBuff = new StringBuilder();
                    runner.setOutBuffer(outBuff);
                    try {
                        appendLine(outBuff, "⏵ " + datetime.format(ZonedDateTime.now()) + " started");
                        List<SnippetEvent> events = runner.eval(cell.editor.getText());
                        for (SnippetEvent ev : events) {
                            if (ev.status() == Snippet.Status.VALID && ev.value() != null) {
                                appendLine(outBuff, "⇒ " + ev.value());
                            }
                            runner.diagnostics(ev.snippet()).forEach(d -> {
                                String kind = d.isError() ? "ERROR" : "WARN";
                                appendLine(outBuff, String.format("%s: %s (%s)",
                                        kind, d.getMessage(Locale.getDefault()),
                                        ev.snippet().source()));
                            });
                            if (ev.exception() instanceof EvalException ex) {
                                appendLine(outBuff, "Exception: " + ex.getExceptionClassName());
                                if (ex.getMessage() != null) appendLine(outBuff, ex.getMessage());
                            }
                        }
                        appendLine(outBuff, "⏹ " + datetime.format(ZonedDateTime.now()) + " finished");
                    } catch (Throwable t) {
                        appendLine(outBuff, "✖ Error during execution: " + t);
                    } finally {
                        runner.removeOutBuffer();
                        final String text = outBuff.toString();
                        SwingUtilities.invokeLater(() -> {
                            cell.setOutput(text);
                            cell.output.setCaretPosition(0);
                            cell.setRunning(false);
                        });
                    }
                }
                return null;
            }

            @Override protected void done() {
                runner.setRunning(false);
            }
        };
        worker.execute();
    }

    /**
     * Appends a line to buffer.
     * @param sb string buffer.
     * @param s a new line.
     */
    private static void appendLine(StringBuilder sb, String s) {
        sb.append(s).append(System.lineSeparator());
    }

    /**
     * Clear the outputs of all cells.
     */
    public void clearAllOutputs() {
        for (int i = 0; i < cells.getComponentCount(); i++) {
            getCell(i).setOutput("");
        }
    }
}
