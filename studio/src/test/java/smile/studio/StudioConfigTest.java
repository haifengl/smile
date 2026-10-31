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
package smile.studio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import photos.sluice.secrets.SecretStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StudioConfig}.
 *
 * @author Haifeng Li
 */
public class StudioConfigTest {

    @Test
    public void testParseFullConfig(@TempDir Path dir) throws IOException {
        // Given
        Path file = dir.resolve("studio.json");
        Files.writeString(file, """
                {
                  "inferenceServer": {
                    "autoStart": true,
                    "host": "0.0.0.0",
                    "port": 9090,
                    "modelPath": "/models"
                  }
                }
                """);

        // When
        var config = StudioConfig.parse(file);

        // Then
        assertTrue(config.autoStart());
        assertEquals("0.0.0.0", config.host());
        assertEquals(9090, config.port());
        assertEquals("/models", config.modelPath());
    }

    @Test
    public void testParseAppliesDefaults(@TempDir Path dir) throws IOException {
        // Given: only autoStart is specified.
        Path file = dir.resolve("studio.json");
        Files.writeString(file, "{\"inferenceServer\":{\"autoStart\":true}}");

        // When
        var config = StudioConfig.parse(file);

        // Then
        assertTrue(config.autoStart());
        assertEquals(StudioConfig.DEFAULT_HOST, config.host());
        assertEquals(StudioConfig.DEFAULT_PORT, config.port());
        assertEquals(StudioConfig.DEFAULT_MODEL_PATH, config.modelPath());
    }

    @Test
    public void testBlankModelPathFallsBackToDefault(@TempDir Path dir) throws IOException {
        // Given: modelPath present but blank (the shipped file used to ship "").
        Path file = dir.resolve("studio.json");
        Files.writeString(file, "{\"inferenceServer\":{\"modelPath\":\"\"}}");

        // When
        var config = StudioConfig.parse(file);

        // Then
        assertEquals(StudioConfig.DEFAULT_MODEL_PATH, config.modelPath());
    }

    @Test
    public void testParseWithoutInferenceServer(@TempDir Path dir) throws IOException {
        // Given
        Path file = dir.resolve("studio.json");
        Files.writeString(file, "{}");

        // When
        var config = StudioConfig.parse(file);

        // Then
        assertFalse(config.autoStart());
        assertEquals(StudioConfig.DEFAULT_HOST, config.host());
        assertEquals(StudioConfig.DEFAULT_PORT, config.port());
    }

    @Test
    public void testDefaults() {
        // Then: the built-in defaults are loopback, 8888, and ./model.
        assertEquals("localhost", StudioConfig.DEFAULT_HOST);
        assertEquals(8888, StudioConfig.DEFAULT_PORT);
        assertEquals("./model", StudioConfig.DEFAULT_MODEL_PATH);
        assertFalse(StudioConfig.DEFAULT_INFERENCE_SERVER.autoStart());
        assertEquals(StudioConfig.DEFAULT_MODEL_PATH,
                StudioConfig.DEFAULT_INFERENCE_SERVER.modelPath());
        assertTrue(StudioConfig.DEFAULT_AGENT_HUB.autoStart());
    }

    @Test
    public void testResolveDoesNotConsultWorkingDirectory(@TempDir Path dir) throws IOException {
        // Given: a studio.json in the working directory only.
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path local = cwd.resolve(".smile").resolve("studio.json");
        boolean preexisting = Files.exists(local);
        if (preexisting) {
            // Do not disturb an existing user file; the assertion below still holds.
            return;
        }
        Files.createDirectories(local.getParent());
        Files.writeString(local, "{\"inferenceServer\":{\"port\":1234}}");
        try {
            // When
            Path resolved = StudioConfig.resolve();

            // Then: the project-local file is not returned.
            assertTrue(resolved == null || !resolved.equals(local),
                    "studio.json must not be read from the working directory");
        } finally {
            Files.deleteIfExists(local);
        }
    }

    @Test
    public void testParsePluginsMarketplaces(@TempDir Path dir) throws IOException {
        // Given: a studio.json naming marketplaces to seed.
        Path file = dir.resolve("studio.json");
        Files.writeString(file, """
                {
                  "plugins": {
                    "marketplaces": [ "anthropics/claude-plugins-official", "  acme/plugins  " ]
                  }
                }
                """);

        // When
        var plugins = StudioConfig.parsePlugins(file);

        // Then: entries are trimmed and matched, and anything else is not listed.
        assertTrue(plugins.allows("anthropics/claude-plugins-official"));
        assertTrue(plugins.allows("acme/plugins"));
        assertFalse(plugins.allows("evil/market"));
        assertFalse(plugins.allows(null));
    }

