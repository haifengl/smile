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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code /plugin} text facade that both the slash command and the CLI
 * drive, exercising the exact path {@code AgentCLI.runSlashCommand} uses.
 *
 * @author Haifeng Li
 */
public class PluginServiceTest {

    @Test
    public void testEmptyShowsUsage() {
        // Given
        PluginService service = new PluginService(new PluginHome(Path.of("unused")), Path.of("."), Path.of("."));

        // Then
        assertTrue(service.execute(List.of()).contains("/plugin"));
    }

    @Test
    public void testMarketplaceAddInstallListUninstall(@TempDir Path root) throws IOException {
        // Given: a local marketplace and an isolated service.
        Path marketplace = writeMarketplace(root);
        Path cwd = root.resolve("project");
        Files.createDirectories(cwd);
        PluginService service = new PluginService(
                new PluginHome(root.resolve("home")), cwd, root.resolve("state-home"));

        // When: add the marketplace.
        String added = service.execute(List.of("marketplace", "add", marketplace.toString()));
        // Then
        assertTrue(added.contains("Added marketplace"), added);

        // When: install the plugin.
        String installed = service.execute(List.of("install", "hello@test-market"));
        // Then
        assertTrue(installed.contains("Installed"), installed);

        // When: list.
        String listed = service.execute(List.of("list"));
        // Then
        assertTrue(listed.contains("hello@test-market"), listed);

        // When: opt the MCP server in.
        String mcp = service.execute(List.of("mcp", "hello@test-market", "echo", "on"));
        // Then
        assertTrue(mcp.contains("Enabled"), mcp);

        // When: uninstall.
        String removed = service.execute(List.of("uninstall", "hello@test-market"));
        // Then
        assertTrue(removed.contains("Uninstalled"), removed);
        assertTrue(service.installed().isEmpty());
    }

    @Test
    public void testInstallUnknownMarketplaceFails(@TempDir Path root) throws IOException {
        // Given
        Path cwd = root.resolve("project");
        Files.createDirectories(cwd);
        PluginService service = new PluginService(
                new PluginHome(root.resolve("home")), cwd, root.resolve("state-home"));

        // When
        String result = service.execute(List.of("install", "nope@missing"));

        // Then
        assertTrue(result.startsWith("Error") || result.contains("not found"), result);
    }

    @Test
    public void testPanelOperations(@TempDir Path root) throws IOException {
        // Given
        Path marketplace = writeMarketplace(root);
        Path cwd = root.resolve("project");
        Files.createDirectories(cwd);
        PluginService service = new PluginService(
                new PluginHome(root.resolve("home")), cwd, root.resolve("state-home"));

        // When: the panel adds a marketplace through its own method.
        InstallResult added = service.addMarketplace(marketplace.toString());
        // Then
        assertTrue(added.success(), added.message());
        assertTrue(service.catalog().stream().anyMatch(c -> c.entry().name().equals("hello")));

        // When: install and set the per-plugin MCP default.
        service.install(new PluginId("hello", "test-market"), PluginScope.USER);
        InstallResult mcp = service.setMcpDefault(new PluginId("hello", "test-market"), true);
        // Then
        assertTrue(mcp.success(), mcp.message());
        assertTrue(service.state().mcpEnabled(new PluginId("hello", "test-market"), "echo"));

        // When: remove the marketplace.
        InstallResult removed = service.removeMarketplace("test-market");
        // Then
        assertTrue(removed.success(), removed.message());
        assertTrue(service.marketplaces().isEmpty());
    }

    /** Writes a minimal local marketplace with one plugin. */
    private static Path writeMarketplace(Path root) throws IOException {
        Path marketplace = root.resolve("market");
        Path claude = marketplace.resolve(".claude-plugin");
        Files.createDirectories(claude);
        Files.writeString(claude.resolve("marketplace.json"), """
                { "name": "test-market", "owner": { "name": "T" },
                  "plugins": [ { "name": "hello", "source": "./plugins/hello" } ] }
                """);
        Path plugin = marketplace.resolve("plugins/hello");
        Files.createDirectories(plugin.resolve(".claude-plugin"));
        Files.writeString(plugin.resolve(".claude-plugin/plugin.json"),
                "{ \"name\": \"hello\", \"version\": \"0.1.0\" }");
        Path skill = plugin.resolve("skills/hello");
        Files.createDirectories(skill);
        Files.writeString(skill.resolve("SKILL.md"),
                "---\nname: hello\ndescription: Says hello.\n---\nHi.\n");
        Files.writeString(plugin.resolve(".mcp.json"), """
                { "mcpServers": { "echo": { "type": "stdio", "command": "node", "args": ["s.js"] } } }
                """);
        return marketplace;
    }
}
