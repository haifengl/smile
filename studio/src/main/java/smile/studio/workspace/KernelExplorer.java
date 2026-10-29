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
import java.util.Locale;
import java.util.ResourceBundle;
import com.formdev.flatlaf.util.SystemFileChooser;
import jdk.jshell.VarSnippet;
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
    /** File chooser for saving models. */
    private final SystemFileChooser fileChooser;

    /**
     * Constructor.
     * @param fileChooser the file chooser for saving models.
     */
    public KernelExplorer(SystemFileChooser fileChooser) {
        super(new BorderLayout());
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
                    setText(snippet.name());
                    setToolTipText(snippet.source().trim());
                }
                return this;
            }
        };
        tree.setCellRenderer(renderer);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // double-click
                    // Get the path and node associated with the double click
                    TreePath treePath = tree.getPathForLocation(e.getX(), e.getY());
                    if (treePath != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                        if (node.isLeaf()) {
                            if (kernel == null) return;
                            var parent = node.getParent();
                            if (parent == frames || parent == matrix) {
                                var variable = (Variable) node.getUserObject();
                                var name = variable.name();
                                kernel.eval(String.format("""
                                        var %sWindow = smile.swing.SmileUtilities.show(%s);
                                        %sWindow.setTitle("%s");
                                        """, name, name, name, name));
                            } else if (parent == models) {
                                if (kernel == null) return;
                                var title = fileChooser.getDialogTitle();
                                fileChooser.setDialogTitle(bundle.getString("SaveModel"));
                                var filter = new SystemFileChooser.FileNameExtensionFilter(bundle.getString("ModelFile"), "sml");
                                fileChooser.setFileFilter(filter);
                                if (fileChooser.showSaveDialog(SwingUtilities.getWindowAncestor(KernelExplorer.this)) == JFileChooser.APPROVE_OPTION) {
                                    File file = fileChooser.getSelectedFile();
                                    if (!file.getName().toLowerCase().endsWith(".sml")) {
                                        file = new File(file.getParentFile(), file.getName() + ".sml");
                                    }
                                    var snippet = (VarSnippet) node.getUserObject();
                                    var name = snippet.name();
                                    // replace backslash with slash in case of Windows
                                    String path = file.getAbsolutePath().replace('\\', '/');
                                    kernel.eval(String.format("""
                                            smile.io.Write.object(%s, java.nio.file.Path.of("%s"));
                                            """, name, path));
                                }
                                // Restore the original dialog title after the file chooser is closed
                                fileChooser.setDialogTitle(title);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Refreshes the tree with JShell active variables.
     * @param kernel the JavaKernel instance to get variables from.
     */
    public void refresh(Kernel<?> kernel) {
        this.kernel = kernel;
        frames.removeAllChildren();
        matrix.removeAllChildren();
        models.removeAllChildren();
        treeModel.reload(root);
        if (kernel == null) return;

        kernel.variables().forEach(variable -> {
            var node = new DefaultMutableTreeNode(variable);
            String typeName = variable.typeName();
            switch (typeName) {
                case "DataFrame", "smile.data.DataFrame":
                    treeModel.insertNodeInto(node, frames, frames.getChildCount());
                    break;

                case "DenseMatrix", "BandMatrix", "SymmMatrix", "SparseMatrix",
                     "smile.tensor.DenseMatrix", "smile.tensor.BandMatrix",
                     "smile.tensor.SymmMatrix", "smile.tensor.SparseMatrix":
                    treeModel.insertNodeInto(node, matrix, matrix.getChildCount());
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
                    treeModel.insertNodeInto(node, models, models.getChildCount());
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
                        treeModel.insertNodeInto(node, models, models.getChildCount());
                    }
            }
        });

        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}
