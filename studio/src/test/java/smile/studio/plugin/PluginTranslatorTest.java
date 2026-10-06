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
import ioa.agent.memory.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the anti-corruption layer: a Claude-layout plugin is materialized into an
 * {@code ioa}-native tree, and every component gets an honest disposition.
 *
 * @author Haifeng Li
 */
public class PluginTranslatorTest {

    @Test
    public void testTranslateFullPlugin(@TempDir Path dir) throws IOException {
        // Given: a plugin with an agent, a skill, a command, MCP, and a hook.
        Path source = dir.resolve("source");
        writeAgent(source.resolve("agents/security-reviewer.md"), "security-reviewer",
                "Reviews code for security issues.",
                "model: sonnet\ntools: [Read, Bash(git:*)]\ndisallowedTools: [Write]\n");
        writeSkill(source.resolve("skills/review/SKILL.md"), "review",
                "Reviews a pull request.", null);
        Files.createDirectories(source.resolve("commands/db"));
        Files.writeString(source.resolve("commands/db/migrate.md"), """
                ---
                description: Migrates the database.
                ---
                Run the migration.
                """);
        Files.writeString(source.resolve(".mcp.json"), """
                { "mcpServers": {
                    "db": { "type": "stdio", "command": "node",
                            "args": ["${CLAUDE_PLUGIN_ROOT}/server.js"] } } }
                """);
        Path claude = source.resolve(".claude-plugin");
        Files.createDirectories(claude);
        Files.writeString(claude.resolve("plugin.json"),
                "{ \"name\": \"p\", \"hooks\": { \"PostToolUse\": [] } }");
        PluginManifest manifest = PluginManifest.from(source, "p");

        PluginId id = new PluginId("p", "m");
        Path target = dir.resolve("target");

        // When
        TranslatedPlugin translated = new PluginTranslator()
                .translate(id, source, target, dir, manifest);

        // Then: the agent is materialized as a directory AGENT.md and is degraded.
        assertEquals(1, translated.agents().size());
        assertEquals("p--security-reviewer", translated.agents().getFirst().name());
        Path agentFile = target.resolve("agents/p--security-reviewer/AGENT.md");
        assertTrue(Files.isRegularFile(agentFile));
        String agentText = Files.readString(agentFile);
        assertTrue(agentText.contains("name: 'p--security-reviewer'"));
        // Bash was mapped to the host's shell tool.
        assertTrue(agentText.contains("PowerShell") || agentText.contains("Bash"));
        assertTrue(translated.agents().getFirst().degraded());

        // The skill and the command both became ioa skills.
        assertTrue(translated.skills().stream().anyMatch(s -> s.name().equals("p--review")));
        assertTrue(translated.skills().stream().anyMatch(s -> s.name().equals("p--db--migrate")));

        // MCP server renamed and path variable rewritten.
        assertEquals(1, translated.mcp().size());
        assertEquals("p--db", translated.mcp().getFirst().name());
        String args = translated.mcp().getFirst().config().get("args").get(0).asString();
        assertFalse(args.contains("${CLAUDE_PLUGIN_ROOT}"));
        assertTrue(args.contains("/server.js"));

        // The hook is dropped with a reason, and nothing was silent.
        assertTrue(translated.hasDrops());
        assertTrue(translated.dispositions().stream()
                .anyMatch(d -> d.kind().equals("hooks") && d.isDropped()));
    }

    @Test
    public void testTranslatedSkillLoadsAsIoaSkill(@TempDir Path dir) throws IOException {
        // Given: a plugin with one skill.
        Path source = dir.resolve("source");
        writeSkill(source.resolve("skills/hello/SKILL.md"), "hello",
                "Says hello.", "argument-hint: '[name]'\n");
        PluginManifest manifest = PluginManifest.from(source, "p");

        // When
        TranslatedPlugin translated = new PluginTranslator()
                .translate(new PluginId("p", "m"), source, dir.resolve("target"), dir, manifest);

        // Then: ioa can read the produced SKILL.md back.
        Path skillDir = dir.resolve("target/skills/p--hello");
        Skill skill = Skill.of(skillDir);
        assertEquals("p--hello", skill.name());
        assertEquals("Says hello.", skill.description());
        assertTrue(skill.hint().isPresent());
    }

    @Test
    public void testUserConfigServerIsDropped(@TempDir Path dir) throws IOException {
        // Given: an MCP server that needs a user_config value Studio cannot supply.
        Path source = dir.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve(".mcp.json"), """
                { "mcpServers": {
                    "remote": { "type": "http", "url": "https://x/mcp",
                                "headers": { "Authorization": "Bearer ${user_config.token}" } } } }
                """);
        PluginManifest manifest = PluginManifest.from(source, "p");

        // When
        TranslatedPlugin translated = new PluginTranslator()
                .translate(new PluginId("p", "m"), source, dir.resolve("target"), dir, manifest);

        // Then
        assertTrue(translated.mcp().isEmpty());
        assertTrue(translated.dispositions().stream()
                .anyMatch(d -> d.kind().equals("mcpServers") && d.isDropped()));
    }

    /** Writes an agent file with the given frontmatter. */
    private static void writeAgent(Path file, String name, String description, String extra)
            throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "---\nname: " + name + "\ndescription: " + description + "\n"
                + extra + "---\nYou are a security reviewer.\n");
    }

    /** Writes a skill file with optional frontmatter fields. */
    private static void writeSkill(Path file, String name, String description, String extra)
            throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "---\nname: " + name + "\ndescription: " + description + "\n"
                + (extra == null ? "" : extra) + "---\nReview the changed files.\n");
    }
}
