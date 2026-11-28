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
package smile.studio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Retains a bounded number of per-session Studio log files ({@code smile-YYYYMMDDTHHmmss.log})
 * while pruning older session logs on startup.
 *
 * @author Haifeng Li
 */
public final class SessionLogs {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SessionLogs.class);

    /** Default directory where {@code logback.xml} writes per-session logs. */
    public static final Path DEFAULT_LOG_DIR = Path.of(".smile", "logs");

    /** Default maximum number of session log files to retain per workspace. */
    public static final int DEFAULT_MAX_FILES = 50;

    /** Matches {@code smile-yyyyMMdd'T'HHmmss.log} produced by {@code logback.xml}. */
    private static final Pattern SESSION_LOG_PATTERN =
            Pattern.compile("^smile-\\d{8}T\\d{6}\\.log$");

    private static final Comparator<LogEntry> NEWEST_FIRST = Comparator
            .comparing(LogEntry::lastModified, Comparator.reverseOrder())
            .thenComparing(LogEntry::name, Comparator.reverseOrder());

    private record LogEntry(Path path, Instant lastModified, String name) {}

    private SessionLogs() {
    }

    /**
     * Prunes old session logs in {@link #DEFAULT_LOG_DIR} asynchronously on a virtual thread,
     * keeping at most {@link #DEFAULT_MAX_FILES} most recent session logs.
     */
    public static void pruneAsync() {
        pruneAsync(DEFAULT_LOG_DIR, DEFAULT_MAX_FILES);
    }

    /**
     * Prunes old session logs in {@code logDir} asynchronously on a virtual thread.
     *
     * @param logDir   the directory containing session log files.
     * @param maxFiles the maximum number of session log files to keep.
     */
    public static void pruneAsync(Path logDir, int maxFiles) {
        Objects.requireNonNull(logDir, "logDir");
        if (maxFiles < 0) {
            throw new IllegalArgumentException("maxFiles must be non-negative: " + maxFiles);
        }
        Thread.ofVirtual()
                .name("session-log-pruner")
                .start(() -> prune(logDir, maxFiles));
    }

    /**
     * Deletes the oldest Studio session log files in {@code logDir} beyond {@code maxFiles}.
     * Non-matching files and subdirectories are left untouched. Files that cannot be deleted
     * (for example, a log held open by another running Studio instance) are skipped.
     *
     * @param logDir   the directory containing session log files.
     * @param maxFiles the maximum number of session log files to keep.
     * @return the number of deleted log files.
     */
    public static int prune(Path logDir, int maxFiles) {
        Objects.requireNonNull(logDir, "logDir");
        if (maxFiles < 0) {
            throw new IllegalArgumentException("maxFiles must be non-negative: " + maxFiles);
        }
        if (!Files.isDirectory(logDir)) {
            return 0;
        }

        List<LogEntry> entries = collectSessionLogs(logDir);
        if (entries.size() <= maxFiles) {
            return 0;
        }

        entries.sort(NEWEST_FIRST);
        int deleted = 0;
        for (int i = maxFiles; i < entries.size(); i++) {
            Path file = entries.get(i).path();
            try {
                if (Files.deleteIfExists(file)) {
                    deleted++;
                }
            } catch (IOException ex) {
                logger.debug("Skipping locked or undeletable session log {}: {}", file, ex.getMessage());
            }
        }
        if (deleted > 0) {
            logger.info("Pruned {} old session log(s) from {}", deleted, logDir);
        }
        return deleted;
    }

    private static List<LogEntry> collectSessionLogs(Path logDir) {
        List<LogEntry> entries = new ArrayList<>();
        try (var stream = Files.list(logDir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!Files.isRegularFile(path) || !SESSION_LOG_PATTERN.matcher(name).matches()) {
                    continue;
                }
                try {
                    Instant modified = Files.getLastModifiedTime(path).toInstant();
                    entries.add(new LogEntry(path, modified, name));
                } catch (IOException ex) {
                    logger.debug("Skipping unreadable session log {}: {}", path, ex.getMessage());
                }
            }
        } catch (IOException ex) {
            logger.warn("Failed to list session logs in {}: {}", logDir, ex.getMessage());
        }
        return entries;
    }
}
