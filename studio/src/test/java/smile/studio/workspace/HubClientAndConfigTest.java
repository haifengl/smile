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
import smile.studio.bot.BotActivity;

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

    @Test
    public void testParseTranscriptFormatsToolCallsAndAssistantTurns() {
        String jsonl = """
                {"role":"user","type":"TEXT","content":"<system-reminder>\\nCurrent UTC time: 2026-10-30T00:00:00Z\\n</system-reminder>\\nRun the daily recap for @frank","timestamp":"2026-10-30T02:21:41.692Z"}
                {"role":"tool","type":"TEXT","content":{"message":[{"arguments":{"path":"studio/src/main","pattern":"**/*Bot*"},"id":"call_1","name":"Glob","type":"function_call"},{"arguments":{"offset":0,"limit":2000,"file_path":"/workspace/BotActivity.java"},"id":"call_2","name":"Read","type":"function_call"}],"outputs":[{"id":"call_1","name":"Glob","output":"BotActivity.java","success":true},{"id":"call_2","name":"Read","output":"Error: File does not exist","success":false}]},"timestamp":"2026-10-30T02:21:55.000Z"}
                {"role":"tool","type":"TEXT","content":{"message":[{"arguments":{"todos":[{"status":"completed","active_form":"Reading logs","content":"Read daily logs"},{"status":"in_progress","active_form":"Writing executive brief","content":"Write executive brief"}]},"id":"call_3","name":"TodoWrite","type":"function_call"}],"outputs":[{"id":"call_3","name":"TodoWrite","output":"Todo list was updated.","success":true}]},"timestamp":"2026-10-30T02:22:05.000Z"}
                {"role":"assistant","type":"TEXT","content":"### Executive Brief\\nAll daily logs have been consolidated.","timestamp":"2026-10-30T02:22:42.000Z"}
                {"role":"user","type":"TEXT","content":"Stage follow-up email","timestamp":"2026-10-30T02:24:06.000Z"}
                {"role":"tool","type":"TEXT","content":{"message":{"content":"I will run the check first.","role":"assistant","tool_calls":[{"id":"call_4","type":"function","function":{"name":"PowerShell","arguments":"{\\"description\\":\\"Check hub status\\",\\"command\\":\\"curl http://localhost:8891/q/health\\"}"}}]},"outputs":[{"id":"call_4","name":"PowerShell","output":"UP","success":true}]},"timestamp":"2026-10-30T02:24:15.000Z"}
                {"role":"assistant","type":"TEXT","content":"Follow-up email draft has been staged.","timestamp":"2026-10-30T02:24:30.000Z"}
                """;

        List<BotActivity.TranscriptTurn> turns = BotActivity.parseTranscript(jsonl);
        assertEquals(2, turns.size());

        BotActivity.TranscriptTurn first = turns.get(0);
        assertEquals("Run the daily recap for @frank", first.prompt());
        assertFalse(first.status().isBlank());
        assertTrue(first.output().contains("  ✅ Glob **/*Bot*"));
        assertTrue(first.output().contains("  ❌ Read /workspace/BotActivity.java"));
        assertTrue(first.output().contains("📝 Current Tasks:"));
        assertTrue(first.output().contains("✅ Read daily logs"));
        assertTrue(first.output().contains("🚧 Write executive brief"));
        assertTrue(first.output().contains("### Executive Brief"));

        BotActivity.TranscriptTurn second = turns.get(1);
        assertEquals("Stage follow-up email", second.prompt());
        assertTrue(second.output().contains("I will run the check first."));
        assertTrue(second.output().contains("  ✅ Check hub status"));
        assertTrue(second.output().contains("Follow-up email draft has been staged."));
    }

    @Test
    public void testParseTranscriptHandlesBotServiceFallbackSummaryAndChatCompletionsFallback() {
        String fallbackJsonl = """
                {"role":"assistant","routineId":"daily-log","persona":"chief-of-staff","status":"SKIPPED_IDLE","summary":"Skipped: persona 'chief-of-staff' had 0 modified sessions today."}
                """;
        List<BotActivity.TranscriptTurn> turns = BotActivity.parseTranscript(fallbackJsonl);
        assertEquals(1, turns.size());
        assertEquals("Routine: daily-log (@chief-of-staff)", turns.getFirst().prompt());
        assertEquals("SKIPPED_IDLE", turns.getFirst().status());
        assertEquals("Skipped: persona 'chief-of-staff' had 0 modified sessions today.", turns.getFirst().output());

        // ChatCompletions union serialization where arguments are omitted on tool_calls
        String chatCompletionsJsonl = """
                {"role":"user","type":"TEXT","content":"Fix UI","timestamp":"2026-10-12T02:49:36Z"}
                {"role":"tool","type":"TEXT","content":{"message":{"content":"Editing the file now.","role":"assistant","tool_calls":[{"custom":false,"function":true,"valid":true}],"valid":true},"outputs":[{"id":"chatcmpl-1","name":"Edit","output":"File '/src/App.jsx' edited successfully.","success":true}]},"timestamp":"2026-10-12T02:50:36Z"}
                {"role":"assistant","type":"TEXT","content":"Done.","timestamp":"2026-10-12T02:50:54Z"}
                """;
        List<BotActivity.TranscriptTurn> chatTurns = BotActivity.parseTranscript(chatCompletionsJsonl);
        assertEquals(1, chatTurns.size());
        assertTrue(chatTurns.getFirst().output().contains("Editing the file now."));
        assertTrue(chatTurns.getFirst().output().contains("  ✅ Edit /src/App.jsx"));
        assertTrue(chatTurns.getFirst().output().contains("Done."));

        // Verify rendering into Intent widgets in BotActivity
        BotActivity activity = new BotActivity(1);
        activity.renderTranscript(chatCompletionsJsonl, null);
        var intents = activity.transcriptIntentList();
        assertEquals(1, intents.size());
        assertFalse(intents.getFirst().editor().isEditable());
        assertEquals("Fix UI", intents.getFirst().editor().getText());
        assertTrue(intents.getFirst().output().getText().contains("  ✅ Edit /src/App.jsx"));
        assertTrue(intents.getFirst().output().getText().contains("Done."));
    }
}
