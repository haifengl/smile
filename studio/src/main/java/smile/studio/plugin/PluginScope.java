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
 * The install scope of a plugin, mirroring Claude Code's three scopes but written
 * to {@code .smile} settings files rather than {@code .claude} ones.
 *
 * <p>Precedence is most-specific-first: {@code local} overrides {@code project},
 * which overrides {@code user}. {@link #ordered()} returns them in that order so a
 * resolver can take the first match.
 *
 * @author Haifeng Li
 */
public enum PluginScope {
    /** Enabled for the current user in this project only. */
    LOCAL("plugins.local.json", 0),
    /** Enabled for everyone who works in this repository. */
    PROJECT("plugins.json", 1),
    /** Enabled for the current user in every project. */
    USER("plugins.json", 2);

    private final String fileName;
    private final int precedence;

    PluginScope(String fileName, int precedence) {
        this.fileName = fileName;
        this.precedence = precedence;
    }

    /**
     * Returns the state file this scope writes to.
     *
     * <p>The files are named {@code plugins.json} / {@code plugins.local.json} rather
     * than {@code settings.json}, because Studio's settings are already spread across
     * {@code studio.json} (inference server), {@code studio.properties} (open tabs),
     * and Java {@code Preferences} (API keys). Dedicated plugin-state files keep the
     * plugin feature self-contained and remove any ambiguity about where a future
     * Studio setting belongs.
     *
     * @param cwd the project working directory.
     * @param userHome the user's home directory, for the {@link #USER} scope.
     * @return the state file path.
     */
    public Path settingsFile(Path cwd, Path userHome) {
        return switch (this) {
            case LOCAL -> cwd.resolve(".smile").resolve(fileName);
            case PROJECT -> cwd.resolve(".smile").resolve(fileName);
            case USER -> userHome.resolve(".smile").resolve(fileName);
        };
    }

    /**
     * Returns the scopes in precedence order, most specific first.
     * @return local, project, user.
     */
    public static PluginScope[] ordered() {
        return new PluginScope[] { LOCAL, PROJECT, USER };
    }

    /**
     * Returns the precedence rank; a smaller number wins.
     * @return the precedence rank.
     */
    public int precedence() {
        return precedence;
    }
}
