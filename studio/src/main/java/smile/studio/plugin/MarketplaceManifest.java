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
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * A parsed {@code .claude-plugin/marketplace.json}: a catalog of plugins.
 *
 * <p>The file is read from the marketplace root. A marketplace is registered by
 * pointing at a source that resolves to this file; see {@link MarketplaceSource}.
 *
 * @param name the marketplace's registered name (required).
 * @param owner the maintainer, informational only.
 * @param plugins the catalog entries. An entry that fails to parse is replaced by
 *                a {@link ParseError} rather than failing the marketplace.
 * @param description the marketplace description.
 * @param version the marketplace manifest version.
 * @param pluginRoot the directory bare plugin source names resolve under, or null.
 *
 * @author Haifeng Li
 */
public record MarketplaceManifest(
        String name,
        Owner owner,
        List<PluginEntry> plugins,
        List<ParseError> errors,
        String description,
        String version,
        String pluginRoot) {

    /** The marketplace's relative plugin-name root, relative to the marketplace root. */
    public static final String MARKETPLACE_FILE = ".claude-plugin/marketplace.json";

    /**
     * Maintainer information.
     * @param name the owner's name (required by the format).
     * @param email the owner's email.
     * @param url the owner's URL.
     */
    public record Owner(String name, String email, String url) { }

    /**
     * A {@code plugins[]} entry that could not be parsed. Kept so the UI can name
     * the offending entry instead of silently hiding it.
     * @param index the entry's index in the array.
     * @param reason why it failed.
     */
    public record ParseError(int index, String reason) { }

    /** Normalizes the lists. */
    public MarketplaceManifest {
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Reads and parses a marketplace manifest.
     *
     * @param root the marketplace root (the directory containing {@code .claude-plugin/}).
     * @return the parsed manifest.
     * @throws IOException if the file is missing or cannot be read.
     */
    public static MarketplaceManifest from(Path root) throws IOException {
        Path file = root.resolve(MARKETPLACE_FILE);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Marketplace manifest not found: " + file);
        }
        return parse(Json.read(file));
    }

    /**
     * Parses a marketplace manifest from a JSON tree.
     * @param node the root node.
     * @return the parsed manifest.
     */
    public static MarketplaceManifest parse(JsonNode node) {
        String name = Json.text(node, "name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Marketplace manifest is missing its 'name'");
        }

        Owner owner = null;
        JsonNode ownerNode = node.get("owner");
        if (ownerNode != null && ownerNode.isObject()) {
            owner = new Owner(
                    Json.text(ownerNode, "name"),
                    Json.text(ownerNode, "email"),
                    Json.text(ownerNode, "url"));
        }

        List<PluginEntry> entries = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        JsonNode pluginsNode = node.get("plugins");
        if (pluginsNode != null && pluginsNode.isArray()) {
            int index = 0;
            for (JsonNode entryNode : pluginsNode) {
                try {
                    entries.add(PluginEntry.parse(entryNode));
                } catch (IllegalArgumentException ex) {
                    errors.add(new ParseError(index, ex.getMessage()));
                }
                index++;
            }
        }

        String description = Json.text(node, "description");
        String version = Json.text(node, "version");
        JsonNode metadata = node.get("metadata");
        if (metadata != null && metadata.isObject()) {
            if (description == null) description = Json.text(metadata, "description");
            if (version == null) version = Json.text(metadata, "version");
        }
        String pluginRoot = metadata != null && metadata.isObject()
                ? Json.text(metadata, "pluginRoot") : null;

        return new MarketplaceManifest(name, owner, entries, errors, description, version, pluginRoot);
    }
}
