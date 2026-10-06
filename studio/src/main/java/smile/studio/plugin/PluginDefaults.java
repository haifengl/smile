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
import java.util.Optional;

/**
 * The marketplaces Studio knows about out of the box.
 *
 * <p>SMILE ships no marketplace of its own, so a fresh Studio would show an empty
 * Discover list and no obvious next step. To give the user something to explore,
 * the panel seeds the <b>official Anthropic marketplace</b> the first time it is
 * opened — the same one Claude Code adds automatically on first interactive start.
 *
 * <p>Seeding is deliberately narrow. An implicit add is only allowed for a source
 * that is either one of the built-in defaults below or named by
 * {@code plugins.marketplaces} in {@code studio.json}; any other source a user
 * types goes through {@link PluginService}'s explicit add path. That keeps a
 * network fetch from happening silently for an arbitrary URL while still giving a
 * first-run user one trusted catalog to browse (ADR-008's least-surprise stance).
 *
 * <p>The fetch that backs a seed happens on a background thread and only when the
 * user actually opens the panel to look at plugins, never at Studio startup
 * (constraint: no implicit network on startup).
 *
 * @author Haifeng Li
 */
final class PluginDefaults {

    /**
     * One known marketplace: the source string to register it by, and the name its
     * manifest declares (so the caller can tell whether it is already registered).
     *
     * @param source the source string, in the {@code marketplace add} shorthand.
     * @param name the marketplace name its manifest declares.
     */
    record Known(String source, String name) { }

    /** The official Anthropic marketplace, as Claude Code adds it on first start. */
    static final Known ANTHROPIC_OFFICIAL =
            new Known("anthropics/claude-plugins-official", "claude-plugins-official");

    /**
     * The marketplaces Studio seeds when {@code studio.json} names none, in order.
     */
    private static final List<Known> KNOWN = List.of(ANTHROPIC_OFFICIAL);

    private PluginDefaults() {
    }

    /** @return the source strings of the built-in marketplaces. */
    static List<String> knownSources() {
        return KNOWN.stream().map(Known::source).toList();
    }

    /**
     * Returns the known marketplace a source string names, if any.
     *
     * @param source the source string.
     * @return the known marketplace, or empty.
     */
    private static Optional<Known> bySource(String source) {
        if (source == null) {
            return Optional.empty();
        }
        String normalized = source.trim();
        return KNOWN.stream().filter(k -> k.source().equals(normalized)).findFirst();
    }

    /**
     * Returns whether a source may be registered without the user typing it: either
     * a built-in known default or a source named by {@code plugins.marketplaces}.
     *
     * @param source the source string.
     * @param configured the configured marketplace list (may be empty).
     * @return true when the source is trusted for an implicit add.
     */
    static boolean isTrustedSource(String source, List<String> configured) {
        if (source == null) {
            return false;
        }
        String normalized = source.trim();
        if (bySource(normalized).isPresent()) {
            return true;
        }
        return configured != null && configured.stream().anyMatch(s -> s.trim().equals(normalized));
    }

    /**
     * Builds the seed list the panel should add on first open.
     *
     * <p>When {@code configured} is empty the built-in known marketplaces are used;
     * otherwise it is exactly the configured list. Either way each entry is trusted
     * by construction — a built-in source is a known default, and a configured
     * source is named by the user — so it is safe to add without an explicit action.
     *
     * @param configured the {@code plugins.marketplaces} sources, or empty.
     * @return the seeds, in order.
     */
    static List<Known> seeds(List<String> configured) {
        List<String> sources = configured == null || configured.isEmpty()
                ? knownSources()
                : configured;
        return sources.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> bySource(s).orElse(new Known(s, deriveName(s))))
                .toList();
    }

    /**
     * Derives a marketplace name from a source string, used only to skip a seed that
     * is already registered under that name. A known source uses its declared name;
     * an unknown one falls back to its last {@code /} segment, stripped of a
     * {@code .git} suffix — the shape a marketplace manifest usually declares.
     *
     * @param source the source string.
     * @return a best-effort marketplace name.
     */
    private static String deriveName(String source) {
        String text = source;
        int hash = text.indexOf('#');
        if (hash >= 0) {
            text = text.substring(0, hash);
        }
        if (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        int slash = text.lastIndexOf('/');
        String last = slash >= 0 ? text.substring(slash + 1) : text;
        if (last.endsWith(".git")) {
            last = last.substring(0, last.length() - 4);
        }
        return last;
    }
}
