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

    /**
     * Returns the address a client should actually connect to for a configured host.
     *
     * <p>A wildcard bind address ({@code 0.0.0.0}) tells the server to listen on
     * every interface; it is not a destination, and connecting to it is unreliable
     * across platforms. Clients are therefore redirected to loopback, which also
     * keeps the localhost-only management endpoints working. A blank host falls
     * back to loopback for the same reason.
     *
     * <p>Any other host is returned unchanged, so an explicitly configured remote
     * engine is still addressed as written.
     *
     * @param host the configured bind or target host.
     * @return the host to connect to.
     */
    public static String connectHost(String host) {
        if (host == null || host.isBlank()) {
            return LOOPBACK;
        }
        String trimmed = host.trim();
        return isWildcard(trimmed) ? LOOPBACK : trimmed;
    }

    /** Default loopback address used for local connections. */
    static final String LOOPBACK = "127.0.0.1";

    private static boolean isWildcard(String host) {
        return "0.0.0.0".equals(host)
                || "::".equals(host)
                || "[::]".equals(host);
    }
}
