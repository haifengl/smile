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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;

/**
 * A parsed {@code .claude-plugin/plugin.json}: one plugin's manifest.
 *
 * <p>Only the fields the translator needs are modelled explicitly. Component
 * declarations are kept as raw nodes and resolved by the translator, because the
 * Claude manifest lets a key name either a directory or an explicit path and the
 * resolution rule differs per component type.
 *
 * <p>When a fetched plugin has no {@code plugin.json}, the marketplace entry's
 * fields serve as the manifest (Claude's {@code strict} semantics); callers use
 * {@link #absent} to represent that case.
 *
 * @param name the plugin's name (required when a file is present).
 * @param description a short description.
 * @param version the plugin version. Takes precedence over the entry's.
 * @param author the author, informational.
 * @param present whether a {@code plugin.json} was actually found.
 * @param raw the full manifest node, for component resolution.
 *
 * @author Haifeng Li
 */
public record PluginManifest(
        String name,
        String description,
        String version,
        String author,
        boolean present,
        JsonNode raw) {

    /** The manifest file name inside a plugin. */
    public static final String PLUGIN_FILE = ".claude-plugin/plugin.json";

    /**
     * Reads a plugin manifest from a fetched plugin directory.
     * @param root the plugin directory.
     * @param fallbackName the entry's name, used when no manifest is present.
     * @return the manifest, or an absent manifest carrying the fallback name.
     * @throws IOException if a manifest exists but cannot be read.
     */
    public static PluginManifest from(Path root, String fallbackName) throws IOException {
        Path file = root.resolve(PLUGIN_FILE);
        if (!Files.isRegularFile(file)) {
            return absent(fallbackName);
        }
        JsonNode node = Json.read(file);
        String name = Json.text(node, "name", fallbackName);
        String author = null;
        JsonNode authorNode = node.get("author");
        if (authorNode != null) {
            author = authorNode.isObject() ? Json.text(authorNode, "name") : authorNode.asString();
        }
        return new PluginManifest(
                name,
                Json.text(node, "description"),
                Json.text(node, "version"),
                author,
                true,
                node);
    }

    /**
     * An absent manifest, where the marketplace entry is the authority.
     * @param fallbackName the plugin's name from the marketplace entry.
     * @return an absent manifest.
     */
    public static PluginManifest absent(String fallbackName) {
        return new PluginManifest(fallbackName, null, null, null, false, Json.object());
    }

    /**
     * Returns the raw node for a component key, or null when unset.
     * @param key the manifest key, e.g. {@code skills}, {@code agents}, {@code hooks}.
     * @return the raw node, or null.
     */
    public JsonNode component(String key) {
        return raw == null ? null : raw.get(key);
    }

    /**
     * Returns true when the manifest declares a component key at all.
     * @param key the manifest key.
     * @return true when present and non-null.
     */
    public boolean declares(String key) {
        JsonNode node = component(key);
        return node != null && !node.isNull();
    }
}
