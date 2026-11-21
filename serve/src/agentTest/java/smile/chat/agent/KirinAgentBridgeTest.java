/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.chat.agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ioa.agent.Agent;
import ioa.agent.calendar.CalendarServices;
import ioa.agent.email.EmailServices;
import ioa.agent.email.SharedSecrets;
import ioa.llm.Conversation;
import ioa.llm.Model;
import ioa.llm.client.LLM;
import ioa.llm.client.StreamResponseHandler;
import ioa.llm.tool.Calendar;
import ioa.llm.tool.Email;
import smile.auth.AuthProviderKind;
import smile.auth.User;
import smile.chat.CompletionRequest;
import smile.llm.ChatCompletion;
import smile.llm.Message;
import smile.llm.Role;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link KirinAgentBridge} verifying:
 * <ul>
 *   <li>{@code REQ-KIRIN-01}: {@code KirinAgentBridge} availability and {@code ioa-agent} integration.</li>
 *   <li>{@code REQ-KIRIN-02}: Built-in Google and Microsoft OAuth credential resolution via {@code OAuthClientVault}.</li>
 *   <li>{@code REQ-KIRIN-04}: Per-user home directory provisioning under {@code /smile/users/<user-key>/.smile/},
 *       {@code agents/kirin/AGENT.md}, and hot-reloaded custom skills in {@code agents/kirin/skills/}.</li>
 *   <li>{@code REQ-KIRIN-05}: Strict {@code [WebFetch, WebSearch, Email, Calendar, Skill, TodoWrite]} tool allowlist
 *       and multi-user {@link EmailServices}, {@link CalendarServices}, and {@link photos.sluice.secrets.SecretStore} isolation.</li>
 * </ul>
 */
public class KirinAgentBridgeTest {

    @TempDir
    Path tempSmileUsersDir;

    private String previousSmileUsersProp;

    @BeforeEach
    void setUp() {
        previousSmileUsersProp = System.getProperty("smile.home.users");
        System.setProperty("smile.home.users", tempSmileUsersDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousSmileUsersProp != null) {
            System.setProperty("smile.home.users", previousSmileUsersProp);
        } else {
            System.clearProperty("smile.home.users");
        }
    }

    @Test
    void resolvesBuiltInGoogleAndMicrosoftOAuthCredentialsFromVault() {
        KirinAgentBridge bridge = new KirinAgentBridge();
        assertTrue(bridge.isAvailable());
        bridge.agentEnabled = false;
        assertFalse(bridge.isAvailable());
        bridge.agentEnabled = true;

        Optional<ChatAgentBridge.OAuthAppCredentials> google =
                bridge.resolveOAuthCredentials(AuthProviderKind.GOOGLE, null, null, null);
        assertTrue(google.isPresent(), "Built-in Google OAuth credentials must resolve from OAuthClientVault");
        assertTrue(google.get().builtIn());
        assertNotNull(google.get().clientId());
        assertFalse(google.get().clientId().isBlank());
        assertNotNull(google.get().clientSecret());

        Optional<ChatAgentBridge.OAuthAppCredentials> microsoft =
                bridge.resolveOAuthCredentials(AuthProviderKind.MICROSOFT, null, null, null);
        assertTrue(microsoft.isPresent(), "Built-in Microsoft OAuth credentials must resolve from OAuthClientVault");
        assertTrue(microsoft.get().builtIn());
        assertNotNull(microsoft.get().clientId());
        assertFalse(microsoft.get().clientId().isBlank());
        assertEquals("common", microsoft.get().tenantId());
    }

