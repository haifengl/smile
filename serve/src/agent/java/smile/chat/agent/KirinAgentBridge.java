/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.Timeout;
import io.quarkus.arc.profile.UnlessBuildProfile;
import org.jboss.logging.Logger;
import ioa.agent.Agent;
import ioa.agent.calendar.CalendarConfig;
import ioa.agent.calendar.CalendarServices;
import ioa.agent.email.EmailConfig;
import ioa.agent.email.EmailServices;
import ioa.agent.email.OAuthClientVault;
import ioa.agent.email.OAuthFlow;
import ioa.agent.email.SharedSecrets;
import ioa.llm.Model;
import ioa.llm.client.ChatCompletions;
import ioa.llm.client.LLM;
import ioa.llm.client.StreamResponseHandler;
import ioa.llm.tool.Question;
import photos.sluice.secrets.SecretStore;
import smile.auth.AuthConfig;
import smile.auth.AuthProviderKind;
import smile.auth.User;
import smile.chat.ChatCompletionResource;
import smile.chat.CompletionRequest;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.Role;

/**
 * Server-side {@code kirin} personal assistant integration for {@code smile-serve:latest-gpu}.
 *
 * <p>Implements:
 * <ul>
 *   <li>{@code REQ-KIRIN-01}: Links against {@code ioa-agent} when compiled with the {@code src/agent/java} source set.</li>
 *   <li>{@code REQ-KIRIN-02}: Resolves built-in Google and Microsoft OAuth credentials via {@link OAuthClientVault}.</li>
 *   <li>{@code REQ-KIRIN-04}: Provisions per-user home directories under {@code /smile/users/<user-key>/.smile/}
 *       with {@code agents/kirin/AGENT.md} and {@code agents/kirin/skills/}.</li>
 *   <li>{@code REQ-KIRIN-05}: Restricts {@code kirin} to {@code [WebFetch, WebSearch, Email, Calendar, Skill, TodoWrite]}
 *       and isolates each user's {@link EmailServices}, {@link CalendarServices}, and {@link SecretStore}.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
@UnlessBuildProfile("micro")
@ApplicationScoped
public class KirinAgentBridge implements ChatAgentBridge {
    private static final Logger LOG = Logger.getLogger(KirinAgentBridge.class);

    /** Canonical agent name. */
    public static final String KIRIN_AGENT_NAME = "kirin";

    /**
     * Strictly enforced host-safety allowlist for {@code kirin} ({@code REQ-KIRIN-05}).
     * Host shell and filesystem mutation tools ({@code Bash}, {@code PowerShell}, {@code Write},
     * {@code Edit}, {@code ApplyPatch}, {@code KillShell}, {@code NotebookEdit}, {@code Computer})
     * are never permitted in web chat, even if a user edits {@code AGENT.md} on the mounted volume.
     */
    public static final List<String> DEFAULT_KIRIN_TOOLS = List.of(
            "WebFetch",
            "WebSearch",
            "Email",
            "Calendar",
            "Skill",
            "TodoWrite"
    );

    private static final Set<String> ALLOWED_KIRIN_TOOLS = Set.copyOf(DEFAULT_KIRIN_TOOLS);

    /**
     * Strictly enforced 4-tool allowlist for Enterprise OIDC users ({@code REQ-OIDC-08}).
     * Excludes {@code Email} and {@code Calendar} (no consumer Gmail/Graph delegation tokens)
     * as well as all host shell and filesystem mutation tools.
     */
    public static final List<String> OIDC_KIRIN_TOOLS = List.of(
            "WebFetch",
            "WebSearch",
            "Skill",
            "TodoWrite"
    );

    private static final Set<String> ALLOWED_OIDC_KIRIN_TOOLS = Set.copyOf(OIDC_KIRIN_TOOLS);

    /** Default {@code AGENT.md} seeded for OIDC SSO users (4-tool scoped mode). */
    public static final String DEFAULT_OIDC_KIRIN_AGENT_MD = """
            ---
            name: kirin
            description: >
              Enterprise AI assistant for web research, reusable skills, and task tracking.
            callName: kirin
            title: "Kirin Assistant"
            subagent: false
            tools:
              - WebFetch
              - WebSearch
              - Skill
              - TodoWrite
            ---
            ## Role and Persona
            You are **Kirin**, a helpful, accurate, and privacy-first enterprise AI assistant running on SMILE Serve.

            ## Capabilities
            - **Web Research (`WebSearch`, `WebFetch`):** Search the live web for current facts, news, and documentation, and fetch full web pages as clean Markdown.
            - **Custom Skills (`Skill`):** Execute specialized workflows and domain skills installed in your `.smile/agents/kirin/skills/` directory.
            - **Task Tracking (`TodoWrite`):** Track multi-step tasks transparently as you work.

            ## Operating Guidelines
            1. **Verify before answering:** Use `WebSearch` or `WebFetch` when up-to-date external information is required rather than guessing.
            2. **Clear, concise answers:** Format responses in clean Markdown with relevant links and summaries.
            """;

    /** Default {@code AGENT.md} seeded into {@code <userHome>/.smile/agents/kirin/AGENT.md}. */
    public static final String DEFAULT_KIRIN_AGENT_MD = """
            ---
            name: kirin
            description: >
              Personal and small-office AI assistant for web research, email management,
              calendar scheduling, reusable skills, and task tracking.
            callName: kirin
            title: "Kirin Personal Assistant"
            subagent: false
            tools:
              - WebFetch
              - WebSearch
              - Email
              - Calendar
              - Skill
              - TodoWrite
            ---
            ## Role and Persona
            You are **Kirin**, a helpful, accurate, and privacy-first personal and small-office AI assistant running on SMILE Serve.

            ## Capabilities
            - **Web Research (`WebSearch`, `WebFetch`):** Search the live web for current facts, news, and documentation, and fetch full web pages as clean Markdown.
            - **Email Management (`Email`):** Search, read, draft, reply to, and send emails on behalf of the signed-in user using their connected Google Gmail or Microsoft Outlook/365 account.
            - **Calendar & Scheduling (`Calendar`):** Check schedules, find free/busy slots, schedule or update events, and respond to meeting invitations on the signed-in user's Google or Microsoft Calendar.
            - **Custom Skills (`Skill`):** Execute specialized workflows and domain skills installed in your `.smile/agents/kirin/skills/` directory.
            - **Task Tracking (`TodoWrite`):** Track multi-step tasks transparently as you work.

            ## Operating Guidelines
            1. **Verify before acting:** When answering questions about the user's schedule or inbox, query `Calendar` or `Email` first rather than guessing.
            2. **Safe outbound communication:** Prefer creating drafts (`action: "draft"`) when asked to prepare an email unless the user explicitly asks you to send or reply immediately.
            3. **Clear, concise answers:** Format responses in clean Markdown with relevant links, timestamps, and summaries.
            """;

    private static final String DEFAULT_WEB_UI_SYSTEM_PROMPT =
            "You are a helpful, respectful and honest assistant.";

    @Inject
    AuthConfig authConfig;

    private final Map<String, UserWorkspaceContext> workspacesByKey = new ConcurrentHashMap<>();
    private final Map<Integer, ChatCompletions> loopbackClientsByPort = new ConcurrentHashMap<>();
    private volatile LLM testLlmOverride;

    /**
     * Per-user isolated workspace state holding the user's home path, isolated
     * {@link SecretStore}, and isolated {@link EmailServices} / {@link CalendarServices}.
     */
    public record UserWorkspaceContext(
            String userKey,
            Path userHome,
            Path smileDir,
            Path kirinDir,
            SecretStore secretStore,
            EmailServices emailServices,
            CalendarServices calendarServices
    ) { }

    /** Package-private override for injecting a deterministic mock {@link LLM} in unit tests. */
    public void setTestLlmOverride(LLM llmOverride) {
        this.testLlmOverride = llmOverride;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<OAuthAppCredentials> resolveOAuthCredentials(
            AuthProviderKind provider,
            String customClientId,
            String customClientSecret,
            String customTenantId
    ) {
        if (provider != AuthProviderKind.GOOGLE && provider != AuthProviderKind.MICROSOFT) {
            return Optional.empty();
        }
        EmailConfig.Provider emailProvider = provider == AuthProviderKind.GOOGLE
                ? EmailConfig.Provider.GMAIL
                : EmailConfig.Provider.MSGRAPH;
        try {
            OAuthClientVault.OAuthAppCredentials creds = OAuthClientVault.resolve(
                    emailProvider,
                    blankToNull(customClientId),
                    blankToNull(customClientSecret),
                    blankToNull(customTenantId)
            );
            if (creds.clientId() == null || creds.clientId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new OAuthAppCredentials(
                    creds.clientId(),
                    creds.clientSecret(),
                    creds.tenantId(),
                    creds.builtIn()
            ));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to resolve OAuth credentials for %s", provider);
            return Optional.empty();
        }
    }

    @Override
    public void onOAuthLogin(User user, OAuthLoginPayload payload) {
        if (user == null || payload == null) {
            return;
        }
        if (payload.provider() == AuthProviderKind.OIDC || user.authProvider == AuthProviderKind.OIDC) {
            try {
                UserWorkspaceContext ctx = ensureUserWorkspace(user);
                LOG.infof("Provisioned scoped OIDC kirin workspace for %s at %s",
                        user.externalId, ctx.userHome());
            } catch (Exception e) {
                LOG.warnf(e, "Failed to provision OIDC kirin workspace for user %s", user.externalId);
            }
            return;
        }
        if (payload.provider() != AuthProviderKind.GOOGLE
                && payload.provider() != AuthProviderKind.MICROSOFT) {
            return;
        }
        try {
            UserWorkspaceContext ctx = ensureUserWorkspace(user);
            String email = !isNullOrBlank(payload.emailAddress())
                    ? payload.emailAddress().trim()
                    : (!isNullOrBlank(user.email) ? user.email.trim() : "");
            if (email.isEmpty()) {
                LOG.warnf("OAuth login for user %s did not include an email address; skipping Email/Calendar provisioning",
                        user.externalId);
                return;
            }
            String displayName = !isNullOrBlank(payload.displayName())
                    ? payload.displayName().trim()
                    : user.displayName;
            EmailConfig.Provider emailProvider = payload.provider() == AuthProviderKind.GOOGLE
                    ? EmailConfig.Provider.GMAIL
                    : EmailConfig.Provider.MSGRAPH;

            Path emailJsonPath = ctx.smileDir().resolve("conf").resolve("email.json");
            Path calendarJsonPath = ctx.smileDir().resolve("conf").resolve("calendar.json");
            EmailConfig existingEmailCfg = EmailConfig.load(emailJsonPath);
            CalendarConfig existingCalCfg = CalendarConfig.load(calendarJsonPath);

            OAuthFlow.OAuthLoginResult loginResult = new OAuthFlow.OAuthLoginResult(
                    emailProvider,
                    email,
                    displayName,
                    !isNullOrBlank(payload.refreshToken())
                            ? payload.refreshToken().trim()
                            : (!isNullOrBlank(payload.accessToken()) ? "bearer:" + payload.accessToken().trim() : ""),
                    payload.accessToken(),
                    blankToNull(payload.customClientId()),
                    emailProvider == EmailConfig.Provider.MSGRAPH
                            ? (!isNullOrBlank(payload.customTenantId()) ? payload.customTenantId().trim() : "common")
                            : null
            );

            OAuthFlow.ProvisionedAccounts provisioned = OAuthFlow.provisionAccounts(
                    loginResult,
                    existingEmailCfg,
                    existingCalCfg,
                    ctx.secretStore()
            );

            provisioned.emailConfig().save(emailJsonPath);
            provisioned.calendarConfig().save(calendarJsonPath);

            ctx.emailServices().reload(provisioned.emailConfig(), ctx.secretStore());
            ctx.calendarServices().setEmailConfigSupplier(ctx.emailServices()::config);
            ctx.calendarServices().reload(provisioned.calendarConfig(), ctx.secretStore());

            LOG.infof("Provisioned kirin workspace and OAuth credentials for %s (%s) at %s",
                    email, payload.provider(), ctx.userHome());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to provision kirin workspace for user %s", user.externalId);
        }
    }

    /**
     * Ensures the user's home directory, {@code .smile/agents/kirin/AGENT.md},
     * {@code .smile/agents/kirin/skills/}, {@code .smile/conf/}, {@code .smile/credentials/},
     * and isolated {@link EmailServices} / {@link CalendarServices} exist and are initialized.
     *
     * @param user authenticated OAuth user.
     * @return the user's isolated {@link UserWorkspaceContext}.
     */
    public UserWorkspaceContext ensureUserWorkspace(User user) {
        Path baseDir = resolveUsersBaseDir();
        String userKey = computeUserKey(user);
        Path userHome = baseDir.resolve(userKey).toAbsolutePath().normalize();
        if (!userHome.startsWith(baseDir)) {
            throw new SecurityException("Invalid user home path outside base directory: " + userHome);
        }

        UserWorkspaceContext ctx = workspacesByKey.computeIfAbsent(
                userKey, key -> createWorkspaceContext(user, key, userHome));
        seedKirinFiles(user, ctx);
        return ctx;
    }

    private UserWorkspaceContext createWorkspaceContext(User user, String userKey, Path userHome) {
        try {
            Path smileDir = userHome.resolve(".smile");
            Path kirinDir = smileDir.resolve("agents").resolve(KIRIN_AGENT_NAME);
            Path skillsDir = kirinDir.resolve("skills");

            Files.createDirectories(skillsDir);

            if (isOidcUser(user, userKey)) {
                // OIDC users are strictly scoped to [WebFetch, WebSearch, Skill, TodoWrite]
                // without conf/email.json, conf/calendar.json, or credentials/ secrets.
                return new UserWorkspaceContext(
                        userKey,
                        userHome,
                        smileDir,
                        kirinDir,
                        null,
                        null,
                        null
                );
            }

            Path confDir = smileDir.resolve("conf");
            Path credsDir = smileDir.resolve("credentials");
            Files.createDirectories(confDir);
            Files.createDirectories(credsDir);

            SecretStore secretStore = SharedSecrets.storeForDirectory(credsDir);
            EmailConfig emailCfg = EmailConfig.load(confDir.resolve("email.json"));
            CalendarConfig calCfg = CalendarConfig.load(confDir.resolve("calendar.json"));

            EmailServices emailServices = EmailServices.forUser(emailCfg, secretStore);
            CalendarServices calendarServices = CalendarServices.forUser(calCfg, emailServices::config, secretStore);

            return new UserWorkspaceContext(
                    userKey,
                    userHome,
                    smileDir,
                    kirinDir,
                    secretStore,
                    emailServices,
                    calendarServices
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize user home at " + userHome, e);
        }
    }

    private void seedKirinFiles(User user, UserWorkspaceContext ctx) {
        try {
            Files.createDirectories(ctx.kirinDir().resolve("skills"));
            Path agentMd = ctx.kirinDir().resolve("AGENT.md");
            if (!Files.exists(agentMd)) {
                String template = isOidcUser(user, ctx.userKey())
                        ? DEFAULT_OIDC_KIRIN_AGENT_MD
                        : DEFAULT_KIRIN_AGENT_MD;
                Files.writeString(agentMd, template, StandardCharsets.UTF_8);
            }
            Path localMd = ctx.smileDir().resolve("SMILE.local.md");
            if (user != null && user.personalInstructions != null && !user.personalInstructions.isBlank()) {
                Files.writeString(localMd, user.personalInstructions.trim() + "\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.warnf(e, "Failed to seed kirin files in %s", ctx.kirinDir());
        }
    }

    /**
     * Loads and sanitizes the user's {@code kirin} {@link Agent.Spec} from
     * {@code <userHome>/.smile/agents/kirin/AGENT.md}, enforcing the provider-appropriate
     * host-safety tool allowlist.
     *
     * @param ctx user workspace context.
     * @return sanitized {@link Agent.Spec}.
     */
    public Agent.Spec loadKirinSpec(UserWorkspaceContext ctx) {
        return loadKirinSpec(null, ctx);
    }

    /**
     * Loads and sanitizes the user's {@code kirin} {@link Agent.Spec} enforcing
     * {@link #ALLOWED_OIDC_KIRIN_TOOLS} for {@link AuthProviderKind#OIDC} users and
     * {@link #ALLOWED_KIRIN_TOOLS} for Google/Microsoft OAuth users.
     *
     * @param user authenticated user (may be {@code null}; OIDC is also detected via workspace key).
     * @param ctx  user workspace context.
     * @return sanitized {@link Agent.Spec}.
     */
    public Agent.Spec loadKirinSpec(User user, UserWorkspaceContext ctx) {
        boolean oidc = isOidcUser(user, ctx != null ? ctx.userKey() : null);
        List<String> defaultTools = oidc ? OIDC_KIRIN_TOOLS : DEFAULT_KIRIN_TOOLS;
        Set<String> allowedTools = oidc ? ALLOWED_OIDC_KIRIN_TOOLS : ALLOWED_KIRIN_TOOLS;
        try {
            Agent.Spec raw = Agent.Spec.from(ctx.kirinDir());
            List<String> requestedTools = raw.tools() == null || raw.tools().isEmpty()
                    ? defaultTools
                    : raw.tools().stream()
                            .filter(allowedTools::contains)
                            .distinct()
                            .toList();
            List<String> safeTools = requestedTools.isEmpty() ? defaultTools : requestedTools;
            return new Agent.Spec(
                    KIRIN_AGENT_NAME,
                    raw.description(),
                    raw.prompt(),
                    raw.model(),
                    safeTools,
                    List.of(),
                    raw.rules(),
                    raw.skills(),
                    KIRIN_AGENT_NAME,
                    raw.title(),
                    false
            );
        } catch (Exception e) {
            LOG.warnf(e, "Could not parse custom AGENT.md in %s; falling back to default kirin spec", ctx.kirinDir());
            return new Agent.Spec(
                    KIRIN_AGENT_NAME,
                    oidc
                            ? "Enterprise AI assistant for web research, skills, and task tracking."
                            : "Personal and small-office AI assistant for web research, email, calendar, skills, and task tracking.",
                    oidc ? DEFAULT_OIDC_KIRIN_AGENT_MD : DEFAULT_KIRIN_AGENT_MD,
                    null,
                    defaultTools,
                    List.of(),
                    List.of(),
                    List.of(),
                    KIRIN_AGENT_NAME,
                    oidc ? "Kirin Assistant" : "Kirin Personal Assistant",
                    false
            );
        }
    }

    /**
     * Instantiates an isolated {@link Agent} for the given user workspace context.
     *
     * @param ctx         user workspace context.
     * @param llm         LLM client to use for the turn.
     * @param loadedModel loaded model name.
     * @return configured {@link Agent} instance.
     */
    public Agent createAgent(UserWorkspaceContext ctx, LLM llm, String loadedModel) {
        return createAgent(null, ctx, llm, loadedModel);
    }

    /**
     * Instantiates an isolated {@link Agent} for the given user and workspace context.
     * Omits {@link EmailServices} and {@link CalendarServices} attributes for
     * {@link AuthProviderKind#OIDC} users.
     *
     * @param user        authenticated user.
     * @param ctx         user workspace context.
     * @param llm         LLM client to use for the turn.
     * @param loadedModel loaded model name.
     * @return configured {@link Agent} instance.
     */
    public Agent createAgent(User user, UserWorkspaceContext ctx, LLM llm, String loadedModel) {
        boolean oidc = isOidcUser(user, ctx != null ? ctx.userKey() : null);
        Agent.Spec spec = loadKirinSpec(user, ctx);
        Agent agent = new Agent(spec, () -> llm, ctx.userHome(), false);
        String effectiveModel = (loadedModel != null && !loadedModel.isBlank() && !"unknown".equals(loadedModel))
                ? loadedModel
                : "local-llm";
        agent.setDefaultModel(() -> Model.of(effectiveModel));
        agent.conversation().params().setProperty(LLM.MODEL, effectiveModel);
        if (!oidc && ctx.emailServices() != null) {
            agent.conversation().attributes().put(EmailServices.CONVERSATION_KEY, ctx.emailServices());
        }
        if (!oidc && ctx.calendarServices() != null) {
            agent.conversation().attributes().put(CalendarServices.CONVERSATION_KEY, ctx.calendarServices());
        }
        return agent;
    }

    private static boolean isOidcUser(User user, String userKey) {
        if (user != null && user.authProvider == AuthProviderKind.OIDC) {
            return true;
        }
        return userKey != null && userKey.startsWith("oidc-");
    }

    @Override
    public ChatCompletion complete(
            User user,
            CompletionRequest request,
            int localPort,
            String loadedModel,
            Consumer<String> chunkSink
    ) {
        UserWorkspaceContext ctx = ensureUserWorkspace(user);
        LLM llm = testLlmOverride != null ? testLlmOverride : loopbackClient(localPort);
        String modelId = (request != null && request.model != null && !request.model.isBlank())
                ? request.model.trim()
                : ((loadedModel != null && !loadedModel.isBlank()) ? loadedModel : "local-llm");

        Agent agent = createAgent(user, ctx, llm, modelId);
        if (request != null && request.temperature >= 0.0) {
            agent.conversation().params().setProperty(LLM.TEMPERATURE, Double.toString(request.temperature));
        }
        if (request != null && request.hasExplicitMaxTokens()) {
            Integer maxTok = request.maxCompletionTokens != null ? request.maxCompletionTokens : request.maxTokens;
            if (maxTok != null && maxTok > 0) {
                agent.conversation().params().setProperty(LLM.MAX_OUTPUT_TOKENS, Integer.toString(maxTok));
            }
        }
        if (request != null && request.reasoningEffort != null && !request.reasoningEffort.isBlank()) {
            agent.conversation().params().setProperty(LLM.REASONING_EFFORT, request.reasoningEffort.trim());
        }

        String prompt = populateHistoryAndExtractPrompt(agent, request);
        StringBuilder fullResponse = new StringBuilder();
        CompletableFuture<ChatCompletion> doneFuture = new CompletableFuture<>();

        agent.stream(prompt, new StreamResponseHandler() {
            @Override
            public void onNext(String chunk) {
                if (chunk == null || chunk.isEmpty()) {
                    return;
                }
                synchronized (fullResponse) {
                    fullResponse.append(chunk);
                }
                if (chunkSink != null) {
                    chunkSink.accept(chunk);
                }
            }

            @Override
            public void onComplete(long totalTokens, long outputTokens, long inputTokens) {
                String text;
                synchronized (fullResponse) {
                    text = fullResponse.toString();
                }
                doneFuture.complete(new ChatCompletion(
                        modelId,
                        text,
                        new int[0],
                        new int[0],
                        FinishReason.stop,
                        null
                ));
            }

            @Override
            public void onException(Throwable ex) {
                doneFuture.completeExceptionally(ex);
            }

            @Override
            public void onStatus(String status) {
                LOG.debugf("Kirin [%s]: %s", ctx.userKey(), status);
            }

            @Override
            public void onQuestion(Question question) {
                // In interactive web chat, kirin only runs for the authenticated OAuth user
                // acting on their own session; auto-approve so the unidirectional SSE turn
                // does not block waiting for a desktop Swing modal dialog.
                if (question != null) {
                    question.complete("Approve");
                }
            }
        }, llm, Model.of(modelId));

        try {
            return doneFuture.get(300, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Kirin agent turn failed: " + e.getMessage(), e);
        }
    }

    private String populateHistoryAndExtractPrompt(Agent agent, CompletionRequest request) {
        if (request == null || request.messages == null || request.messages.length == 0) {
            return "";
        }
        int lastUserIdx = -1;
        for (int i = request.messages.length - 1; i >= 0; i--) {
            if (request.messages[i] != null && request.messages[i].role() == Role.user) {
                lastUserIdx = i;
                break;
            }
        }
        if (lastUserIdx < 0) {
            lastUserIdx = request.messages.length - 1;
        }

        List<String> customSystemPrompts = new ArrayList<>();
        for (int i = 0; i < lastUserIdx; i++) {
            var m = request.messages[i];
            if (m == null) {
                continue;
            }
            String text = m.content() != null ? m.content().trim() : "";
            if (text.isEmpty()) {
                continue;
            }
            if (m.role() == Role.system) {
                if (!DEFAULT_WEB_UI_SYSTEM_PROMPT.equals(text)) {
                    customSystemPrompts.add(text);
                }
            } else if (m.role() == Role.user) {
                agent.conversation().add(ioa.llm.Message.user(text));
            } else if (m.role() == Role.assistant) {
                agent.conversation().add(ioa.llm.Message.assistant(text));
            }
        }

        String latestPrompt = request.messages[lastUserIdx] != null && request.messages[lastUserIdx].content() != null
                ? request.messages[lastUserIdx].content()
                : "";
        if (!customSystemPrompts.isEmpty() && agent.conversation().messages().isEmpty()) {
            // If the caller supplied custom system instructions on the request (and not via SMILE.local.md),
            // prepend them as context on the first turn.
            return String.join("\n\n", customSystemPrompts) + "\n\n" + latestPrompt;
        }
        return latestPrompt;
    }

    private ChatCompletions loopbackClient(int localPort) {
        int port = localPort > 0 ? localPort : 8080;
        return loopbackClientsByPort.computeIfAbsent(port, p -> {
            String baseUrl = "http://127.0.0.1:" + p + "/api/v1";
            OpenAIClientAsync client = OpenAIOkHttpClientAsync.builder()
                    .baseUrl(baseUrl)
                    .apiKey("kirin-internal-loopback")
                    .putHeader(InternalAgentAuth.HEADER_TOKEN, InternalAgentAuth.token())
                    .timeout(Timeout.builder()
                            .connect(Duration.ofMinutes(1))
                            .read(Duration.ofMinutes(10))
                            .request(Duration.ZERO)
                            .build())
                    .build();
            return new ChatCompletions(client);
        });
    }

    public Path resolveUsersBaseDir() {
        String raw = authConfig != null ? authConfig.usersHomeDir() : null;
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("smile.home.users",
                    System.getenv().getOrDefault("SMILE_USERS_DIR", "/smile/users"));
        }
        return Path.of(raw).toAbsolutePath().normalize();
    }

    /**
     * Computes a deterministic, collision-resistant, filesystem-safe directory name
     * for {@code user} under {@code /smile/users/}.
     *
     * @param user authenticated user.
     * @return sanitized directory key (e.g. {@code google-alice_at_gmail.com-a1b2c3d4}).
     */
    public static String computeUserKey(User user) {
        String providerPrefix = (user != null && user.authProvider != null)
                ? user.authProvider.name().toLowerCase(Locale.ROOT)
                : "user";
        String rawIdentity = (user != null && user.email != null && !user.email.isBlank())
                ? user.email.trim().toLowerCase(Locale.ROOT)
                : ((user != null && user.externalId != null && !user.externalId.isBlank())
                        ? user.externalId.trim()
                        : "anonymous");
        String safeSlug = rawIdentity
                .replace("@", "_at_")
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (safeSlug.isEmpty() || ".".equals(safeSlug) || "..".equals(safeSlug)) {
            safeSlug = "account";
        }
        if (safeSlug.length() > 48) {
            safeSlug = safeSlug.substring(0, 48);
        }
        String uniqueSeed = providerPrefix + ":" + (user != null && user.externalId != null ? user.externalId : rawIdentity);
        String shortHash = sha256HexPrefix(uniqueSeed, 4);
        return providerPrefix + "-" + safeSlug + "-" + shortHash;
    }

    private static String sha256HexPrefix(String input, int numBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, Math.min(numBytes, digest.length));
        } catch (Exception e) {
            return "00000000";
        }
    }

    private static boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isNullOrBlank(s) ? null : s.trim();
    }
}
