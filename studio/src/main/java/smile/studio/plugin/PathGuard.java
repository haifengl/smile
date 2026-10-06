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
package smile.studio.plugin;

import java.nio.file.Path;

/**
 * Defends against path traversal: a marketplace entry or a plugin archive must not
 * be able to write outside the directory Studio chose for it.
 *
 * <p>Every path that comes from untrusted input — a relative plugin source, a
 * {@code command} file inside an archive, a skill's declared path — passes through
 * {@link #resolve} before it is used. A {@code ..} segment, an absolute path, or a
 * Windows drive/UNC prefix is refused.
 *
 * @author Haifeng Li
 */
final class PathGuard {

    private PathGuard() {
    }

    /**
     * Resolves an untrusted relative path against a trusted root.
     *
     * @param root the trusted base directory.
     * @param relative the untrusted relative path.
     * @return the resolved, normalized path, guaranteed to be under {@code root}.
     * @throws SecurityException if the path escapes the root or is absolute.
     */
    static Path resolve(Path root, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new SecurityException("Empty path in plugin source");
        }
        String normalized = relative.replace('\\', '/').trim();
        if (normalized.startsWith("/") || normalized.startsWith("~")) {
            throw new SecurityException("Absolute path not allowed: " + relative);
        }
        if (normalized.matches("^[A-Za-z]:.*")) {
            throw new SecurityException("Drive-qualified path not allowed: " + relative);
        }
        if (normalized.startsWith("//")) {
            throw new SecurityException("UNC path not allowed: " + relative);
        }
        for (String segment : normalized.split("/")) {
            if (segment.equals("..")) {
                throw new SecurityException("Path traversal not allowed: " + relative);
            }
        }

        Path resolved = root.resolve(normalized).normalize();
        Path base = root.normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Path escapes its root: " + relative);
        }
        return resolved;
    }

    /**
     * Verifies that a path lies under a root, without resolving a relative string.
     * Used to double-check a path produced by a fetch or extraction step.
     *
     * @param root the trusted base directory.
     * @param candidate the path to check.
     * @return the normalized candidate.
     * @throws SecurityException if the candidate escapes the root.
     */
    static Path assertInside(Path root, Path candidate) {
        Path base = root.normalize().toAbsolutePath();
        Path resolved = candidate.normalize().toAbsolutePath();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Path escapes its root: " + candidate);
        }
        return resolved;
    }
}
