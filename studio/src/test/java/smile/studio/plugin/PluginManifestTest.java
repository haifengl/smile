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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for parsing the Claude Code plugin manifests.
 *
 * @author Haifeng Li
 */
public class PluginManifestTest {

    @Test
    public void testParseMarketplace(@TempDir Path dir) throws IOException {
        // Given: a marketplace with two plugins and a malformed third entry.
        Path claude = dir.resolve(".claude-plugin");
        Files.createDirectories(claude);
        Files.writeString(claude.resolve("marketplace.json"), """
                {
                  "name": "my-marketplace",
                  "owner": { "name": "Team" },
                  "plugins": [
                    { "name": "formatter", "source": "./plugins/formatter",
                      "description": "Formats code", "category": "dev" },
                    { "name": "gh", "source": { "source": "github", "repo": "org/gh", "ref": "v1" } },
                    { "source": "./no-name" }
                  ]
                }
                """);

        // When
        MarketplaceManifest manifest = MarketplaceManifest.from(dir);

        // Then
        assertEquals("my-marketplace", manifest.name());
        assertEquals("Team", manifest.owner().name());
        assertEquals(2, manifest.plugins().size());
        assertEquals(1, manifest.errors().size());
        assertEquals("formatter", manifest.plugins().getFirst().name());
        assertTrue(manifest.plugins().getFirst().source() instanceof PluginSource.RelativePath);
        assertTrue(manifest.plugins().get(1).source() instanceof PluginSource.Github);
    }

    @Test
    public void testParseCommandSourceIsRecognizedNotUnknown() {
        // Given: a command source entry.
        var node = Json.MAPPER.readTree("""
                { "source": "command", "command": "my-tool plugin-path", "timeout": 30 }
                """);

        // When
        PluginSource source = PluginSource.parse(node);

        // Then: it parses into the Command type and is flagged as refused.
        assertTrue(source instanceof PluginSource.Command);
        assertTrue(source.isRefused());
    }

    @Test
    public void testUnknownSourceTypeIsRejected() {
        // Given
        var node = Json.MAPPER.readTree("{ \"source\": \"carrier-pigeon\" }");

        // Then
        assertThrows(IllegalArgumentException.class, () -> PluginSource.parse(node));
    }

    @Test
    public void testAbsentPluginManifestFallsBackToEntryName(@TempDir Path dir) throws IOException {
        // When: there is no .claude-plugin/plugin.json.
        PluginManifest manifest = PluginManifest.from(dir, "fallback");

        // Then
        assertFalse(manifest.present());
        assertEquals("fallback", manifest.name());
    }

    @Test
    public void testPluginManifestReadsComponents(@TempDir Path dir) throws IOException {
        // Given
        Path claude = dir.resolve(".claude-plugin");
        Files.createDirectories(claude);
        Files.writeString(claude.resolve("plugin.json"), """
                { "name": "p", "version": "1.2.3", "hooks": { "PostToolUse": [] },
                  "description": "d" }
                """);

        // When
        PluginManifest manifest = PluginManifest.from(dir, "p");

        // Then
        assertTrue(manifest.present());
        assertEquals("1.2.3", manifest.version());
        assertTrue(manifest.declares("hooks"));
        assertFalse(manifest.declares("themes"));
    }
}
