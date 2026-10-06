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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The install index: the set of plugins installed on this machine, keyed by id.
 * Backed by a single JSON file under the plugin home.
 *
 * <p>The index is a cache of facts the filesystem already holds, but keeping it
 * avoids walking every plugin directory at startup and preserves each component's
 * disposition for the Errors tab.
 *
 * @author Haifeng Li
 */
final class InstallIndex {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InstallIndex.class);
    private static final String FILE = "installed.json";

    private final Path file;

    /**
     * Constructor.
     * @param home the plugin home.
     */
    InstallIndex(PluginHome home) {
        this.file = home.root().resolve(FILE);
    }

    /**
     * Loads every installed plugin, keyed by id.
     * @return the installed plugins, in index order.
     */
    Map<PluginId, InstalledPlugin> load() {
        Map<PluginId, InstalledPlugin> map = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return map;
        }
        try {
            JsonNode root = Json.read(file);
            for (JsonNode node : Json.array(root, "plugins")) {
                try {
                    InstalledPlugin plugin = InstalledPlugin.fromJson(node);
                    map.put(plugin.id(), plugin);
                } catch (RuntimeException ex) {
                    logger.warn("Ignoring malformed install index entry: {}", ex.getMessage());
                }
            }
        } catch (IOException ex) {
            logger.warn("Could not read install index {}: {}", file, ex.getMessage());
        }
        return map;
    }

    /**
     * Records or replaces one plugin's index entry.
     * @param plugin the installed plugin.
     * @throws IOException if the index cannot be written.
     */
    void put(InstalledPlugin plugin) throws IOException {
        Map<PluginId, InstalledPlugin> map = load();
        map.put(plugin.id(), plugin);
        save(map);
    }

    /**
     * Removes one plugin from the index.
     * @param id the plugin id.
     * @throws IOException if the index cannot be written.
     */
    void remove(PluginId id) throws IOException {
        Map<PluginId, InstalledPlugin> map = load();
        if (map.remove(id) != null) {
            save(map);
        }
    }

    /**
     * Returns one plugin's entry.
     * @param id the plugin id.
     * @return the entry, or null.
     */
    InstalledPlugin get(PluginId id) {
        return load().get(id);
    }

    private void save(Map<PluginId, InstalledPlugin> map) throws IOException {
        ObjectNode root = Json.object();
        ArrayNode array = root.putArray("plugins");
        for (InstalledPlugin plugin : map.values()) {
            array.add(plugin.toJson());
        }
        Json.write(root, file);
    }
}
