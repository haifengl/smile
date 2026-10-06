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

import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The result of translating one plugin: the materialized {@code ioa}-native tree
 * plus a per-component account of what was done.
 *
 * <p>This is the internal contract between the translator (Installation) and the
 * loader (Activation), and the data the Errors/Warnings tab renders. See
 * {@code design.md} §4.3.
 *
 * @param id the plugin id.
 * @param root the materialized ioa-native root directory.
 * @param agents the translated subagents.
 * @param skills the translated skills (including translated commands).
 * @param mcp the translated MCP servers.
 * @param dispositions every component, with what happened to it.
 *
 * @author Haifeng Li
 */
public record TranslatedPlugin(
        PluginId id,
        Path root,
        List<TranslatedAgent> agents,
        List<TranslatedSkill> skills,
        List<TranslatedMcpServer> mcp,
        List<ComponentDisposition> dispositions) {

    /** Normalizes the lists. */
    public TranslatedPlugin {
        agents = agents == null ? List.of() : List.copyOf(agents);
        skills = skills == null ? List.of() : List.copyOf(skills);
        mcp = mcp == null ? List.of() : List.copyOf(mcp);
        dispositions = dispositions == null ? List.of() : List.copyOf(dispositions);
    }

    /**
     * One translated subagent, materialized as {@code agents/<name>/AGENT.md}.
     * @param name the materialized, namespaced subagent name.
     * @param file the {@code AGENT.md} path.
     * @param degraded whether the agent carries a limitation (always true until ioa
     *                 change A6 makes user-defined subagents discoverable — ADR-009).
     */
    public record TranslatedAgent(String name, Path file, boolean degraded) { }

    /**
     * One translated skill, materialized as {@code skills/<name>/SKILL.md} with its
     * supporting {@code scripts/}, {@code references/}, and {@code assets/}.
     * @param name the materialized, namespaced skill name.
     * @param dir the skill directory.
     * @param command whether this skill came from a Claude {@code commands/*.md} file.
     */
    public record TranslatedSkill(String name, Path dir, boolean command) { }

    /**
     * One translated MCP server.
     * @param name the materialized, namespaced server name (the fragment key).
     * @param localName the server's name within the plugin, used for the opt-in key.
     * @param config the server's configuration node.
     */
    public record TranslatedMcpServer(String name, String localName, JsonNode config) { }

    /** @return true when at least one component was dropped. */
    public boolean hasDrops() {
        return dispositions.stream().anyMatch(ComponentDisposition::isDropped);
    }

    /** @return true when at least one component is degraded. */
    public boolean hasDegradations() {
        return dispositions.stream().anyMatch(ComponentDisposition::isDegraded);
    }
}
