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
import java.util.regex.Pattern;
import smile.util.Strings;

/**
 * A plugin's identity: its local name and the marketplace it came from, the
 * {@code name@marketplace} form users type and settings files record.
 *
 * <p>The name is also the namespace prefix for every artifact the plugin
 * contributes. Claude names plugin content {@code plugin:artifact}, but {@code :}
 * is not path-safe on Windows, so the translator materializes {@code plugin--artifact}
 * (see {@code design.md} §5.3). Keeping that prefix computation here means the
 * install directory, the skill directory, the subagent directory, and the MCP
 * server key can never disagree.
 *
 * @param name the plugin's local name.
 * @param marketplace the marketplace's registered name.
 *
 * @author Haifeng Li
 */
public record PluginId(String name, String marketplace) {
    /** Characters permitted in a plugin id part, per the Claude format. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /** Validates both parts so a malformed id cannot reach the filesystem. */
    public PluginId {
        if (Strings.isNullOrBlank(name) || !VALID.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid plugin name: " + name);
        }
        if (Strings.isNullOrBlank(marketplace) || !VALID.matcher(marketplace).matches()) {
            throw new IllegalArgumentException("Invalid marketplace name: " + marketplace);
        }
    }

    /**
     * Parses a {@code name@marketplace} identifier.
     * @param text the identifier.
     * @return the parsed id.
     * @throws IllegalArgumentException if the text is not a valid id.
     */
    public static PluginId parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Plugin id cannot be null");
        }
        int at = text.indexOf('@');
        if (at <= 0 || at == text.length() - 1) {
            throw new IllegalArgumentException(
                    "Plugin id must be 'name@marketplace': " + text);
        }
        return new PluginId(text.substring(0, at), text.substring(at + 1));
    }

    /**
     * Returns the namespace prefix this plugin owns, e.g. {@code commit-commands}
     * for the plugin {@code commit-commands@claude-plugins-official}.
     * @return the prefix used for materialized artifact names.
     */
    public String prefix() {
        return name;
    }

    /**
     * Namespaces one artifact name with this plugin's prefix, using the {@code --}
     * separator. The artifact is lower-cased and any run of characters that is not
     * a letter, digit, dot, underscore, or hyphen is collapsed to a hyphen, so the
     * result is a single path-safe segment on every platform.
     *
     * @param artifact the artifact's local name (a skill, agent, or server name).
     * @return the materialized, namespaced name.
     */
    public String namespace(String artifact) {
        return prefix() + "--" + sanitize(artifact);
    }

    /**
     * Returns the on-disk directory name for this plugin. The marketplace is
     * included so two plugins of the same name from different marketplaces do not
     * collide under {@code ~/.smile/plugins/}.
     * @return a single path-safe directory name.
     */
    public String directory() {
        return sanitize(marketplace) + "--" + sanitize(name);
    }

    /**
     * Sanitizes a name into a single path-safe segment.
     * @param raw the raw name.
     * @return a lower-cased, path-safe segment.
     */
    static String sanitize(String raw) {
        if (raw == null) return "";
        String lowered = raw.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder(lowered.length());
        boolean lastDash = false;
        for (int i = 0; i < lowered.length(); i++) {
            char c = lowered.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            if (ok) {
                sb.append(c);
                lastDash = false;
            } else if (!lastDash) {
                sb.append('-');
                lastDash = true;
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return name + "@" + marketplace;
    }
}
