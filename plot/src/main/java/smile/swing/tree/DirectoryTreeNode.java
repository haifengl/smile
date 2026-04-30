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
import java.io.Serial;
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
    @Serial
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DirectoryTreeNode.class);

    /** Reusable filter that skips hidden files (names starting with '.'). */
    private static final java.util.function.Predicate<Path> VISIBLE =
            p -> !p.getFileName().toString().startsWith(".");

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

    /**
     * Returns {@code true} if this node is a leaf (i.e. not a directory).
     *
     * <p>Delegates to {@link #getAllowsChildren()} which was set once from
     * {@link Files#isDirectory} in the constructor, avoiding a filesystem
     * call on every repaint.
     */
    @Override
    public boolean isLeaf() {
        return !getAllowsChildren();
    }

    /**
     * Adds the child nodes, skipping hidden files (those whose name starts with '.').
     * @param model the tree model to insert child nodes into.
     */
    public void addChildren(DefaultTreeModel model) {
        if (getAllowsChildren() && getChildCount() == 0) {
            try (Stream<Path> stream = Files.list(path)) {
                comparator.decorated(stream.filter(VISIBLE))
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
        if (!getAllowsChildren()) return;

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
            stream.filter(VISIBLE).forEach(current::add);
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

        // Pass 2: insert new nodes in sorted order.
        // decorated() pre-fetches isDirectory once per path so the binary-search
        // below does not cause repeated filesystem calls for the same entry.
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
     * <p>The {@link #decorated(Stream)} method pre-fetches each path's
     * {@code isDirectory} flag once before sorting begins, avoiding repeated
     * {@link Files#isDirectory} filesystem calls inside the comparison loop.
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
         * Maps each path in {@code paths} to a {@link PathWithType} (pre-fetching
         * the {@code isDirectory} flag exactly once), sorts the resulting list
         * using a stable ordering of directories first then
         * {@link String#CASE_INSENSITIVE_ORDER case-insensitive} file-name order,
         * and returns the sorted list.
         *
         * @param paths the raw paths to decorate.
         * @return a sorted, unmodifiable list of decorated path entries.
         */
        List<PathWithType> decorated(Stream<Path> paths) {
            return paths.map(PathWithType::of)
                        .sorted(Comparator.comparingInt((PathWithType e) -> e.isDir() ? 0 : 1)
                                          .thenComparing(e -> e.path().getFileName().toString(),
                                                         String.CASE_INSENSITIVE_ORDER))
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
