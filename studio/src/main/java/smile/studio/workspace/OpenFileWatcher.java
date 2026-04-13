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

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * A file-change watcher for the opened files in the workspace.
 *
 * @author Haifeng Li
 */
public class OpenFileWatcher {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OpenFileWatcher.class);
    /**
     * OS-level file-change watcher.
     */
    private WatchService watchService;
    /**
     * Background thread that polls the WatchService.
     */
    private Thread watchThread;
    /**
     * Maps each WatchKey to the directory it was registered for.
     */
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    /**
     * Records the last modification time (ms) of every open file as of the
     * most recent save/open performed by <em>this</em> process. Used to
     * distinguish our own writes from external changes.
     */
    private final Map<String, Long> knownModTimes = new ConcurrentHashMap<>();
    /**
     * Pending reload-prompt timers keyed by absolute file path string.
     * A timer is started when the first MODIFY event arrives; it fires after a
     * short debounce period so that rapid sequences of events produce only one
     * dialog.
     */
    private final Map<String, javax.swing.Timer> pendingReloads = new ConcurrentHashMap<>();
    /**
     * Callback to invoke when a file change is detected.
     */
    private final Consumer<Path> onFileChanged;
    /**
     * The absolute paths of files to watch.
     */
    private final List<String> files;

    /**
     * Constructor.
     *
     * @param onFileChanged the callback to invoke when a file change is detected.
     */
    public OpenFileWatcher(List<String> files, Consumer<Path> onFileChanged) {
        this.files = files;
        this.onFileChanged = onFileChanged;
        start();
    }

    /**
     * Returns the list of currently opened files being watched.
     *
     * @return the list of currently opened files being watched.
     */
    public List<String> files() {
        return files;
    }

    /**
     * Starts the background {@link WatchService} thread.
     * Must be called once during construction after all notebooks are opened.
     */
    private void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException ex) {
            logger.error("Failed to create WatchService; external change detection disabled.", ex);
            return;
        }

        watchThread = Thread.ofVirtual().name("workspace-file-watcher").start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watchService.take();
                    if (key == null) continue;

                    Path dir = watchKeys.get(key);
                    if (dir == null) {
                        key.cancel();
                        continue;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                        @SuppressWarnings("unchecked")
                        Path changed = dir.resolve(((WatchEvent<Path>) event).context())
                                .toAbsolutePath().normalize();

                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY
                                && files.contains(changed.toString())) {
                            scheduleReloadPrompt(changed);
                        }
                    }

                    if (!key.reset()) {
                        watchKeys.remove(key);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Registers a directory with the {@link WatchService} if not already
     * registered. Safe to call multiple times for the same directory.
     *
     * @param dir the directory to watch.
     */
    public void watchDirectory(Path dir) {
        if (watchService == null || dir == null) return;
        // Skip if already watching this directory
        if (watchKeys.containsValue(dir)) return;
        try {
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeys.put(key, dir);
            logger.debug("Watching directory: {}", dir);
        } catch (IOException ex) {
            logger.warn("Cannot watch directory {}: {}", dir, ex.getMessage());
        }
    }

    /**
     * Records the current on-disk modification time of {@code path} in
     * {@link #knownModTimes}. Call this after opening or saving a file so
     * that the watcher can tell apart our own writes from external ones.
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
     * Schedules (or re-schedules) a debounced reload prompt for the given
     * file.  Multiple MODIFY events arriving within {@code DEBOUNCE_MS}
     * milliseconds are coalesced into a single dialog.
     *
     * @param path the changed file path (absolute, normalized).
     */
    private void scheduleReloadPrompt(Path path) {
        final int DEBOUNCE_MS = 500;
        String key = path.toString();

        // Cancel any existing timer for this file
        javax.swing.Timer existing = pendingReloads.remove(key);
        if (existing != null) existing.stop();

        javax.swing.Timer timer = new javax.swing.Timer(DEBOUNCE_MS, e -> {
            pendingReloads.remove(key);
            // Double-check: is the mod time actually different from what we know?
            try {
                long diskMod = Files.getLastModifiedTime(path).toMillis();
                Long known = knownModTimes.get(key);
                if (known != null && diskMod <= known) {
                    // Ignore our own save.
                    return;
                }
                // Update the recorded time so we don't prompt again for the
                // same modification if the user chooses not to reload.
                knownModTimes.put(key, diskMod);
            } catch (IOException ex) {
                logger.warn("Cannot read mod time for {}: {}", path, ex.getMessage());
            }
            SwingUtilities.invokeLater(() -> onFileChanged.accept(path));
        });
        timer.setRepeats(false);
        pendingReloads.put(key, timer);
        timer.start();
    }

    /**
     * Shuts down the file-change watcher.  Should be called when the
     * workspace is being disposed (e.g. application shutdown).
     */
    public void shutdown() {
        if (watchThread != null) {
            watchThread.interrupt();
        }
        // Cancel all pending debounce timers
        pendingReloads.values().forEach(javax.swing.Timer::stop);
        pendingReloads.clear();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ex) {
                logger.warn("Error closing WatchService", ex);
            }
        }
    }
}
