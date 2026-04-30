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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        // getFileName() returns null for filesystem root paths (e.g. "/" or "C:\").
        // Fall back to the full path string so the root node always has a label.
        var filename = path.getFileName();
        this.name = filename != null ? filename.toString() : path.toString();
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
        if (Files.isDirectory(path) && getChildCount() == 0) {
            try (Stream<Path> stream = Files.list(path)) {
                comparator.decorated(
                        stream.filter(p -> !p.getFileName().toString().startsWith(".")))
                    .forEach(e ->
                        model.insertNodeInto(new DirectoryTreeNode(e.path()), this, getChildCount()));
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

        // Snapshot existing children keyed by path for O(1) lookup.
        Map<Path, DirectoryTreeNode> existing = new HashMap<>();
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof DirectoryTreeNode child) {
                existing.put(child.path(), child);
            }
        }

        // Snapshot the current filesystem entries (excluding hidden files).
        Set<Path> current = new HashSet<>();
        try (Stream<Path> stream = Files.list(path)) {
            stream.filter(p -> !p.getFileName().toString().startsWith("."))
                  .forEach(current::add);
        } catch (IOException ex) {
            logger.warn("Error listing directory '{}' during refresh: ", path, ex);
            return;
        }

        // Pre-compute the two sets of mutations before touching the tree model.
        // This avoids re-indexing children between the remove and insert passes.
        List<DirectoryTreeNode> toRemove = new ArrayList<>();
        existing.forEach((p, node) -> {
            if (!current.contains(p)) toRemove.add(node);
        });

        Set<Path> toAdd = new HashSet<>();
        current.forEach(p -> {
            if (!existing.containsKey(p)) toAdd.add(p);
        });

        // Pass 1: remove stale nodes.
        toRemove.forEach(model::removeNodeFromParent);

        // Pass 2: insert new nodes in sorted order, using pre-fetched isDirectory
        // flags to avoid repeated filesystem calls inside the comparator loop.
        comparator.decorated(toAdd.stream()).forEach(e -> {
            Path p = e.path();
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
     * A custom {@link Comparator} that sorts paths with directories first,
     * then alphabetically (case-insensitive) by file name.
     *
     * <p>The directory/file distinction is determined once per path object
     * before the sort begins (via {@link PathWithType}), avoiding repeated
     * {@link Files#isDirectory} filesystem calls during the comparison loop.
     */
    static class PathComparator implements Comparator<Path> {
        @Override
        public int compare(Path path1, Path path2) {
            boolean isDir1 = Files.isDirectory(path1);
            boolean isDir2 = Files.isDirectory(path2);
            if (isDir1 != isDir2) return isDir1 ? -1 : 1;
            return path1.getFileName().toString()
                        .compareToIgnoreCase(path2.getFileName().toString());
        }

        /**
         * Wraps a path together with its pre-computed {@code isDirectory} flag
         * so that sorting a list of paths does not require repeated filesystem
         * calls inside the comparator.
         *
         * @param paths the raw paths to decorate.
         * @return a list of decorated entries sorted by this comparator's order.
         */
        List<PathWithType> decorated(Stream<Path> paths) {
            return paths.map(PathWithType::of)
                        .sorted(Comparator.comparingInt((PathWithType e) -> e.isDir ? 0 : 1)
                                          .thenComparing(e -> e.path.getFileName()
                                                                     .toString()
                                                                     .toLowerCase()))
                        .toList();
        }
    }

    /**
     * A path together with its pre-fetched {@code isDirectory} flag, used to
     * avoid repeated {@link Files#isDirectory} calls during sorting.
     */
    record PathWithType(Path path, boolean isDir) {
        static PathWithType of(Path p) {
            return new PathWithType(p, Files.isDirectory(p));
        }
    }
}