    @Test
    public void testParsePluginsWithoutKey(@TempDir Path dir) throws IOException {
        // Given: a studio.json with no plugins object.
        Path file = dir.resolve("studio.json");
        Files.writeString(file, "{\"inferenceServer\":{\"port\":8888}}");

        // When
        var plugins = StudioConfig.parsePlugins(file);

        // Then: the default policy names no marketplaces (the built-in default is
        // used by the seed logic, not by this config record).
        assertTrue(plugins.marketplaces().isEmpty());
        assertFalse(plugins.allows("anthropics/claude-plugins-official"));
    }

    @Test
    public void testDefaultPluginsPolicy() {
        // Then
        assertTrue(StudioConfig.DEFAULT_PLUGINS.marketplaces().isEmpty());
        assertFalse(StudioConfig.DEFAULT_PLUGINS.allows("anything"));
    }

    private SecretStore newTestSecretStore(Path dir) {
        return SecretStore.forApplication("SmileStudioConfigTest")
                .inNamespace("ai.smile.studio.config.test")
                .onOperatingSystem("none")
                .withCredentialFilesIn(dir.resolve("credentials"))
                .open();
    }

    @Test
    public void testAgentHubAdminTokenDefaultsAndStoresInSecretStore(@TempDir Path dir) throws IOException {
        // Given: a studio.json without adminToken and an empty SecretStore
        SecretStore secrets = newTestSecretStore(dir);
        Path file = dir.resolve("studio.json");
        Files.writeString(file, """
                {
                  "agentHub": {
                    "autoStart": true,
                    "host": "localhost",
                    "port": 8890,
                    "keepAliveOnExit": false
                  }
                }
                """);

        // When: parsing without a stored secret
        var initialHub = StudioConfig.parseAgentHub(file, secrets);

        // Then: falls back to DEFAULT_HUB_ADMIN_TOKEN ("dev-admin-token")
        assertTrue(initialHub.autoStart());
        assertEquals("localhost", initialHub.host());
        assertEquals(8890, initialHub.port());
        assertEquals(StudioConfig.DEFAULT_HUB_ADMIN_TOKEN, initialHub.adminToken());
        assertFalse(initialHub.keepAliveOnExit());
        assertEquals(Optional.empty(), StudioConfig.readAdminTokenSecret(secrets));

        // When: saving updated InferenceServer and AgentHub with a custom adminToken
        var updatedServe = new StudioConfig.InferenceServer(true, "127.0.0.1", 8889, "./custom-models");
        var updatedHub = new StudioConfig.AgentHub(false, "10.0.0.5", 9900, "prod-secret-token-xyz", true);
        StudioConfig.save(file, updatedServe, updatedHub, secrets);

        // Then: studio.json does NOT contain adminToken in plaintext, and SecretStore holds it
        String jsonText = Files.readString(file);
        assertFalse(jsonText.contains("prod-secret-token-xyz"),
                "studio.json must never store adminToken in plaintext");
        assertFalse(jsonText.contains("adminToken"),
                "studio.json must omit the adminToken field");
        assertEquals(Optional.of("prod-secret-token-xyz"), StudioConfig.readAdminTokenSecret(secrets));

        // And re-parsing reads both updated configs and resolves the secret token
        var reloadedServe = StudioConfig.parse(file);
        assertTrue(reloadedServe.autoStart());
        assertEquals("127.0.0.1", reloadedServe.host());
        assertEquals(8889, reloadedServe.port());
        assertEquals("./custom-models", reloadedServe.modelPath());

        var reloadedHub = StudioConfig.parseAgentHub(file, secrets);
        assertFalse(reloadedHub.autoStart());
        assertEquals("10.0.0.5", reloadedHub.host());
        assertEquals(9900, reloadedHub.port());
        assertEquals("prod-secret-token-xyz", reloadedHub.adminToken());
        assertTrue(reloadedHub.keepAliveOnExit());
    }

    @Test
    public void testLegacyPlaintextAdminTokenMigratesToSecretStore(@TempDir Path dir) throws IOException {
        // Given: a legacy studio.json with a custom plaintext adminToken
        SecretStore secrets = newTestSecretStore(dir);
        Path file = dir.resolve("studio.json");
        Files.writeString(file, """
                {
                  "agentHub": {
                    "autoStart": true,
                    "host": "localhost",
                    "port": 8890,
                    "adminToken": "legacy-custom-token",
                    "keepAliveOnExit": true
                  }
                }
                """);

        // When: parsing with SecretStore
        var hub = StudioConfig.parseAgentHub(file, secrets);

        // Then: token is returned and migrated into SecretStore
        assertEquals("legacy-custom-token", hub.adminToken());
        assertEquals(Optional.of("legacy-custom-token"), StudioConfig.readAdminTokenSecret(secrets));
    }
}
