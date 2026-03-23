/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.swing;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.nio.file.Path;
import smile.swing.tree.DirectoryTreeNode;

/**
 * A simple file explorer based on JTree.
 *
 * @author Haifeng Li
 */
public class FileExplorer extends JTree implements TreeSelectionListener {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FileExplorer.class);

    /**
     * Constructor.
     * @param root the root directory of the file explorer.
     */
    public FileExplorer(Path root) {
        super(new DefaultTreeModel(new DirectoryTreeNode(root)));
        setShowsRootHandles(true);
        addTreeSelectionListener(this);
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        var rootNode = (DirectoryTreeNode) model.getRoot();
        rootNode.addChildren(model);
        var rootPath = new TreePath(rootNode);
        expandPath(rootPath);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        if (getLastSelectedPathComponent() instanceof DirectoryTreeNode node) {
            node.addChildren((DefaultTreeModel) getModel());
        }
    }
}
