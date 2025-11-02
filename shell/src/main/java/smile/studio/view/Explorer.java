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
import java.util.Locale;
import java.util.ResourceBundle;
import smile.studio.model.Runner;

/**
 * A workspace explorer.
 *
 * @author Haifeng Li
 */
public class Explorer extends JPanel implements TreeSelectionListener {
    /** The message resource bundle. */
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Explorer.class.getName(), Locale.getDefault());
    /** Root node. */
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(bundle.getString("Root"));
    /** Tree of workspace runtime information. */
    private final JTree tree = new JTree(root);

    /**
     * Constructor.
     */
    public Explorer(Runner runner) {
        super(new BorderLayout());
        createNodes();

        // Allow one selection at a time.
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        // Listen for when the selection changes.
        tree.addTreeSelectionListener(this);
        // Expand the tree
        tree.expandPath(new TreePath(root));

        // Add the tree to the scroll pane.
        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Creates tree nodes.
     */
    private void createNodes() {
        DefaultMutableTreeNode category;
        DefaultMutableTreeNode category2;
        DefaultMutableTreeNode algorithm = null;

        category = new DefaultMutableTreeNode(bundle.getString("DataFrames"));
        root.add(category);

        category = new DefaultMutableTreeNode(bundle.getString("Tables"));
        root.add(category);

        category = new DefaultMutableTreeNode(bundle.getString("Models"));
        root.add(category);

        category = new DefaultMutableTreeNode(bundle.getString("Services"));
        root.add(category);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node != null && node.isLeaf()) {
            /*
            int pos = workspace.getDividerLocation();
            workspace.setTopComponent((JPanel) node.getUserObject());
            workspace.setDividerLocation(pos);
             */
        }
    }
}
