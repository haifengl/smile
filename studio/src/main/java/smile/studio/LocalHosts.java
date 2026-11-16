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

import java.util.Locale;

/**
 * Host classification shared by the Agent Hub and inference service managers.
 *
 * <p>"Local" matters for two decisions: whether Studio may spawn a service for a
 * configured host, and whether the localhost-only management endpoints of
 * SMILE Serve are reachable.
 *
 * @author Haifeng Li
 */
public final class LocalHosts {

    private LocalHosts() {
    }

    /**
     * Returns {@code true} when the host refers to this machine.
     *
     * @param host the configured host (may be empty).
     * @return {@code true} when the host is loopback or a wildcard bind address.
     */
    public static boolean isLocalHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized)
                || "0.0.0.0".equals(normalized);
    }
}
