/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.workspace;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import org.fife.rsta.ui.search.SearchEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the idle-triggered auto saver.
 *
 * <p>The saver persists open files that report unsaved edits. It must write
 * shortly after the user stops typing (debounced), must skip clean files, and
 * must not save at all while disabled.
 */
public class AutoSaverTest {

    /** Minimal {@link OpenFile} whose dirtiness and path the test controls. */
    private static final class FakeOpenFile implements OpenFile {
        private Path file;
        private boolean saved;

        FakeOpenFile(Path file, boolean saved) {
            this.file = file;
            this.saved = saved;
        }

        @Override public Path getFile() { return file; }
        @Override public void setFile(Path file) { this.file = file; }
        @Override public boolean isSaved() { return saved; }
        @Override public void save() { saved = true; }
        @Override public void reload() { saved = true; }
        @Override public void close() { }
        @Override public void setChangeListener(Runnable listener) { }

        // SearchListener is unused by the auto saver.
        @Override public String getSelectedText() { return null; }
        @Override public void searchEvent(SearchEvent e) { }
    }

    @Test
    public void testDocumentChangeSchedulesDebouncedSave() throws Exception {
        // Given: an enabled auto saver watching one dirty file, with the
        // fallback timer parked far in the future so only the debounce can fire.
        FakeOpenFile dirty = new FakeOpenFile(Path.of("dirty.jsh"), false);
        var saved = new CopyOnWriteArrayList<OpenFile>();
        var saver = new AutoSaver(() -> List.of(dirty), saved::add, 50, 600_000);
        Runnable listener = () -> {
            dirty.setChangeListener(saver::documentChanged);
            saver.documentChanged();
        };

        // When: auto save is enabled and a document change is reported.
        invokeAndWait(() -> {
            saver.start();
            listener.run();
        });

        // Then: the file is saved shortly after, via the debounce path.
        awaitTrue(() -> saved.contains(dirty), 2000);
        assertTrue(saved.contains(dirty), "dirty file should be saved after the debounce");
        saver.stop();
    }

    @Test
    public void testCleanFileIsNotSaved() throws Exception {
        // Given: an enabled auto saver watching a file with no unsaved edits.
        FakeOpenFile clean = new FakeOpenFile(Path.of("clean.jsh"), true);
        var saved = new CopyOnWriteArrayList<OpenFile>();
        var saver = new AutoSaver(() -> List.of(clean), saved::add, 50, 600_000);

        // When: a change is reported and the debounce elapses.
        invokeAndWait(() -> {
            saver.start();
            saver.documentChanged();
        });
        Thread.sleep(300);

        // Then: nothing is written.
        assertTrue(saved.isEmpty(), "a clean file must not be auto-saved");
        saver.stop();
    }

    @Test
    public void testFileWithoutPathIsNotSaved() throws Exception {
        // Given: a dirty file that has never been associated with a path.
        FakeOpenFile untitled = new FakeOpenFile(null, false);
        var saved = new CopyOnWriteArrayList<OpenFile>();
        var saver = new AutoSaver(() -> List.of(untitled), saved::add, 50, 600_000);

        // When: a change is reported and the debounce elapses.
        invokeAndWait(() -> {
            saver.start();
            saver.documentChanged();
        });
        Thread.sleep(300);

        // Then: it is skipped, because there is nowhere to write it.
        assertTrue(saved.isEmpty(), "a file without a path must not be auto-saved");
        saver.stop();
    }

    @Test
    public void testNoSaveWhileDisabled() throws Exception {
        // Given: a dirty file and an auto saver that has not been started.
        FakeOpenFile dirty = new FakeOpenFile(Path.of("dirty.jsh"), false);
        var saved = new CopyOnWriteArrayList<OpenFile>();
        var saver = new AutoSaver(() -> List.of(dirty), saved::add, 50, 600_000);

        // When: edits are reported but auto save is off.
        invokeAndWait(saver::documentChanged);
        Thread.sleep(300);

        // Then: a document change alone never writes.
        assertFalse(saver.isRunning(), "auto saver should be idle while disabled");
        assertTrue(saved.isEmpty(), "a disabled auto saver must not write");
    }

    @Test
    public void testFallbackIntervalSavesWithoutChangeNotification() throws Exception {
        // Given: an enabled auto saver with a short fallback and a long debounce,
        // so only the fallback can persist an edit that never notified.
        FakeOpenFile dirty = new FakeOpenFile(Path.of("dirty.jsh"), false);
        var saved = new CopyOnWriteArrayList<OpenFile>();
        var saver = new AutoSaver(() -> List.of(dirty), saved::add, 600_000, 50);

        // When: auto save is enabled but no document change is reported.
        invokeAndWait(saver::start);

        // Then: the fallback safety net still persists the dirty file.
        awaitTrue(() -> saved.contains(dirty), 2000);
        assertTrue(saved.contains(dirty), "the fallback should save edits that never notified");
        saver.stop();
    }

    @Test
    public void testStopCancelsPendingDebouncedSave() throws Exception {
        // Given: an enabled auto saver with a long debounce.
        FakeOpenFile dirty = new FakeOpenFile(Path.of("dirty.jsh"), false);
        var saved = new CopyOnWriteArrayList<OpenFile>();
        var saver = new AutoSaver(() -> List.of(dirty), saved::add, 300, 600_000);

        // When: a change is reported and auto save is disabled before the
        // debounce elapses.
        invokeAndWait(() -> {
            saver.start();
            saver.documentChanged();
        });
        invokeAndWait(saver::stop);
        Thread.sleep(500);

        // Then: the pending save is cancelled.
        assertTrue(saved.isEmpty(), "stop() should cancel a pending debounced save");
    }

    /** Runs {@code action} on the Swing event dispatch thread and waits for it. */
    private static void invokeAndWait(Runnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeAndWait(action);
        }
    }

    /** Polls {@code condition} until it holds or the timeout elapses. */
    private static void awaitTrue(java.util.function.BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
    }
}