    @Test
    void provisionsPerUserHomesAndIsolatesEmailCalendarAndSecretsAcrossUsers() throws Exception {
        KirinAgentBridge bridge = new KirinAgentBridge();

        User alice = new User();
        alice.id = 101L;
        alice.externalId = "google-alice-101";
        alice.email = "alice@gmail.com";
        alice.displayName = "Alice Smith";
        alice.authProvider = AuthProviderKind.GOOGLE;

        User bob = new User();
        bob.id = 202L;
        bob.externalId = "ms:bob-202";
        bob.email = "bob@contoso.com";
        bob.displayName = "Bob Jones";
        bob.authProvider = AuthProviderKind.MICROSOFT;

        bridge.onOAuthLogin(alice, new ChatAgentBridge.OAuthLoginPayload(
                AuthProviderKind.GOOGLE,
                "alice@gmail.com",
                "Alice Smith",
                "alice-google-refresh-token",
                "alice-google-access-token",
                null,
                null
        ));

        bridge.onOAuthLogin(bob, new ChatAgentBridge.OAuthLoginPayload(
                AuthProviderKind.MICROSOFT,
                "bob@contoso.com",
                "Bob Jones",
                "bob-ms-refresh-token",
                "bob-ms-access-token",
                null,
                "common"
        ));

        KirinAgentBridge.UserWorkspaceContext aliceCtx = bridge.ensureUserWorkspace(alice);
        KirinAgentBridge.UserWorkspaceContext bobCtx = bridge.ensureUserWorkspace(bob);

        assertNotEquals(aliceCtx.userHome(), bobCtx.userHome());
        assertTrue(Files.isRegularFile(aliceCtx.kirinDir().resolve("AGENT.md")));
        assertTrue(Files.isDirectory(aliceCtx.kirinDir().resolve("skills")));
        assertTrue(Files.isRegularFile(aliceCtx.smileDir().resolve("conf").resolve("email.json")));
        assertTrue(Files.isRegularFile(aliceCtx.smileDir().resolve("conf").resolve("calendar.json")));

        assertTrue(Files.isRegularFile(bobCtx.kirinDir().resolve("AGENT.md")));
        assertTrue(Files.isDirectory(bobCtx.kirinDir().resolve("skills")));
        assertTrue(Files.isRegularFile(bobCtx.smileDir().resolve("conf").resolve("email.json")));
        assertTrue(Files.isRegularFile(bobCtx.smileDir().resolve("conf").resolve("calendar.json")));

        // Verify isolated SecretStores hold only the respective user's refresh token
        String aliceAccountId = aliceCtx.emailServices().config().accounts().getFirst().emailAddress();
        String bobAccountId = bobCtx.emailServices().config().accounts().getFirst().emailAddress();
        try {
            assertEquals(Optional.of("alice-google-refresh-token"),
                    SharedSecrets.resolveEmailSecret(aliceCtx.secretStore(), aliceAccountId));
            assertEquals(Optional.empty(),
                    SharedSecrets.resolveEmailSecret(aliceCtx.secretStore(), bobAccountId));
            assertEquals(Optional.of("bob-ms-refresh-token"),
                    SharedSecrets.resolveEmailSecret(bobCtx.secretStore(), bobAccountId));
            assertEquals(Optional.empty(),
                    SharedSecrets.resolveEmailSecret(bobCtx.secretStore(), aliceAccountId));

            // Verify Email and Calendar tools bound to each user's Agent conversation never cross-leak
            LLM dummyLlm = createStubLlm("OK");
            Agent aliceAgent = bridge.createAgent(aliceCtx, dummyLlm, "test-model");
            Agent bobAgent = bridge.createAgent(bobCtx, dummyLlm, "test-model");

            Email emailTool = new Email();
            emailTool.action = "list_accounts";
            Calendar calendarTool = new Calendar();
            calendarTool.action = "list_accounts";

            String aliceEmails = emailTool.call(aliceAgent.conversation(), null).output();
            String bobEmails = emailTool.call(bobAgent.conversation(), null).output();
            assertTrue(aliceEmails.contains("alice@gmail.com"), aliceEmails);
            assertFalse(aliceEmails.contains("bob@contoso.com"), aliceEmails);
            assertTrue(bobEmails.contains("bob@contoso.com"), bobEmails);
            assertFalse(bobEmails.contains("alice@gmail.com"), bobEmails);

            String aliceCalendars = calendarTool.call(aliceAgent.conversation(), null).output();
            String bobCalendars = calendarTool.call(bobAgent.conversation(), null).output();
            assertTrue(aliceCalendars.contains(aliceAccountId), aliceCalendars);
            assertFalse(aliceCalendars.contains(bobAccountId), aliceCalendars);
            assertTrue(bobCalendars.contains(bobAccountId), bobCalendars);
            assertFalse(bobCalendars.contains(aliceAccountId), bobCalendars);
        } finally {
            SharedSecrets.deleteEmailSecret(aliceCtx.secretStore(), aliceAccountId);
            SharedSecrets.deleteCalendarSecret(aliceCtx.secretStore(), aliceAccountId);
            SharedSecrets.deleteEmailSecret(bobCtx.secretStore(), bobAccountId);
            SharedSecrets.deleteCalendarSecret(bobCtx.secretStore(), bobAccountId);
        }
    }

