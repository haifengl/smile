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
import tools.jackson.databind.JsonNode;

/**
 * One plugin listed by a marketplace: the {@code plugins[]} element of
 * {@code marketplace.json}.
 *
 * <p>An entry is validated on its own. A malformed entry is skipped and reported;
 * it never fails the whole marketplace, matching the Claude format's semantics.
 *
 * @param name the plugin's local name (required).
 * @param source where to fetch it (required).
 * @param description a short description shown in the Discover list.
 * @param version the version, when the entry declares one. {@code plugin.json}
 *                wins when both set it, as in Claude.
 * @param category a free-form catalog category.
 * @param tags free-form search tags.
 * @param strict whether {@code plugin.json} is authoritative when both it and the
 *               entry declare components.
 * @param defaultEnabled whether the plugin starts enabled once installed.
 * @param dependencies plugin ids this plugin requires.
 * @param displayName a human-readable name for the UI.
 *
 * @author Haifeng Li
 */
public record PluginEntry(
        String name,
        PluginSource source,
        String description,
        String version,
        String category,
        List<String> tags,
        boolean strict,
        boolean defaultEnabled,
        List<String> dependencies,
        String displayName) {

    /** Validates and normalizes the entry. */
    public PluginEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Plugin entry is missing its 'name'");
        }
        tags = tags == null ? List.of() : List.copyOf(tags);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    /**
     * Parses one {@code plugins[]} entry.
     * @param node the entry object.
     * @return the parsed entry.
     * @throws IllegalArgumentException if the entry is malformed.
     */
    public static PluginEntry parse(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Plugin entry must be an object");
        }
        return new PluginEntry(
                Json.text(node, "name"),
                PluginSource.parse(node.get("source")),
                Json.text(node, "description"),
                Json.text(node, "version"),
                Json.text(node, "category"),
                Json.strings(node, "tags"),
                Json.bool(node, "strict", true),
                Json.bool(node, "defaultEnabled", true),
                Json.strings(node, "dependencies"),
                Json.text(node, "displayName"));
    }

    /**
     * The name shown to the user: the display name when set, else the local name.
     * @return the display name.
     */
    public String label() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }
}
