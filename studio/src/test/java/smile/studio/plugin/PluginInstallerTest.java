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
 * End-to-end install tests against a local marketplace: browse, install, enable,
 * disable, and uninstall — asserting that install writes the ioa-native tree,
 * that a {@code command} source is refused rather than run, and that a
 * non-opted-in MCP server is not connected by the loader.
 *
 * @author Haifeng Li
 */
public class PluginInstallerTest {

    @Test
    public void testInstallLocalPlugin(@TempDir Path root) throws IOException {
        // Given: a local marketplace with one plugin.
        Path marketplace = writeMarketplace(root);
        Path cwd = root.resolve("project");
        Files.createDirectories(cwd);

        PluginHome home = new PluginHome(root.resolve("home"));
        MarketplaceRegistry registry = new MarketplaceRegistry(home);
        registry.add(marketplace.toString());
        PluginStateStore state = new PluginStateStore(cwd, root.resolve("state-home"));
        PluginInstaller installer = new PluginInstaller(home, registry, state, cwd);

        // When
        InstallResult result = installer.install(new PluginId("hello", "test-market"), PluginScope.USER);

        // Then: the plugin installed, its content is present, and it is enabled.
        assertTrue(result.success(), result.message());
        Path skill = home.pluginDir(new PluginId("hello", "test-market"))
                .resolve("0.1.0/skills/hello--hello/SKILL.md");
        assertTrue(Files.isRegularFile(skill), "translated skill should exist at " + skill);
        assertTrue(state.isEnabled(new PluginId("hello", "test-market")));

        // The MCP server is disabled in the fragment until opted in.
        Path fragment = home.pluginDir(new PluginId("hello", "test-market")).resolve("0.1.0/mcp.json");
        String fragmentText = Files.readString(fragment);
        assertTrue(fragmentText.contains("\"disabled\""));
        assertTrue(fragmentText.contains("true"));
    }

    @Test
    public void testLoaderDoesNotConnectUnoptedInServers(@TempDir Path root) throws IOException {
        // Given: an installed plugin with one MCP server that is not opted in.
        Path marketplace = writeMarketplace(root);
        Path cwd = root.resolve("project");
        Files.createDirectories(cwd);
        PluginHome home = new PluginHome(root.resolve("home"));
        MarketplaceRegistry registry = new MarketplaceRegistry(home);
        registry.add(marketplace.toString());
        PluginStateStore state = new PluginStateStore(cwd, root.resolve("state-home"));
        PluginInstaller installer = new PluginInstaller(home, registry, state, cwd);
        installer.install(new PluginId("hello", "test-market"), PluginScope.USER);

        // When: bootstrap runs with the default opt-in (off).
        PluginLoader loader = new PluginLoader(home, state, installer.index(), cwd);
        loader.bootstrap();

        // Then: no MCP fragment is advertised for connection.
        assertTrue(loader.mcpFragments().isEmpty(),
                "a server that is not opted in must not be connected");
        // And the skill is loaded.
        assertTrue(loader.pluginSkills().stream().anyMatch(s -> s.name().equals("hello--hello")));
    }

    @Test
    public void testOptedInServerIsAdvertised(@TempDir Path root) throws IOException {
        // Given: the same plugin, with its MCP server opted in.
        Path marketplace = writeMarketplace(root);
        Path cwd = root.resolve("project");
        Files.createDirectories(cwd);
        PluginHome home = new PluginHome(root.resolve("home"));
        MarketplaceRegistry registry = new MarketplaceRegistry(home);
        registry.add(marketplace.toString());
        PluginStateStore state = new PluginStateStore(cwd, root.resolve("state-home"));
        PluginInstaller installer = new PluginInstaller(home, registry, state, cwd);
        PluginId id = new PluginId("hello", "test-market");
        installer.install(id, PluginScope.USER);
        state.setMcpServerEnabled(id, "echo", true, PluginScope.USER);

        // When
        PluginLoader loader = new PluginLoader(home, state, installer.index(), cwd);
        loader.bootstrap();

        // Then
        assertFalse(loader.mcpFragments().isEmpty(), "an opted-in server should produce a fragment");
        String effective = Files.readString(loader.mcpFragments().getFirst());
        assertTrue(effective.contains("\"disabled\" : false") || effective.contains("\"disabled\": false"));
    }

    @Test
    public void testCommandSourceIsRefusedNotRun(@TempDir Path root) throws IOException {
        // Given: a marketplace whose only plugin uses a command source.
        Path marketplace = root.resolve("market");
        Path claude = marketplace.resolve(".claude-plugin");
        Files.createDirectories(claude);
        Files.writeString(claude.resolve("marketplace.json"), """
                { "name": "test-market", "owner": { "name": "T" }, "plugins": [
                  { "name": "evil", "source": { "source": "command",
                    "command": "touch pwned" } } ] }
                """);
        Path cwd = root.resolve("project");
        Files.createDirectories(cwd);

        PluginHome home = new PluginHome(root.resolve("home"));
        MarketplaceRegistry registry = new MarketplaceRegistry(home);
        registry.add(marketplace.toString());
        PluginStateStore state = new PluginStateStore(cwd, root.resolve("state-home"));
        PluginInstaller installer = new PluginInstaller(home, registry, state, cwd);

        // When
        InstallResult result = installer.install(new PluginId("evil", "test-market"), PluginScope.USER);

        // Then: the install is refused, and no marker file was created.
        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("command"));
        assertFalse(Files.exists(root.resolve("pwned")));
        assertFalse(Files.exists(cwd.resolve("pwned")));
    }

    @Test
    public void testUninstallRemovesContent(@TempDir Path root) throws IOException {
        // Given
        Path marketplace = writeMarketplace(root);
        Path cwd = root.resolve("project");
        Files.createDirectories(cwd);
        PluginHome home = new PluginHome(root.resolve("home"));
        MarketplaceRegistry registry = new MarketplaceRegistry(home);
        registry.add(marketplace.toString());
        PluginStateStore state = new PluginStateStore(cwd, root.resolve("state-home"));
        PluginInstaller installer = new PluginInstaller(home, registry, state, cwd);
        PluginId id = new PluginId("hello", "test-market");
        installer.install(id, PluginScope.USER);

        // When
        InstallResult result = installer.uninstall(id);

        // Then
        assertTrue(result.success(), result.message());
        assertFalse(Files.exists(home.pluginDir(id)));
        assertFalse(state.isEnabled(id));
    }

    /** Writes a local marketplace holding one plugin with a skill and an MCP server. */
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
        Files.writeString(skill.resolve("SKILL.md"), """
                ---
                name: hello
                description: Says hello.
                ---
                Say hello to the user.
                """);
        Files.writeString(plugin.resolve(".mcp.json"), """
                { "mcpServers": {
                    "echo": { "type": "stdio", "command": "node", "args": ["server.js"] } } }
                """);
        return marketplace;
    }
}
