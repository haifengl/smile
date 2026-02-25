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

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import jdk.jshell.*;
import smile.agent.Coder;
import smile.studio.SmileStudio;
import smile.studio.kernel.PostRunNavigation;
import smile.studio.kernel.JavaRunner;
import smile.swing.ScrollablePanel;

/**
 * Interactive environment to write and execute Java code combining code,
 * documentation, and visualizations. The notebook consists of a sequence
 * of cells.
 *
 * @author Haifeng Li
 */
public class Notebook extends JPanel implements DocumentListener {
    private static final String CELL_SEPARATOR = "//--- CELL ---";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Notebook.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Notebook.class.getName(), Locale.getDefault());
    private final JPanel cells = new ScrollablePanel();
    private final JScrollPane scrollPane = new JScrollPane(cells);
    private final DateTimeFormatter datetime = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    /** The coding assistant agent. */
    private final Coder coder;
    private final JavaRunner runner;
    private final Runnable postRunAction;
    private int runCount = 0;
    private Path file;
    private boolean saved = true;

    /**
     * Constructor.
     * @param file the notebook file. If null, a new notebook will be created.
     * @param runner Java code execution engine.
     * @param postRunAction the action to perform after running cells.
     */
    public Notebook(Path file, JavaRunner runner, Runnable postRunAction) {
        super(new BorderLayout());
        this.file = file;
        this.runner = runner;
        this.postRunAction = postRunAction;
        this.coder = new Coder(SmileStudio::llm, Path.of(System.getProperty("smile.home") + "/agents/java-coder"));

        coder.loadMemory(file.getParent().resolve(".smile", "coder"));
        cells.setLayout(new BoxLayout(cells, BoxLayout.Y_AXIS));
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // Note that JShell runs in another JVM so that
        // we need to set up FlatLaf again.
        runner.eval("""
            javax.swing.SwingUtilities.invokeLater(() -> {
                com.formdev.flatlaf.FlatLightLaf.setup();
            });""");

        if (Files.exists(file)) {
            try {
                open(file);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                        null,
                        ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        } else {
            // Start with one cell
            Cell cell = addCell(null);
            cell.editor().setText("""
                    import java.awt.Color;
                    import java.time.*;
                    import java.util.*;
                    import static java.lang.Math.*;
                    import smile.plot.swing.*;
                    import static smile.swing.SmileUtilities.*;
                    
                    import org.apache.commons.csv.CSVFormat;
                    import smile.io.*;
                    import smile.data.*;
                    import smile.data.formula.*;
                    import smile.data.measure.*;
                    import smile.data.type.*;
                    import smile.data.vector.*;
                    import static smile.data.formula.Terms.*;
                    import smile.feature.extraction.*;
                    import smile.feature.importance.*;
                    import smile.feature.imputation.*;
                    import smile.feature.selection.*;
                    import smile.feature.transform.*;
                    import smile.tensor.*;
                    import smile.graph.*;
                    import smile.math.*;
                    import smile.math.distance.*;
                    import smile.math.kernel.*;
                    import smile.math.rbf.*;
                    import smile.stat.*;
                    import smile.stat.distribution.*;
                    import smile.stat.hypothesis.*;
                    import smile.model.*;
                    import smile.model.mlp.*;
                    import smile.association.*;
                    import smile.classification.*;
                    import smile.clustering.*;
                    import smile.manifold.*;
                    import smile.regression.OLS;
                    import smile.regression.LASSO;
                    import smile.regression.ElasticNet;
                    import smile.regression.RidgeRegression;
                    import smile.regression.GaussianProcessRegression;
                    import smile.regression.RegressionTree;
                    import smile.validation.*;
                    import smile.validation.metric.*;
                    import smile.hpo.*;
                    import smile.vq.*;""");
            cell.editor().setPreferredRows();
            // Add an empty cell for user to start with
            cell = addCell(null);
            cell.editor().requestFocus();
        }

        if (cells.getComponentCount() > 0 && cells.getComponent(0) instanceof Cell first) {
            // Scroll to the first cell
            SwingUtilities.invokeLater(() -> {
                first.editor().requestFocusInWindow();
                first.editor().setCaretPosition(0);
                scrollTo(first.editor());
            });
        }

        Monospaced.addListener((e) ->
                SwingUtilities.invokeLater(() -> {
                    Font font = (Font) e.getNewValue();
                    for (int i = 0; i < cells.getComponentCount(); i++) {
                        if (cells.getComponent(i) instanceof Cell cell) {
                            cell.editor().setFont(font);
                            cell.output().setFont(font);
                        }
                    }
                })
        );
    }

    /**
     * Returns the coding assistant agent.
     * @return the coding assistant agent.
     */
    public Coder coder() {
        return coder;
    }

    /**
     * Returns the notebook file.
     *
     * @return the notebook file.
     */
    public Path getFile() {
        return file;
    }

    /**
     * Sets the notebook file.
     *
     * @param file the notebook file.
     */
    public void setFile(Path file) {
        this.file = file;
    }

    /**
     * Opens a notebook.
     *
     * @param file the notebook file.
     * @throws IOException If an I/O error occurs.
     */
    public void open(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> snippets = parseCells(lines);
        cells.removeAll();
        for (String src : snippets) {
            Cell cell = new Cell(this);
            cell.editor().setText(src);
            cell.editor().getDocument().addDocumentListener(this);
            cells.add(cell);
        }
        if (snippets.isEmpty()) addCell(null);
        cells.revalidate();
        cells.repaint();
        this.file = file;
    }

    /**
     * Saves the notebook to file.
     *
     * @throws IOException If an I/O error occurs.
     */
    public void save() throws IOException {
        if (file == null) throw new IOException("Notebook file is null");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < cells.getComponentCount(); i++) {
            Cell cell = getCell(i);
            lines.addAll(codeToLines(cell.editor().getText()));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        saved = true;
    }

    /**
     * Returns true if the notebook is saved.
     *
     * @return true if the notebook is saved.
     */
    public boolean isSaved() {
        return saved;
    }

    /**
     * Sets the flag if the notebook is saved.
     * @param saved the flag if the notebook is saved.
     */
    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    /**
     * Parses code lines into cells.
     * @param lines code lines.
     * @return the list of cells.
     */
    private static List<String> parseCells(List<String> lines) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.trim().equals(CELL_SEPARATOR)) {
                if (!current.isEmpty()) cells.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(line).append('\n');
            }
        }
        // The last cell may not end with separator.
        if (!current.isEmpty()) cells.add(current.toString());
        return cells;
    }

    /**
     * Splits code snippet into lines.
     *
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
     *
     * @param insertAfter adds the new cell after this one.
     *                    If null, add the cell at the end of notebook.
     */
    public Cell addCell(Cell insertAfter) {
        Cell cell = new Cell(this);
        cell.editor().getDocument().addDocumentListener(this);
        int idx = (insertAfter == null) ? cells.getComponentCount()
                                        : indexOf(insertAfter) + 1;
        cells.add(cell, idx);
        cells.revalidate();
        cells.repaint();

        SwingUtilities.invokeLater(() -> {
            cell.editor().requestFocusInWindow();
            scrollTo(cell.editor());
        });
        return cell;
    }

    /**
     * Deletes a cell.
     *
     * @param cell the cell to delete.
     */
    public void deleteCell(Cell cell) {
        if (cells.getComponentCount() == 1) {
            cell.editor().setText("");
            cell.output().setText("");
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
     *
     * @param cell the cell to move.
     */
    public void moveCellUp(Cell cell) {
        int idx = indexOf(cell);
        if (idx > 0) {
            cells.remove(cell);
            cells.add(cell, idx - 1);
            cells.revalidate();
            cells.repaint();
            scrollTo(cell.editor());
        }
    }

    /**
     * Moves down a cell.
     *
     * @param cell the cell to move.
     */
    public void moveCellDown(Cell cell) {
        int idx = indexOf(cell);
        if (idx < cells.getComponentCount() - 1) {
            cells.remove(cell);
            cells.add(cell, idx + 1);
            cells.revalidate();
            cells.repaint();
            scrollTo(cell.editor());
        }
    }

    /**
     * Scroll the notebook to the given component.
     *
     * @param c the component.
     */
    public void scrollTo(Component c) {
        Rectangle r = c.getBounds();
        scrollPane.getViewport().scrollRectToVisible(r);
    }

    /**
     * Returns the index of cell.
     *
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
     *
     * @param index the cell index.
     * @return the cell.
     */
    public Cell getCell(int index) {
        return (Cell) cells.getComponent(index);
    }

    /**
     * Focus on the cell at given cell.
     *
     * @param index the cell index.
     */
    public void focusCell(int index) {
        Cell cell = getCell(index);
        cell.editor().requestFocusInWindow();
    }

    /**
     * Evaluates the code of cell.
     * @param cell the cell to evaluate.
     * @return true if code evaluation succeeded without exceptions.
     */
    private boolean runCell(Cell cell) {
        runCount++;
        runner.setOutputArea(cell.output()); // Direct JShell prints
        SwingUtilities.invokeLater(() -> {
            cell.setRunning(true);
            cell.editor().setPreferredRows();
        });

        try {
            cell.output().clear();
            ZonedDateTime start = ZonedDateTime.now();
            cell.output().appendLine("⏵ " + datetime.format(start) + " started");
            boolean okay = runner.eval(cell.editor().getText(), (events) -> {
                // The number of errors;
                int errors = 0;
                // Capture values, diagnostics, and exceptions in order
                for (SnippetEvent event : events) {
                    if (event.status() == Snippet.Status.VALID && event.snippet() instanceof VarSnippet variable) {
                        if (!variable.name().matches("\\$\\d+")) {
                            String typeName = variable.typeName();
                            cell.output().appendBuffer("⇒ " + typeName + " " + variable.name() + " = ");

                            String value = event.value();
                            if (value == null) {
                                cell.output().appendLine("null");
                            } else {
                                if (typeName.endsWith("DataFrame")) {
                                    cell.output().appendBuffer("\n");
                                } else if (typeName.contains("[]")) {
                                    // The type may be generic with array, e.g., SVM<double[]>
                                    int index = value.indexOf('{');
                                    if (index > 0) {
                                        value = value.substring(0, index);
                                    }
                                }
                                cell.output().appendLine(value);
                            }
                        }
                    } else if (event.status() == Snippet.Status.REJECTED) {
                        errors++;
                        cell.output().appendLine("✖ Rejected snippet: " + event.snippet().source());
                    } else if (event.status() == Snippet.Status.RECOVERABLE_DEFINED ||
                               event.status() == Snippet.Status.RECOVERABLE_NOT_DEFINED) {
                        errors++;
                        cell.output().appendLine("⚠ Recoverable issue: " + event.snippet().source());
                        if (event.snippet() instanceof DeclarationSnippet snippet) {
                            cell.output().appendLine("⚠ Unresolved dependencies:");
                            runner.unresolvedDependencies(snippet).forEach(name -> cell.output().appendLine("  └ " + name));
                        }
                    }

                    errors += runner.diagnostics(event.snippet()).mapToInt(diag -> {
                        String kind = diag.isError() ? "ERROR" : "WARN";
                        cell.output().appendLine(String.format("%s: %s",
                                kind, diag.getMessage(Locale.getDefault())));
                        return diag.isError() ? 1 : 0;
                    }).sum();

                    if (event.exception() instanceof EvalException ex) {
                        errors++;
                        cell.output().appendLine(ex.getExceptionClassName() + ": " + (ex.getMessage() != null ? ex.getMessage() : ""));
                        // JShell exception stack trace is often concise
                        for (StackTraceElement ste : ex.getStackTrace()) {
                            cell.output().appendLine("  at " + ste.toString());
                        }
                    }
                }
                return errors;
            });

            ZonedDateTime end = ZonedDateTime.now();
            Duration duration = Duration.between(start, end);
            cell.output().appendLine("⏹ " + datetime.format(end) + " finished (" + duration + ")");
            return okay;
        } catch (Throwable t) {
            cell.output().appendLine("✖ ERROR during execution: " + t);
            logger.error("Error during execution: ", t);
            return false;
        } finally {
            runner.removeOutputArea();;
            // Generates title before calling invokeLater
            // as runCount may have changed in case of runAllCells.
            String title = "[" + runCount + "]";
            SwingUtilities.invokeLater(() -> {
                cell.setRunning(false);
                cell.setTitle(title);
                cell.output().flush();
            });
        }
    }

    /**
     * Shows the dialog that code evaluation is running.
     */
    private void showRaceConditionDialog() {
        JOptionPane.showMessageDialog(this,
                bundle.getString("RaceConditionMessage"),
                bundle.getString("RaceConditionTitle"),
                JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Evaluates a cell and handles post-run navigation.
     * @param cell the cell to evaluate.
     * @param behavior post-run navigation behavior.
     */
    public synchronized void runCell(Cell cell, PostRunNavigation behavior) {
        if (runner.isRunning()) {
            showRaceConditionDialog();
            return;
        }

        final String code = cell.editor().getText();
        if (code.trim().isEmpty()) {
            // Honor the navigation behavior even on empty run
            handlePostRunNav(cell, behavior);
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                runner.setRunning(true);
                runCell(cell);
                return null;
            }

            @Override
            protected void done() {
                runner.setRunning(false);
                // Post-run actions
                handlePostRunNav(cell, behavior);
                SwingUtilities.invokeLater(postRunAction);
            }
        };
        worker.execute();
    }

    /**
     * Handles post-run navigation.
     * @param cell the cell evaluated.
     * @param behavior post-run navigation behavior.
     */
    private void handlePostRunNav(Cell cell, PostRunNavigation behavior) {
        switch (behavior) {
            case STAY -> SwingUtilities.invokeLater(cell.editor()::requestFocusInWindow);
            case NEXT_OR_NEW -> {
                int idx = indexOf(cell);
                if (idx < cells.getComponentCount() - 1) {
                    Cell next = getCell(idx + 1);
                    SwingUtilities.invokeLater(() -> {
                        next.editor().requestFocusInWindow();
                        scrollTo(next.editor());
                    });
                } else {
                    Cell next = addCell(cell);
                    SwingUtilities.invokeLater(() -> {
                        next.editor().requestFocusInWindow();
                        scrollTo(next.editor());
                    });
                }
            }
            case INSERT_BELOW -> {
                Cell next = addCell(cell);
                SwingUtilities.invokeLater(() -> {
                    next.editor().requestFocusInWindow();
                    scrollTo(next.editor());
                });
            }
        }
    }

    /**
     * Runs a set of cells.
     * @param cells the cells to evaluate.
     */
    private void runCells(List<Cell> cells) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                runner.setRunning(true);
                for (Cell cell : cells) {
                    if (cell.editor().getText().trim().isEmpty()) continue;
                    if (!runCell(cell)) break;
                }
                return null;
            }

            @Override
            protected void done() {
                runner.setRunning(false);
                SwingUtilities.invokeLater(postRunAction);
            }
        };
        worker.execute();
    }

    /**
     * Runs the cell and all below.
     * @param cell the selected cell.
     */
    public synchronized void runCellAndBelow(Cell cell) {
        if (runner.isRunning()) {
            showRaceConditionDialog();
            return;
        }

        // Sequentially run all non-empty cells
        List<Cell> cells = new ArrayList<>();
        int index = Math.max(0, indexOf(cell)); // start from beginning if cell is not found
        for (int i = index; i < this.cells.getComponentCount(); i++) {
            cells.add(getCell(i));
        }

        runCells(cells);
    }

    /**
     * Runs all cells.
     */
    public synchronized void runAllCells() {
        if (runner.isRunning()) {
            showRaceConditionDialog();
            return;
        }

        // Sequentially run all non-empty cells
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < this.cells.getComponentCount(); i++) {
            cells.add(getCell(i));
        }

        runCells(cells);
    }

    /**
     * Clear the outputs of all cells.
     */
    public void clearAllOutputs() {
        for (int i = 0; i < cells.getComponentCount(); i++) {
            getCell(i).output().setText("");
        }
    }
}
