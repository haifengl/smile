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
import javax.swing.event.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import smile.swing.tree.DirectoryTreeNode;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * A simple file explorer based on {@link JTree} that automatically monitors
 * the filesystem for changes using a {@link WatchService}.
 *
 * <p>When files or directories are created or deleted inside any directory
 * that is currently visible in the tree, the corresponding
 * {@link DirectoryTreeNode} is refreshed on the Event Dispatch Thread so
 * that the tree stays in sync without requiring a manual reload.
 *
 * <p>{@code FileExplorer} implements {@link Closeable}; call {@link #close()}
 * when the component is no longer needed to stop the background watcher thread
 * and release the underlying OS watch handles.  If {@code close()} is not
 * called the watcher thread is a daemon thread and will be reclaimed when the
 * JVM exits.
 *
 * @author Haifeng Li
 */
public class FileExplorer extends JTree
        implements TreeSelectionListener, TreeWillExpandListener, Closeable {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(FileExplorer.class);

    /** The {@link WatchService} used to monitor filesystem events. */
    private WatchService watchService;

    /**
     * Maps each active {@link WatchKey} back to the {@link DirectoryTreeNode}
     * that owns the corresponding directory, so that events can be dispatched
     * to the correct node without a tree walk.
     */
    private final Map<WatchKey, DirectoryTreeNode> watchKeys = new ConcurrentHashMap<>();

    /** Background thread that processes {@link WatchKey} events. */
    private Thread watchThread;

    /**
     * Constructor.
     *
     * @param root the root directory of the file explorer.
     * @throws IOException if the {@link WatchService} cannot be created or the
     *         root directory cannot be registered.
     */
    public FileExplorer(Path root) {
        super(new DefaultTreeModel(new DirectoryTreeNode(root)));
        setShowsRootHandles(true);
        addTreeSelectionListener(this);
        addTreeWillExpandListener(this);

        // Populate the root level immediately.
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        var rootNode = (DirectoryTreeNode) model.getRoot();
        rootNode.addChildren(model);
        expandPath(new TreePath(rootNode));

        // Create the watch service and register the root directory.
        try {
            watchService = root.getFileSystem().newWatchService();
            register(rootNode);

            // Start a daemon watcher thread so it does not prevent JVM exit.
            watchThread = Thread.ofVirtual()
                    .name("FileExplorer-WatchService")
                    .start(this::watchLoop);
        } catch (IOException e) {
            logger.error("Failed to create WatchService: ", e);
        }
    }

    // -------------------------------------------------------------------------
    // TreeSelectionListener
    // -------------------------------------------------------------------------

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        if (getLastSelectedPathComponent() instanceof DirectoryTreeNode node) {
            expandNode(node);
        }
    }

    // -------------------------------------------------------------------------
    // TreeWillExpandListener
    // -------------------------------------------------------------------------

    @Override
    public void treeWillExpand(TreeExpansionEvent event) {
        if (event.getPath().getLastPathComponent() instanceof DirectoryTreeNode node) {
            expandNode(node);
        }
    }

    @Override
    public void treeWillCollapse(TreeExpansionEvent event) {
        // Nothing to do on collapse.
    }

    // -------------------------------------------------------------------------
    // Closeable
    // -------------------------------------------------------------------------

    /**
     * Stops the background watcher thread and closes the underlying
     * {@link WatchService}, releasing all OS watch handles.
     *
     * <p>After this method returns the tree is no longer refreshed
     * automatically, but it remains fully functional as a read-only widget.
     */
    @Override
    public void close() {
        watchThread.interrupt();
        try {
            watchService.close();
        } catch (IOException e) {
            logger.warn("Error closing WatchService: ", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Populates children of {@code node} (if not already done) and registers
     * its directory — and every newly added subdirectory — with the watcher.
     */
    private void expandNode(DirectoryTreeNode node) {
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        int before = node.getChildCount();
        node.addChildren(model);
        // Register any subdirectories that were just added.
        if (node.getChildCount() > before) {
            register(node);
        }
    }

    /**
     * Registers the directory owned by {@code node} with the {@link WatchService}
     * for {@code ENTRY_CREATE} and {@code ENTRY_DELETE} events, and recursively
     * registers every subdirectory that is already represented as a child node
     * (i.e. already expanded).
     *
     * @param node the node whose directory should be watched.
     */
    private void register(DirectoryTreeNode node) {
        Path dir = node.path();
        if (!Files.isDirectory(dir)) return;
        try {
            WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE);
            watchKeys.put(key, node);
            logger.debug("Watching: {}", dir);
        } catch (IOException e) {
            logger.warn("Cannot register watch for '{}': {}", dir, e.getMessage());
        }
        // Recursively register already-expanded children.
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof DirectoryTreeNode child) {
                register(child);
            }
        }
    }

    /**
     * Registers a newly created directory (and all of its subdirectories) with
     * the watcher by walking the tree under {@code dir}.  The corresponding
     * {@link DirectoryTreeNode} is looked up from the parent node's children.
     *
     * @param parent the tree node that is the direct parent of the new entry.
     * @param dir    the newly created directory path.
     */
    private void registerNewDirectory(DirectoryTreeNode parent, Path dir) {
        // Find the child node that was just inserted for this path.
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) instanceof DirectoryTreeNode child
                    && child.path().equals(dir)) {
                try {
                    // Walk the whole new subtree in case it was copied/moved in.
                    Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path d,
                                BasicFileAttributes attrs) {
                            // Only register dirs that have a tree node for them.
                            if (d.equals(dir)) {
                                register(child);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    logger.warn("Error walking new directory '{}': {}", dir, e.getMessage());
                }
                break;
            }
        }
    }

    /**
     * Main loop executed by the background watcher thread.
     * Blocks on {@link WatchService#take()} and dispatches each
     * {@code ENTRY_CREATE} / {@code ENTRY_DELETE} event to the EDT.
     */
    private void watchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                // close() was called — exit cleanly.
                break;
            }

            DirectoryTreeNode node = watchKeys.get(key);
            if (node != null) {
                boolean hasChanges = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        // OS event queue overflowed; do a full refresh.
                        hasChanges = true;
                        continue;
                    }
                    if (kind != ENTRY_CREATE && kind != ENTRY_DELETE) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changed = node.path().resolve(pathEvent.context());

                    // Skip hidden files.
                    String fname = changed.getFileName().toString();
                    if (fname.startsWith(".")) continue;

                    hasChanges = true;
                    logger.debug("{} event: {}", kind.name(), changed);

                    // For a newly created directory, register it for watching
                    // after the EDT has had a chance to insert its node.
                    if (kind == ENTRY_CREATE && Files.isDirectory(changed)) {
                        final DirectoryTreeNode parentNode = node;
                        final Path newDir = changed;
                        SwingUtilities.invokeLater(() ->
                                registerNewDirectory(parentNode, newDir));
                    }
                }

                if (hasChanges) {
                    final DirectoryTreeNode affectedNode = node;
                    SwingUtilities.invokeLater(() -> {
                        DefaultTreeModel model = (DefaultTreeModel) getModel();
                        affectedNode.refresh(model);
                    });
                }
            }

            // Re-queue the key to receive further events; cancel it if invalid
            // (directory was deleted).
            if (!key.reset()) {
                logger.debug("Watch key invalidated (directory deleted?): {}",
                        node != null ? node.path() : "unknown");
                watchKeys.remove(key);
            }
        }
        logger.debug("FileExplorer watcher thread exiting.");
    }
}
