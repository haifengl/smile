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
import tools.jackson.databind.node.ObjectNode;

/**
 * Translates a Claude plugin's {@code .mcp.json} into {@code ioa} MCP servers.
 *
 * <p>The server shape is compatible: {@code ioa} reads {@code mcpServers} (the Claude
 * key) as an alias for {@code servers}. What changes is the server <em>name</em>, which
 * is namespaced with the plugin prefix so two plugins' {@code db} servers cannot
 * collide, and the path variables, which are rewritten to absolute local paths.
 *
 * <p>Claude's MCP entry sits under {@code mcpServers} (also accepts the bare
 * top-level form). Every server is emitted — the installer stamps {@code disabled: true}
 * on servers whose opt-in resolves to false, so {@code ioa} skips them without the
 * fragment knowing the user's choice (ADR-008 hybrid).
 *
 * @author Haifeng Li
 */
final class McpTranslator {

    /**
     * Translates the plugin's MCP servers.
     * @param context the shared translation state.
     * @return the translated servers, each carrying a copy of its config with the
     *         namespaced name and rewritten path variables.
     * @throws IOException if the source file cannot be read.
     */
    List<TranslatedPlugin.TranslatedMcpServer> translate(Translation context) throws IOException {
        List<TranslatedPlugin.TranslatedMcpServer> servers = new ArrayList<>();
        Path file = context.source.resolve(".mcp.json");
        if (!Files.isRegularFile(file)) {
            return servers;
        }

        JsonNode root = Json.read(file);
        JsonNode map = root.get("mcpServers");
        if (map == null || !map.isObject()) {
            map = root.get("servers");
        }
        if (map == null || !map.isObject()) {
            // Claude also allows the servers directly at the top level.
            map = root;
        }

        for (var entry : map.properties()) {
            String localName = entry.getKey();
            JsonNode config = entry.getValue();
            if (!config.isObject()) {
                context.record(ComponentDisposition.dropped("mcpServers", localName,
                        "server entry is not an object"));
                continue;
            }
            if (referencesUserConfig(config)) {
                // Claude substitutes ${user_config.*} from a configuration dialog;
                // Studio has no equivalent, so the server would not start correctly.
                context.record(ComponentDisposition.dropped("mcpServers", localName,
                        "server needs user configuration (${user_config.*}), which Studio cannot supply"));
                continue;
            }

            String namespaced = context.id.namespace(localName);
            ObjectNode translated = (ObjectNode) config.deepCopy();
            rewrite(context, translated);

            servers.add(new TranslatedPlugin.TranslatedMcpServer(namespaced, localName, translated));
            context.record(ComponentDisposition.converted("mcpServers", localName,
                    "renamed to " + namespaced + "; starts only after MCP opt-in"));
        }
        return servers;
    }

    /** Rewrites path variables in every string value of a node, in place. */
    private void rewrite(Translation context, JsonNode node) {
        if (node.isObject()) {
            for (var entry : node.properties()) {
                JsonNode child = entry.getValue();
                if (child.isString()) {
                    ((ObjectNode) node).put(entry.getKey(), context.rewritePathVariables(child.asString()));
                } else {
                    rewrite(context, child);
                }
            }
        } else if (node.isArray()) {
            tools.jackson.databind.node.ArrayNode array = (tools.jackson.databind.node.ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode child = array.get(i);
                if (child.isString()) {
                    array.set(i, context.rewritePathVariables(child.asString()));
                } else {
                    rewrite(context, child);
                }
            }
        }
    }

    /** Returns true when any string value in the config references a user_config option. */
    private static boolean referencesUserConfig(JsonNode node) {
        if (node.isString()) {
            return Translation.referencesUserConfig(node.asString());
        }
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) {
                if (referencesUserConfig(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
