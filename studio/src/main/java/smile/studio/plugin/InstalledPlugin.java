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
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The record of one installed plugin: where its translated content lives, what
 * components it contributed, and how each was dispositioned. Persisted in the
 * install index so the loader can wire the plugin up and the panel can show what
 * was installed without re-reading the whole tree.
 *
 * @param id the plugin id.
 * @param version the active version directory name.
 * @param root the translated root directory.
 * @param fragmentFile the plugin's MCP fragment file, or null when it has none.
 * @param agents the materialized subagent names.
 * @param skills the materialized skill names.
 * @param mcpServers the MCP servers' local names within the plugin (the opt-in key;
 *                   the namespaced fragment key is {@code id.namespace(local)}).
 * @param dispositions every component's disposition.
 *
 * @author Haifeng Li
 */
public record InstalledPlugin(
        PluginId id,
        String version,
        Path root,
        Path fragmentFile,
        List<String> agents,
        List<String> skills,
        List<String> mcpServers,
        List<ComponentDisposition> dispositions) {

    /** Normalizes the lists. */
    public InstalledPlugin {
        agents = agents == null ? List.of() : List.copyOf(agents);
        skills = skills == null ? List.of() : List.copyOf(skills);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        dispositions = dispositions == null ? List.of() : List.copyOf(dispositions);
    }

    /**
     * Builds the record from a translation result.
     * @param translated the translation result.
     * @param version the version directory name.
     * @param fragmentFile the MCP fragment file, or null.
     * @return the installed record.
     */
    static InstalledPlugin of(TranslatedPlugin translated, String version, Path fragmentFile) {
        List<String> agents = new ArrayList<>();
        translated.agents().forEach(a -> agents.add(a.name()));
        List<String> skills = new ArrayList<>();
        translated.skills().forEach(s -> skills.add(s.name()));
        List<String> mcp = new ArrayList<>();
        translated.mcp().forEach(m -> mcp.add(m.name()));
        return new InstalledPlugin(translated.id(), version, translated.root(), fragmentFile,
                agents, skills, mcp, translated.dispositions());
    }

    /**
     * Serializes to a JSON object.
     * @return the object node.
     */
    ObjectNode toJson() {
        ObjectNode node = Json.object();
        node.put("id", id.toString());
        node.put("version", version);
        node.put("root", root.toString());
        if (fragmentFile != null) {
            node.put("fragment", fragmentFile.toString());
        }
        ArrayNode agentArray = node.putArray("agents");
        agents.forEach(agentArray::add);
        ArrayNode skillArray = node.putArray("skills");
        skills.forEach(skillArray::add);
        ArrayNode mcpArray = node.putArray("mcpServers");
        mcpServers.forEach(mcpArray::add);
        ArrayNode dispositionArray = node.putArray("dispositions");
        for (ComponentDisposition disposition : dispositions) {
            ObjectNode item = Json.object();
            item.put("kind", disposition.kind());
            item.put("name", disposition.name());
            item.put("result", disposition.disposition().name());
            item.put("reason", disposition.reason());
            dispositionArray.add(item);
        }
        return node;
    }

    /**
     * Deserializes from a JSON object.
     * @param node the object node.
     * @return the installed record.
     */
    static InstalledPlugin fromJson(JsonNode node) {
        PluginId id = PluginId.parse(Json.text(node, "id"));
        String version = Json.text(node, "version");
        Path root = Path.of(Json.text(node, "root"));
        String fragment = Json.text(node, "fragment");
        List<String> agents = Json.strings(node, "agents");
        List<String> skills = Json.strings(node, "skills");
        List<String> mcp = Json.strings(node, "mcpServers");

        List<ComponentDisposition> dispositions = new ArrayList<>();
        for (JsonNode item : Json.array(node, "dispositions")) {
            dispositions.add(new ComponentDisposition(
                    Json.text(item, "kind"),
                    Json.text(item, "name"),
                    ComponentDisposition.Disposition.valueOf(Json.text(item, "result")),
                    Json.text(item, "reason")));
        }
        return new InstalledPlugin(id, version, root,
                fragment == null ? null : Path.of(fragment), agents, skills, mcp, dispositions);
    }

    /** @return the plugin's namespaced subagent directory root. */
    Path agentsDir() {
        return root.resolve("agents");
    }

    /** @return the plugin's namespaced skills directory root. */
    Path skillsDir() {
        return root.resolve("skills");
    }
}
