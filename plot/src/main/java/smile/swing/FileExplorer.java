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
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import smile.swing.tree.DirectoryTreeNode;
import smile.util.Utf8ResourceBundleControl;
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
 * <p>A right-click context menu provides <em>Rename</em> and <em>Delete</em>
 * operations.  Files and directories can also be moved by dragging a node and
 * dropping it onto a directory node.
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
    private static final ResourceBundle bundle =
            ResourceBundle.getBundle(FileExplorer.class.getName(), Locale.getDefault(),
                    new Utf8ResourceBundleControl());

    /** DataFlavor used to transfer a {@link DirectoryTreeNode} during drag-and-drop. */
    private static final DataFlavor NODE_FLAVOR;
    static {
        DataFlavor f;
        try {
            f = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=" + DirectoryTreeNode.class.getName());
        } catch (ClassNotFoundException e) {
            f = DataFlavor.stringFlavor; // fallback — should never happen
        }
        NODE_FLAVOR = f;
    }

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

        // Context menu
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowPopup(e);
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowPopup(e);
            }
            private void maybeShowPopup(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                // Select the node under the cursor before showing the menu.
                TreePath path = getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                setSelectionPath(path);
                if (path.getLastPathComponent() instanceof DirectoryTreeNode node
                        // Do not offer rename/delete for the root node.
                        && node != model.getRoot()) {
                    buildContextMenu(node).show(FileExplorer.this, e.getX(), e.getY());
                }
            }
        });

        // Drag-and-drop
        setDragEnabled(true);
        setDropMode(DropMode.ON);
        setTransferHandler(new FileTransferHandler());

        // Create the watch service and register the root directory.
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
    // Context menu
    // -------------------------------------------------------------------------

    /** Builds a right-click context menu for the given node. */
    private JPopupMenu buildContextMenu(DirectoryTreeNode node) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem renameItem = new JMenuItem(bundle.getString("Rename"));
        renameItem.addActionListener(e -> renameNode(node));
        menu.add(renameItem);

        JMenuItem deleteItem = new JMenuItem(bundle.getString("Delete"));
        deleteItem.addActionListener(e -> deleteNode(node));
        menu.add(deleteItem);

        return menu;
    }

    /** Prompts the user for a new name and renames the file/directory. */
    private void renameNode(DirectoryTreeNode node) {
        Path oldPath = node.path();
        String current = oldPath.getFileName().toString();
        String newName = (String) JOptionPane.showInputDialog(
                this,
                bundle.getString("RenamePrompt"),
                bundle.getString("RenameTitle"),
                JOptionPane.PLAIN_MESSAGE,
                null, null, current);

        if (newName == null || newName.isBlank() || newName.equals(current)) return;

        Path newPath = oldPath.resolveSibling(newName.strip());
        try {
            Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE);
            // The WatchService ENTRY_DELETE + ENTRY_CREATE events will update the
            // tree automatically; no explicit model mutation needed here.
        } catch (IOException ex) {
            logger.error("Rename failed: {} -> {}", oldPath, newPath, ex);
            JOptionPane.showMessageDialog(this,
                    MessageFormat.format(bundle.getString("RenameError"), ex.getMessage()),
                    bundle.getString("RenameTitle"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Confirms with the user and recursively deletes the file/directory. */
    private void deleteNode(DirectoryTreeNode node) {
        Path target = node.path();
        boolean isDir = Files.isDirectory(target);
        String confirmKey = isDir ? "DeleteConfirmDir" : "DeleteConfirmFile";
        String message = MessageFormat.format(
                bundle.getString(confirmKey), target.getFileName());

        int choice = JOptionPane.showConfirmDialog(this, message,
                bundle.getString("DeleteTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            deleteRecursively(target);
            // WatchService events will remove the node from the tree.
        } catch (IOException ex) {
            logger.error("Delete failed: {}", target, ex);
            JOptionPane.showMessageDialog(this,
                    MessageFormat.format(bundle.getString("DeleteError"), ex.getMessage()),
                    bundle.getString("DeleteTitle"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Recursively deletes {@code path} (works for both files and directories). */
    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : (Iterable<Path>) entries::iterator) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }

    // -------------------------------------------------------------------------
    // Drag-and-drop (move)
    // -------------------------------------------------------------------------

    /**
     * {@link TransferHandler} that exports the dragged {@link DirectoryTreeNode}
     * and accepts a drop onto a directory node by moving the source path into
     * the target directory.
     */
    private class FileTransferHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            if (!(c instanceof JTree tree)) return null;
            TreePath sel = tree.getSelectionPath();
            if (sel == null) return null;
            if (!(sel.getLastPathComponent() instanceof DirectoryTreeNode node)) return null;
            // Disallow dragging the root node.
            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
            if (node == model.getRoot()) return null;
            return new NodeTransferable(node);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) return false;
            if (!support.isDataFlavorSupported(NODE_FLAVOR)) return false;
            // Target must be a directory node.
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            TreePath targetPath = dl.getPath();
            if (targetPath == null) return false;
            if (!(targetPath.getLastPathComponent() instanceof DirectoryTreeNode target)) return false;
            return Files.isDirectory(target.path());
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            DirectoryTreeNode sourceNode;
            try {
                sourceNode = (DirectoryTreeNode) support.getTransferable().getTransferData(NODE_FLAVOR);
            } catch (Exception ex) {
                logger.error("DnD transfer failed", ex);
                return false;
            }

            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            DirectoryTreeNode targetNode = (DirectoryTreeNode) dl.getPath().getLastPathComponent();

            Path src = sourceNode.path();
            Path dest = targetNode.path().resolve(src.getFileName());

            // Don't move into itself.
            if (dest.equals(src) || dest.startsWith(src)) return false;

            String confirmMsg = MessageFormat.format(
                    bundle.getString("MoveConfirm"),
                    src.getFileName(), targetNode.path().getFileName());
            int choice = JOptionPane.showConfirmDialog(FileExplorer.this, confirmMsg,
                    bundle.getString("MoveTitle"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return false;

            try {
                Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE);
                // WatchService will handle the tree update.
                return true;
            } catch (AtomicMoveNotSupportedException ex) {
                // Retry without ATOMIC_MOVE (cross-filesystem move).
                try {
                    Files.move(src, dest);
                    return true;
                } catch (IOException ex2) {
                    logger.error("Move failed: {} -> {}", src, dest, ex2);
                    JOptionPane.showMessageDialog(FileExplorer.this,
                            MessageFormat.format(bundle.getString("MoveError"), ex2.getMessage()),
                            bundle.getString("MoveTitle"),
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            } catch (IOException ex) {
                logger.error("Move failed: {} -> {}", src, dest, ex);
                JOptionPane.showMessageDialog(FileExplorer.this,
                        MessageFormat.format(bundle.getString("MoveError"), ex.getMessage()),
                        bundle.getString("MoveTitle"),
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            // Tree update is handled by the WatchService on successful move.
        }
    }

    /** Wraps a {@link DirectoryTreeNode} as a {@link Transferable} for DnD. */
    private record NodeTransferable(DirectoryTreeNode node) implements Transferable {
        private static final DataFlavor[] FLAVORS = {NODE_FLAVOR};

        @Override
        public DataFlavor[] getTransferDataFlavors() { return FLAVORS; }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return NODE_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return node;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Populates children of {@code node} (if not already done) and ensures
     * the node's directory is registered with the watcher.
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
