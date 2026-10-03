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
package smile.studio.notebook;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import ioa.agent.Coder;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import smile.io.Paths;
import smile.studio.SmileStudio;
import smile.studio.kernel.*;
import smile.studio.text.Editor;
import smile.studio.workspace.OpenFile;
import smile.swing.ScrollablePanel;
import smile.util.ipynb.JupyterNotebook;

/**
 * Interactive environment to write and execute Java code combining code,
 * documentation, and visualizations. The notebook consists of a sequence
 * of cells.
 *
 * @author Haifeng Li
 */
public class Notebook extends JPanel implements OpenFile, DocumentListener {
    private static final String JAVA_CELL_SEPARATOR = "//--- CELL ---";
    private static final String PY_CELL_SEPARATOR = "#--- CELL ---";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Notebook.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Notebook.class.getName(), Locale.getDefault());
    private final JPanel cells = new ScrollablePanel();
    private final JScrollPane scrollPane = new JScrollPane(cells);
    /** Programming language. */
    private final String lang;
    /** Programming language syntax highlight style. */
    private final String syntaxStyle;
    /** The original Jupyter notebook read from .ipynb file. */
    private JupyterNotebook jupyter;
    /** Execution engine. */
    private volatile Kernel<?> kernel;
    /** The lifecycle state of the execution engine. */
    private volatile KernelState kernelState = KernelState.STARTING;
    // TODO: Use LazyConstant as kernel initialization is expensive and
    // we want to delay it until first run. For now, we initialize it
    // in constructor as LazyConstant is still in preview.
    //private final LazyConstant<Kernel<?>> kernel = LazyConstant.of(() -> createKernel());
    /** The coding assistant agent. */
    private final Coder coder;
    private final Consumer<Kernel<?>> postRunAction;
    private int runCount = 0;
    private Path file;
    private boolean saved = true;
    /** Notified on every document change, used to schedule a debounced auto save. */
    private Runnable changeListener;

    /**
     * Constructor.
     * @param file the notebook file. If null, a new notebook will be created.
     * @param coders the coding assistant agents.
     * @param postRunAction the action to perform after running cells.
     */
    public Notebook(Path file, Map<String, Coder> coders, Consumer<Kernel<?>> postRunAction) {
        super(new BorderLayout());
        this.file = file;
        this.postRunAction = postRunAction;

        cells.setLayout(new BoxLayout(cells, BoxLayout.Y_AXIS));
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        JupyterNotebook ipynb = null;
        if (Files.exists(file) && Paths.getFileExtension(file).equals("ipynb")) {
            try {
                ipynb = JupyterNotebook.from(file);
            } catch (Exception ex) {
                logger.error("Failed to read Jupyter notebook {}: {}", file, ex.getMessage());
                showErrorMessage(ex.getMessage());
            }
        }

        jupyter = ipynb;
        lang = initLang();
        syntaxStyle = initSyntaxStyle();
        coder = coders.get(lang);
        initKernel();

        if (Files.exists(file) && (jupyter != null || !Paths.getFileExtension(file).equals("ipynb"))) {
            try {
                loadCells(file);
            } catch (Exception ex) {
                logger.error("Failed to load notebook cells {}: {}", file, ex.getMessage());
                showErrorMessage(bundle.getString("OpenNotebookErrorMessage") + ": " + ex.getMessage());
            }
        }

        // If the file is empty or failed to read,
        // initialize with a starter cell.
        if (cells.getComponentCount() == 0) {
            initStarter();
        }

        if (cells.getComponentCount() > 0 && cells.getComponent(0) instanceof Cell first) {
            // Scroll to the first cell
            SwingUtilities.invokeLater(() -> {
                first.editor().requestFocusInWindow();
                first.editor().setCaretPosition(0);
                scrollTo(first.editor());
            });
        }
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
        if (jupyter != null) {
            var lang = jupyter.metadata().kernelspec().language();
            return switch (lang.toLowerCase()) {
                case "java" -> "Java";
                case "scala" -> "Scala";
                case "kotlin" -> "Kotlin";
                case "python" -> "Python";
                default -> lang;
            };
        }

        var ext = Paths.getFileExtension(file);
        return switch (ext) {
            case "jsh" -> "Java";
            case "sc" -> "Scala";
            case "kts" -> "Kotlin";
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

    /** Initialize the notebook with a starter cell. */
    private void initStarter() {
        if (lang.equals("Java")) {
            // Import essential SMILE packages
            var cell = addCell(null);
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
        }

        // Add an empty cell for user to start with
        var cell = addCell(null);
        cell.editor().requestFocus();
        // Mark the notebook as saved for starter content
        setSaved(true);
    }

    /**
     * The lifecycle state of the execution engine.
     *
     * <p>Kernel construction is expensive — the Scala and Python kernels start
     * an external process and can take seconds to minutes to become ready — so
     * it runs on a background thread while the notebook is already usable. This
     * state distinguishes a kernel that is merely not ready yet ({@link #STARTING})
     * from one that can never run ({@link #UNSUPPORTED}), so that running a cell
     * early reports the former instead of the misleading latter.
     */
    /**
     * The lifecycle state of the execution engine.
     *
     * <p>Kernel construction is expensive — the Scala and Python kernels start
     * an external process and can take seconds to minutes to become ready — so
     * it runs on a background thread while the notebook is already usable. This
     * state distinguishes a kernel that is merely not ready yet ({@link #STARTING})
     * from one that can never run ({@link #UNSUPPORTED}), so that running a cell
     * early reports the former instead of the misleading latter.
     */
    public enum KernelState {
        /** The kernel is being created on a background thread. */
        STARTING,
        /** The kernel is constructed and ready to evaluate code. */
        READY,
        /** No kernel is available for the notebook language. */
        UNSUPPORTED
    }

    /**
     * Returns the lifecycle state of the execution engine.
     * @return the lifecycle state of the execution engine.
     */
    public KernelState kernelState() {
        return kernelState;
    }

    /**
     * Sets the status in the status bar of the enclosing SmileStudio.
     * If the notebook is not yet attached to a window ancestor, an
     * AncestorListener is registered to update the status once attached.
     *
     * @param status the status message to display.
     */
    private void setKernelStatus(String status) {
        if (SwingUtilities.getWindowAncestor(this) instanceof SmileStudio) {
            SmileStudio.setStatus(this, status);
        } else {
            addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorAdded(AncestorEvent event) {
                    if (SwingUtilities.getWindowAncestor(Notebook.this) instanceof SmileStudio) {
                        removeAncestorListener(this);
                        String currentStatus = switch (kernelState) {
                            case STARTING -> bundle.getString("KernelStarting");
                            case READY -> bundle.getString("KernelReady");
                            case UNSUPPORTED -> "";
                        };
                        SmileStudio.setStatus(Notebook.this, currentStatus);
                    }
                }

                @Override
                public void ancestorRemoved(AncestorEvent event) {}

                @Override
                public void ancestorMoved(AncestorEvent event) {}
            });
        }
    }

    /** Initialize the kernel. */
    private void initKernel() {
        if (!switch (lang) {
            case "Java", "Scala", "Kotlin", "Python" -> true;
            default -> false;
        }) {
            kernelState = KernelState.UNSUPPORTED;
            return;
        }

        setKernelStatus(bundle.getString("KernelStarting"));

        SwingWorker<Kernel<?>, Void> worker = new SwingWorker<>() {
            @Override
            protected Kernel<?> doInBackground() throws IOException {
                return switch (lang) {
                    case "Java" -> new JavaKernel();
                    case "Scala" -> new ScalaKernel();
                    case "Kotlin" -> new KotlinKernel();
                    case "Python" -> new PythonKernel();
                    default -> null;
                };
            }

            @Override
            protected void done() {
                try {
                    kernel = get();
                    kernelState = kernel == null ? KernelState.UNSUPPORTED : KernelState.READY;
                    if (kernelState == KernelState.READY) {
                        SmileStudio.setStatus(Notebook.this, bundle.getString("KernelReady"));
                    } else {
                        SmileStudio.setStatus(Notebook.this, "");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    kernelState = KernelState.UNSUPPORTED;
                    SmileStudio.setStatus(Notebook.this, "");
                } catch (ExecutionException ex) {
                    // The kernel constructor failed (e.g. Python is not installed).
                    logger.error("Failed to initialize {} kernel: {}", lang, ex.getCause().getMessage());
                    kernelState = KernelState.UNSUPPORTED;
                    SmileStudio.setStatus(Notebook.this, "");
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(Notebook.this,
                            MessageFormat.format(bundle.getString("KernelInitErrorMessage"), lang),
                            "Error",
                            JOptionPane.ERROR_MESSAGE));
                }
            }
        };
        worker.execute();
    }

    /**
     * Shows the dialog that a cell cannot run because the kernel is not ready.
     *
     * @return true if the kernel is ready, false if a dialog was shown.
     */
    private boolean checkKernelReady() {
        if (kernelState != KernelState.READY || kernel == null) {
            if (kernelState == KernelState.STARTING) {
                JOptionPane.showMessageDialog(this,
                        MessageFormat.format(bundle.getString("KernelStartingMessage"), lang),
                        bundle.getString("KernelStartingTitle"),
                        JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        MessageFormat.format(bundle.getString("UnsupportedKernelMessage"), lang),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }
        return true;
    }

    /**
     * Shuts down the execution engine and frees resources.
     */
    @Override
    public void close() {
        if (kernel != null) {
            kernel.close();
        }

        // close autocomplete providers
        for (int i = 0; i < cells.getComponentCount(); i++) {
            getCell(i).editor().close();
        }
    }

    /** Restarts the kernel and clears all output. */
    public void restart() {
        if (kernel == null) return;
        kernel.restart();
        clearAllOutputs();
    }

    /** Attempts to stop currently running code. */
    public void stop() {
        if (kernel == null) return;
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
    @Override
    public Path getFile() {
        return file;
    }

    /**
     * Sets the notebook file.
     *
     * @param file the notebook file.
     */
    @Override
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
     * Loads cells from a notebook file.
     *
     * @param file the notebook file.
     * @throws IOException If an I/O error occurs.
     */
    private void loadCells(Path file) throws IOException {
        List<Cell> snippets = jupyter != null ? getJupyterCells(jupyter) : getSourceCells(file);

        cells.removeAll();
        for (var cell : snippets) {
            cells.add(cell);
        }
    }

    /**
     * Returns the cell type enum.
     * @param type the cell type string.
     * @return the cell type.
     */
    private CellType cellType(String type) {
        return switch (type) {
            case "code" -> CellType.Code;
            case "markdown" -> CellType.Markdown;
            default -> CellType.Raw;
        };
    }

    /**
     * Creates a cell with source code and type.
     * @param source the source code.
     * @param type the cell type.
     * @return the created cell.
     */
    private Cell createCell(String source, String type) {
        Cell cell = new Cell(this);
        cell.editor().setText(source);
        cell.setType(cellType(type));
        cell.editor().getDocument().addDocumentListener(this);
        enableAutoComplete(cell.editor());
        return cell;
    }

    /** Enables the auto-completion for a cell based on the notebook file. */
    private void enableAutoComplete(Editor editor) {
        // Delay 10 seconds if notebook is not fully loaded,
        // which usually happens at start up time. This heuristic
        // is to ensure language servers ready.
        int delay = isShowing() ? 100 : 10000;
        Timer timer = new Timer(delay, e -> {
            var path = file.toAbsolutePath().toString().replace("\\", "/");
            var fileUrl = "untitled://" + path;
            editor.setAutoComplete(fileUrl, editor.getSyntaxEditingStyle());
        });

        timer.setRepeats(false); // Only fire once
        timer.start();
    }

    /** Determines the cell separator based on file extension. */
    private String separator(Path file) {
        return file.getFileName().toString().endsWith(".py") ?
                PY_CELL_SEPARATOR :
                JAVA_CELL_SEPARATOR;
    }

    /**
     * Returns the cells from a source code file.
     * @param file the source code file.
     * @return the list of cells.
     */
    private List<Cell> getSourceCells(Path file) throws IOException{
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Cell> cells = new ArrayList<>();
        String separator = separator(file);

        List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().equals(separator)) {
                if (!current.isEmpty()) {
                    cells.add(createCell(String.join("\n", current).stripTrailing(), "code"));
                }
                current = new ArrayList<>();
            } else {
                current.add(line);
            }
        }

        // The last cell may not end with separator.
        if (!current.isEmpty()) {
            cells.add(createCell(String.join("\n", current).stripTrailing(), "code"));
        }
        return cells;
    }

    /**
     * Returns cells from a Jupyter notebook.
     * @param jupyter the Jupyter notebook.
     * @return the list of cells.
     */
    private List<Cell> getJupyterCells(JupyterNotebook jupyter) throws IOException {
        List<Cell> cells = new ArrayList<>();
        for (var jupyterCell : jupyter.cells()) {
            Cell cell = createCell(jupyterCell.source().value(), jupyterCell.cellType());
            cell.setId(jupyterCell.id());
            cell.setMetadata(jupyterCell.metadata());

            if (jupyterCell instanceof smile.util.ipynb.CodeCell codeCell) {
                if (codeCell.executionCount() != null) {
                    cell.setExecutionCount(codeCell.executionCount());
                    runCount = Math.max(runCount, codeCell.executionCount());
                }
                if (codeCell.outputs() != null && !codeCell.outputs().isEmpty()) {
                    cell.setOutputs(codeCell.outputs());
                }
            } else if (jupyterCell instanceof smile.util.ipynb.MarkdownCell markdownCell) {
                cell.setAttachments(markdownCell.attachments());
            } else if (jupyterCell instanceof smile.util.ipynb.RawCell rawCell) {
                cell.setAttachments(rawCell.attachments());
            }
            cells.add(cell);
        }
        return cells;
    }

    /**
     * Saves the notebook to file.
     *
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void save() throws IOException {
        if (file == null) {
            logger.error("Notebook file is null");
            return;
        }

        if (Paths.getFileExtension(file).equals("ipynb")) {
            saveAsIpynb();
        } else {
            saveAsSource();
        }
        setSaved(true);
    }

    /**
     * Re-reads the notebook from disk in place, preserving the kernel.
     *
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void reload() throws IOException {
        if (file == null) {
            logger.error("Notebook file is null");
            return;
        }

        if (Paths.getFileExtension(file).equals("ipynb")) {
            jupyter = JupyterNotebook.from(file);
        }

        loadCells(file);
        setSaved(true);
    }

    /**
     * Creates default metadata for a Jupyter notebook when no metadata is present.
     * @return default notebook metadata.
     */
    private smile.util.ipynb.Metadata defaultIpynbMetadata() {
        String langName = lang().toLowerCase();
        var kernelSpec = switch (langName) {
            case "scala" -> new smile.util.ipynb.KernelSpec("Scala", "scala", "scala");
            case "kotlin" -> new smile.util.ipynb.KernelSpec("Kotlin", "kotlin", "kotlin");
            case "python" -> new smile.util.ipynb.KernelSpec("Python 3", "python", "python3");
            default -> new smile.util.ipynb.KernelSpec("Java", "java", "java");
        };
        var langInfo = new smile.util.ipynb.LanguageInfo(
                langName,
                null,
                "text/x-" + langName,
                switch (langName) {
                    case "scala" -> ".scala";
                    case "kotlin" -> ".kt";
                    case "python" -> ".py";
                    default -> ".java";
                },
                null,
                null,
                null,
                null
        );
        return new smile.util.ipynb.Metadata(kernelSpec, langInfo, null, List.of(), null);
    }

    /**
     * Saves the notebook as Jupyter file.
     *
     * @throws IOException If an I/O error occurs.
     */
    private void saveAsIpynb() throws IOException {
        var metadata = jupyter != null && jupyter.metadata() != null
                ? jupyter.metadata()
                : defaultIpynbMetadata();
        int nbformat = jupyter != null ? jupyter.nbformat() : smile.util.ipynb.JupyterNotebook.NBFORMAT;
        int nbformatMinor = jupyter != null ? jupyter.nbformatMinor() : smile.util.ipynb.JupyterNotebook.NBFORMAT_MINOR;

        List<smile.util.ipynb.Cell> cells = new ArrayList<>();
        var notebook = new smile.util.ipynb.JupyterNotebook(cells, metadata, nbformat, nbformatMinor);
        for (int i = 0; i < this.cells.getComponentCount(); i++) {
            var c = getCell(i);
            String cellId = (c.id() != null && !c.id().isBlank()) ? c.id() : "cell-" + (i + 1);
            var cellMeta = c.metadata() != null ? c.metadata() : new smile.util.ipynb.CellMetadata();

            var cell = switch (c.type()) {
                case Code -> new smile.util.ipynb.CodeCell(
                        cellId,
                        cellMeta,
                        smile.util.ipynb.MultilineString.of(c.editor().getText()),
                        new ArrayList<>(c.outputs()),
                        c.getExecutionCount()
                );
                case Markdown -> new smile.util.ipynb.MarkdownCell(
                        cellId,
                        cellMeta,
                        smile.util.ipynb.MultilineString.of(c.editor().getText()),
                        c.attachments() != null ? c.attachments() : Map.of()
                );
                case Raw -> new smile.util.ipynb.RawCell(
                        cellId,
                        cellMeta,
                        smile.util.ipynb.MultilineString.of(c.editor().getText()),
                        c.attachments() != null ? c.attachments() : Map.of()
                );
            };

            notebook.cells().add(cell);
        }
        notebook.write(file);
        this.jupyter = notebook;
    }

    /**
     * Saves the notebook as source code file.
     *
     * @throws IOException If an I/O error occurs.
     */
    private void saveAsSource() throws IOException {
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < cells.getComponentCount(); i++) {
            blocks.add(getCell(i).editor().getText());
        }
        String sep = "\n" + separator(file) + "\n";
        Files.writeString(file, String.join(sep, blocks), StandardCharsets.UTF_8);
    }

    /**
     * Returns true if the notebook is saved.
     *
     * @return true if the notebook is saved.
     */
    @Override
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
    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        setSaved(false);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        setSaved(false);
        if (changeListener != null) {
            changeListener.run();
        }
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
        enableAutoComplete(cell.editor());

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
     * Returns the number of cells.
     * @return the number of cells.
     */
    public int getCellCount() {
        return cells.getComponentCount();
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
        if (!checkKernelReady()) {
            return;
        }
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
                cell.output().highlight();
                setSaved(false);
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
                for (var cell : cells) {
                    if (cell.editor().getText().trim().isEmpty()) continue;
                    if (!cell.run(kernel, ++runCount)) break;
                }
                return null;
            }

            @Override
            protected void done() {
                kernel.setRunning(false);
                for (var cell : cells) {
                    cell.output().highlight();
                }
                setSaved(false);
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
        if (!checkKernelReady()) {
            return;
        }
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
        if (!checkKernelReady()) {
            return;
        }
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
            getCell(i).clearOutput();
        }
        setSaved(false);
    }

    /**
     * Returns the text selected in any cell, or null if nothing is selected.
     *
     * @return the selected text.
     */
    @Override
    public String getSelectedText() {
        for (int i = 0; i < cells.getComponentCount(); i++) {
            var selectedText = getCell(i).editor().getSelectedText();
            if (selectedText != null) return selectedText;
        }
        return null;
    }

    /**
     * Applies a search or replace event across all cells.
     *
     * @param e the search event.
     */
    @Override
    public void searchEvent(SearchEvent e) {
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();
        int count = cells.getComponentCount();

        switch (type) {
            case MARK_ALL, FIND -> {
                context.setMarkAll(true);
                int marked = 0;
                for (int i = 0; i < count; i++) {
                    var result = SearchEngine.markAll(getCell(i).editor(), context);
                    marked += result.getMarkedCount();
                }
                var text = MessageFormat.format(bundle.getString("MarkCount"), marked);
                SwingUtilities.invokeLater(() -> SmileStudio.setStatus(this, text));
            }
            case REPLACE -> {
                var editor = getCell(0).editor();
                var result = SearchEngine.replace(editor, context);
                if (!result.wasFound() || result.isWrapped()) {
                    UIManager.getLookAndFeel().provideErrorFeedback(editor);
                }
            }
            case REPLACE_ALL -> {
                int replaced = 0;
                for (int i = 0; i < count; i++) {
                    var result = SearchEngine.replaceAll(getCell(i).editor(), context);
                    replaced += result.getCount();
                }
                JOptionPane.showMessageDialog(
                        this,
                        MessageFormat.format(bundle.getString("ReplaceCount"), replaced));
            }
        }
    }

    /** Shows an error dialog on the EDT when not running in a headless or test environment. */
    private static void showErrorMessage(String message) {
        if (!GraphicsEnvironment.isHeadless() &&
            Arrays.stream(Thread.currentThread().getStackTrace())
                    .noneMatch(e -> e.getClassName().contains("org.junit") || e.getClassName().endsWith("Test"))) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null,
                    message,
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            ));
        }
    }
}
