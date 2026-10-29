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
import ioa.agent.bot.BotsConfig;
import jdk.jshell.VarSnippet;
import smile.studio.StudioConfig;
import smile.studio.bot.BotManagerDialog;
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
    private final DefaultMutableTreeNode frames = new DefaultMutableTreeNode(bundle.getString("DataFrames"));;
    private final DefaultMutableTreeNode matrix = new DefaultMutableTreeNode(bundle.getString("Matrix"));
    private final DefaultMutableTreeNode models = new DefaultMutableTreeNode(bundle.getString("Models"));
    private final DefaultMutableTreeNode services = new DefaultMutableTreeNode(bundle.getString("Services"));
    /** The single inference service node under {@link #services}. */
    private final DefaultMutableTreeNode serviceNode =
            new DefaultMutableTreeNode(new ServeService(StudioConfig.DEFAULT_HOST, StudioConfig.DEFAULT_PORT));
    /** The Agent Hub service node under {@link #services}. */
    private final DefaultMutableTreeNode hubNode =
            new DefaultMutableTreeNode(new HubService(
                    StudioConfig.DEFAULT_HOST,
                    StudioConfig.DEFAULT_HUB_PORT,
                    StudioConfig.DEFAULT_HUB_ADMIN_TOKEN));

    /** User object for a Bot persona tree node under {@link #hubNode}. */
    public record BotTreeItem(BotsConfig.BotSpec bot) {
        @Override
        public String toString() {
            String prefix = "*".equals(bot.persona()) ? "★ " : "@";
            return prefix + bot.botId() + " (" + bot.name() + ")";
        }
    }

    /** User object for a Routine tree node under a {@link BotTreeItem}. */
    public record RoutineTreeItem(BotsConfig.BotSpec bot, BotsConfig.RoutineSpec routine) {
        @Override
        public String toString() {
            String mark = (bot.enabled() && routine.enabled()) ? "● " : "○ ";
            return mark + routine.routineId() + " — " + routine.name();
        }
    }
    private static final ImageIcon matrixIcon = loadImageIcon(KernelExplorer.class, "images/matrix.png", LARGE_ICON_SIZE);
    private static final ImageIcon modelIcon = loadImageIcon(KernelExplorer.class, "images/model.png", LARGE_ICON_SIZE);
    private static final ImageIcon serverIcon = loadImageIcon(KernelExplorer.class, "images/server.png", LARGE_ICON_SIZE);
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
        treeModel.insertNodeInto(services, root, root.getChildCount());
        // One shared inference service; saved models are added as its children.
        treeModel.insertNodeInto(serviceNode, services, services.getChildCount());
        // Agent Hub service; Bot personas and their Routines are added as its children.
        treeModel.insertNodeInto(hubNode, services, services.getChildCount());
        refreshBotsTree();

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
                } else if (object instanceof ServeService service) {
                    setIcon(serverIcon);
                    setText(bundle.getString("InferenceService"));
                    setToolTipText(service.host() + ":" + service.port());
                } else if (object instanceof HubService hub) {
                    setIcon(serverIcon);
                    setText(bundle.getString("AgentHubService") + (HubManager.getInstance().isRunning() ? " (Running)" : ""));
                    setToolTipText(hub.host() + ":" + hub.port());
                } else if (object instanceof BotTreeItem botItem) {
                    setText(botItem.toString());
                    setToolTipText(botItem.bot().description());
                } else if (object instanceof RoutineTreeItem routineItem) {
                    setText(routineItem.toString());
                    setToolTipText(routineItem.routine().trigger().source() + ": " + routineItem.routine().workflow().target());
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
                    // Get the path and node associated with the double click
                    TreePath treePath = tree.getPathForLocation(e.getX(), e.getY());
                    if (treePath != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                        // The service node is not a leaf once models are added, so it
                        // is handled before the leaf guard below.
                        if (node == serviceNode) {
                            StartServiceDialog dialog = new StartServiceDialog(
                                    SwingUtilities.getWindowAncestor(KernelExplorer.this),
                                    (ServeService) serviceNode.getUserObject());
                            dialog.setVisible(true);
                            return;
                        }
                        if (node == hubNode || node.getUserObject() instanceof BotTreeItem) {
                            Workspace ws = (Workspace) SwingUtilities.getAncestorOfClass(Workspace.class, KernelExplorer.this);
                            if (ws != null) {
                                ws.openBotActivity(0);
                            }
                            return;
                        }
                        if (node.getUserObject() instanceof RoutineTreeItem routineItem) {
                            BotManagerDialog dialog = new BotManagerDialog(
                                    SwingUtilities.getWindowAncestor(KernelExplorer.this),
                                    routineItem.routine().routineId(),
                                    KernelExplorer.this::refreshBotsTree);
                            dialog.setVisible(true);
                            return;
                        }
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

                                    if (snippet.typeName().equals("ClassificationModel")
                                            || snippet.typeName().equals("RegressionModel")
                                            || snippet.typeName().equals("ClusteringModel")
                                            || snippet.typeName().equals("VectorQuantizerModel")
                                            || snippet.typeName().equals("AnomalyModel")) {
                                        var schema = kernel.eval(name + ".schema();");
                                        if (schema != null) {
                                            var serviceNode = new DefaultMutableTreeNode(new PersistedModel(name, schema.toString(), path));
                                            treeModel.insertNodeInto(serviceNode, KernelExplorer.this.serviceNode, KernelExplorer.this.serviceNode.getChildCount());
                                            tree.expandPath(new TreePath(new Object[]{root, services, KernelExplorer.this.serviceNode}));
                                        }
                                    }
                                }
                                // Restore the original dialog title after the file chooser is closed
                                fileChooser.setDialogTitle(title);
                            } else if (parent == serviceNode) {
                                // Double-click a saved model: start the service if needed, then load it.
                                var model = (PersistedModel) node.getUserObject();
                                loadModel(model);
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

    /**
     * Populates the {@link #hubNode} subtree from {@code ${smile.home}/conf/bots.json}
     * (or the running Agent Hub).
     */
    public final void refreshBotsTree() {
        hubNode.removeAllChildren();
        try {
            BotsConfig cfg = BotsConfig.load();
            for (BotsConfig.BotSpec bot : cfg.bots()) {
                DefaultMutableTreeNode botTreeNode = new DefaultMutableTreeNode(new BotTreeItem(bot));
                for (BotsConfig.RoutineSpec routine : bot.routines()) {
                    botTreeNode.add(new DefaultMutableTreeNode(new RoutineTreeItem(bot, routine)));
                }
                hubNode.add(botTreeNode);
            }
        } catch (Exception ex) {
            logger.debug("Could not populate Agent Hub tree nodes: {}", ex.getMessage());
        }
        treeModel.reload(hubNode);
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
        Window owner = SwingUtilities.getWindowAncestor(this);

        if (node == hubNode || userObj instanceof BotTreeItem || userObj instanceof RoutineTreeItem) {
            if (userObj instanceof RoutineTreeItem item) {
                JMenuItem runItem = new JMenuItem("▶ Run Now (" + item.routine().routineId() + ")");
                runItem.addActionListener(ev -> Thread.ofPlatform().name("hub-tree-run").daemon(true).start(() -> {
                    try {
                        if (!HubManager.getInstance().isRunning()) {
                            HubManager.getInstance().start();
                        }
                        HubManager.getInstance().client().runRoutineNow(
                                item.bot().botId(), item.routine().routineId());
                    } catch (Exception ex) {
                        showError(ex.getMessage());
                    }
                }));
                popup.add(runItem);

                boolean currentlyEnabled = item.routine().enabled();
                JMenuItem toggleItem = new JMenuItem(currentlyEnabled ? "Disable Routine" : "Enable Routine");
                toggleItem.addActionListener(ev -> Thread.ofPlatform().name("hub-tree-toggle").daemon(true).start(() -> {
                    try {
                        HubManager.getInstance().client().setRoutineEnabled(
                                item.bot().botId(), item.routine().routineId(), !currentlyEnabled);
                        SwingUtilities.invokeLater(this::refreshBotsTree);
                    } catch (Exception ex) {
                        showError(ex.getMessage());
                    }
                }));
                popup.add(toggleItem);

                JMenuItem editItem = new JMenuItem("Configure Routine…");
                editItem.addActionListener(ev -> {
                    BotManagerDialog dlg = new BotManagerDialog(owner, item.routine().routineId(), this::refreshBotsTree);
                    dlg.setVisible(true);
                });
                popup.add(editItem);
                popup.addSeparator();
            }

            JMenuItem manageBotsItem = new JMenuItem("Configure Bot Routines…");
            manageBotsItem.addActionListener(ev -> {
                BotManagerDialog dlg = new BotManagerDialog(owner, null, this::refreshBotsTree);
                dlg.setVisible(true);
            });
            popup.add(manageBotsItem);

            JMenuItem activityItem = new JMenuItem("Bot Activity…");
            activityItem.addActionListener(ev -> {
                Workspace ws = (Workspace) SwingUtilities.getAncestorOfClass(Workspace.class, KernelExplorer.this);
                if (ws != null) {
                    ws.openBotActivity(0);
                }
            });
            popup.add(activityItem);
            popup.addSeparator();

            boolean running = HubManager.getInstance().isRunning();
            JMenuItem startStopItem = new JMenuItem(running ? "Stop Agent Hub" : "Start Agent Hub");
            startStopItem.addActionListener(ev -> Thread.ofPlatform().name("hub-tree-startstop").daemon(true).start(() -> {
                if (running) {
                    HubManager.getInstance().stop();
                } else {
                    if (!HubManager.getInstance().start()) {
                        showError(bundle.getString("HubStartFailed"));
                    }
                }
                SwingUtilities.invokeLater(this::refreshBotsTree);
            }));
            popup.add(startStopItem);

            popup.show(tree, e.getX(), e.getY());
        }
    }

    /**
     * Loads a saved model into the shared inference service, starting the
     * service first if it is not running. Runs off the EDT because both the
     * start (readiness wait) and the load call block.
     *
     * @param model the saved model to load.
     */
    private void loadModel(PersistedModel model) {
        var service = (ServeService) serviceNode.getUserObject();
        Thread.ofPlatform().name("serve-load-model").daemon(true).start(() -> {
            try {
                var manager = ServeManager.getInstance();
                if (!manager.isRunning() && !manager.start(service.host(), service.port())) {
                    showError(bundle.getString("ServiceStartFailed"));
                    return;
                }
                manager.loadModel(model.path());
                SwingUtilities.invokeLater(() -> tree.repaint());
            } catch (Exception ex) {
                logger.error("Failed to load model '{}': {}", model.name(), ex.getMessage());
                showError(ex.getMessage());
            }
        });
    }

    /**
     * Shows an error dialog on the EDT.
     *
     * @param message the message.
     */
    private void showError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(KernelExplorer.this),
                message,
                bundle.getString("Error"),
                JOptionPane.ERROR_MESSAGE));
    }

    /** The dialog to start model inference service. */
    static class StartServiceDialog extends JDialog {
        private final JTextField hostField = new JTextField(25);
        private final JTextField portField = new JTextField(25);

        /**
         * Constructor.
         *
         * @param owner   the owner window.
         * @param service the current service settings, used to prefill the fields.
         */
        public StartServiceDialog(Window owner, ServeService service) {
            super(owner, bundle.getString("StartServiceDialogTitle"));
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout());

            hostField.setText(service.host());
            portField.setText(String.valueOf(service.port()));

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
                String host = hostField.getText().isBlank() ? StudioConfig.DEFAULT_HOST : hostField.getText().trim();
                int port;
                try {
                    port = Integer.parseInt(portField.getText().trim());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, bundle.getString("InvalidPort"),
                            bundle.getString("Error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }

                Thread.ofPlatform().name("serve-start").daemon(true).start(() -> {
                    if (!ServeManager.getInstance().start(host, port)) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                getOwner(),
                                bundle.getString("ServiceStartFailed"),
                                bundle.getString("Error"), JOptionPane.ERROR_MESSAGE));
                    }
                });
            });

            cancelButton.addActionListener((e) -> dispose());

            add(inputPane, BorderLayout.CENTER);
            add(buttonPane, BorderLayout.SOUTH);
            pack();
            setLocationRelativeTo(owner);
        }
    }
}
