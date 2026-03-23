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
package smile.swing.tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A tree node for file directory.
 *
 * @author Haifeng Li
 */
public class DirectoryTreeNode extends DefaultMutableTreeNode {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DirectoryTreeNode.class);
    /** Comparator for sorting paths with directories first, then alphabetically by file name. */
    private static final PathComparator comparator = new PathComparator();
    /** The directory path. */
    private final Path path;
    /** The display name. */
    private final String name;

    /**
     * Constructor.
     * @param path the directory path.
     */
    public DirectoryTreeNode(Path path) {
        super(path, Files.isDirectory(path));
        this.path = Objects.requireNonNull(path);
        this.name = path.getFileName().toString();
    }

    /**
     * Returns the directory path.
     * @return the directory path.
     */
    public Path path() {
        return path;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean isLeaf() {
        return !Files.isDirectory(path);
    }

    /**
     * Adds the child nodes.
     * @param model the tree model to insert child nodes into.
     */
    public void addChildren(DefaultTreeModel model) {
        var self = this;
        if (Files.isDirectory(path) && getChildCount() == 0) {
            try (Stream<Path> stream = Files.list(path)) {
                stream.sorted(comparator).forEach(path ->
                        model.insertNodeInto(new DirectoryTreeNode(path), self, getChildCount()));
            } catch (IOException ex) {
                logger.warn("Error creating child node: ", ex);
            }
        }
    }

    /**
     * A custom Comparator that sorts paths with directories first, then alphabetically by file name.
     */
    static class PathComparator implements Comparator<Path> {
        @Override
        public int compare(Path path1, Path path2) {
            // First, check if one is a directory and the other is not
            boolean isDir1 = Files.isDirectory(path1);
            boolean isDir2 = Files.isDirectory(path2);

            // Primary sorting: directories first (false before true)
            if (isDir1 && !isDir2) {
                return -1;
            } else if (!isDir1 && isDir2) {
                return 1;
            } else {
                // Secondary sorting: then by file name (lexicographically)
                return path1.getFileName().toString().compareToIgnoreCase(path2.getFileName().toString());
            }
        }
    }
}
