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

/**
 * The on-disk layout plugin state lives under, rooted at {@code ~/.smile/plugins}.
 *
 * <pre>
 * ~/.smile/plugins/
 *   marketplaces.json                       registered marketplaces
 *   cache/                                  downloaded marketplaces and archives
 *   installed/&lt;marketplace&gt;--&lt;name&gt;/
 *     current                               a file naming the active version
 *     &lt;version&gt;/                            the translated, ioa-native tree
 * </pre>
 *
 * <p>Centralizing the layout here means the installer, loader, and panel never
 * disagree about where a plugin's content is.
 *
 * @author Haifeng Li
 */
final class PluginHome {
    /** The user's home directory property. */
    private static final String USER_HOME = "user.home";

    private final Path root;

    /**
     * Constructor for the default location, {@code ~/.smile/plugins}.
     */
    PluginHome() {
        this(Path.of(System.getProperty(USER_HOME), ".smile", "plugins"));
    }

    /**
     * Constructor.
     * @param root the plugins root directory.
     */
    PluginHome(Path root) {
        this.root = root;
    }

    /** @return the plugins root. */
    Path root() {
        return root;
    }

    /** @return the download cache directory. */
    Path cache() {
        return root.resolve("cache");
    }

    /** @return the registered-marketplaces file. */
    Path marketplacesFile() {
        return root.resolve("marketplaces.json");
    }

    /** @return the directory holding every installed plugin. */
    Path installedRoot() {
        return root.resolve("installed");
    }

    /**
     * Returns the directory holding every version of one plugin.
     * @param id the plugin id.
     * @return the per-plugin install directory.
     */
    Path pluginDir(PluginId id) {
        return installedRoot().resolve(id.directory());
    }

    /**
     * Returns the directory one version of a plugin is installed into.
     * @param id the plugin id.
     * @param version the version directory name.
     * @return the version directory.
     */
    Path versionDir(PluginId id, String version) {
        return pluginDir(id).resolve(version);
    }

    /**
     * Returns the file naming the active version of a plugin.
     * @param id the plugin id.
     * @return the current-version marker file.
     */
    Path currentFile(PluginId id) {
        return pluginDir(id).resolve("current");
    }
}
