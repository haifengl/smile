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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.node.ObjectNode;

/**
 * Installs, enables, disables, and uninstalls plugins.
 *
 * <p>This is the only writer of {@code ~/.smile/plugins/**} and of the plugin state
 * in the settings files. The panel and CLI call it; they never write state directly.
 *
 * <p><b>Install is atomic and side-effect-free.</b> A plugin is fetched into the
 * cache, translated into a version directory, and only then does a {@code current}
 * marker atomically name it the active version. A failure leaves no partial install.
 * No plugin code runs during install (ADR-008): the only process Studio spawns is
 * {@code git}/{@code unzip} for a remote source, which is a fetch, not plugin content.
 *
 * @author Haifeng Li
 */
public final class PluginInstaller {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PluginInstaller.class);

    private final PluginHome home;
    private final MarketplaceRegistry registry;
    private final InstallIndex index;
    private final PluginStateStore state;
    private final PluginTranslator translator;
    private final Path projectDir;

    /**
     * Constructor.
     *
     * @param home the plugin home.
     * @param registry the marketplace registry.
     * @param state the state store.
     * @param projectDir the project working directory, used for project/local scopes.
     */
    public PluginInstaller(PluginHome home, MarketplaceRegistry registry, PluginStateStore state,
                           Path projectDir) {
        this.home = home;
        this.registry = registry;
        this.state = state;
        this.index = new InstallIndex(home);
        this.translator = new PluginTranslator();
        this.projectDir = projectDir;
    }

    /**
     * Installs a plugin from a registered marketplace and records it in a scope.
     *
     * @param id the plugin id.
     * @param scope the scope to enable it in.
     * @return the result, including any dropped or degraded components.
     */
    public InstallResult install(PluginId id, PluginScope scope) {
        MarketplaceRegistry.CatalogEntry entry;
        try {
            entry = registry.entry(id).orElse(null);
        } catch (RuntimeException ex) {
            return InstallResult.failed("Could not read the marketplace: " + ex.getMessage());
        }
        if (entry == null) {
            return InstallResult.failed("Plugin not found in marketplace '"
                    + id.marketplace() + "': " + id.name());
        }

        // Security gate: refuse a source type Studio will not execute (ADR-008).
        TrustPolicy.Decision decision = TrustPolicy.check(entry.entry().source());
        if (!decision.allowed()) {
            return InstallResult.failed(decision.reason());
        }

        Path staging = null;
        try {
            Path marketplaceRoot = entry.marketplaceRoot();
            Path source = fetch(entry.entry(), marketplaceRoot, id);
            PluginManifest manifest = PluginManifest.from(source, id.name());
            String version = version(id, entry.entry(), manifest);

            Path versionDir = home.versionDir(id, version);
            staging = home.pluginDir(id).resolve(version + ".staging");
            deleteRecursively(staging);
            Files.createDirectories(staging.getParent());

            TranslatedPlugin translated = translator.translate(id, source, staging, projectDir, manifest);
            writeMcpFragment(id, translated, staging);

            // Validate the produced names against the rest of the install, then commit.
            rejectCollisions(id, translated);

            Path finalDir = home.versionDir(id, version);
            deleteRecursively(finalDir);
            Files.move(staging, finalDir, StandardCopyOption.ATOMIC_MOVE);
            staging = null;

            Files.writeString(home.currentFile(id), version + "\n");

            // The fragment is written inside the version dir before the move, so it
            // travels with the content; record its final location.
            Path fragmentFile = Files.exists(finalDir.resolve("mcp.json"))
                    ? finalDir.resolve("mcp.json") : null;
            InstalledPlugin installed = new InstalledPlugin(id, version, finalDir, fragmentFile,
                    translated.agents().stream().map(TranslatedPlugin.TranslatedAgent::name).toList(),
                    translated.skills().stream().map(TranslatedPlugin.TranslatedSkill::name).toList(),
                    translated.mcp().stream().map(TranslatedPlugin.TranslatedMcpServer::localName).toList(),
                    translated.dispositions());
            index.put(installed);

            state.setEnabled(id, true, scope);

            logger.info("Installed plugin {} ({} components{})", id, translated.dispositions().size(),
                    translated.hasDrops() ? ", with dropped components" : "");
            return InstallResult.ok("Installed " + id + " (" + version + ")", translated.dispositions());
        } catch (SecurityException ex) {
            cleanup(staging);
            return InstallResult.failed("Refused to install " + id + ": " + ex.getMessage());
        } catch (IOException | RuntimeException ex) {
            cleanup(staging);
            logger.error("Failed to install {}", id, ex);
            return InstallResult.failed("Failed to install " + id + ": " + ex.getMessage());
        }
    }

    /**
     * Enables or disables an installed plugin in a scope.
     * @param id the plugin id.
     * @param enabled the new state.
     * @param scope the scope.
     * @return the result.
     */
    public InstallResult setEnabled(PluginId id, boolean enabled, PluginScope scope) {
        try {
            if (index.get(id) == null) {
                return InstallResult.failed("Plugin is not installed: " + id);
            }
            state.setEnabled(id, enabled, scope);
            return InstallResult.ok((enabled ? "Enabled " : "Disabled ") + id + " for " + scope.name().toLowerCase());
        } catch (IOException ex) {
            return InstallResult.failed("Failed to update state for " + id + ": " + ex.getMessage());
        }
    }

    /**
     * Uninstalls a plugin: removes its content and every state entry.
     * @param id the plugin id.
     * @return the result.
     */
    public InstallResult uninstall(PluginId id) {
        try {
            InstalledPlugin installed = index.get(id);
            if (installed == null) {
                return InstallResult.failed("Plugin is not installed: " + id);
            }
            index.remove(id);
            deleteRecursively(home.pluginDir(id));
            for (PluginScope scope : PluginScope.ordered()) {
                state.setEnabled(id, false, scope);
            }
            return InstallResult.ok("Uninstalled " + id);
        } catch (IOException ex) {
            return InstallResult.failed("Failed to uninstall " + id + ": " + ex.getMessage());
        }
    }

    /**
     * Returns the plugin home, so callers can locate installed content.
     * @return the plugin home.
     */
    public PluginHome home() {
        return home;
    }

    /**
     * Returns the install index.
     * @return the index.
     */
    public InstallIndex index() {
        return index;
    }

    // ------------------------------------------------------------------
    // Fetch
    // ------------------------------------------------------------------

    /** Fetches a plugin's content to a local directory and returns it. */
    private Path fetch(PluginEntry entry, Path marketplaceRoot, PluginId id) throws IOException {
        Path cache = home.cache().resolve("plugins").resolve(id.directory());
        Fetcher fetcher = new Fetcher();
        return fetcher.pluginRoot(entry.source(), marketplaceRoot, cache);
    }

    /** Writes the plugin's MCP fragment, marking every server disabled at first install. */
    private void writeMcpFragment(PluginId id, TranslatedPlugin translated, Path versionDir) throws IOException {
        if (translated.mcp().isEmpty()) {
            return;
        }
        ObjectNode root = Json.object();
        ObjectNode servers = root.putObject("servers");
        for (TranslatedPlugin.TranslatedMcpServer server : translated.mcp()) {
            ObjectNode config = (ObjectNode) server.config().deepCopy();
            // Every server starts disabled; the loader flips `disabled` off only for
            // servers the user opted into (ADR-008 hybrid).
            config.put("disabled", true);
            servers.set(server.name(), config);
        }
        Json.write(root, versionDir.resolve("mcp.json"));
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /** Refuses a plugin whose materialized names collide with an existing artifact. */
    private void rejectCollisions(PluginId id, TranslatedPlugin translated) throws IOException {
        Set<String> skillNames = new LinkedHashSet<>();
        translated.skills().forEach(s -> skillNames.add(s.name()));
        if (skillNames.size() != translated.skills().size()) {
            throw new IOException("Plugin produces duplicate skill names");
        }
        Set<String> agentNames = new LinkedHashSet<>();
        translated.agents().forEach(a -> agentNames.add(a.name()));
        if (agentNames.size() != translated.agents().size()) {
            throw new IOException("Plugin produces duplicate subagent names");
        }

        for (InstalledPlugin other : index.load().values()) {
            if (other.id().equals(id)) continue;
            for (String name : translated.skills().stream().map(TranslatedPlugin.TranslatedSkill::name).toList()) {
                if (other.skills().contains(name)) {
                    throw new IOException("Skill name '" + name + "' already provided by " + other.id());
                }
            }
            for (String name : translated.agents().stream().map(TranslatedPlugin.TranslatedAgent::name).toList()) {
                if (other.agents().contains(name)) {
                    throw new IOException("Subagent name '" + name + "' already provided by " + other.id());
                }
            }
        }
    }

    /** Computes the version directory name, preferring the manifest, then the entry. */
    private String version(PluginId id, PluginEntry entry, PluginManifest manifest) {
        String version = manifest.version();
        if (version == null || version.isBlank()) version = entry.version();
        if (version == null || version.isBlank()) {
            version = "0.0.0-" + Instant.now().toEpochMilli();
        }
        return PluginId.sanitize(version);
    }

    private void cleanup(Path staging) {
        if (staging != null) {
            try {
                deleteRecursively(staging);
            } catch (IOException ex) {
                logger.warn("Failed to clean up staging dir {}: {}", staging, ex.getMessage());
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
