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

import tools.jackson.databind.JsonNode;

/**
 * Where a plugin is fetched from: the {@code source} of one marketplace entry.
 * The variants mirror the Claude Code marketplace reference.
 *
 * <p>A source is either a string — a relative path inside the marketplace — or an
 * object whose {@code source} key names the type. Everything except a relative path
 * needs a remote fetch step.
 *
 * <p>{@link Command} is parsed but <b>never fetched</b>: it is the one source type
 * that runs arbitrary code on the user's machine at install, update, and every
 * session, so Studio refuses it outright (ADR-008). It is modelled here (rather than
 * treated as an unknown type) so the refusal can be reported precisely instead of
 * surfacing as a generic parse failure.
 *
 * @author Haifeng Li
 */
public sealed interface PluginSource {

    /** A directory inside the marketplace, resolved from the marketplace root. */
    record RelativePath(String path) implements PluginSource { }

    /** A GitHub repository in {@code owner/repo} form. */
    record Github(String repo, String ref, String sha) implements PluginSource { }

    /** Any git repository reachable by URL. */
    record Url(String url, String ref, String sha) implements PluginSource { }

    /** One subdirectory of a git repository, fetched with a sparse checkout. */
    record GitSubdir(String url, String path, String ref, String sha) implements PluginSource { }

    /** An npm package. */
    record Npm(String packageName, String version, String registry) implements PluginSource { }

    /** A zip archive over HTTPS, optionally pinned by SHA-256. */
    record Archive(String url, String sha256) implements PluginSource { }

    /**
     * A command that prints a plugin directory. <b>Refused by policy (ADR-008)</b>;
     * never executed.
     */
    record Command(String command, int timeout, String mode) implements PluginSource { }

    /**
     * Parses a marketplace entry's {@code source}.
     *
     * @param node the {@code source} value: a string or an object.
     * @return the parsed source.
     * @throws IllegalArgumentException if the source matches no known type.
     */
    static PluginSource parse(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Plugin entry is missing 'source'");
        }
        if (node.isString()) {
            return new RelativePath(node.asString());
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Plugin 'source' must be a string or object");
        }

        String type = Json.text(node, "source");
        if (type == null) {
            throw new IllegalArgumentException("Plugin source object is missing its 'source' type");
        }
        return switch (type) {
            case "github" -> new Github(
                    require(node, "repo"), Json.text(node, "ref"), Json.text(node, "sha"));
            case "url" -> new Url(
                    require(node, "url"), Json.text(node, "ref"), Json.text(node, "sha"));
            case "git-subdir" -> new GitSubdir(
                    require(node, "url"), require(node, "path"), Json.text(node, "ref"), Json.text(node, "sha"));
            case "npm" -> new Npm(
                    require(node, "package"), Json.text(node, "version"), Json.text(node, "registry"));
            case "archive" -> new Archive(
                    require(node, "url"), Json.text(node, "sha256"));
            case "command" -> new Command(
                    require(node, "command"),
                    node.has("timeout") ? node.get("timeout").asInt() : 60,
                    Json.text(node, "mode", "copy"));
            default -> throw new IllegalArgumentException("Unsupported plugin source type: " + type);
        };
    }

    /**
     * Returns true for the one source type Studio refuses to execute.
     * @return true when this source is a {@link Command}.
     */
    default boolean isRefused() {
        return this instanceof Command;
    }

    /**
     * A short label for the UI and logs.
     * @return the source type name.
     */
    default String typeName() {
        return switch (this) {
            case RelativePath ignored -> "path";
            case Github ignored -> "github";
            case Url ignored -> "url";
            case GitSubdir ignored -> "git-subdir";
            case Npm ignored -> "npm";
            case Archive ignored -> "archive";
            case Command ignored -> "command";
        };
    }

    private static String require(JsonNode node, String field) {
        String value = Json.text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Plugin source is missing required field: " + field);
        }
        return value;
    }
}
