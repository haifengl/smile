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
 * Where a marketplace's {@code marketplace.json} lives — the marketplace source,
 * which is a distinct concept from a plugin source ({@link PluginSource}).
 *
 * <p>Studio supports the local forms directly. Remote forms resolve to a local
 * checkout through {@link Fetcher}; a marketplace added from a URL registers the
 * resolved path so later reads do not need the network.
 *
 * @author Haifeng Li
 */
public sealed interface MarketplaceSource {

    /**
     * A path to a {@code marketplace.json} file or a directory that holds one.
     * @param path the local path.
     */
    record Local(Path path) implements MarketplaceSource { }

    /**
     * A GitHub repository that holds the marketplace.
     * @param repo {@code owner/repo}.
     * @param ref optional branch or tag.
     */
    record Github(String repo, String ref) implements MarketplaceSource { }

    /**
     * A git repository reachable by URL.
     * @param url the clone URL.
     * @param ref optional branch or tag.
     */
    record Git(String url, String ref) implements MarketplaceSource { }

    /**
     * A directly hosted {@code marketplace.json} over HTTP(S).
     * @param url the URL of the file.
     */
    record Hosted(String url) implements MarketplaceSource { }

    /**
     * A short label for the UI and logs.
     * @return the source type name.
     */
    default String typeName() {
        return switch (this) {
            case Local ignored -> "local";
            case Github ignored -> "github";
            case Git ignored -> "git";
            case Hosted ignored -> "url";
        };
    }

    /**
     * Parses the shorthand string a user types to add a marketplace, mirroring
     * {@code /plugin marketplace add}.
     *
     * <ul>
     *   <li>{@code owner/repo} → a GitHub repository.</li>
     *   <li>An {@code http(s)://…} URL ending in {@code .json} → a hosted manifest;
     *       any other {@code http(s)://…} or {@code git@…} URL → a git clone.</li>
     *   <li>Anything else (a relative or absolute path) → a local directory or file.</li>
     * </ul>
     *
     * @param text the source string.
     * @return the parsed source.
     */
    static MarketplaceSource parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Marketplace source cannot be blank");
        }
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            String withoutRef = trimmed.contains("#") ? trimmed.substring(0, trimmed.indexOf('#')) : trimmed;
            if (withoutRef.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                return new Hosted(withoutRef);
            }
            String ref = trimmed.contains("#") ? trimmed.substring(trimmed.indexOf('#') + 1) : null;
            return new Git(withoutRef, ref);
        }
        if (trimmed.startsWith("git@") || lower.endsWith(".git")) {
            String ref = null;
            String url = trimmed;
            if (trimmed.contains("#")) {
                url = trimmed.substring(0, trimmed.indexOf('#'));
                ref = trimmed.substring(trimmed.indexOf('#') + 1);
            }
            return new Git(url, ref);
        }
        // GitHub owner/repo shorthand: exactly one slash and no path separators beyond it.
        if (trimmed.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(#.+)?") && !trimmed.contains("./")) {
            String repo = trimmed;
            String ref = null;
            if (repo.contains("#")) {
                ref = repo.substring(repo.indexOf('#') + 1);
                repo = repo.substring(0, repo.indexOf('#'));
            }
            return new Github(repo, ref);
        }
        return new Local(java.nio.file.Path.of(trimmed));
    }

    /**
     * Returns the original string form, for persistence.
     * @return a string that {@link #parse} round-trips.
     */
    default String asText() {
        return switch (this) {
            case Local local -> local.path().toString();
            case Github github -> github.repo() + (github.ref() == null ? "" : "#" + github.ref());
            case Git git -> git.url() + (git.ref() == null ? "" : "#" + git.ref());
            case Hosted hosted -> hosted.url();
        };
    }
}
