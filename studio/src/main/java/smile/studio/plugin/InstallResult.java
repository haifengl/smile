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

import java.util.List;

/**
 * The outcome of an operation that a user initiated: a plugin install, enable,
 * disable, or uninstall. The message is shown verbatim in the panel or CLI, so it
 * is written for the user, not for a log.
 *
 * @param success whether the operation succeeded.
 * @param message a human-readable summary.
 * @param dispositions the components that were dropped or degraded, or an empty
 *                     list when there are none.
 *
 * @author Haifeng Li
 */
public record InstallResult(boolean success, String message, List<ComponentDisposition> dispositions) {

    /** Normalizes the list. */
    public InstallResult {
        dispositions = dispositions == null ? List.of() : List.copyOf(dispositions);
    }

    /**
     * A success with no caveats.
     * @param message the summary.
     * @return the result.
     */
    public static InstallResult ok(String message) {
        return new InstallResult(true, message, List.of());
    }

    /**
     * A success that dropped or degraded components.
     * @param message the summary.
     * @param dispositions the caveats.
     * @return the result.
     */
    public static InstallResult ok(String message, List<ComponentDisposition> dispositions) {
        return new InstallResult(true, message, dispositions);
    }

    /**
     * A failure.
     * @param message the reason.
     * @return the result.
     */
    public static InstallResult failed(String message) {
        return new InstallResult(false, message, List.of());
    }

    /** @return the dropped or degraded dispositions. */
    public List<ComponentDisposition> caveats() {
        return dispositions.stream()
                .filter(d -> d.isDropped() || d.isDegraded())
                .toList();
    }
}
