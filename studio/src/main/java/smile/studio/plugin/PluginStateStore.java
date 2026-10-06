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
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads and writes the plugin state that lives in the {@code .smile} state files:
 * which plugins are enabled per scope, and the per-server MCP opt-in.
 *
 * <p>Two state blocks are owned here, under a single top-level {@code plugins}
 * key:
 *
 * <pre>{@code
 * {
 *   "plugins": {
 *     "enabledPlugins": { "commit-commands@claude-plugins-official": true },
 *     "pluginMcp": {
 *       "commit-commands@claude-plugins-official": {
 *         "enabled": true,
 *         "servers": { "heavy-server": false }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>The file is {@code plugins.json} ({@code plugins.local.json} for the local
 * scope) — deliberately not {@code settings.json}, so plugin state does not compete
 * with the other Studio configuration files (ADR-006). Writes are read-modify-write
 * and preserve unknown keys, so a file a user has edited by hand is not clobbered.
 * Precedence is local &gt; project &gt; user ({@link PluginScope#ordered()}).
 *
 * <p><b>MCP opt-in resolution</b> is single-sourced in {@link #mcpEnabled}: the
 * per-server override wins, else the plugin default, else off. The loader and the
 * panel both call it, so the toggle shown and the server started cannot disagree
 * (ADR-008 hybrid).
 *
 * @author Haifeng Li
 */
public final class PluginStateStore {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PluginStateStore.class);
    /** The top-level key holding all plugin state. */
    private static final String PLUGINS = "plugins";
    private static final String ENABLED = "enabledPlugins";
    private static final String MCP = "pluginMcp";
    private static final String SERVERS = "servers";

    /** The working directory used to resolve project and local scopes. */
    private final Path cwd;
    /** The user home used to resolve the user scope; overridable for tests. */
    private final Path userHome;

    /**
     * Constructor using the real user home.
     * @param cwd the project working directory.
     */
    public PluginStateStore(Path cwd) {
        this(cwd, Path.of(System.getProperty("user.home")));
    }

    /**
     * Constructor.
     * @param cwd the project working directory.
     * @param userHome the user's home directory.
     */
    public PluginStateStore(Path cwd, Path userHome) {
        this.cwd = cwd;
        this.userHome = userHome;
    }

    // ------------------------------------------------------------------
    // Enablement
    // ------------------------------------------------------------------

    /**
     * Records whether a plugin is enabled in a scope.
     * @param id the plugin id.
     * @param enabled the new value.
     * @param scope the scope to write.
     * @throws IOException if the settings file cannot be written.
     */
    public void setEnabled(PluginId id, boolean enabled, PluginScope scope) throws IOException {
        update(scope, plugins -> {
            ObjectNode map = childObject(plugins, ENABLED);
            map.put(id.toString(), enabled);
            plugins.set(ENABLED, map);
        });
    }

    /**
     * Returns whether a plugin is enabled, resolving scope precedence.
     * @param id the plugin id.
     * @return true when the most specific scope that mentions it says enabled.
     */
    public boolean isEnabled(PluginId id) {
        for (PluginScope scope : PluginScope.ordered()) {
            JsonNode map = plugins(read(scope)).get(ENABLED);
            if (map != null && map.has(id.toString())) {
                return map.get(id.toString()).asBoolean();
            }
        }
        return false;
    }

    /**
     * Returns the id of every plugin enabled in any scope, most-specific scope
     * winning when the same plugin appears twice.
     * @return the enabled plugin ids.
     */
    public List<PluginId> enabledPlugins() {
        Map<String, Boolean> resolved = new LinkedHashMap<>();
        // Iterate least-specific first so a more specific scope overwrites it.
        PluginScope[] scopes = PluginScope.ordered();
        for (int i = scopes.length - 1; i >= 0; i--) {
            JsonNode map = plugins(read(scopes[i])).get(ENABLED);
            if (map == null || !map.isObject()) continue;
            map.properties().forEach(entry ->
                    resolved.put(entry.getKey(), entry.getValue().asBoolean()));
        }
        List<PluginId> enabled = new ArrayList<>();
        resolved.forEach((key, value) -> {
            if (Boolean.TRUE.equals(value)) {
                try {
                    enabled.add(PluginId.parse(key));
                } catch (IllegalArgumentException ex) {
                    logger.warn("Ignoring malformed plugin id in settings: {}", key);
                }
            }
        });
        return enabled;
    }

    // ------------------------------------------------------------------
    // MCP opt-in (per server, with a per-plugin default)
    // ------------------------------------------------------------------

    /**
     * Sets the per-plugin default MCP opt-in.
     * @param id the plugin id.
     * @param enabled the new default for servers without an override.
     * @param scope the scope to write.
     * @throws IOException if the settings file cannot be written.
     */
    public void setMcpEnabled(PluginId id, boolean enabled, PluginScope scope) throws IOException {
        update(scope, plugins -> {
            ObjectNode mcp = childObject(plugins, MCP);
            ObjectNode entry = childObject(mcp, id.toString());
            entry.put("enabled", enabled);
            mcp.set(id.toString(), entry);
            plugins.set(MCP, mcp);
        });
    }

    /**
     * Sets a per-server override.
     * @param id the plugin id.
     * @param server the server's local name within the plugin.
     * @param enabled the override value.
     * @param scope the scope to write.
     * @throws IOException if the settings file cannot be written.
     */
    public void setMcpServerEnabled(PluginId id, String server, boolean enabled, PluginScope scope)
            throws IOException {
        update(scope, plugins -> {
            ObjectNode mcp = childObject(plugins, MCP);
            ObjectNode entry = childObject(mcp, id.toString());
            ObjectNode servers = childObject(entry, SERVERS);
            servers.put(server, enabled);
            entry.set(SERVERS, servers);
            mcp.set(id.toString(), entry);
            plugins.set(MCP, mcp);
        });
    }

    /**
     * Resolves whether one MCP server of a plugin is opted in.
     *
     * <p>Resolution order, single-sourced here: the most specific scope's per-server
     * override, else that scope's per-plugin default, else the next scope, else off.
     *
     * @param id the plugin id.
     * @param server the server's local name within the plugin.
     * @return true when the server should be connected.
     */
    public boolean mcpEnabled(PluginId id, String server) {
        for (PluginScope scope : PluginScope.ordered()) {
            JsonNode entry = plugins(read(scope)).get(MCP);
            if (entry == null) continue;
            JsonNode plugin = entry.get(id.toString());
            if (plugin == null || !plugin.isObject()) continue;

            JsonNode servers = plugin.get(SERVERS);
            if (servers != null && servers.isObject() && servers.has(server)) {
                return servers.get(server).asBoolean();
            }
            JsonNode enabled = plugin.get("enabled");
            if (enabled != null && !enabled.isNull()) {
                return enabled.asBoolean();
            }
        }
        return false;
    }

    /**
     * Returns the per-plugin MCP default recorded in the most specific scope, for
     * the panel's plugin-level switch. Falls back to false.
     * @param id the plugin id.
     * @return the plugin default.
     */
    public boolean mcpDefault(PluginId id) {
        for (PluginScope scope : PluginScope.ordered()) {
            JsonNode entry = plugins(read(scope)).get(MCP);
            if (entry == null) continue;
            JsonNode plugin = entry.get(id.toString());
            if (plugin != null && plugin.has("enabled")) {
                return plugin.get("enabled").asBoolean();
            }
        }
        return false;
    }

    /**
     * Returns the scope that most specifically defines a plugin's MCP default, for
     * the panel to write an override to the same place.
     * @param id the plugin id.
     * @return the scope, or {@link PluginScope#USER} when none is set.
     */
    public PluginScope mcpScope(PluginId id) {
        for (PluginScope scope : PluginScope.ordered()) {
            JsonNode entry = plugins(read(scope)).get(MCP);
            if (entry == null) continue;
            JsonNode plugin = entry.get(id.toString());
            if (plugin != null && plugin.has("enabled")) {
                return scope;
            }
        }
        return PluginScope.USER;
    }

    // ------------------------------------------------------------------
    // Settings file I/O
    // ------------------------------------------------------------------

    /**
     * Reads a scope's settings file, returning an empty node when absent or unreadable.
     * @param scope the scope.
     * @return the root node, never null.
     */
    private ObjectNode read(PluginScope scope) {
        Path file = scope.settingsFile(cwd, userHome);
        if (!Files.isRegularFile(file)) {
            return Json.object();
        }
        try {
            JsonNode node = Json.read(file);
            return node != null && node.isObject() ? (ObjectNode) node : Json.object();
        } catch (Exception ex) {
            // A corrupt file is backed up and treated as empty rather than fatal.
            logger.warn("Ignoring unreadable settings file {}: {}", file, ex.getMessage());
            try {
                Path backup = file.resolveSibling(file.getFileName() + ".bak");
                Files.move(file, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.warn("Backed up corrupt settings file to {}", backup);
            } catch (IOException io) {
                logger.error("Failed to back up corrupt settings file {}", file, io);
            }
            return Json.object();
        }
    }

    /**
     * Read-modify-write of a scope's settings file. Other top-level keys are preserved.
     * @param scope the scope.
     * @param mutation the mutation to apply to the {@code plugins} object.
     * @throws IOException if the file cannot be written.
     */
    private void update(PluginScope scope, java.util.function.Consumer<ObjectNode> mutation)
            throws IOException {
        ObjectNode root = read(scope);
        ObjectNode plugins = childObject(root, PLUGINS);
        mutation.accept(plugins);
        root.set(PLUGINS, plugins);
        Json.write(root, scope.settingsFile(cwd, userHome));
    }

    /**
     * Returns a child object node, creating it when absent or of the wrong type.
     * @param parent the parent node.
     * @param field the field name.
     * @return the child object node.
     */
    private static ObjectNode childObject(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        return node != null && node.isObject() ? (ObjectNode) node : Json.object();
    }

    /**
     * Returns the {@code plugins} sub-object of a settings root, or an empty node.
     * @param root the settings root.
     * @return the plugins object, never null.
     */
    private static ObjectNode plugins(ObjectNode root) {
        return childObject(root, PLUGINS);
    }
}
