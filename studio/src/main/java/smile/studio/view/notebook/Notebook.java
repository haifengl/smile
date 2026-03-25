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
package smile.studio.view.notebook;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import jdk.jshell.*;
import ioa.agent.Coder;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import smile.io.Paths;
import smile.studio.kernel.*;
import smile.studio.view.Monospaced;
import smile.swing.ScrollablePanel;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    /** Programming language. */
    private final String lang;
    /** Programming language syntax highlight style. */
    private final String syntaxStyle;
    /** Execution engine. */
    private final Kernel<?> kernel;
    /** The coding assistant agent. */
    private final Coder coder;
    private final Consumer<Kernel<?>> postRunAction;
    private int runCount = 0;
    private Path file;
    private boolean saved = true;

    /**
     * Constructor.
     * @param file the notebook file. If null, a new notebook will be created.
     * @param coder the coding assistant agent.
     * @param postRunAction the action to perform after running cells.
     */
    public Notebook(Path file, Coder coder, Consumer<Kernel<?>> postRunAction) {
        super(new BorderLayout());
        this.file = file;
        this.postRunAction = postRunAction;
        this.coder = coder;

        cells.setLayout(new BoxLayout(cells, BoxLayout.Y_AXIS));
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        lang = initLang();
        syntaxStyle = initSyntaxStyle();
        kernel = initKernel();
        if (file != null && Files.exists(file)) {
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
     * Returns the programming language.
     * @return the programming language.
     */
    public String lang() {
        return lang;
    }

    /**
     * Returns the programming language syntax highlight style.
     * @return the programming language syntax highlight style.
     */
    public String syntaxStyle() {
        return syntaxStyle;
    }

    /**
     * Returns the execution engine.
     * @return the execution engine.
     */
    public Kernel<?> kernel() {
        return kernel;
    }

    /** Initialize the programming language. */
    private String initLang() {
        var ext = Paths.getFileExtension(file);
        return switch (ext) {
            case "java", "jsh" -> "Java";
            case "scala", "sc" -> "Scala";
            case "kt", "kts" -> "Kotlin";
            case "py", "ipynb" -> "Python";
            default -> ext;
        };
    }

    /** Initialize the programming language syntax highlight style. */
    private String initSyntaxStyle() {
        return switch (lang) {
            case "Java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "Scala" -> SyntaxConstants.SYNTAX_STYLE_SCALA;
            case "Kotlin" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            case "Python" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }

    /** Initialize the kernel. */
    private Kernel<?> initKernel() {
        return switch (lang) {
            case "Java" -> new JavaKernel();
            case "Scala" -> new ScriptKernel("scala");
            case "Kotlin" -> new ScriptKernel("kotlin");
            case "Python" -> {
                try {
                    yield new PythonKernel();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Failed to initialize Python kernel: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    yield null;
                }
            }
            default -> {
                JOptionPane.showMessageDialog(this,
                        String.format(bundle.getString("UnsupportedNotebookMessage"), file.getFileName()),
                        bundle.getString("UnsupportedNotebookTitle"),
                        JOptionPane.ERROR_MESSAGE);
                yield null;
            }
        };
    }

    /**
     * Shuts down the execution engine and frees resources.
     */
    public void close() {
        kernel.close();
    }

    /** Restarts the kernel and clears all output. */
    public void restart() {
        kernel.restart();
        clearAllOutputs();
    }

    /** Attempts to stop currently running code. */
    public void stop() {
        kernel.stop();
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
        if (SwingUtilities.getAncestorOfClass(JTabbedPane.class, this) instanceof JTabbedPane tabs) {
            for(int i = 0; i < tabs.getTabCount(); i++) {
                if (SwingUtilities.isDescendingFrom(this, tabs.getComponentAt(i))) {
                    tabs.setTitleAt(i, file.getFileName().toString());
                    break;
                }
            }
        }
    }

    /**
     * Opens a notebook.
     *
     * @param file the notebook file.
     * @throws IOException If an I/O error occurs.
     */
    public void open(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> snippets = Paths.getFileExtension(file).equals("ipynb")
                ? readIpynb(file)
                : readSource(file);

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
     * Reads source code into cells.
     * @param file the source code file.
     * @return the list of cells.
     */
    private List<String> readSource(Path file) throws IOException{
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> snippets = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.trim().equals(CELL_SEPARATOR)) {
                if (!current.isEmpty()) snippets.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(line).append('\n');
            }
        }

        // The last cell may not end with separator.
        if (!current.isEmpty()) snippets.add(current.toString());
        return snippets;
    }

    /**
     * Reads Jupyter file into cells.
     * @param file the Jupyter file.
     * @return the list of cells.
     */
    private List<String> readIpynb(Path file) {
        List<String> snippets = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        JsonNode cells = root.get("cells");
        if (cells != null && cells.isArray()) {
            for (JsonNode cell : cells) {
                // TODO: cell.get("cell_type")
                snippets.add(cell.get("source").toString());
            }
        }
        return snippets;
    }

    /**
     * Saves the notebook to file.
     *
     * @throws IOException If an I/O error occurs.
     */
    public void save() throws IOException {
        if (file == null) {
            logger.error("Notebook file is null");
        }

        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < cells.getComponentCount(); i++) {
            Cell cell = getCell(i);
            blocks.add(getCell(i).editor().getText());
        }
        String sep = "\n" + CELL_SEPARATOR + "\n";
        Files.writeString(file, String.join(sep, blocks), StandardCharsets.UTF_8);
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
        if (kernel.isRunning()) {
            showRaceConditionDialog();
            return;
        }

        final String code = cell.editor().getText();
        if (code.trim().isEmpty()) {
            // Honor the navigation behavior even on empty run
            SwingUtilities.invokeLater(() -> handlePostRunNav(cell, behavior));
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                kernel.setRunning(true);
                cell.run(kernel, ++runCount);
                return null;
            }

            @Override
            protected void done() {
                kernel.setRunning(false);
                // Post-run actions
                handlePostRunNav(cell, behavior);
                postRunAction.accept(kernel);
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
            case STAY -> cell.editor().requestFocusInWindow();
            case NEXT_OR_NEW -> {
                int idx = indexOf(cell);
                if (idx < cells.getComponentCount() - 1) {
                    Cell next = getCell(idx + 1);
                    next.editor().requestFocusInWindow();
                    scrollTo(next.editor());
                } else {
                    Cell next = addCell(cell);
                    next.editor().requestFocusInWindow();
                    scrollTo(next.editor());
                }
            }
            case INSERT_BELOW -> {
                Cell next = addCell(cell);
                next.editor().requestFocusInWindow();
                scrollTo(next.editor());
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
                kernel.setRunning(true);
                for (Cell cell : cells) {
                    if (cell.editor().getText().trim().isEmpty()) continue;
                    boolean success = cell.run(kernel, ++runCount);
                    System.out.println(success);
                    if (!success) break;
                }
                return null;
            }

            @Override
            protected void done() {
                kernel.setRunning(false);
                postRunAction.accept(kernel);
            }
        };
        worker.execute();
    }

    /**
     * Runs the cell and all below.
     * @param cell the selected cell.
     */
    public synchronized void runCellAndBelow(Cell cell) {
        if (kernel.isRunning()) {
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
        if (kernel.isRunning()) {
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
