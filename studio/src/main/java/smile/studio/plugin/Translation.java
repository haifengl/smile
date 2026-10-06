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

/**
 * Shared state for one translation pass: the plugin identity, the source and
 * target roots, the directories path variables resolve to, and the running list of
 * dispositions.
 *
 * <p>Passing this to each component translator keeps them decoupled from each
 * other while still producing one combined account of what happened.
 *
 * @author Haifeng Li
 */
final class Translation {
    /** The plugin being translated. */
    final PluginId id;
    /** The fetched plugin directory (Claude layout). */
    final Path source;
    /** The directory the ioa-native tree is written to. */
    final Path target;
    /** The project working directory, for {@code ${CLAUDE_PROJECT_DIR}}. */
    final Path projectDir;
    /** The plugin's durable data directory, for {@code ${CLAUDE_PLUGIN_DATA}}. */
    final Path dataDir;
    /** The accumulated dispositions. */
    private final List<ComponentDisposition> dispositions = new ArrayList<>();

    /**
     * Constructor.
     * @param id the plugin id.
     * @param source the fetched source directory.
     * @param target the materialized tree root.
     * @param projectDir the project working directory.
     * @param dataDir the plugin's data directory.
     */
    Translation(PluginId id, Path source, Path target, Path projectDir, Path dataDir) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.projectDir = projectDir;
        this.dataDir = dataDir;
    }

    /**
     * Records a disposition.
     * @param disposition the disposition to record.
     */
    void record(ComponentDisposition disposition) {
        dispositions.add(disposition);
    }

    /** @return the accumulated dispositions. */
    List<ComponentDisposition> dispositions() {
        return dispositions;
    }

    /**
     * Rewrites Claude plugin path variables in a string to local absolute paths,
     * with forward slashes so the value is portable and shell-safe on Windows.
     *
     * <p>{@code ${CLAUDE_PLUGIN_ROOT}} becomes the installed plugin directory,
     * {@code ${CLAUDE_PLUGIN_DATA}} the durable data directory, and
     * {@code ${CLAUDE_PROJECT_DIR}} the project working directory.
     *
     * @param value the raw value.
     * @return the rewritten value.
     */
    String rewritePathVariables(String value) {
        if (value == null || value.indexOf('$') < 0) {
            return value;
        }
        return value
                .replace("${CLAUDE_PLUGIN_ROOT}", slashes(target))
                .replace("${CLAUDE_PLUGIN_DATA}", slashes(dataDir))
                .replace("${CLAUDE_PROJECT_DIR}", slashes(projectDir));
    }

    /**
     * Returns true when a value still references a {@code ${user_config.*}} option,
     * which Studio has no equivalent for.
     * @param value the value.
     * @return true when it references user configuration.
     */
    static boolean referencesUserConfig(String value) {
        return value != null && value.contains("${user_config.");
    }

    /** Renders a path with forward slashes. */
    static String slashes(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}
