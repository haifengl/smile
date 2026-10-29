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
package smile.studio.workspace;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import com.formdev.flatlaf.util.SystemFileChooser;
import jdk.jshell.VarSnippet;
import smile.io.Paths;
import smile.studio.SmileStudio;
import smile.studio.StudioConfig;
import smile.studio.kernel.Kernel;
import smile.studio.kernel.Variable;
import static smile.swing.SmileUtilities.LARGE_ICON_SIZE;
import static smile.swing.SmileUtilities.loadImageIcon;

/**
 * A kernel workspace explorer.
 *
 * @author Haifeng Li
 */
public class KernelExplorer extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(KernelExplorer.class);
    private static final ResourceBundle bundle = ResourceBundle.getBundle(KernelExplorer.class.getName(), Locale.getDefault());
    private static final Set<String> IGNORED_DIRS = Set.of(
            "build", "target", "node_modules", "venv", ".venv", "__pycache__", "jbr", "out", "dist"
    );

    /**
     * User object representing a persisted {@code .sml} or {@code .onnx} model file on disk.
     *
     * @param path        the absolute normalized model file path.
     * @param displayName the workspace-relative path (or file name).
     */
    public record ModelFileItem(Path path, String displayName) {
        /** Returns the file stem (without extension). */
        public String baseName() {
            return Paths.getFileName(path);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /** Tree nodes. */
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(bundle.getString("Root"));
    private final DefaultMutableTreeNode frames = new DefaultMutableTreeNode(bundle.getString("DataFrames"));
    private final DefaultMutableTreeNode matrix = new DefaultMutableTreeNode(bundle.getString("Matrix"));
    private final DefaultMutableTreeNode models = new DefaultMutableTreeNode(bundle.getString("Models"));
    private static final ImageIcon matrixIcon = loadImageIcon(KernelExplorer.class, "images/matrix.png", LARGE_ICON_SIZE);
    private static final ImageIcon modelIcon = loadImageIcon(KernelExplorer.class, "images/model.png", LARGE_ICON_SIZE);
    private static final ImageIcon tableIcon = loadImageIcon(KernelExplorer.class, "images/table.png", LARGE_ICON_SIZE);
    /** Tree of workspace runtime information. */
    private final JTree tree = new JTree(root);
    /**
     * To correctly update the JTree and display new children, we must use the
     * DefaultTreeModel's methods to manage the nodes.
     */
    private final DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
    /** Kernel instance. */
    private Kernel<?> kernel = null;
    /** Workspace root directory scanned for .sml and .onnx model files. */
    private final Path cwd;
    /** File chooser for saving models. */
    private final SystemFileChooser fileChooser;
    /** Cached set of model IDs currently loaded in the running Inference Service. */
    private volatile Set<String> loadedModelIds = Set.of();

    /**
     * Constructor.
     * @param fileChooser the file chooser for saving models.
     */
    public KernelExplorer(SystemFileChooser fileChooser) {
        this(Path.of(System.getProperty("user.dir")), fileChooser);
    }

    /**
     * Constructor.
     * @param cwd         the workspace directory to scan for persisted models.
     * @param fileChooser the file chooser for saving models.
     */
    public KernelExplorer(Path cwd, SystemFileChooser fileChooser) {
        super(new BorderLayout());
        this.cwd = cwd.toAbsolutePath().normalize();
        this.fileChooser = fileChooser;

        setBorder(new EmptyBorder(0, 8, 0, 0));
        initTree();

        // Add the tree to the scroll pane.
        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Initializes tree nodes.
     */
    private void initTree() {
        treeModel.insertNodeInto(frames, root, root.getChildCount());
        treeModel.insertNodeInto(matrix, root, root.getChildCount());
        treeModel.insertNodeInto(models, root, root.getChildCount());
        refresh(null);

        // Allow one selection at a time.
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        // Expand the tree
        tree.expandPath(new TreePath(root));
        // Hide the root node
        tree.setRootVisible(false);
        // JTree needs to be registered with the ToolTipManager to enable tooltips.
        ToolTipManager.sharedInstance().registerComponent(tree);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object object = node.getUserObject();
                if (node == frames) {
                    setIcon(tableIcon);
                } else if (node == matrix) {
                    setIcon(matrixIcon);
                } else if (node == models) {
                    setIcon(modelIcon);
                } else if (object instanceof VarSnippet snippet) {
                    setText(snippet.name() + " (" + snippet.typeName() + ")");
                    setToolTipText(snippet.source().trim());
                } else if (object instanceof Variable variable) {
                    setText(variable.name() + (node.getParent() == models ? " (" + variable.typeName() + ")" : ""));
                    setToolTipText(variable.typeName());
                } else if (object instanceof ModelFileItem modelFile) {
                    boolean loaded = findLoadedId(modelFile).isPresent();
                    setText((loaded ? "● " : "○ ") + modelFile.displayName());
                    setToolTipText(modelFile.path().toString());
                }
                return this;
            }
        };
        tree.setCellRenderer(renderer);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // double-click
                    TreePath treePath = tree.getPathForLocation(e.getX(), e.getY());
                    if (treePath != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                        if (node.isLeaf()) {
                            var parent = node.getParent();
                            Object userObj = node.getUserObject();
                            if (parent == frames || parent == matrix) {
                                if (kernel == null) return;
                                var variable = (Variable) userObj;
                                var name = variable.name();
                                kernel.eval(String.format("""
                                        var %sWindow = smile.swing.SmileUtilities.show(%s);
                                        %sWindow.setTitle("%s");
                                        """, name, name, name, name));
                            } else if (parent == models) {
                                if (userObj instanceof ModelFileItem modelFile) {
                                    openModelInWebUi(modelFile.path());
                                } else {
                                    saveInMemoryModel(userObj);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Prompts the user to save an in-memory kernel model variable to a {@code .sml} file.
     */
    private void saveInMemoryModel(Object userObj) {
        if (kernel == null) return;
        String name = switch (userObj) {
            case Variable v -> v.name();
            case VarSnippet s -> s.name();
            default -> null;
        };
        if (name == null) return;

        var title = fileChooser.getDialogTitle();
        fileChooser.setDialogTitle(bundle.getString("SaveModel"));
        var filter = new SystemFileChooser.FileNameExtensionFilter(bundle.getString("ModelFile"), "sml");
        fileChooser.setFileFilter(filter);
        if (fileChooser.showSaveDialog(SwingUtilities.getWindowAncestor(this)) == SystemFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".sml")) {
                file = new File(file.getParentFile(), file.getName() + ".sml");
            }
            // replace backslash with slash in case of Windows
            String path = file.getAbsolutePath().replace('\\', '/');
            kernel.eval(String.format("""
                    smile.io.Write.object(%s, java.nio.file.Path.of("%s"));
                    """, name, path));
            refresh(kernel);
        }
        fileChooser.setDialogTitle(title);
    }

    private void showContextMenu(MouseEvent e) {
        TreePath treePath = tree.getPathForLocation(e.getX(), e.getY());
        if (treePath == null) {
            return;
        }
        tree.setSelectionPath(treePath);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        Object userObj = node.getUserObject();

        JPopupMenu popup = new JPopupMenu();

        if (userObj instanceof ModelFileItem modelFile) {
            Optional<String> loadedId = findLoadedId(modelFile);

            JMenuItem openUiItem = new JMenuItem("▶ Open in Web UI");
            openUiItem.addActionListener(ev -> openModelInWebUi(modelFile.path()));
            popup.add(openUiItem);
            popup.addSeparator();

            JMenuItem loadItem = new JMenuItem("Load into Inference Service");
            loadItem.addActionListener(ev -> loadModelAsync(modelFile.path(), false));
            popup.add(loadItem);

            JMenuItem reloadItem = new JMenuItem("Reload Model");
            reloadItem.setEnabled(loadedId.isPresent());
            reloadItem.addActionListener(ev -> loadedId.ifPresent(this::reloadModelAsync));
            popup.add(reloadItem);

            JMenuItem unloadItem = new JMenuItem("Unload Model");
            unloadItem.setEnabled(loadedId.isPresent());
            unloadItem.addActionListener(ev -> loadedId.ifPresent(this::unloadModelAsync));
            popup.add(unloadItem);
            popup.addSeparator();

            JMenuItem refreshItem = new JMenuItem("↻ Refresh Models");
            refreshItem.addActionListener(ev -> refresh(kernel));
            popup.add(refreshItem);

            popup.show(tree, e.getX(), e.getY());
        } else if (node == models) {
            JMenuItem refreshItem = new JMenuItem("↻ Refresh Models");
            refreshItem.addActionListener(ev -> refresh(kernel));
            popup.add(refreshItem);
            popup.show(tree, e.getX(), e.getY());
        } else if (node.getParent() == models && (userObj instanceof Variable || userObj instanceof VarSnippet)) {
            JMenuItem saveItem = new JMenuItem(bundle.getString("SaveModel") + "…");
            saveItem.addActionListener(ev -> saveInMemoryModel(userObj));
            popup.add(saveItem);
            popup.show(tree, e.getX(), e.getY());
        }
    }

    /**
     * Returns the loaded model ID in {@link ServeManager} matching the given model file, if loaded.
     */
    private Optional<String> findLoadedId(ModelFileItem item) {
        String base = item.baseName();
        for (String id : loadedModelIds) {
            if (id.equals(base) || id.startsWith(base + "-")) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    /**
     * Starts the Inference Service if needed, loads the given model file, and opens
     * the Inference Web UI pointing directly to that model.
     *
     * @param modelPath the path to the {@code .sml} or {@code .onnx} model file.
     */
    public void openModelInWebUi(Path modelPath) {
        loadModelAsync(modelPath, true);
    }

    private void loadModelAsync(Path modelPath, boolean openBrowser) {
        Thread.ofPlatform().name("serve-load-model").daemon(true).start(() -> {
            try {
                var manager = ServeManager.getInstance();
                var config = StudioConfig.inferenceServer();
                if (!manager.isRunning()) {
                    SmileStudio.setStatus(this, "Starting Inference Service…");
                    if (!manager.start(config.host(), config.port())) {
                        showError(bundle.getString("ServiceStartFailed"));
                        return;
                    }
                }
                String modelId = manager.loadModel(modelPath.toString());
                List<String> currentIds = manager.loadedModelIds();
                loadedModelIds = Set.copyOf(currentIds);
                String baseName = Paths.getFileName(modelPath);
                if (modelId == null) {
                    modelId = currentIds.stream()
                            .filter(id -> id.equals(baseName) || id.startsWith(baseName + "-"))
                            .findFirst()
                            .orElse(baseName);
                }
                final String targetId = modelId;
                SwingUtilities.invokeLater(() -> {
                    tree.repaint();
                    SmileStudio.setStatus(this, "Loaded model '" + targetId + "' into Inference Service.");
                });

                if (openBrowser) {
                    String base = manager.baseUrl();
                    if (base != null && Desktop.isDesktopSupported()) {
                        String encoded = URLEncoder.encode(targetId, StandardCharsets.UTF_8);
                        Desktop.getDesktop().browse(URI.create(base + "/?model=" + encoded));
                    }
                }
            } catch (Exception ex) {
                logger.error("Failed to load model '{}': {}", modelPath, ex.getMessage());
                showError(ex.getMessage());
            }
        });
    }

    private void reloadModelAsync(String modelId) {
        Thread.ofPlatform().name("serve-reload-model").daemon(true).start(() -> {
            try {
                boolean ok = ServeManager.getInstance().reloadModel(modelId);
                refreshLoadedModelIdsAsync();
                SmileStudio.setStatus(this, ok
                        ? "Reloaded model '" + modelId + "'."
                        : "Failed to reload model '" + modelId + "'.");
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void unloadModelAsync(String modelId) {
        Thread.ofPlatform().name("serve-unload-model").daemon(true).start(() -> {
            try {
                boolean ok = ServeManager.getInstance().unloadModel(modelId);
                refreshLoadedModelIdsAsync();
                SmileStudio.setStatus(this, ok
                        ? "Unloaded model '" + modelId + "'."
                        : "Failed to unload model '" + modelId + "'.");
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });
    }

    /**
     * Queries {@link ServeManager#loadedModelIds()} on a background thread and repaints the tree.
     */
    public void refreshLoadedModelIdsAsync() {
        Thread.ofPlatform().name("serve-loaded-models").daemon(true).start(() -> {
            var manager = ServeManager.getInstance();
            Set<String> ids = manager.isRunning() ? Set.copyOf(manager.loadedModelIds()) : Set.of();
            loadedModelIds = ids;
            SwingUtilities.invokeLater(tree::repaint);
        });
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                message,
                bundle.getString("Error"),
                JOptionPane.ERROR_MESSAGE));
    }

    /**
     * Scans the workspace directory recursively (up to depth 4, skipping build/hidden folders)
     * for {@code .sml} and {@code .onnx} model files.
     */
    List<ModelFileItem> scanProjectModels() {
        if (cwd == null || !Files.isDirectory(cwd)) {
            return List.of();
        }
        List<ModelFileItem> found = new ArrayList<>();
        try {
            Files.walkFileTree(cwd, Set.of(), 4, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(cwd)) {
                        Path name = dir.getFileName();
                        if (name != null) {
                            String dirName = name.toString();
                            if (dirName.startsWith(".") || IGNORED_DIRS.contains(dirName)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (fileName.endsWith(".sml") || fileName.endsWith(".onnx")) {
                            Path abs = file.toAbsolutePath().normalize();
                            String rel = cwd.relativize(abs).toString().replace('\\', '/');
                            found.add(new ModelFileItem(abs, rel));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            logger.debug("Failed to scan workspace for models: {}", ex.getMessage());
        }
        found.sort(Comparator.comparing(ModelFileItem::displayName));
        return found;
    }

    /**
     * Refreshes the tree with active kernel variables and persisted project models.
     * @param kernel the Kernel instance to get variables from (may be {@code null}).
     */
    public final void refresh(Kernel<?> kernel) {
        this.kernel = kernel;
        frames.removeAllChildren();
        matrix.removeAllChildren();
        models.removeAllChildren();

        if (kernel != null) {
            kernel.variables().forEach(variable -> {
                var node = new DefaultMutableTreeNode(variable);
                String typeName = variable.typeName();
                switch (typeName) {
                    case "DataFrame", "smile.data.DataFrame":
                        frames.add(node);
                        break;

                    case "DenseMatrix", "BandMatrix", "SymmMatrix", "SparseMatrix",
                         "smile.tensor.DenseMatrix", "smile.tensor.BandMatrix",
                         "smile.tensor.SymmMatrix", "smile.tensor.SparseMatrix":
                        matrix.add(node);
                        break;

                    case "FLD", "LDA", "QDA", "RDA", "NaiveBayes", "MLP", "Maxent",
                         "LogisticRegression", "SparseLogisticRegression",
                         "DecisionTree", "RegressionTree", "AdaBoost", "RandomForest",
                         "GradientTreeBoost", "LinearSVM", "SparseLinearSVM",
                         "LinearModel", "GaussianProcessRegression",
                         "SOM", "NeuralGas", "GrowingNeuralGas", "NeuralMap", "BIRCH", "VectorQuantizer",
                         "IsolationForest", "LOF",
                         "ClassificationModel", "RegressionModel", "ClusteringModel",
                         "VectorQuantizerModel", "AnomalyModel":
                        models.add(node);
                        break;

                    default:
                        if (typeName.startsWith("Classifier<") ||
                            typeName.startsWith("Regression<") ||
                            typeName.startsWith("CentroidClustering<") ||
                            typeName.startsWith("LOF<") ||
                            typeName.startsWith("SVM<") ||
                            typeName.startsWith("KNN<") ||
                            typeName.startsWith("RBFNetwork<") ||
                            typeName.startsWith("KernelMachine<") ||
                            typeName.startsWith("OneVersusOne<") ||
                            typeName.startsWith("OneVersusRest<")) {
                            models.add(node);
                        }
                }
            });
        }

        for (ModelFileItem modelFile : scanProjectModels()) {
            models.add(new DefaultMutableTreeNode(modelFile));
        }

        treeModel.reload(root);
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        refreshLoadedModelIdsAsync();
    }
}
