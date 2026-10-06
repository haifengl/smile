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

import java.util.Locale;
import java.util.Set;

/**
 * The policy that decides what Studio will and will not do with a plugin.
 *
 * <p>Two rules carry the security model of ADR-008:
 * <ol>
 *   <li><b>{@code command} sources are refused.</b> They are the one source type
 *       that runs an arbitrary shell command at install, update, and every session.
 *       Studio never executes one; the entry is skipped and reported.</li>
 *   <li><b>Nothing else runs at install.</b> Fetching and translating are pure file
 *       operations. The only code that a plugin can cause to run is an MCP server,
 *       and that is gated by an explicit per-server opt-in at activation time, not
 *       by install.</li>
 * </ol>
 *
 * <p>Archive downloads require an {@code https} URL so a plugin cannot be pulled
 * over plaintext, and hosts that resolve to the local machine or a cloud metadata
 * endpoint are refused, matching Claude's download policy.
 *
 * @author Haifeng Li
 */
final class TrustPolicy {

    /** Hosts an archive download may not target. */
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "169.254.169.254", "metadata.google.internal");

    private TrustPolicy() {
    }

    /**
     * Decides whether a plugin source may be used.
     *
     * @param source the entry's source.
     * @return a decision; {@link Decision#allowed()} is false with a reason when refused.
     */
    static Decision check(PluginSource source) {
        if (source instanceof PluginSource.Command command) {
            return Decision.refused(
                    "The 'command' source type is not supported: it runs '" + command.command()
                            + "' on your machine at install and every session. Studio never executes a "
                            + "command source (ADR-008).");
        }
        if (source instanceof PluginSource.Archive archive) {
            Decision scheme = requireHttps(archive.url());
            if (!scheme.allowed()) {
                return scheme;
            }
            Decision host = requirePublicHost(archive.url());
            if (!host.allowed()) {
                return host;
            }
        }
        return Decision.ALLOWED;
    }

    /**
     * Requires an {@code https} URL whose host is not local or a metadata endpoint.
     * @param url the archive URL.
     * @return the decision.
     */
    private static Decision requireHttps(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://")) {
            return Decision.refused("Archive source must use https://: " + url);
        }
        return Decision.ALLOWED;
    }

    /**
     * Refuses loopback, link-local, and cloud-metadata hosts.
     * @param url the archive URL.
     * @return the decision.
     */
    private static Decision requirePublicHost(String url) {
        try {
            String authority = java.net.URI.create(url).getHost();
            if (authority == null) {
                return Decision.refused("Archive source has no host: " + url);
            }
            String host = authority.toLowerCase(Locale.ROOT);
            if (BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost")
                    || host.startsWith("169.254.") || host.startsWith("127.")) {
                return Decision.refused("Archive source host is not allowed: " + host);
            }
            return Decision.ALLOWED;
        } catch (IllegalArgumentException ex) {
            return Decision.refused("Archive source is not a valid URL: " + url);
        }
    }

    /**
     * A trust decision for one source.
     * @param allowed whether the source may be used.
     * @param reason the refusal reason, or null when allowed.
     */
    record Decision(boolean allowed, String reason) {
        /** The shared allow result. */
        static final Decision ALLOWED = new Decision(true, null);

        /**
         * A refusal carrying its reason.
         * @param reason why the source is refused.
         * @return the decision.
         */
        static Decision refused(String reason) {
            return new Decision(false, reason);
        }
    }
}
