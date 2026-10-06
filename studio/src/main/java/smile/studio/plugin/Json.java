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
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Shared JSON helpers for the plugin marketplace. Jackson v3 ({@code tools.jackson}),
 * matching the rest of the Studio module.
 *
 * <p>Java-style comments ({@code //} and {@code /* ... *}{@code /}) are permitted, so a
 * marketplace or plugin file copied from a third party that contains a comment can be
 * read as-is. The mapper is deliberately permissive: vendor extensions and unknown
 * keys are ignored rather than rejected, because the Claude Code format evolves and a
 * newer key on an older Studio must not fail the whole file.
 *
 * @author Haifeng Li
 */
final class Json {
    /** The shared, permissive mapper. */
    static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build();

    private Json() {
    }

    /**
     * Reads a JSON file into a node.
     * @param path the file to read.
     * @return the parsed root node.
     * @throws IOException if the file cannot be read or parsed.
     */
    static JsonNode read(Path path) throws IOException {
        return MAPPER.readTree(path.toFile());
    }

    /**
     * Returns the text value of a field, or {@code null} when absent or null.
     * @param node the object node.
     * @param field the field name.
     * @return the text value, or {@code null}.
     */
    static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /**
     * Returns the text value of a field, or the default when absent or blank.
     * @param node the object node.
     * @param field the field name.
     * @param defaultValue the fallback.
     * @return the text value or the default.
     */
    static String text(JsonNode node, String field, String defaultValue) {
        String value = text(node, field);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * Returns a boolean field, defaulting when absent.
     * @param node the object node.
     * @param field the field name.
     * @param defaultValue the fallback.
     * @return the boolean value or the default.
     */
    static boolean bool(JsonNode node, String field, boolean defaultValue) {
        if (node == null) return defaultValue;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asBoolean();
    }

    /**
     * Returns an array field as a list of nodes, or an empty list when absent.
     * A scalar value is returned as a single-element list, matching the Claude
     * convention where a one-element list may be written bare.
     * @param node the object node.
     * @param field the field name.
     * @return the list of element nodes.
     */
    static java.util.List<JsonNode> array(JsonNode node, String field) {
        if (node == null) return java.util.List.of();
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return java.util.List.of();
        if (value.isArray()) {
            var list = new java.util.ArrayList<JsonNode>();
            value.forEach(list::add);
            return list;
        }
        return java.util.List.of(value);
    }

    /**
     * Returns an array field of strings, or an empty list when absent.
     * @param node the object node.
     * @param field the field name.
     * @return the list of strings.
     */
    static java.util.List<String> strings(JsonNode node, String field) {
        var list = new java.util.ArrayList<String>();
        for (JsonNode element : array(node, field)) {
            if (!element.isNull()) list.add(element.asString());
        }
        return list;
    }

    /**
     * Creates an empty object node for serialization.
     * @return a new object node.
     */
    static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    /**
     * Writes a node to a file with UTF-8 and a trailing newline, creating parent
     * directories as needed. The newline makes the file diff-friendly.
     * @param node the node to write.
     * @param path the target file.
     * @throws IOException if the file cannot be written.
     */
    static void write(JsonNode node, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node) + "\n");
    }
}