    @Test
    void enforcesStrictKirinToolAllowlistAndDiscoversCustomUserSkills() throws Exception {
        KirinAgentBridge bridge = new KirinAgentBridge();

        User user = new User();
        user.id = 303L;
        user.externalId = "google-custom-303";
        user.email = "custom@gmail.com";
        user.displayName = "Custom User";
        user.authProvider = AuthProviderKind.GOOGLE;

        KirinAgentBridge.UserWorkspaceContext ctx = bridge.ensureUserWorkspace(user);

        // 1. Add a custom skill under <userHome>/.smile/agents/kirin/skills/weekly-status/SKILL.md
        Path skillDir = ctx.kirinDir().resolve("skills").resolve("weekly-status");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: weekly-status
                description: Formats a concise weekly status report for small office teams.
                ---
                Always format the status report with Highlights, Blockers, and Next Week.
                """, StandardCharsets.UTF_8);

        // 2. Attempt to inject forbidden host shell/file tools in AGENT.md
        Files.writeString(ctx.kirinDir().resolve("AGENT.md"), """
                ---
                name: kirin
                description: Custom Kirin
                tools:
                  - WebSearch
                  - Email
                  - Calendar
                  - Skill
                  - Bash
                  - PowerShell
                  - Write
                  - Edit
                ---
                You are Custom Kirin.
                """, StandardCharsets.UTF_8);

        Agent.Spec sanitized = bridge.loadKirinSpec(ctx);
        assertEquals(List.of("WebSearch", "Email", "Calendar", "Skill"), sanitized.tools());
        assertFalse(sanitized.tools().contains("Bash"));
        assertFalse(sanitized.tools().contains("PowerShell"));
        assertFalse(sanitized.tools().contains("Write"));
        assertFalse(sanitized.tools().contains("Edit"));

        // 3. Execute a streaming turn through KirinAgentBridge and verify skill injection + streaming chunks
        List<String> capturedPrompts = new ArrayList<>();
        List<String> capturedSystemInstructions = new ArrayList<>();
        LLM stubLlm = new LLM() {
            @Override
            public boolean initSession(Conversation conversation) {
                return false;
            }

            @Override
            public CompletableFuture<String> complete(String prompt, Properties params, Model model) {
                return CompletableFuture.completedFuture("Weekly status report generated by Kirin.");
            }

            @Override
            public void complete(String prompt, Conversation conv, StreamResponseHandler handler, Model model) {
                capturedPrompts.add(prompt);
                capturedSystemInstructions.add(conv.params().getProperty(LLM.SYSTEM_PROMPT, ""));
                handler.onNext("Weekly status ");
                handler.onNext("report generated by Kirin.");
                handler.onComplete(20, 10, 10);
            }
        };
        bridge.setTestLlmOverride(stubLlm);

        CompletionRequest req = new CompletionRequest();
        req.model = "Qwen/Qwen3-8B";
        req.messages = new Message[] {
                new Message(Role.user, "Use /weekly-status to summarize my week")
        };

        List<String> streamedChunks = new ArrayList<>();
        ChatCompletion completion = bridge.complete(user, req, 8080, "Qwen/Qwen3-8B", streamedChunks::add);
        assertNotNull(completion);
        assertEquals("Weekly status report generated by Kirin.", completion.content());
        assertEquals(List.of("Weekly status ", "report generated by Kirin."), streamedChunks);
        assertFalse(capturedPrompts.isEmpty());
        String combinedContext = capturedPrompts.getFirst() + "\n" + capturedSystemInstructions.getFirst();
        assertTrue(combinedContext.contains("weekly-status"),
                "Custom user skill in .smile/agents/kirin/skills/ must be discovered and injected into Kirin's context");
    }

    @Test
    void provisionsScopedOidcWorkspaceWithoutEmailOrCalendarAndEnforcesFourToolAllowlist() throws Exception {
        KirinAgentBridge bridge = new KirinAgentBridge();

        User oidcUser = new User();
        oidcUser.id = 404L;
        oidcUser.externalId = "oidc:0123456789abcdef:carol";
        oidcUser.email = "carol@enterprise.example";
        oidcUser.displayName = "Carol Enterprise";
        oidcUser.authProvider = AuthProviderKind.OIDC;

        bridge.onOAuthLogin(oidcUser, new ChatAgentBridge.OAuthLoginPayload(
                AuthProviderKind.OIDC,
                oidcUser.email,
                oidcUser.displayName,
                null,
                null,
                null,
                null
        ));

        KirinAgentBridge.UserWorkspaceContext ctx = bridge.ensureUserWorkspace(oidcUser);
        assertTrue(ctx.userKey().startsWith("oidc-"), "OIDC workspace key must start with oidc-: " + ctx.userKey());
        assertTrue(Files.isRegularFile(ctx.kirinDir().resolve("AGENT.md")));
        assertTrue(Files.isDirectory(ctx.kirinDir().resolve("skills")));

        // No email.json, calendar.json, or credentials/ directory created for OIDC users
        assertFalse(Files.exists(ctx.smileDir().resolve("conf").resolve("email.json")));
        assertFalse(Files.exists(ctx.smileDir().resolve("conf").resolve("calendar.json")));
        assertFalse(Files.exists(ctx.smileDir().resolve("credentials")));
        assertNull(ctx.secretStore());
        assertNull(ctx.emailServices());
        assertNull(ctx.calendarServices());

        // Default seeded OIDC spec strictly exposes only [WebFetch, WebSearch, Skill, TodoWrite]
        Agent.Spec defaultSpec = bridge.loadKirinSpec(oidcUser, ctx);
        assertEquals(List.of("WebFetch", "WebSearch", "Skill", "TodoWrite"), defaultSpec.tools());
        assertFalse(defaultSpec.prompt().contains("Gmail"));

        // Even if AGENT.md is manually edited to add Email, Calendar, Bash, Write -> stripped!
        Files.writeString(ctx.kirinDir().resolve("AGENT.md"), """
                ---
                name: kirin
                description: Tampered OIDC Kirin
                tools:
                  - WebFetch
                  - WebSearch
                  - Email
                  - Calendar
                  - Skill
                  - TodoWrite
                  - Bash
                  - Write
                ---
                Tampered prompt.
                """, StandardCharsets.UTF_8);

        Agent.Spec sanitized = bridge.loadKirinSpec(oidcUser, ctx);
        assertEquals(List.of("WebFetch", "WebSearch", "Skill", "TodoWrite"), sanitized.tools());
        assertFalse(sanitized.tools().contains("Email"));
        assertFalse(sanitized.tools().contains("Calendar"));
        assertFalse(sanitized.tools().contains("Bash"));
        assertFalse(sanitized.tools().contains("Write"));

        // Agent instantiated for OIDC user must NOT bind EmailServices or CalendarServices
        Agent oidcAgent = bridge.createAgent(oidcUser, ctx, createStubLlm("OK"), "test-model");
        assertFalse(oidcAgent.conversation().attributes().containsKey(EmailServices.CONVERSATION_KEY));
        assertFalse(oidcAgent.conversation().attributes().containsKey(CalendarServices.CONVERSATION_KEY));
    }

    private static LLM createStubLlm(String reply) {
        return new LLM() {
            @Override
            public boolean initSession(Conversation conversation) {
                return false;
            }

            @Override
            public CompletableFuture<String> complete(String prompt, Properties params, Model model) {
                return CompletableFuture.completedFuture(reply);
            }

            @Override
            public void complete(String prompt, Conversation conv, StreamResponseHandler handler, Model model) {
                handler.onNext(reply);
                handler.onComplete(10, 5, 5);
            }
        };
    }
}
