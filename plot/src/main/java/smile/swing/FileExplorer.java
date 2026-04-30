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
import java.io.Serial;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
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

    @Serial
    private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(FileExplorer.class);

    /** The {@link WatchService} used to monitor filesystem events, or {@code null} if unavailable. */
    private final WatchService watchService;

    /**
     * Maps each active {@link WatchKey} back to the {@link DirectoryTreeNode}
     * that owns the corresponding directory, so that events can be dispatched
     * to the correct node without a tree walk.
     */
    private final Map<WatchKey, DirectoryTreeNode> watchKeys = new ConcurrentHashMap<>();

    /** Background thread that processes {@link WatchKey} events, or {@code null} if unavailable. */
    private final Thread watchThread;

    /**
     * Constructor.
     *
     * @param root the root directory of the file explorer.
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
        // Use local temporaries so watchService and watchThread can be final.
        WatchService ws = null;
        Thread wt = null;
        try {
            ws = root.getFileSystem().newWatchService();
            register(rootNode, ws);
            wt = Thread.ofVirtual()
                    .name("FileExplorer-WatchService")
                    .start(this::watchLoop);
        } catch (IOException e) {
            logger.error("Failed to create WatchService: ", e);
        }
        watchService = ws;
        watchThread  = wt;
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
     * Safe to call even if the {@link WatchService} failed to initialise.
     */
    @Override
    public void close() {
        if (watchThread != null) watchThread.interrupt();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.warn("Error closing WatchService: ", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Populates children of {@code node} (if not already done) and ensures
     * the node's directory is registered with the watcher.
     *
     * <p>Registering is unconditional: re-registering an already-watched
     * directory is idempotent ({@link WatchService} returns the existing key),
     * so this is safe to call every time a node is selected or expanded,
     * including nodes whose children were loaded in a previous session.
     */
    private void expandNode(DirectoryTreeNode node) {
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        node.addChildren(model);
        // Always register (or re-register) the node's directory so that new
        // subdirectories created after the first expansion are also watched.
        register(node, watchService);
    }

    /**
     * Registers the directory owned by {@code node} with {@code ws} for
     * {@code ENTRY_CREATE} and {@code ENTRY_DELETE} events, and recursively
     * registers every subdirectory already represented as a child node.
     * Does nothing if {@code ws} is {@code null}.
     *
     * @param node the node whose directory should be watched.
     * @param ws   the {@link WatchService} to register with; may be {@code null}.
     */
    private void register(DirectoryTreeNode node, WatchService ws) {
        if (ws == null) return;
        Path dir = node.path();
        if (!Files.isDirectory(dir)) return;
        try {
            WatchKey key = dir.register(ws, ENTRY_CREATE, ENTRY_DELETE);
            watchKeys.put(key, node);
            logger.debug("Watching: {}", dir);
        } catch (IOException e) {
            logger.warn("Cannot register watch for '{}': {}", dir, e.getMessage());
        }
        // Recursively register already-expanded children.
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof DirectoryTreeNode child) {
                register(child, ws);
            }
        }
    }

    /**
     * Registers a newly created directory with the {@link WatchService} so that
     * future events inside it are captured.  The corresponding
     * {@link DirectoryTreeNode} is looked up from {@code parent}'s children.
     *
     * <p>Only the immediate new directory is registered here.  Any subdirectories
     * it contains are lazily registered when the user expands the node (via
     * {@link #expandNode}).  This avoids mapping deeply nested OS watch keys to
     * incorrect tree nodes — a node for a deep subdirectory does not yet exist
     * in the tree at this point.
     *
     * <p>Must be called on the EDT, after the preceding {@code refresh()} call
     * has already inserted the new child node into the model.
     *
     * @param parent the tree node that is the direct parent of the new entry.
     * @param dir    the newly created directory path.
     */
    private void registerNewDirectory(DirectoryTreeNode parent, Path dir) {
        if (watchService == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) instanceof DirectoryTreeNode child
                    && child.path().equals(dir)) {
                register(child, watchService);
                break;
            }
        }
    }

    /**
     * Removes from {@link #watchKeys} all entries whose {@link WatchKey} is
     * no longer valid (i.e. the watched directory has been deleted).
     * Called after {@code key.reset()} returns {@code false} to eagerly purge
     * any sibling keys that were also invalidated by the same deletion event.
     */
    private void purgeInvalidKeys() {
        watchKeys.keySet().removeIf(k -> !k.isValid());
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
                // Collect newly created directories so we can register them
                // for watching after the refresh has inserted their nodes.
                List<Path> newDirs = new ArrayList<>();

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

                    // Collect newly created directories; they are registered
                    // *after* refresh() has inserted their nodes into the model.
                    if (kind == ENTRY_CREATE && Files.isDirectory(changed)) {
                        newDirs.add(changed);
                    }
                }

                if (hasChanges) {
                    final DirectoryTreeNode affectedNode = node;
                    final List<Path> dirsToRegister = List.copyOf(newDirs);
                    SwingUtilities.invokeLater(() -> {
                        DefaultTreeModel model = (DefaultTreeModel) getModel();
                        // refresh() inserts the new child nodes first …
                        affectedNode.refresh(model);
                        // … then register the new directories so the node
                        // lookup in registerNewDirectory finds what refresh just inserted.
                        dirsToRegister.forEach(d -> registerNewDirectory(affectedNode, d));
                    });
                }
            }

            // Re-queue the key to receive further events; cancel it if invalid
            // (directory was deleted).  Also purge any other keys that were
            // transitively invalidated by the same deletion (e.g. sub-directories
            // of the removed tree).
            if (!key.reset()) {
                logger.debug("Watch key invalidated (directory deleted?): {}",
                        node != null ? node.path() : "unknown");
                watchKeys.remove(key);
                purgeInvalidKeys();
            }
        }
        logger.debug("FileExplorer watcher thread exiting.");
    }
}
