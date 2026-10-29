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
package smile.studio.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import ioa.agent.bot.BotsConfig;
import ioa.agent.bot.ProjectsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import smile.shell.Hub;
import smile.studio.StudioConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Unit 4 ({@link Hub} CLI, {@link StudioConfig#agentHub()},
 * {@link HubClient} offline fallback, and {@link ProjectsConfig} tracking).
 *
 * @author Haifeng Li
 */
public class HubClientAndConfigTest {

    @Test
    public void testHubCliDefaultAndCustomBuildCommand() {
        Hub hub = new Hub();
        new CommandLine(hub).parseArgs("-p", "8895", "--host", "127.0.0.1", "--admin-token", "secret-123");
        List<String> cmd = hub.buildCommand();
        assertTrue(cmd.contains("--add-opens=java.base/java.lang=ALL-UNNAMED"));
        assertTrue(cmd.contains("--enable-native-access=ALL-UNNAMED"));
        assertTrue(cmd.contains("-Dquarkus.profile=local"));
        assertTrue(cmd.contains("-Dquarkus.http.host=127.0.0.1"));
        assertTrue(cmd.contains("-Dquarkus.http.port=8895"));
        assertTrue(cmd.contains("-Dhub.admin.token=secret-123"));
        assertTrue(cmd.contains("-jar"));
    }

    @Test
    public void testStudioConfigAgentHubParsing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("studio.json");
        Files.writeString(file, """
                {
                  "agentHub": {
                    "autoStart": true,
                    "host": "127.0.0.1",
                    "port": 8892,
                    "adminToken": "my-token",
                    "keepAliveOnExit": true
                  }
                }
                """);
        var hubCfg = StudioConfig.parseAgentHub(file);
        assertTrue(hubCfg.autoStart());
        assertEquals("127.0.0.1", hubCfg.host());
        assertEquals(8892, hubCfg.port());
        assertEquals("my-token", hubCfg.adminToken());
        assertTrue(hubCfg.keepAliveOnExit());
    }

    @Test
    public void testHubClientOfflineFallbackReadsAndUpdatesBotsJson(@TempDir Path tempHome) throws Exception {
        String prevHome = System.getProperty("smile.home");
        try {
            System.setProperty("smile.home", tempHome.toString());
            // Use an unused port so HubClient operates in offline fallback mode
            HubClient client = new HubClient("127.0.0.1", 59999, "dev-admin-token");
            assertFalse(client.isReady());

            HubClient.BotsOverview overview = client.getOverview();
            assertNotNull(overview.config());
            assertEquals("chief-of-staff", overview.config().primaryBot());
            assertTrue(overview.config().findRoutine("frank-daily-recap").isPresent());

            // Toggle routine enabled offline -> persists to ${smile.home}/conf/bots.json
            HubClient.BotsOverview updated = client.setRoutineEnabled("julien", "julien-community-watch", false);
            assertFalse(updated.config().findRoutine("julien-community-watch").orElseThrow().enabled());
            assertTrue(Files.isRegularFile(tempHome.resolve("conf").resolve("bots.json")));

            // Record a project in projects.json
            Path projDir = tempHome.resolve("my-project");
            Files.createDirectories(projDir.resolve(".smile"));
            ProjectsConfig recorded = ProjectsConfig.registerProject(projDir);
            assertEquals(1, recorded.projects().size());
            assertEquals("my-project", recorded.projects().getFirst().name());
        } finally {
            if (prevHome == null) {
                System.clearProperty("smile.home");
            } else {
                System.setProperty("smile.home", prevHome);
            }
        }
    }

    @Test
    public void testBotTreeItemUsesAgentNativeTitleFromAgentMd() {
        BotsConfig cfg = BotsConfig.defaultConfig();
        List<String> titles = cfg.bots().stream()
                .map(b -> new BotExplorer.BotTreeItem(b).toString())
                .toList();
        assertTrue(titles.contains("★ Daily Memory Logger"));
        assertTrue(titles.contains("👔 Frank the Chief of Staff"));
        assertTrue(titles.contains("📣 Julien the Evangelist"));
        assertTrue(titles.contains("🤝 Mike the DealMaker"));
    }
}
