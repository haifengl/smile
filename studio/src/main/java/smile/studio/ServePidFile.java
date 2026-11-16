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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ownership record for the inference service process that Studio launched.
 *
 * <p>Studio attaches to any healthy engine at the configured {@code host:port},
 * including engines it did not start, and detaching never terminates those. This
 * pid file records which child this Studio spawned so a later Studio can report
 * or clean up an engine orphaned by a crashed session.
 *
 * <p>It is <em>not</em> an authorization to kill: the file lives under a shared
 * {@code smile.home}, so two Studios on the same machine see the same file.
 * Termination is driven exclusively by the {@link Process} handle a manager
 * holds for a child it spawned itself.
 *
 * @author Haifeng Li
 */
final class ServePidFile {

    private ServePidFile() {
    }

    /** Returns {@code ${smile.home}/serve/serve.pid}. */
    private static Path path() {
        return Path.of(System.getProperty("smile.home", "."))
                .toAbsolutePath()
                .normalize()
                .resolve("serve")
                .resolve("serve.pid");
    }

    /**
     * Records the pid of a child this Studio just spawned.
     *
     * @param process the spawned process.
     */
    static void write(Process process) {
        if (process == null) {
            return;
        }
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, Long.toString(process.pid()));
        } catch (Exception ex) {
            // Best effort: losing the pid file only costs orphan reporting.
        }
    }

    /** Removes the pid file. */
    static void delete() {
        try {
            Files.deleteIfExists(path());
        } catch (Exception ignored) {
        }
    }
}
