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
import javax.swing.tree.TreeSelectionModel;

public class Explorer extends JScrollPane implements TreeSelectionListener {
    private final JTree tree;

    public Explorer() {
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("Smile");
        createNodes(top);

        // Create a tree that allows one selection at a time.
        tree = new JTree(top);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // Listen for when the selection changes.
        tree.addTreeSelectionListener(this);

        // Add the tree to the scroll pane.
        add(tree);
    }

    private void createNodes(DefaultMutableTreeNode top) {
        DefaultMutableTreeNode category;
        DefaultMutableTreeNode category2;
        DefaultMutableTreeNode algorithm = null;

        category = new DefaultMutableTreeNode("DataFrame");
        top.add(category);

        category = new DefaultMutableTreeNode("SQL");
        top.add(category);

        category = new DefaultMutableTreeNode("Model");
        top.add(category);

        category = new DefaultMutableTreeNode("Service");
        top.add(category);
    }

    /** Required by TreeSelectionListener interface. */
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
