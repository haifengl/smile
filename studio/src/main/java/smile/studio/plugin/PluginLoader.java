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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import ioa.agent.memory.Skill;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Makes installed plugins visible to the {@code ioa} agent runtime at load time.
 *
 * <p>This is the only component that touches {@code ioa} ({@code Skill} and the MCP
 * connect call) and the project's {@code .smile/agents} staging area. It runs in two
 * phases because {@code ioa} both reads directories and is fed objects:
 *
 * <ol>
 *   <li>{@link #bootstrap} — before MCP starts and before agents are built: stage each
 *       enabled plugin's subagents into {@code .smile/agents/<name>} (a directory
 *       {@code createSubagent} already scans), and produce an <em>effective</em> MCP
 *       fragment per plugin whose {@code disabled} flags reflect the user's opt-in.</li>
 *   <li>{@link #pluginSkills} — after agents are built: the translated skills as
 *       {@code ioa} {@link Skill} objects, for the workspace to add to each agent.</li>
 * </ol>
 *
 * <p>Subagents are staged rather than referenced because {@code ioa} resolves user
 * subagents from fixed directories; a plugin cannot inject one. They remain
 * <b>degraded</b> until ioa change A6 advertises user-defined subagents (ADR-009).
 *
 * @author Haifeng Li
 */
public final class PluginLoader {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PluginLoader.class);

    /** The process-wide loader, configured once at startup. */
    private static volatile PluginLoader instance;

    private final PluginHome home;
    private final PluginStateStore state;
    private final InstallIndex index;
    private final Path cwd;

    private volatile List<Skill> skills = List.of();
    private volatile List<Path> mcpFragments = List.of();
    private volatile List<InstalledPlugin> active = List.of();

    /**
     * Constructor.
     * @param home the plugin home.
     * @param state the state store.
     * @param index the install index.
     * @param cwd the project working directory.
     */
    public PluginLoader(PluginHome home, PluginStateStore state, InstallIndex index, Path cwd) {
        this.home = home;
        this.state = state;
        this.index = index;
        this.cwd = cwd;
    }

    /**
     * Returns the process-wide loader, or null when plugins were never bootstrapped
     * (headless use, or a failure). Callers treat null as "no plugins".
     * @return the shared loader, or null.
     */
    public static PluginLoader shared() {
        return instance;
    }

    /**
     * Configures and runs the shared loader for this process.
     *
     * @param cwd the project working directory.
     * @return the loader.
     */
    public static PluginLoader bootstrapShared(Path cwd) {
        PluginHome home = new PluginHome();
        PluginStateStore state = new PluginStateStore(cwd);
        PluginLoader loader = new PluginLoader(home, state, new InstallIndex(home), cwd);
        loader.bootstrap();
        instance = loader;
        return loader;
    }

    /**
     * Runs the load-time phase: stages subagents and computes effective MCP fragments.
     * Never throws; a failure degrades to "no plugins" so Studio still starts.
     */
    public void bootstrap() {
        List<InstalledPlugin> enabled = new ArrayList<>();
        try {
            for (PluginId id : state.enabledPlugins()) {
                InstalledPlugin plugin = index.get(id);
                if (plugin == null) {
                    logger.warn("Plugin {} is enabled but not installed; skipping", id);
                    continue;
                }
                enabled.add(plugin);
            }
            List<Skill> loadedSkills = new ArrayList<>();
            List<Path> fragments = new ArrayList<>();
            for (InstalledPlugin plugin : enabled) {
                stageSubagents(plugin);
                loadSkills(plugin, loadedSkills);
                Path fragment = effectiveFragment(plugin);
                if (fragment != null) {
                    fragments.add(fragment);
                }
            }
            active = List.copyOf(enabled);
            skills = List.copyOf(loadedSkills);
            mcpFragments = List.copyOf(fragments);
        } catch (Exception ex) {
            logger.error("Failed to bootstrap plugins; continuing without them", ex);
        }
    }

    /**
     * Returns the translated skills of every enabled plugin, as {@code ioa} skills.
     * @return the plugin skills.
     */
    public List<Skill> pluginSkills() {
        return skills;
    }

    /**
     * Returns the effective MCP fragment files of every enabled plugin, for the MCP
     * startup code to connect. A server the user did not opt into is marked
     * {@code disabled: true}, so {@code ioa} never starts it.
     * @return the fragment file paths.
     */
    public List<Path> mcpFragments() {
        return mcpFragments;
    }

    /**
     * Returns the plugins currently active.
     * @return the active plugins.
     */
    public List<InstalledPlugin> activePlugins() {
        return active;
    }

    /** Copies a plugin's subagents into the directory ioa's createSubagent scans. */
    private void stageSubagents(InstalledPlugin plugin) throws IOException {
        Path target = cwd.resolve(".smile").resolve("agents");
        for (String name : plugin.agents()) {
            Path source = plugin.root().resolve("agents").resolve(name);
            if (!Files.isDirectory(source)) continue;
            Path dest = PathGuard.assertInside(target, target.resolve(name));
            copyDirectory(source, dest);
        }
    }

    /** Loads a plugin's skills as ioa Skill objects. */
    private void loadSkills(InstalledPlugin plugin, List<Skill> into) {
        Path skillsDir = plugin.root().resolve("skills");
        if (!Files.isDirectory(skillsDir)) return;
        for (String name : plugin.skills()) {
            Path dir = skillsDir.resolve(name);
            if (!Files.isRegularFile(dir.resolve("SKILL.md"))) continue;
            try {
                into.add(Skill.of(dir));
            } catch (IOException ex) {
                logger.warn("Skipping unreadable skill {} from {}: {}", name, plugin.id(), ex.getMessage());
            }
        }
    }

    /**
     * Writes an effective fragment for a plugin: the installed fragment with each
     * server's {@code disabled} flag resolved from the user's opt-in. Returns null
     * when the plugin has no MCP servers or none are enabled.
     */
    private Path effectiveFragment(InstalledPlugin plugin) throws IOException {
        if (plugin.fragmentFile() == null || !Files.isRegularFile(plugin.fragmentFile())) {
            return null;
        }
        JsonNode root = Json.read(plugin.fragmentFile());
        JsonNode servers = root.get("servers");
        if (servers == null || !servers.isObject()) {
            return null;
        }

        ObjectNode effective = Json.object();
        ObjectNode outServers = effective.putObject("servers");
        boolean anyEnabled = false;
        // Match by the plugin's own id prefix rather than a guessed separator, so a
        // server name that itself contains "--" cannot be mis-stripped.
        String prefix = plugin.id().prefix() + "--";
        for (var entry : servers.properties()) {
            String namespaced = entry.getKey();
            JsonNode config = entry.getValue();
            if (!config.isObject()) continue;
            String localName = namespaced.startsWith(prefix)
                    ? namespaced.substring(prefix.length()) : namespaced;

            ObjectNode copy = (ObjectNode) config.deepCopy();
            boolean enabled = state.mcpEnabled(plugin.id(), localName);
            copy.put("disabled", !enabled);
            if (enabled) {
                anyEnabled = true;
            }
            outServers.set(namespaced, copy);
        }
        if (!anyEnabled) {
            return null;
        }

        Path effectiveFile = plugin.root().resolve("mcp.effective.json");
        Json.write(effective, effectiveFile);
        return effectiveFile;
    }

    /** Recursively copies a directory, replacing the destination. */
    private static void copyDirectory(Path source, Path target) throws IOException {
        if (Files.exists(target)) {
            deleteRecursively(target);
        }
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        List<Path> paths = new ArrayList<>();
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(paths::add);
        }
        for (Path p : paths) {
            Files.deleteIfExists(p);
        }
    }
}
