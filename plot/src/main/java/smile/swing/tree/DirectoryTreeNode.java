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
import java.util.HashMap;
import java.util.Map;
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
     * Adds the child nodes, skipping hidden files (those whose name starts with '.').
     * @param model the tree model to insert child nodes into.
     */
    public void addChildren(DefaultTreeModel model) {
        var self = this;
        if (Files.isDirectory(path) && getChildCount() == 0) {
            try (Stream<Path> stream = Files.list(path)) {
                stream.filter(p -> !p.getFileName().toString().startsWith("."))
                      .sorted(comparator)
                      .forEach(path ->
                        model.insertNodeInto(new DirectoryTreeNode(path), self, getChildCount()));
            } catch (IOException ex) {
                logger.warn("Error creating child node: ", ex);
            }
        }
    }

    /**
     * Refreshes the immediate children of this node to match the current state
     * of the filesystem directory.
     *
     * <p>This method:
     * <ul>
     *   <li>Removes child nodes whose paths no longer exist on disk.</li>
     *   <li>Inserts new child nodes for paths that appeared on disk since the
     *       last refresh, maintaining the directories-first alphabetical order.</li>
     *   <li>Does <em>not</em> touch already-expanded grandchildren, so open
     *       subtrees are preserved across a refresh cycle.</li>
     * </ul>
     *
     * <p>Must be called on the Swing Event Dispatch Thread.
     *
     * @param model the tree model (used to fire the appropriate structural events).
     */
    public void refresh(DefaultTreeModel model) {
        if (!Files.isDirectory(path)) return;

        // Index existing children by their path for O(1) lookup.
        Map<Path, DirectoryTreeNode> existing = new HashMap<>();
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof DirectoryTreeNode child) {
                existing.put(child.path(), child);
            }
        }

        // Determine the current filesystem entries (excluding hidden files).
        Map<Path, Boolean> current = new HashMap<>();
        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(p -> !p.getFileName().toString().startsWith("."))
                  .forEach(p -> current.put(p, Boolean.TRUE));
        } catch (IOException ex) {
            logger.warn("Error listing directory '{}' during refresh: ", path, ex);
            return;
        }

        // Remove nodes whose backing paths no longer exist.
        existing.forEach((p, node) -> {
            if (!current.containsKey(p)) {
                model.removeNodeFromParent(node);
            }
        });

        // Insert nodes for newly appeared paths (re-index after removals).
        Map<Path, DirectoryTreeNode> remaining = new HashMap<>();
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof DirectoryTreeNode child) {
                remaining.put(child.path(), child);
            }
        }

        current.keySet().stream()
               .filter(p -> !remaining.containsKey(p))
               .sorted(comparator)
               .forEach(p -> {
                   // Determine insertion index to keep the sorted order.
                   DirectoryTreeNode newNode = new DirectoryTreeNode(p);
                   int insertAt = 0;
                   while (insertAt < getChildCount()) {
                       if (getChildAt(insertAt) instanceof DirectoryTreeNode sibling) {
                           if (comparator.compare(p, sibling.path()) <= 0) break;
                       }
                       insertAt++;
                   }
                   model.insertNodeInto(newNode, this, insertAt);
               });
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
