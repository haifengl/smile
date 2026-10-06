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
import smile.studio.StudioConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        return writeMarketplace(root, "market", "test-market");
    }

    /** Writes a minimal local marketplace with one plugin under a named directory. */
    private static Path writeMarketplace(Path root, String dir, String name) throws IOException {
        Path marketplace = root.resolve(dir);
        Path claude = marketplace.resolve(".claude-plugin");
        Files.createDirectories(claude);
        Files.writeString(claude.resolve("marketplace.json"), """
                { "name": "%s", "owner": { "name": "T" },
                  "plugins": [ { "name": "hello", "source": "./plugins/hello" } ] }
                """.formatted(name));
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

    @Test
    public void testSeedsKnownMarketplaceOnFirstOpen(@TempDir Path root) throws IOException {
        // Given: a local marketplace serving as the "known" seed.
        Path marketplace = writeMarketplace(root, "seed", "seed-market");
        PluginHome home = new PluginHome(root.resolve("home"));
        PluginService service = new PluginService(home, root.resolve("project"),
                root.resolve("state-home"), new StudioConfig.Plugins(List.of(marketplace.toString())),
                List.of(new PluginDefaults.Known(marketplace.toString(), "seed-market")));

        // When: the panel seeds on first open.
        String seeded = service.ensureDefaultMarketplaces();

        // Then: the marketplace is registered and the catalog is populated.
        assertTrue(seeded.contains("seed-market"), seeded);
        assertTrue(service.marketplaces().stream().anyMatch(m -> m.name().equals("seed-market")));
        assertTrue(service.catalog().stream().anyMatch(c -> c.entry().name().equals("hello")));
        assertTrue(Files.exists(home.seededMarker()));

        // When: the panel opens again.
        String again = service.ensureDefaultMarketplaces();

        // Then: seeding is one-time and adds nothing.
        assertTrue(again.isEmpty(), again);
    }

    @Test
    public void testSeedIsIdempotentWhenAlreadyRegistered(@TempDir Path root) throws IOException {
        // Given: the seed marketplace is already registered by the user.
        Path marketplace = writeMarketplace(root, "seed", "seed-market");
        PluginHome home = new PluginHome(root.resolve("home"));
        PluginService service = new PluginService(home, root.resolve("project"),
                root.resolve("state-home"), new StudioConfig.Plugins(List.of(marketplace.toString())),
                List.of(new PluginDefaults.Known(marketplace.toString(), "seed-market")));
        service.addMarketplace(marketplace.toString());

        // When
        String seeded = service.ensureDefaultMarketplaces();

        // Then: nothing is added, but the marker is still written.
        assertTrue(seeded.isEmpty(), seeded);
        assertEquals(1, service.marketplaces().size());
        assertTrue(Files.exists(home.seededMarker()));
    }

    @Test
    public void testConfiguredMarketplaceIsSeeded(@TempDir Path root) throws IOException {
        // Given: studio.json (the policy) names a local marketplace to seed, and the
        // seeds are derived from the policy exactly as production does.
        Path marketplace = writeMarketplace(root, "seed", "seed-market");
        PluginHome home = new PluginHome(root.resolve("home"));
        PluginService service = new PluginService(home, root.resolve("project"),
                root.resolve("state-home"),
                new StudioConfig.Plugins(List.of(marketplace.toString())));

        // When
        String seeded = service.ensureDefaultMarketplaces();

        // Then
        assertTrue(seeded.contains("seed-market"), seeded);
        assertTrue(service.catalog().stream().anyMatch(c -> c.entry().name().equals("hello")));
        assertTrue(Files.exists(home.seededMarker()));
    }

    @Test
    public void testImplicitAddRefusedWhenNotTrusted(@TempDir Path root) throws IOException {
        // Given: a marketplace that is neither a known default nor allowlisted.
        Path marketplace = writeMarketplace(root, "other", "other-market");
        PluginHome home = new PluginHome(root.resolve("home"));
        MarketplaceRegistry registry = new MarketplaceRegistry(home, StudioConfig.DEFAULT_PLUGINS);

        // When/Then: an implicit add is refused before any fetch.
        assertThrows(IOException.class, () -> registry.addImplicit(marketplace.toString()));
        assertTrue(registry.list().isEmpty());

        // When: the explicit path is used (the user typed it).
        registry.add(marketplace.toString());

        // Then: it succeeds.
        assertEquals(1, registry.list().size());
    }

    @Test
    public void testImplicitAddAllowedByConfiguredPolicy(@TempDir Path root) throws IOException {
        // Given: a policy that names the source.
        Path marketplace = writeMarketplace(root, "trusted", "trusted-market");
        PluginHome home = new PluginHome(root.resolve("home"));
        MarketplaceRegistry registry = new MarketplaceRegistry(home,
                new StudioConfig.Plugins(List.of(marketplace.toString())));

        // When
        registry.addImplicit(marketplace.toString());

        // Then
        assertTrue(registry.list().stream().anyMatch(m -> m.name().equals("trusted-market")));
    }

    @Test
    public void testBuiltInDefaultIsSeedableAndTrusted() {
        // Then: an empty config seeds the official Anthropic marketplace, and that
        // source is trusted for an implicit add.
        var seeds = PluginDefaults.seeds(List.of());
        assertFalse(seeds.isEmpty());
        assertEquals("anthropics/claude-plugins-official", seeds.getFirst().source());
        assertEquals("claude-plugins-official", seeds.getFirst().name());
        assertTrue(PluginDefaults.isTrustedSource(seeds.getFirst().source(), List.of()));
    }

    @Test
    public void testConfiguredSeedsOverrideBuiltInDefault() {
        // When: a config names its own marketplaces.
        var seeds = PluginDefaults.seeds(List.of("acme/plugins"));

        // Then: only the configured list is seeded, and it is trusted.
        assertEquals(1, seeds.size());
        assertEquals("acme/plugins", seeds.getFirst().source());
        assertEquals("plugins", seeds.getFirst().name());
        assertTrue(PluginDefaults.isTrustedSource("acme/plugins", List.of("acme/plugins")));
    }
}
