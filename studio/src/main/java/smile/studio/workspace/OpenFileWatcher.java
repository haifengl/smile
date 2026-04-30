/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.workspace;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * A file-change watcher for the opened files in the workspace.
 *
 * <p>A single background virtual thread blocks on {@link WatchService#take()}
 * and coalesces rapid {@code ENTRY_MODIFY} events into a single debounced
 * callback via a {@link javax.swing.Timer} that fires on the EDT.
 *
 * @author Haifeng Li
 */
public class OpenFileWatcher {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OpenFileWatcher.class);

    /** Debounce window in milliseconds. */
    private static final int DEBOUNCE_MS = 500;

    /** OS-level file-change watcher; {@code null} if initialization failed. */
    private final WatchService watchService;
    /** Background virtual thread that polls the WatchService. */
    private final Thread watchThread;
    /** Maps each WatchKey to the directory it was registered for. */
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    /**
     * Tracks the set of directories already registered, enabling O(1)
     * duplicate-registration checks without a linear scan of watchKeys values.
     */
    private final Set<Path> watchedDirs = ConcurrentHashMap.newKeySet();
    /**
     * The set of absolute, normalized path strings currently being watched.
     * Using a {@link ConcurrentHashMap}-backed set makes {@code contains}
     * O(1) and safe to call from the watcher thread without synchronization.
     */
    private final Set<String> fileSet = ConcurrentHashMap.newKeySet();
    /**
     * Records the last modification time (ms) of every open file as of the
     * most recent save/open performed by <em>this</em> process. Used to
     * distinguish our own writes from external changes.
     */
    private final Map<String, Long> knownModTimes = new ConcurrentHashMap<>();
    /**
     * Pending reload-prompt timers keyed by absolute file path string.
     * A timer is started when the first MODIFY event arrives; it fires after
     * {@link #DEBOUNCE_MS} so that rapid event sequences produce only one
     * dialog.  Accessed exclusively on the EDT.
     */
    private final Map<String, Timer> pendingReloads = new HashMap<>();
    /** Callback invoked on the EDT when an external file change is detected. */
    private final Consumer<Path> onFileChanged;

    /**
     * Constructor.
     *
     * @param files         the initial list of absolute file-path strings to watch.
     * @param onFileChanged the callback to invoke (on the EDT) when a change is detected.
     */
    public OpenFileWatcher(List<String> files, Consumer<Path> onFileChanged) {
        this.onFileChanged = onFileChanged;
        fileSet.addAll(files);

        // Initialize using local temporaries so the fields can be final.
        WatchService ws = null;
        Thread wt = null;
        try {
            ws = FileSystems.getDefault().newWatchService();
            wt = Thread.ofVirtual().name("workspace-file-watcher").start(this::watchLoop);
        } catch (IOException ex) {
            logger.error("Failed to create WatchService; external change detection disabled.", ex);
        }
        watchService = ws;
        watchThread  = wt;
    }

    /**
     * Adds a file to the watch set.  Safe to call from any thread.
     *
     * @param path the absolute, normalized file path.
     */
    public void addFile(Path path) {
        fileSet.add(path.toString());
    }

    /**
     * Removes a file from the watch set and clears its recorded modification
     * time.  Also cancels any pending debounce timer for that file so that a
     * stale reload dialog cannot fire after the file has been closed.
     *
     * <p>Must be called on the EDT (because it cancels a Swing {@link Timer}).
     *
     * @param path the absolute, normalized file path.
     */
    public void removeFile(Path path) {
        String key = path.toString();
        fileSet.remove(key);
        knownModTimes.remove(key);
        // Cancel any pending debounce timer so a closed file's timer cannot
        // fire and deliver a stale reload dialog.
        Timer pending = pendingReloads.remove(key);
        if (pending != null) pending.stop();
    }

    /**
     * Registers a directory with the {@link WatchService} if not already
     * registered. Safe to call multiple times for the same directory.
     *
     * @param dir the directory to watch.
     */
    public void watchDirectory(Path dir) {
        if (watchService == null || dir == null) return;
        // watchedDirs gives O(1) duplicate check and avoids the O(n)
        // containsValue scan on the watchKeys map.
        if (!watchedDirs.add(dir)) return;   // already watching
        try {
            WatchKey key = dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeys.put(key, dir);
            logger.debug("Watching directory: {}", dir);
        } catch (IOException ex) {
            watchedDirs.remove(dir);  // roll back so the caller can retry
            logger.warn("Cannot watch directory {}: {}", dir, ex.getMessage());
        }
    }

    /**
     * Records the current on-disk modification time of {@code path} in
     * {@link #knownModTimes}. Call this immediately after opening or saving a
     * file so that the watcher can distinguish our own writes from external ones.
     *
     * @param path the absolute, normalized file path.
     */
    public void recordModTime(Path path) {
        try {
            if (Files.exists(path)) {
                knownModTimes.put(path.toString(),
                        Files.getLastModifiedTime(path).toMillis());
            }
        } catch (IOException ex) {
            logger.warn("Cannot record mod time for {}: {}", path, ex.getMessage());
        }
    }

    /**
     * Shuts down the file-change watcher. Should be called when the
     * workspace is being disposed (e.g. application shutdown).
     */
    public void shutdown() {
        if (watchThread != null) watchThread.interrupt();
        // Cancel all pending debounce timers (must be on EDT; shutdown() is
        // called from the EDT by Workspace.shutdown()).
        pendingReloads.values().forEach(Timer::stop);
        pendingReloads.clear();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ex) {
                logger.warn("Error closing WatchService", ex);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Main loop executed by the background virtual thread.
     * Blocks on {@link WatchService#take()} and dispatches debounced
     * {@link #scheduleReloadPrompt} calls to the EDT.
     */
    private void watchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    // shutdown() closed the service — exit cleanly.
                    break;
                }

                Path dir = watchKeys.get(key);
                if (dir == null) {
                    key.cancel();
                    continue;
                }

                List<Path> changed = new ArrayList<>();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    Path candidate = dir.resolve(((WatchEvent<Path>) event).context())
                            .toAbsolutePath().normalize();

                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
                            && fileSet.contains(candidate.toString())) {
                        changed.add(candidate);
                    }
                }

                // Schedule debounce timers on the EDT — Swing timers must only
                // be created/started/stopped from the Event Dispatch Thread.
                if (!changed.isEmpty()) {
                    SwingUtilities.invokeLater(() ->
                            changed.forEach(this::scheduleReloadPrompt));
                }

                if (!key.reset()) {
                    watchKeys.remove(key);
                    watchedDirs.remove(dir);
                }
            }
        } catch (Exception e) {
            // Catch-all guard: log unexpected exceptions so the virtual thread
            // does not exit silently.
            logger.error("Unexpected error in file-watcher loop", e);
        }
    }

    /**
     * Schedules (or re-schedules) a debounced reload prompt for the given
     * file.  Multiple MODIFY events arriving within {@link #DEBOUNCE_MS}
     * milliseconds are coalesced into a single dialog.
     *
     * <p>Must be called on the EDT.
     *
     * @param path the changed file path (absolute, normalized).
     */
    private void scheduleReloadPrompt(Path path) {
        String key = path.toString();

        // Cancel any existing timer for this file (safe: EDT-only access).
        Timer existing = pendingReloads.remove(key);
        if (existing != null) existing.stop();

        Timer timer = new Timer(DEBOUNCE_MS, e -> {
            pendingReloads.remove(key);
            // Guard against our own save: compare disk mod time with recorded.
            try {
                long diskMod = Files.getLastModifiedTime(path).toMillis();
                Long known = knownModTimes.get(key);
                if (known != null && diskMod <= known) {
                    return;  // our own write — ignore
                }
                // Update the recorded time so we don't re-prompt for the same
                // modification if the user declines to reload.
                knownModTimes.put(key, diskMod);
            } catch (IOException ex) {
                logger.warn("Cannot read mod time for {}: {}", path, ex.getMessage());
            }
            onFileChanged.accept(path);
        });
        timer.setRepeats(false);
        pendingReloads.put(key, timer);
        timer.start();
    }
}
