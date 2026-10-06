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
 * The anti-corruption layer: the only component that understands the Claude Code
 * plugin layout. It turns a fetched plugin into an {@code ioa}-native tree and an
 * honest account of what was kept, converted, degraded, or dropped.
 *
 * <p>Design rule (ADR-002): if Anthropic's format changes, only this class and its
 * component translators change. Nothing downstream reads {@code .claude-plugin/},
 * {@code commands/}, or {@code .mcp.json}.
 *
 * <p>Nothing here executes plugin content. Translation is pure file I/O; the only
 * code a plugin can cause to run is an MCP server, gated separately at activation
 * (ADR-008).
 *
 * @author Haifeng Li
 */
public final class PluginTranslator {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PluginTranslator.class);

    /** Manifest keys for components Studio has no equivalent for. */
    private static final List<String> UNSUPPORTED = List.of(
            "hooks", "lspServers", "outputStyles", "themes", "channels",
            "settings", "userConfig", "experimental");

    /**
     * Translates one fetched plugin into the target directory.
     *
     * @param id the plugin id.
     * @param source the fetched plugin directory (Claude layout).
     * @param target the directory to write the ioa-native tree into.
     * @param projectDir the project working directory, for path-variable rewriting.
     * @param manifest the plugin's parsed manifest.
     * @return the translated plugin, including every component's disposition.
     * @throws IOException if a required file cannot be read or written.
     */
    public TranslatedPlugin translate(PluginId id, Path source, Path target, Path projectDir,
                                      PluginManifest manifest) throws IOException {
        Path dataDir = Path.of(System.getProperty("user.home"), ".smile", "plugins", "data", id.directory());
        Translation context = new Translation(id, source, target, projectDir, dataDir);
        Files.createDirectories(target);

        List<TranslatedPlugin.TranslatedAgent> agents = new AgentTranslator().translate(context);
        List<TranslatedPlugin.TranslatedSkill> skills = new ArrayList<>(new SkillTranslator().translate(context));
        skills.addAll(new CommandTranslator().translate(context));
        List<TranslatedPlugin.TranslatedMcpServer> mcp = new McpTranslator().translate(context);

        recordUnsupported(context, manifest, source);

        logger.info("Translated plugin {}: {} agents, {} skills, {} mcp servers, {} dropped",
                id, agents.size(), skills.size(), mcp.size(),
                context.dispositions().stream().filter(ComponentDisposition::isDropped).count());

        return new TranslatedPlugin(id, target, agents, skills, mcp, context.dispositions());
    }

    /**
     * Records a disposition for every component ioa cannot honor, so the user is
     * told rather than left to expect behavior that never happens (ADR-005).
     */
    private void recordUnsupported(Translation context, PluginManifest manifest, Path source) {
        for (String key : UNSUPPORTED) {
            if (!manifest.declares(key)) {
                continue;
            }
            String reason = switch (key) {
                case "hooks" -> "automation hooks are not supported by ioa";
                case "lspServers" -> "plugin LSP servers are not supported; Studio has its own LSP integration";
                case "outputStyles" -> "output styles are not supported by ioa";
                case "themes" -> "themes are not supported; Studio uses FlatLaf themes";
                case "channels" -> "channels are not supported by ioa";
                case "settings" -> "a plugin agent cannot replace the main thread; ignored";
                case "userConfig" -> "user configuration options are not supported";
                case "experimental" -> "experimental components are not supported";
                default -> "not supported by ioa";
            };
            JsonNode node = manifest.component(key);
            String name = node != null && node.isObject() ? key + " (" + node.size() + ")" : key;
            context.record(ComponentDisposition.dropped(key, name, reason));
        }

        // The executable bin/ directory is never copied: Studio holds its staged JBR
        // open and has no safe surface for arbitrary plugin executables (C9).
        if (Files.isDirectory(source.resolve("bin"))) {
            context.record(ComponentDisposition.dropped("bin", "bin/",
                    "plugin executables are not installed; Studio does not run plugin binaries"));
        }
    }
}
