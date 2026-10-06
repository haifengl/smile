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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the plugin state store's scope precedence and MCP opt-in resolution
 * (the ADR-008 hybrid: per-server override, else per-plugin default, else off).
 *
 * @author Haifeng Li
 */
public class PluginStateStoreTest {

    @Test
    public void testEnablePrecedence(@TempDir Path cwd, @TempDir Path home) throws IOException {
        // Given: a plugin disabled at user scope but enabled at project scope.
        var store = new PluginStateStore(cwd, home);
        var id = new PluginId("p", "m");
        store.setEnabled(id, false, PluginScope.USER);
        store.setEnabled(id, true, PluginScope.PROJECT);

        // Then: the more specific project scope wins.
        assertTrue(store.isEnabled(id));
        assertTrue(store.enabledPlugins().contains(id));
    }

    @Test
    public void testLocalOverridesProject(@TempDir Path cwd, @TempDir Path home) throws IOException {
        // Given
        var store = new PluginStateStore(cwd, home);
        var id = new PluginId("p", "m");
        store.setEnabled(id, true, PluginScope.PROJECT);
        store.setEnabled(id, false, PluginScope.LOCAL);

        // Then
        assertFalse(store.isEnabled(id));
    }

    @Test
    public void testMcpDefaultsOff(@TempDir Path cwd, @TempDir Path home) {
        // Then: with no state, no server is opted in.
        var store = new PluginStateStore(cwd, home);
        assertFalse(store.mcpEnabled(new PluginId("p", "m"), "db"));
    }

    @Test
    public void testMcpPerServerOverrideWinsOverPluginDefault(@TempDir Path cwd, @TempDir Path home)
            throws IOException {
        // Given: plugin default on, but one server explicitly off.
        var store = new PluginStateStore(cwd, home);
        var id = new PluginId("p", "m");
        store.setMcpEnabled(id, true, PluginScope.USER);
        store.setMcpServerEnabled(id, "heavy", false, PluginScope.USER);

        // Then
        assertTrue(store.mcpEnabled(id, "db"));
        assertFalse(store.mcpEnabled(id, "heavy"));
    }

    @Test
    public void testMcpProjectDefaultOverridesUserDefault(@TempDir Path cwd, @TempDir Path home)
            throws IOException {
        // Given: user default on, project default off.
        var store = new PluginStateStore(cwd, home);
        var id = new PluginId("p", "m");
        store.setMcpEnabled(id, true, PluginScope.USER);
        store.setMcpEnabled(id, false, PluginScope.PROJECT);

        // Then: the project default wins for a server with no override.
        assertFalse(store.mcpEnabled(id, "db"));
    }

    @Test
    public void testPreservesUnknownSettingsKeys(@TempDir Path cwd, @TempDir Path home) throws IOException {
        // Given: a settings file with an unrelated key.
        Path file = cwd.resolve(".smile/plugins.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ \"other\": { \"keep\": true } }");

        // When
        var store = new PluginStateStore(cwd, home);
        store.setEnabled(new PluginId("p", "m"), true, PluginScope.PROJECT);

        // Then: the unknown key survives.
        String content = Files.readString(file);
        assertTrue(content.contains("\"other\""));
        assertTrue(content.contains("enabledPlugins"));
    }
}
