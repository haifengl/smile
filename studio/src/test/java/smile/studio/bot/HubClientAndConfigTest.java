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
package smile.studio.bot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.event.HyperlinkEvent;
import ioa.agent.bot.BotsConfig;
import ioa.agent.bot.ProjectsConfig;
import ioa.agent.calendar.CalendarConfig;
import ioa.agent.email.EmailConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import smile.shell.Hub;
import smile.studio.StudioConfig;
import smile.studio.text.OutputArea;
import smile.studio.workspace.BotExplorer;

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

        // Absent autoStart defaults to true; explicit false opts out
        Path defaultAutoStartFile = dir.resolve("studio-default-hub.json");
        Files.writeString(defaultAutoStartFile, "{\"agentHub\":{\"port\":8890}}");
        assertTrue(StudioConfig.parseAgentHub(defaultAutoStartFile).autoStart());

        Path optOutFile = dir.resolve("studio-optout-hub.json");
        Files.writeString(optOutFile, "{\"agentHub\":{\"autoStart\":false}}");
        assertFalse(StudioConfig.parseAgentHub(optOutFile).autoStart());
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
        Path bundledBots = Path.of("src", "universal", "conf", "bots.json");
        if (Files.isRegularFile(bundledBots)) {
            BotsConfig bundled = BotsConfig.load(bundledBots);
            assertTrue(bundled.findRoutine("frank-token-telemetry").isPresent());
        }
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

    @Test
    public void testLoadOfflinePagesAndPageForBotMapping(@TempDir Path tempHome) throws Exception {
        String prevHome = System.getProperty("smile.home");
        try {
            System.setProperty("smile.home", tempHome.toString());
            Path pagesDir = tempHome.resolve("bot").resolve("pages");
            Files.createDirectories(pagesDir);
            Files.writeString(pagesDir.resolve("custom-report.html"), "<html><body>Custom</body></html>");

            List<HubClient.BotPageSummary> pages = HubClient.loadOfflinePages();
            assertEquals(5, pages.size());
            assertTrue(pages.stream().anyMatch(p -> "executive-brief".equals(p.slug())));
            assertTrue(pages.stream().anyMatch(p -> "sales-pipeline".equals(p.slug())));
            assertTrue(pages.stream().anyMatch(p -> "community-radar".equals(p.slug())));
            assertTrue(pages.stream().anyMatch(p -> "token-usage".equals(p.slug())));
            assertTrue(pages.stream().anyMatch(p -> "custom-report".equals(p.slug())));

            BotsConfig cfg = BotsConfig.defaultConfig();
            for (BotsConfig.BotSpec bot : cfg.bots()) {
                var pageOpt = HubClient.pageForBot(bot);
                assertTrue(pageOpt.isPresent(), "Expected mapped page for bot " + bot.botId());
            }
        } finally {
            if (prevHome == null) {
                System.clearProperty("smile.home");
            } else {
                System.setProperty("smile.home", prevHome);
            }
        }
    }

    @Test
    public void testFormatPayloadForDisplayBeautifiesCompactJson() {
        String compact = "{\"header\":\"Outbound Email Approval\",\"question\":\"To: alice@example.com\\nSubject: Hello\\n\\nHi Alice!\"}";
        String formatted = BotActivity.formatPayloadForDisplay(compact);
        assertTrue(formatted.contains("\n"), "Expected multi-line pretty-printed JSON");
        assertTrue(formatted.contains("\"header\" : \"Outbound Email Approval\""));
        assertTrue(formatted.contains("\"question\" : \"To: alice@example.com\\nSubject: Hello\\n\\nHi Alice!\""));

        // Plain text and empty values are preserved as-is
        assertEquals("Plain text draft", BotActivity.formatPayloadForDisplay("Plain text draft"));
        assertEquals("", BotActivity.formatPayloadForDisplay(null));
        assertEquals("", BotActivity.formatPayloadForDisplay("   "));
    }

    @Test
    public void testStructuredApprovalPayloadParsingSerializationAndUiState() {
        String emailPayload = """
                {"header":"Outbound Email Approval","question":"Approve sending outbound email via account 'mike@aihalo.dev'?\\n\\nTo: alice@example.com\\nCc: bob@example.com\\nSubject: Re: Q4 Evaluation\\n\\nHi Alice,\\nHere is the updated benchmark report."}
                """.trim();
        HubClient.BotApprovalItem emailItem = new HubClient.BotApprovalItem(
                10L, 1L, "mike", "mike-outreach", "sales",
                "BATCH_DRAFT", "email", "alice@example.com", "Outbound Email Approval",
                emailPayload, null, "PENDING", null, null, "2026-10-30T03:00:00Z", null
        );

        BotActivity.StructuredApprovalPayload parsedDraft = BotActivity.parseApprovalPayload(emailItem);
        assertTrue(parsedDraft.draftMode());
        assertEquals("Outbound Email Approval", parsedDraft.header());
        assertEquals("mike@aihalo.dev", parsedDraft.account());
        assertEquals("alice@example.com", parsedDraft.to());
        assertEquals("bob@example.com", parsedDraft.cc());
        assertEquals("Re: Q4 Evaluation", parsedDraft.subject());
        assertEquals("Hi Alice,\nHere is the updated benchmark report.", parsedDraft.body());

        // Edit structured fields and serialize back to JSON compatible with Email tool
        BotActivity.StructuredApprovalPayload editedDraft = new BotActivity.StructuredApprovalPayload(
                true,
                parsedDraft.header(),
                parsedDraft.account(),
                "alice@example.com, carol@example.com",
                "bob@example.com",
                "Re: Q4 Evaluation (Updated)",
                "Hi Alice and Carol,\nHere is the revised benchmark report.",
                "",
                ""
        );
        String serializedJson = BotActivity.serializeStructuredPayload(emailItem, editedDraft);
        assertTrue(serializedJson.contains("\"to\" : \"alice@example.com, carol@example.com\""));
        assertTrue(serializedJson.contains("\"subject\" : \"Re: Q4 Evaluation (Updated)\""));
        assertTrue(serializedJson.contains("\"body\" : \"Hi Alice and Carol,\\nHere is the revised benchmark report.\""));

        // Verify readable diff baseline formatting
        String readable = BotActivity.formatReadablePayload(emailPayload, emailItem);
        assertTrue(readable.contains("To: alice@example.com"));
        assertTrue(readable.contains("Cc: bob@example.com"));
        assertTrue(readable.contains("Subject: Re: Q4 Evaluation"));
        assertTrue(readable.contains("Hi Alice,\nHere is the updated benchmark report."));
        assertFalse(readable.contains("\"header\""));

        // Non-draft item (TOOL_CONFIRM / SECURE_HANDOFF) with prior payload
        String handoffPayload = "{\"header\":\"Browser Login Required\",\"question\":\"Please complete 2FA in the browser window.\",\"browserStateDir\":\"/home/.smile/browser-state\"}";
        HubClient.BotApprovalItem handoffItem = new HubClient.BotApprovalItem(
                11L, 2L, "julien", "julien-watch", "evangelist",
                "SECURE_HANDOFF", "general", "", "Browser Login Required",
                handoffPayload, "{\"header\":\"Browser Login Required\",\"question\":\"Initial login request.\"}",
                "PENDING", null, null, "2026-10-30T03:10:00Z", null
        );
        BotActivity.StructuredApprovalPayload parsedHandoff = BotActivity.parseApprovalPayload(handoffItem);
        assertFalse(parsedHandoff.draftMode());
        assertEquals("Browser Login Required", parsedHandoff.header());
        assertEquals("Please complete 2FA in the browser window.", parsedHandoff.body());
        assertEquals("/home/.smile/browser-state", parsedHandoff.browserStateDir());

        // Verify UI state in BotActivity: auto-hides prior draft panel when null, shows when present
        BotActivity activity = new BotActivity(0);
        activity.populateApprovals(List.of(emailItem, handoffItem));
        assertTrue(activity.isDraftComposerActive());
        assertFalse(activity.isPriorDraftPanelVisible(), "Prior draft panel should be hidden when previousPayloadJson is null");
        assertEquals("Re: Q4 Evaluation", activity.readStructuredFields().subject());

        // Select second row (which has previousPayloadJson and is SECURE_HANDOFF)
        activity.populateApprovals(List.of(handoffItem));
        assertFalse(activity.isDraftComposerActive());
        assertTrue(activity.isPriorDraftPanelVisible(), "Prior draft panel should be visible when previousPayloadJson is present");
    }

    @Test
    public void testReadLocalTranscriptMatchesSessionDirectoryByStartedAt(@TempDir Path tempHome) throws Exception {
        System.out.println("readLocalTranscript matches session directory by startedAt");
        Path sessionsRoot = tempHome.resolve("bot").resolve(".smile").resolve("chief-of-staff").resolve("sessions");
        Path olderSession = sessionsRoot.resolve("20261028T231946");
        Path newerSession = sessionsRoot.resolve("20261029T234544");
        Files.createDirectories(olderSession);
        Files.createDirectories(newerSession);
        Files.writeString(
                olderSession.resolve("conversation.jsonl"),
                "{\"role\":\"user\",\"type\":\"TEXT\",\"content\":\"Triage the inbound email(s).\"}\n"
        );
        Files.writeString(
                newerSession.resolve("conversation.jsonl"),
                "{\"role\":\"user\",\"type\":\"TEXT\",\"content\":\"Run the /daily-log skill.\"}\n"
        );

        String startedAtIso = java.time.LocalDateTime.of(2026, 10, 28, 23, 19, 46)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toString();

        var resolved = BotActivity.readLocalTranscript(sessionsRoot.toString(), startedAtIso);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().contains("Triage the inbound email(s)."));
        assertFalse(resolved.get().contains("Run the /daily-log skill."));
    }

    @Test
    public void testSessionTranscriptHyperlinkOpensFileInWorkspace(@TempDir Path tempDir) throws Exception {
        System.out.println("Session Transcript LinkGenerator resolves file paths and triggers file open handler");
        Path dailyLog = tempDir.resolve(".smile").resolve("memory").resolve("2026-10-29.md");
        Files.createDirectories(dailyLog.getParent());
        Files.writeString(dailyLog, "# Daily Log\n\n- Logged session work.\n");

        String forwardSlashPath = dailyLog.toAbsolutePath().normalize().toString().replace('\\', '/');
        String jsonl = """
                {"role":"user","type":"TEXT","content":"Run the /daily-log skill."}
                {"role":"assistant","type":"TEXT","content":"Updated daily log at `%s`."}
                """.formatted(forwardSlashPath);

        BotActivity activity = new BotActivity(1);
        activity.renderTranscript(jsonl, null);

        var intents = activity.transcriptIntentList();
        assertEquals(1, intents.size());
        OutputArea output = intents.getFirst().output();
        assertNotNull(output.getLinkGenerator(), "OutputArea must have a LinkGenerator registered");

        String text = output.getText();
        int pathIndex = text.indexOf(forwardSlashPath);
        assertTrue(pathIndex >= 0, "Rendered transcript must contain the file path");

        // Verify LinkGenerator detects the file path at an offset inside the path
        var linkResult = output.isLinkAtOffset(output, pathIndex + 5);
        assertNotNull(linkResult, "LinkGenerator must recognize existing file path inside backticks");

        AtomicReference<Path> openedFile = new AtomicReference<>();
        output.setFileOpenHandler(openedFile::set);

        HyperlinkEvent event = linkResult.execute();
        assertNotNull(event);
        assertEquals(HyperlinkEvent.EventType.ACTIVATED, event.getEventType());

        output.hyperlinkUpdate(event);
        assertEquals(dailyLog.toAbsolutePath().normalize(), openedFile.get());
    }

    @Test
    public void testBotActivityStartHubButtonHiddenWhenHubRunning() {
        BotActivity activity = new BotActivity(0);

        activity.updateHubStatus(true, "http://127.0.0.1:3829", 3);
        assertFalse(activity.isStartHubButtonVisible(),
                "Start / Attach Hub button should be hidden when Agent Hub is running");

        activity.updateHubStatus(false, "http://127.0.0.1:3829", 0);
        assertTrue(activity.isStartHubButtonVisible(),
                "Start / Attach Hub button should be shown when Agent Hub is offline");
    }

    @Test
    public void testBotExplorerAndBotActivityRoutineSelection(@TempDir Path tempHome) {
        String prevHome = System.getProperty("smile.home");
        try {
            System.setProperty("smile.home", tempHome.toString());
            BotExplorer explorer = new BotExplorer();
            assertNull(explorer.selectedRoutineId());

            assertTrue(explorer.selectRoutine("julien-community-watch"));
            assertEquals("julien-community-watch", explorer.selectedRoutineId());

            assertFalse(explorer.selectRoutine("non-existent-routine"));
            assertFalse(explorer.selectRoutine(""));
            assertFalse(explorer.selectRoutine(null));

            // BotActivity selectedRoutineId tracks the selected row in the active tab
            BotActivity activity = new BotActivity(0);
            assertNull(activity.selectedRoutineId());

            HubClient.BotApprovalItem approvalItem = new HubClient.BotApprovalItem(
                    10L, 1L, "mike", "mike-outreach", "sales",
                    "BATCH_DRAFT", "email", "alice@example.com", "Outbound Email Approval",
                    "{}", null, "PENDING", null, null, "2026-10-30T03:00:00Z", null
            );
            activity.populateApprovals(List.of(approvalItem));
            assertEquals("mike-outreach", activity.selectedRoutineId());

            HubClient.BotRunSummary runItem = new HubClient.BotRunSummary(
                    20L, "julien", "julien-community-watch", "evangelist", "gpt-5.4", 1L,
                    "COMPLETED", "2026-10-30T02:00:00Z", "2026-10-30T02:01:00Z",
                    "", "Completed watch", ""
            );
            activity.populateRuns(List.of(runItem));
            activity.selectTab(1);
            assertEquals("julien-community-watch", activity.selectedRoutineId());
        } finally {
            if (prevHome == null) {
                System.clearProperty("smile.home");
            } else {
                System.setProperty("smile.home", prevHome);
            }
        }
    }

    @Test
    public void testParseStreamEventAndNonDestructiveBotActivityRefresh() {
        HubClient.BotStreamEvent ev = HubClient.parseStreamEvent("run",
                "{\"type\":\"run\",\"runId\":42,\"botId\":\"frank\",\"routineId\":\"frank-daily-recap\",\"timestamp\":\"2026-11-01T12:00:00Z\"}");
        assertNotNull(ev);
        assertEquals("run", ev.type());
        assertEquals(42L, ev.runId());
        assertEquals("frank", ev.botId());
        assertEquals("frank-daily-recap", ev.routineId());
        assertEquals("", ev.pageSlug());

        HubClient.BotStreamEvent pageEv = HubClient.parseStreamEvent("page",
                "{\"type\":\"page\",\"runId\":42,\"botId\":\"frank\",\"routineId\":\"frank-daily-recap\",\"pageSlug\":\"executive-brief\",\"timestamp\":\"2026-11-01T12:01:00Z\"}");
        assertNotNull(pageEv);
        assertEquals("page", pageEv.type());
        assertEquals(42L, pageEv.runId());
        assertEquals("frank", pageEv.botId());
        assertEquals("frank-daily-recap", pageEv.routineId());
        assertEquals("executive-brief", pageEv.pageSlug());

        // Verify non-destructive approval refresh preserves user draft edits when the selected approval is unchanged
        BotActivity activity = new BotActivity(0);
        HubClient.BotApprovalItem item1 = new HubClient.BotApprovalItem(
                101L, 1L, "mike", "mike-outreach", "sales",
                "BATCH_DRAFT", "email", "alice@example.com", "Original Subject",
                "{\"to\":\"alice@example.com\",\"subject\":\"Original Subject\",\"body\":\"Original body\"}",
                null, "PENDING", null, null, "2026-11-01T10:00:00Z", null
        );
        HubClient.BotApprovalItem item2 = new HubClient.BotApprovalItem(
                102L, 2L, "mike", "mike-outreach", "sales",
                "BATCH_DRAFT", "email", "bob@example.com", "Second Subject",
                "{\"to\":\"bob@example.com\",\"subject\":\"Second Subject\",\"body\":\"Second body\"}",
                null, "PENDING", null, null, "2026-11-01T10:05:00Z", null
        );
        activity.populateApprovals(List.of(item1));
        assertTrue(activity.isDraftComposerActive());

        // Refreshing with a newly added second approval while item1 remains selected must not clobber item1
        activity.populateApprovals(List.of(item1, item2));
        assertEquals("mike-outreach", activity.selectedRoutineId());
    }

    @Test
    public void testHubManagerNotifiesStartAndStopListenersWhenAttachingToRunningHub() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/q/health", exchange -> {
            byte[] resp = "{\"status\":\"UP\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/v1/admin/projects", exchange -> {
            byte[] resp = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        var startedPanel = new AtomicReference<smile.studio.ProcessPanel>();
        var stoppedPanel = new AtomicReference<smile.studio.ProcessPanel>();
        java.util.function.Consumer<smile.studio.ProcessPanel> onStart = startedPanel::set;
        java.util.function.Consumer<smile.studio.ProcessPanel> onStop = stoppedPanel::set;
        HubManager manager = HubManager.getInstance();
        manager.addStartListener(onStart);
        manager.addStopListener(onStop);

        try {
            boolean ok = manager.start("127.0.0.1", port, "dev-admin-token");
            assertTrue(ok, "HubManager.start should succeed when attaching to a ready Hub");
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            assertSame(manager.panel(), startedPanel.get(),
                    "Start listener must be notified so the Agent Hub tab docks into Workspace");
            assertTrue(manager.panel().getOutput().contains("Attached to running Agent Hub"),
                    "ProcessPanel should display an attachment banner when attached");

            manager.stop();
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            assertSame(manager.panel(), stoppedPanel.get(),
                    "Stop listener must be notified when stopping an attached Agent Hub");

            // After explicit stop(), attachIfRunning() must not re-open the tab until start() is called
            startedPanel.set(null);
            manager.attachIfRunning();
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
            assertNull(startedPanel.get(),
                    "attachIfRunning must respect explicit user stop");
        } finally {
            manager.removeStartListener(onStart);
            manager.removeStopListener(onStop);
            server.stop(0);
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
        }
    }

    @Test
    public void testStatusBarBotBadgeTracksRunningAndPendingApprovals() {
        var statusBar = new smile.studio.StatusBar();
        assertFalse(statusBar.isHubRunning());
        assertEquals(0, statusBar.pendingApprovals());

        statusBar.updateBotBadge(true, 0);
        assertTrue(statusBar.isHubRunning());
        assertEquals(0, statusBar.pendingApprovals());

        statusBar.updateBotBadge(true, 4);
        assertTrue(statusBar.isHubRunning());
        assertEquals(4, statusBar.pendingApprovals());

        statusBar.updateBotBadge(false, 0);
        assertFalse(statusBar.isHubRunning());
        assertEquals(0, statusBar.pendingApprovals());
    }

    @Test
    public void testBotManagerTooltipsAppliedToLabelsAndInputComponents(@TempDir Path tempHome) {
        String prevHome = System.getProperty("smile.home");
        try {
            System.setProperty("smile.home", tempHome.toString());
            BotManager manager = new BotManager("frank-daily-recap", null);
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle(
                    BotManager.class.getName(), java.util.Locale.getDefault());

            List<String> expectedTooltips = List.of(
                    bundle.getString("MissedFirePolicyTooltip"),
                    bundle.getString("CronTooltip"),
                    bundle.getString("IntervalTooltip"),
                    bundle.getString("EndpointTooltip"),
                    bundle.getString("QuietStartTooltip"),
                    bundle.getString("QuietEndTooltip"),
                    bundle.getString("SettleTooltip"),
                    bundle.getString("MaxTurnsPerHourTooltip"),
                    bundle.getString("AwaitRoutineIdTooltip"),
                    bundle.getString("TargetSkillToolTooltip"),
                    bundle.getString("InstructionsTooltip"),
                    bundle.getString("ApprovalTtlTooltip")
            );

            List<javax.swing.JComponent> allComponents = new java.util.ArrayList<>();
            collectJComponents(manager, allComponents);

            for (String tooltip : expectedTooltips) {
                long labelMatches = allComponents.stream()
                        .filter(c -> c instanceof javax.swing.JLabel && tooltip.equals(c.getToolTipText()))
                        .count();
                long inputMatches = allComponents.stream()
                        .filter(c -> !(c instanceof javax.swing.JLabel) && tooltip.equals(c.getToolTipText()))
                        .count();
                assertTrue(labelMatches >= 1, "Expected JLabel with tooltip: " + tooltip);
                assertTrue(inputMatches >= 1, "Expected input JComponent with tooltip: " + tooltip);
            }
        } finally {
            if (prevHome == null) {
                System.clearProperty("smile.home");
            } else {
                System.setProperty("smile.home", prevHome);
            }
        }
    }

    private static void collectJComponents(java.awt.Container root, List<javax.swing.JComponent> out) {
        for (java.awt.Component comp : root.getComponents()) {
            if (comp instanceof javax.swing.JComponent jc) {
                out.add(jc);
            }
            if (comp instanceof java.awt.Container childContainer) {
                collectJComponents(childContainer, out);
            }
        }
    }

    @Test
    public void testBotManagerResolveEndpointOptionsDeduplicatesEmailAndCalendarAccounts() {
        EmailConfig.AccountSpec gmail = EmailConfig.AccountSpec.gmail(
                "haifeng.hli@gmail.com", "Personal Gmail", null);
        EmailConfig.AccountSpec outlook = EmailConfig.AccountSpec.msGraph(
                "haifengl@outlook.com", "Work Outlook", null, "common");
        EmailConfig.AccountSpec smtp = new EmailConfig.AccountSpec(
                "haifeng.li@aihalo.dev", "Haifeng Li", "Halo SMTP",
                EmailConfig.Provider.IMAP, true, 60, "INBOX", null,
                null, null, "imap.aihalo.dev", 993, true, "smtp.aihalo.dev", 465, false);
        EmailConfig emailConfig = EmailConfig.empty()
                .withUpsertedAccount(gmail)
                .withUpsertedAccount(outlook)
                .withUpsertedAccount(smtp);

        CalendarConfig calendarConfig = CalendarConfig.empty()
                .withUpsertedAccount(CalendarConfig.AccountSpec.fromEmailAccount(gmail).orElseThrow())
                .withUpsertedAccount(CalendarConfig.AccountSpec.fromEmailAccount(outlook).orElseThrow());

        List<String> emailOptions = BotManager.resolveEndpointOptions("email", emailConfig, calendarConfig);
        assertEquals(
                List.of("inbox", "haifeng.hli@gmail.com", "haifengl@outlook.com", "haifeng.li@aihalo.dev"),
                emailOptions
        );

        List<String> calendarOptions = BotManager.resolveEndpointOptions("calendar", emailConfig, calendarConfig);
        assertEquals(
                List.of("primary", "haifeng.hli@gmail.com", "haifengl@outlook.com"),
                calendarOptions
        );
    }
}
