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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Timer;

/**
 * Periodically persists open files that have unsaved edits.
 *
 * <p>Auto save is <em>idle-triggered</em>: a document change (typically a
 * keystroke) is reported through {@link #documentChanged()} which schedules a
 * debounced save {@link #DEFAULT_DEBOUNCE_MS} later. Every subsequent change
 * restarts the delay, so a burst of typing collapses into a single write once
 * the user pauses. A repeating {@link #DEFAULT_FALLBACK_MS} timer is the safety
 * net: it bounds the worst-case data loss for edits that never pass through
 * {@link #documentChanged()} (for example, an attribute-only change or a
 * programmatic edit that does not touch a document listener).
 *
 * <p>Both timers are Swing {@link Timer}s and therefore fire on the event
 * dispatch thread, like the rest of the application's timers. Only files that
 * report {@link OpenFile#isSaved()} as {@code false} are handed to the save
 * action, so a tick with nothing dirty never touches disk.
 *
 * <p>This class is deliberately free of UI and I/O concerns so that its timing
 * and dirty-file logic can be unit-tested headlessly; the save action (which
 * knows how to persist and record a file's modification time) is injected by the
 * workspace.
 *
 * @author Haifeng Li
 */
public final class AutoSaver {
    /** Default delay after the last edit before the debounced save fires. */
    public static final int DEFAULT_DEBOUNCE_MS = 3000;
    /** Default repeating interval that bounds the worst-case data loss. */
    public static final int DEFAULT_FALLBACK_MS = 60000;

    private final Supplier<? extends List<? extends OpenFile>> openFiles;
    private final Consumer<OpenFile> save;
    private final int debounceDelayMs;
    private final int fallbackIntervalMs;

    /** Restarted by every change; fires the debounced save. */
    private final Timer debounce;
    /** Repeating safety net that saves regardless of change notifications. */
    private final Timer fallback;

    /**
     * Constructor with the default debounce and fallback intervals.
     *
     * @param openFiles supplies the currently open files.
     * @param save persists a single file (and records its modification time).
     */
    public AutoSaver(Supplier<? extends List<? extends OpenFile>> openFiles, Consumer<OpenFile> save) {
        this(openFiles, save, DEFAULT_DEBOUNCE_MS, DEFAULT_FALLBACK_MS);
    }

    /**
     * Constructor.
     *
     * @param openFiles supplies the currently open files.
     * @param save persists a single file (and records its modification time).
     * @param debounceDelayMs delay after the last edit before saving.
     * @param fallbackIntervalMs repeating interval of the safety-net save.
     */
    public AutoSaver(Supplier<? extends List<? extends OpenFile>> openFiles, Consumer<OpenFile> save,
                     int debounceDelayMs, int fallbackIntervalMs) {
        this.openFiles = openFiles;
        this.save = save;
        this.debounceDelayMs = debounceDelayMs;
        this.fallbackIntervalMs = fallbackIntervalMs;

        debounce = new Timer(debounceDelayMs, e -> saveDirtyFiles());
        debounce.setRepeats(false);

        fallback = new Timer(fallbackIntervalMs, e -> saveDirtyFiles());
        fallback.setInitialDelay(fallbackIntervalMs);
    }

    /**
     * Reports that a document changed. Arms (or restarts) the debounce window so
     * the edit is saved shortly after the user stops typing. A no-op while auto
     * save is disabled, so an edit never triggers a save on its own.
     */
    public void documentChanged() {
        if (!fallback.isRunning()) {
            return;
        }
        if (debounce.isRunning()) {
            debounce.restart();
        } else {
            debounce.start();
        }
    }

    /** Enables auto save. Idempotent. */
    public void start() {
        if (!fallback.isRunning()) {
            fallback.setInitialDelay(fallbackIntervalMs);
            fallback.start();
        }
    }

    /** Disables auto save, cancelling any pending debounced save. Idempotent. */
    public void stop() {
        debounce.stop();
        fallback.stop();
    }

    /**
     * Returns true while auto save is enabled.
     *
     * @return true if the fallback timer is running.
     */
    public boolean isRunning() {
        return fallback.isRunning();
    }

    /**
     * Saves every open file that has unsaved edits and an associated path.
     * Must run on the event dispatch thread.
     *
     * @return the number of files handed to the save action.
     */
    public int saveDirtyFiles() {
        int saved = 0;
        for (OpenFile openFile : openFiles.get()) {
            if (openFile.getFile() != null && !openFile.isSaved()) {
                save.accept(openFile);
                saved++;
            }
        }
        return saved;
    }
}
