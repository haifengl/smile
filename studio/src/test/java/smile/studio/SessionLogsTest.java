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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SessionLogs}.
 *
 * @author Haifeng Li
 */
public class SessionLogsTest {

    @Test
    public void testPruneRetainsNewestSessionLogs(@TempDir Path dir) throws IOException {
        Instant base = Instant.parse("2026-11-01T10:00:00Z");
        for (int i = 1; i <= 5; i++) {
            Path file = dir.resolve(String.format("smile-2026110%dT100000.log", i));
            Files.writeString(file, "session " + i);
            Files.setLastModifiedTime(file, FileTime.from(base.plusSeconds(i * 3600L)));
        }

        int deleted = SessionLogs.prune(dir, 3);

        assertEquals(2, deleted);
        assertFalse(Files.exists(dir.resolve("smile-20261101T100000.log")));
        assertFalse(Files.exists(dir.resolve("smile-20261102T100000.log")));
        assertTrue(Files.exists(dir.resolve("smile-20261103T100000.log")));
        assertTrue(Files.exists(dir.resolve("smile-20261104T100000.log")));
        assertTrue(Files.exists(dir.resolve("smile-20261105T100000.log")));
    }

    @Test
    public void testPruneIgnoresUnrelatedFilesAndDirectories(@TempDir Path dir) throws IOException {
        Path keepOther = dir.resolve("notes.log");
        Path keepSubdir = dir.resolve("smile-20261101T100000.log.d");
        Path matchingSubdir = dir.resolve("smile-20261101T090000.log");
        Files.writeString(keepOther, "unrelated");
        Files.createDirectory(keepSubdir);
        Files.createDirectory(matchingSubdir);

        Path older = dir.resolve("smile-20261101T100000.log");
        Path newer = dir.resolve("smile-20261102T100000.log");
        Files.writeString(older, "old");
        Files.writeString(newer, "new");
        Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-11-01T10:00:00Z")));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-11-02T10:00:00Z")));

        int deleted = SessionLogs.prune(dir, 1);

        assertEquals(1, deleted);
        assertFalse(Files.exists(older));
        assertTrue(Files.exists(newer));
        assertTrue(Files.exists(keepOther));
        assertTrue(Files.isDirectory(keepSubdir));
        assertTrue(Files.isDirectory(matchingSubdir));
    }

    @Test
    public void testPruneNoOpWhenWithinLimitOrMissingDir(@TempDir Path dir) throws IOException {
        assertEquals(0, SessionLogs.prune(dir.resolve("does-not-exist"), 10));

        Path single = dir.resolve("smile-20261128T040000.log");
        Files.writeString(single, "only one");
        assertEquals(0, SessionLogs.prune(dir, 5));
        assertTrue(Files.exists(single));
    }

    @Test
    public void testPruneRejectsNegativeMaxFiles(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> SessionLogs.prune(dir, -1));
    }
}
