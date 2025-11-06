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
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ResourceBundle;
import jdk.jshell.VarSnippet;
import smile.studio.model.Runner;

/**
 * A workspace explorer.
 *
 * @author Haifeng Li
 */
public class Explorer extends JPanel implements TreeSelectionListener {
    /** The message resource bundle. */
    private final ResourceBundle bundle = ResourceBundle.getBundle(Explorer.class.getName(), getLocale());
    /** Tree nodes. */
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(bundle.getString("Root"));
    private final DefaultMutableTreeNode frames = new DefaultMutableTreeNode(bundle.getString("DataFrames"));;
    private final DefaultMutableTreeNode matrix = new DefaultMutableTreeNode(bundle.getString("Matrix"));
    private final DefaultMutableTreeNode models = new DefaultMutableTreeNode(bundle.getString("Models"));
    private final DefaultMutableTreeNode services = new DefaultMutableTreeNode(bundle.getString("Services"));
    /** Tree of workspace runtime information. */
    private final JTree tree = new JTree(root);
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
        root.add(frames);
        root.add(matrix);
        root.add(models);
        root.add(services);

        // Allow one selection at a time.
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        // Listen for when the selection changes.
        tree.addTreeSelectionListener(this);
        // Expand the tree
        tree.expandPath(new TreePath(root));
        // Hide the root node
        tree.setRootVisible(false);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node != null) {
            var parent = node.getParent();
            if (parent == frames || parent == matrix) {
                var snippet = (VarSnippet) node.getUserObject();
                var name = snippet.name();
                runner.eval(String.format("""
                        var %sWindow = smile.swing.SmileSwing.show(%s);
                        %sWindow.setTitle(%s);
                        """, name, name, name, name));
            } else if (parent == models) {
                // Starts an inference service
            }
        }
    }

    /**
     * Refreshes the tree with JShell active variables.
     */
    public void refresh() {
        frames.removeAllChildren();
        matrix.removeAllChildren();
        models.removeAllChildren();

        runner.variables().forEach(snippet -> {
            switch (snippet.typeName()) {
                case "DataFrame" -> {
                    var node = new DefaultMutableTreeNode(snippet);
                    frames.add(node);
                }
                case "DenseMatrix", "BandMatrix", "SymmMatrix", "SparseMatrix" -> {
                    var node = new DefaultMutableTreeNode(snippet);
                    matrix.add(node);
                }
                case "Classifier", "Regression", "KNN", "FLD", "LDA", "QDA", "RDA",
                     "NaiveBayes", "OneVersusOne", "OneVersusRest", "MLP", "RBFNetwork",
                     "LogisticRegression", "SparseLogisticRegression", "Maxent",
                     "DecisionTree", "RegressionTree", "AdaBoost", "RandomForest", "GradientTreeBoost",
                     "KernelMachine", "LinearSVM", "SparseLinearSVM",
                     "LinearModel", "GaussianProcessRegression" -> {
                    var node = new DefaultMutableTreeNode(snippet);
                    models.add(node);
                }
            }
        });
    }
}
