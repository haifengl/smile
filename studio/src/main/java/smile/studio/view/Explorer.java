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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import jdk.jshell.VarSnippet;
import com.formdev.flatlaf.util.SystemInfo;
import smile.studio.kernel.JavaRunner;
import smile.studio.model.PersistedModel;
import smile.swing.FileChooser;
import static smile.swing.SmileUtilities.scaleImageIcon;

/**
 * A workspace explorer.
 *
 * @author Haifeng Li
 */
public class Explorer extends JPanel {
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Explorer.class.getName(), Locale.getDefault());
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
    private final JavaRunner runner;

    /**
     * Constructor.
     * @param runner Java code execution engine.
     */
    public Explorer(JavaRunner runner) {
        super(new BorderLayout());
        this.runner = runner;

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
        treeModel.insertNodeInto(services, root, root.getChildCount());

        Monospaced.addListener((e) ->
                SwingUtilities.invokeLater(() -> tree.setFont((Font) e.getNewValue())));
        tree.setFont(Monospaced.getFont());
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
                } else if (object instanceof PersistedModel model) {
                    setText(model.name());
                    setToolTipText(model.schema());
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
                                    String path = file.getAbsolutePath().replace('\\', '/');
                                    runner.eval(String.format("""
                                            smile.io.Write.object(%s, java.nio.file.Paths.get("%s"));
                                            """, name, path));

                                    if (snippet.typeName().equals("ClassificationModel") || snippet.typeName().equals("RegressionModel")) {
                                        var schema = runner.eval(name + ".schema();");
                                        var serviceNode = new DefaultMutableTreeNode(new PersistedModel(name, schema, path));
                                        treeModel.insertNodeInto(serviceNode, services, services.getChildCount());
                                        tree.expandPath(new TreePath(new Object[]{root, services}));
                                    }
                                }
                            } else if (parent == services) {
                                PersistedModel service = (PersistedModel) node.getUserObject();
                                StartServiceDialog dialog = new StartServiceDialog(SwingUtilities.getWindowAncestor(Explorer.this), service);
                                dialog.setVisible(true);
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
            String typeName = snippet.typeName();
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
                      "ClassificationModel", "RegressionModel":
                    treeModel.insertNodeInto(node, models, models.getChildCount());
                    break;

                default:
                    if (typeName.startsWith("Classifier<") ||
                        typeName.startsWith("Regression<") ||
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

    /** The dialog to start model inference service. */
    static class StartServiceDialog extends JDialog {
        private final PersistedModel model;
        private final JTextField hostField = new JTextField(25);
        private final JTextField portField = new JTextField(25);

        /**
         * Constructor.
         */
        public StartServiceDialog(Window owner, PersistedModel model) {
            super(owner, bundle.getString("StartServiceDialogTitle"));
            this.model = model;
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout());

            JLabel hostLabel = new JLabel(bundle.getString("Host"));
            JLabel portLabel = new JLabel(bundle.getString("Port"));

            // Panel for the input field and label
            JPanel inputPane = new JPanel(new GridBagLayout());
            inputPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);

            // Row 1
            gbc.gridx = 0; // Column 0
            gbc.gridy = 0; // Row 0
            gbc.anchor = GridBagConstraints.WEST;
            inputPane.add(hostLabel, gbc);

            gbc.gridx = 1; // Column 1
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0; // Allow text field to take extra horizontal space
            inputPane.add(hostField, gbc);

            // Row 2
            gbc.gridx = 0; // Column 0
            gbc.gridy = 1; // Row 1
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.NONE; // Reset fill for label
            gbc.weightx = 0.0; // Reset weightx for label
            inputPane.add(portLabel, gbc);

            gbc.gridx = 1; // Column 1
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            inputPane.add(portField, gbc);

            // Panel for the buttons
            JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPane.setBorder(new EmptyBorder(0, 0, 0, 10));
            JButton okButton = new JButton(bundle.getString("OK"));
            JButton cancelButton = new JButton(bundle.getString("Cancel"));
            buttonPane.add(okButton);
            buttonPane.add(cancelButton);
            getRootPane().setDefaultButton(okButton);

            okButton.addActionListener((e) -> {
                dispose();
                ProcessFrame frame = new ProcessFrame(1000);
                frame.setTitle(model.name());

                String home = System.getProperty("smile.home", ".");
                // jvm options should be before -jar argument
                frame.start( "java",
                        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
                        "--enable-native-access", "ALL-UNNAMED",
                        "-Dsmile.serve.model=" + model.path(),
                        "-Dquarkus.http.host=" + hostField.getText(),
                        "-Dquarkus.http.port=" + portField.getText(),
                        "-jar", Path.of(home, "serve", "quarkus-run.jar").normalize().toString());
                frame.setVisible(true);
            });

            cancelButton.addActionListener((e) -> dispose());

            add(inputPane, BorderLayout.CENTER);
            add(buttonPane, BorderLayout.SOUTH);
            pack();
            setLocationRelativeTo(owner);
        }
    }
}
