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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Objects;
import java.util.ResourceBundle;
import jdk.jshell.VarSnippet;
import smile.studio.model.Runner;
import smile.swing.FileChooser;
import static smile.swing.SmileUtilities.scaleImageIcon;

/**
 * A workspace explorer.
 *
 * @author Haifeng Li
 */
public class Explorer extends JPanel {
    /** The message resource bundle. */
    private final ResourceBundle bundle = ResourceBundle.getBundle(Explorer.class.getName(), getLocale());
    /** Tree nodes. */
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(bundle.getString("Root"));
    private final DefaultMutableTreeNode frames = new DefaultMutableTreeNode(bundle.getString("DataFrames"));;
    private final DefaultMutableTreeNode matrix = new DefaultMutableTreeNode(bundle.getString("Matrix"));
    private final DefaultMutableTreeNode models = new DefaultMutableTreeNode(bundle.getString("Models"));
    private final DefaultMutableTreeNode services = new DefaultMutableTreeNode(bundle.getString("Services"));
    private static final ImageIcon matrixIcon = scaleImageIcon(new ImageIcon(Objects.requireNonNull(Explorer.class.getResource("images/matrix.png"))), 24);
    private static final ImageIcon modelIcon = scaleImageIcon(new ImageIcon(Objects.requireNonNull(Explorer.class.getResource("images/model.png"))), 24);
    private static final ImageIcon serverIcon = scaleImageIcon(new ImageIcon(Objects.requireNonNull(Explorer.class.getResource("images/server.png"))), 24);
    private static final ImageIcon tableIcon = scaleImageIcon(new ImageIcon(Objects.requireNonNull(Explorer.class.getResource("images/table.png"))), 24);
    /** Tree of workspace runtime information. */
    private final JTree tree = new JTree(root);
    /**
     * To correctly update the JTree and display new children, we must use the
     * DefaultTreeModel's methods to manage the nodes.
     */
    private final DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
    /** JShell instance. */
    private final Runner runner;

    /**
     * Constructor.
     * @param runner Java code execution engine.
     */
    public Explorer(Runner runner) {
        super(new BorderLayout());
        this.runner = runner;
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
        //treeModel.insertNodeInto(services, root, root.getChildCount());

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
                } else if (node == services) {
                    setIcon(serverIcon);
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
                    // Get the path and node associated with the double-click
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.isLeaf()) {
                            var parent = node.getParent();
                            if (parent == frames || parent == matrix) {
                                var snippet = (VarSnippet) node.getUserObject();
                                var name = snippet.name();
                                runner.eval(String.format("""
                                        var %sWindow = smile.swing.SmileUtilities.show(%s);
                                        %sWindow.setTitle("%s");
                                        """, name, name, name, name));
                            } else if (parent == models) {
                                JFileChooser chooser = FileChooser.getInstance();
                                chooser.setDialogTitle(bundle.getString("SaveModel"));
                                chooser.setFileFilter(new FileNameExtensionFilter(bundle.getString("ModelFile"), "sml"));
                                if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(Explorer.this)) == JFileChooser.APPROVE_OPTION) {
                                    File file = chooser.getSelectedFile();
                                    if (!file.getName().toLowerCase().endsWith(".sml")) {
                                        file = new File(file.getParentFile(), file.getName() + ".sml");
                                    }
                                    var snippet = (VarSnippet) node.getUserObject();
                                    var name = snippet.name();
                                    // replace backslash with slash in case of Windows
                                    runner.eval(String.format("""
                                            smile.io.Write.object(%s, java.nio.file.Paths.get("%s"));
                                            """, name, file.getAbsolutePath().replace('\\', '/')));
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Refreshes the tree with JShell active variables.
     */
    public void refresh() {
        frames.removeAllChildren();
        matrix.removeAllChildren();
        models.removeAllChildren();
        treeModel.reload(root);

        runner.variables().forEach(snippet -> {
            var node = new DefaultMutableTreeNode(snippet);
            switch (snippet.typeName()) {
                case "DataFrame" -> {
                    treeModel.insertNodeInto(node, frames, frames.getChildCount());
                }
                case "DenseMatrix", "BandMatrix", "SymmMatrix", "SparseMatrix" -> {
                    treeModel.insertNodeInto(node, matrix, matrix.getChildCount());
                }
                case "Classifier", "Regression", "KNN", "FLD", "LDA", "QDA", "RDA",
                     "NaiveBayes", "OneVersusOne", "OneVersusRest", "MLP", "RBFNetwork",
                     "LogisticRegression", "SparseLogisticRegression", "Maxent",
                     "DecisionTree", "RegressionTree", "AdaBoost", "RandomForest", "GradientTreeBoost",
                     "KernelMachine", "LinearSVM", "SparseLinearSVM",
                     "LinearModel", "GaussianProcessRegression" -> {
                    treeModel.insertNodeInto(node, models, models.getChildCount());
                }
            }
        });

        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}
