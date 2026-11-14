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

import java.awt.Component;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import ioa.agent.bot.BotsConfig;
import ioa.agent.bot.ProjectsConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client for the Agent Hub &amp; Bot Service REST API ({@code /v1/admin/*},
 * {@code /v1/pages/*}, {@code /q/health}, and {@code /q/metrics}).
 *
 * <p>Works identically against a local child process launched by {@link HubManager}
 * or a remote Agent Hub server. When the Hub is not yet running, configuration
 * read/write operations gracefully fall back to {@code ${smile.home}/conf/bots.json}
 * so the user can edit Bots and Routines offline in SMILE Studio.
 *
 * @author Haifeng Li
 */
public final class HubClient {
    private static final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Runtime metadata summary for one routine from {@codeGET /v1/admin/bots}. */
    public record RoutineRuntimeMeta(
            String routineId,
            String botId,
            String persona,
            String lastFiredAt,
            Long lastRunId,
            String lastRunStatus,
            String lastRunAt,
            int consecutiveFailures,
            String lastError
    ) { }

    /** Overview response from {@code GET /v1/admin/bots}. */
    public record BotsOverview(
            BotsConfig config,
            List<RoutineRuntimeMeta> runtimeMetadata,
            int pendingApprovalCount
    ) { }

    /** Run record from {@code GET /v1/admin/runs}. */
    public record BotRunSummary(
            Long id,
            String botId,
            String routineId,
            String persona,
            String modelId,
            Long eventRecordId,
            String status,
            String startedAt,
            String completedAt,
            String conversationPath,
            String summary,
            String error
    ) { }

    /** Approval item from {@code GET /v1/admin/approvals}. */
    public record BotApprovalItem(
            Long id,
            Long runId,
            String botId,
            String routineId,
            String persona,
            String kind,
            String channel,
            String recipient,
            String subject,
            String payloadJson,
            String previousPayloadJson,
            String status,
            String resolutionJson,
            String expiresAt,
            String createdAt,
            String resolvedAt
    ) { }

    /** Interactive HTML Bot Page summary from {@code GET /v1/pages}. */
    public record BotPageSummary(
            String slug,
            String title,
            String path,
            String updatedAt
    ) { }

    /** Live Server-Sent Event notification from {@code GET /v1/admin/events/stream}. */
    public record BotStreamEvent(
            String type,
            Long runId,
            String botId,
            String routineId,
            String pageSlug,
            String timestamp
    ) {
        public BotStreamEvent(String type, Long runId, String botId, String routineId, String timestamp) {
            this(type, runId, botId, routineId, "", timestamp);
        }
    }

    /** Result of submitting a peer request to {@code POST /v1/admin/requests}. */
    public record RequestSubmission(Long runId, String status, String error) {
        /** Returns true when the Hub accepted the request and returned a run ID. */
        public boolean accepted() {
            return error == null && runId != null;
        }
    }

    private final HttpClient client;
    private final String baseUrl;
    private final String adminToken;

    /**
     * Constructor targeting localhost on the given port.
     *
     * @param port       the hub HTTP port.
     * @param adminToken the admin bearer token.
     */
    public HubClient(int port, String adminToken) {
        this("127.0.0.1", port, adminToken);
    }

    /**
     * Constructor targeting a specific host and port.
     *
     * @param host       the hub host.
     * @param port       the hub HTTP port.
     * @param adminToken the admin bearer token.
     */
    public HubClient(String host, int port, String adminToken) {
        this("http://" + ((host == null || host.isBlank() || "0.0.0.0".equals(host.trim())) ? "127.0.0.1" : host.trim()) + ":" + port,
                adminToken);
    }

    /**
     * Constructor targeting an explicit Hub HTTP base URL.
     *
     * @param baseUrl    the hub HTTP base URL (e.g. {@code "http://lab:8890"}).
     * @param adminToken the admin bearer token.
     */
    public HubClient(String baseUrl, String adminToken) {
        String url = (baseUrl == null || baseUrl.isBlank())
                ? "http://127.0.0.1:8890"
                : baseUrl.trim().replaceAll("/+$", "");
        this.baseUrl = url;
        this.adminToken = adminToken == null || adminToken.isBlank() ? "dev-admin-token" : adminToken.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    /** Returns the base URL of the Agent Hub. */
    public String baseUrl() {
        return baseUrl;
    }

    /** Returns true when {@code GET /q/health} returns HTTP 200 with status UP or DEGRADED. */
    public boolean isReady() {
        try {
            var response = get("/q/health", false);
            return response.statusCode() == 200
                    && (response.body().contains("\"UP\"") || response.body().contains("\"DEGRADED\""));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Fetches the current {@link BotsOverview} from the running Hub, or falls back
     * to loading {@code ${smile.home}/conf/bots.json} from disk if the Hub is offline.
     */
    public BotsOverview getOverview() {
        try {
            var response = get("/v1/admin/bots", true);
            if (response.statusCode() == 200) {
                return parseOverview(response.body());
            }
        } catch (Exception ignored) {
        }
        BotsConfig cfg = BotsConfig.load();
        return new BotsOverview(cfg, List.of(), 0);
    }

    /**
     * Saves the full {@link BotsConfig} (updating the running Hub if online, and always
     * persisting to {@code ${smile.home}/conf/bots.json} on disk).
     */
    public BotsOverview saveConfig(BotsConfig config) throws IOException, InterruptedException {
        config.save();
        if (isReady()) {
            var response = putJson("/v1/admin/bots", config.toJson());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseOverview(response.body());
            }
        }
        return new BotsOverview(config, List.of(), 0);
    }

    /**
     * Enables or disables a Routine in {@code bots.json} (and hot-reloads the Hub if running).
     */
    public BotsOverview setRoutineEnabled(String botId, String routineId, boolean enabled)
            throws IOException, InterruptedException {
        if (isReady()) {
            String path = "/v1/admin/bots/" + encode(botId) + "/routines/" + encode(routineId)
                    + (enabled ? "/enable" : "/disable");
            var response = postJson(path, "{}");
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseOverview(response.body());
            }
        }
        BotsConfig updated = BotsConfig.load().withRoutineEnabled(botId, routineId, enabled);
        updated.save();
        return new BotsOverview(updated, List.of(), 0);
    }

    /**
     * Triggers an immediate manual execution ("Run Now") of a Routine on the running Hub.
     */
    public List<BotRunSummary> runRoutineNow(String botId, String routineId)
            throws IOException, InterruptedException {
        String path = "/v1/admin/bots/" + encode(botId) + "/routines/" + encode(routineId) + "/run";
        var response = postJson(path, "{}");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Run Now failed (HTTP " + response.statusCode() + "): " + response.body());
        }
        return parseRuns(response.body());
    }

    /**
     * Syncs tracked Studio projects (`${smile.home}/conf/projects.json`) to the running Hub.
     */
    public void syncProjects(List<ProjectsConfig.ProjectEntry> projects)
            throws IOException, InterruptedException {
        String json = mapper.writeValueAsString(projects == null ? List.of() : projects);
        putJson("/v1/admin/projects", json);
    }

    /**
     * Queries Bot Run history from the running Hub.
     */
    public List<BotRunSummary> listRuns(String botId, String routineId, int limit) {
        try {
            StringBuilder path = new StringBuilder("/v1/admin/runs?limit=").append(Math.max(1, limit));
            if (botId != null && !botId.isBlank()) {
                path.append("&botId=").append(encode(botId));
            }
            if (routineId != null && !routineId.isBlank()) {
                path.append("&routineId=").append(encode(routineId));
            }
            var response = get(path.toString(), true);
            if (response.statusCode() == 200) {
                return parseRuns(response.body());
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    /**
     * Fetches the transcript for a specific run ID.
     */
    public String getTranscript(long runId) throws IOException, InterruptedException {
        var response = get("/v1/admin/runs/" + runId + "/transcript", true);
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch transcript (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    /**
     * Cancels an in-flight run.
     */
    public void cancelRun(long runId) throws IOException, InterruptedException {
        postJson("/v1/admin/runs/" + runId + "/cancel", "{}");
    }

    /**
     * Finds a run summary by run ID from the running Hub.
     *
     * @param runId the run ID.
     * @return the matching run summary, if found.
     */
    public Optional<BotRunSummary> findRun(long runId) {
        return listRuns(null, null, 100).stream()
                .filter(r -> r.id() != null && r.id() == runId)
                .findFirst();
    }

    /**
     * Submits a peer request to {@code POST /v1/admin/requests} on the Agent Hub.
     *
     * @param from           sender call-out name (e.g. {@code "frank#studio"}).
     * @param to             recipient call-out name on the Hub (any {@code #<instance>} fragment is stripped).
     * @param task           task prompt.
     * @param artifacts      optional workspace-relative or absolute paths.
     * @param expiresAt      optional RFC 3339 UTC start-by deadline ending in {@code Z}.
     * @param idempotencyKey optional client-chosen idempotency key.
     * @return the submission result with {@code runId} when accepted, or an error message.
     */
    public RequestSubmission submitRequest(
            String from,
            String to,
            String task,
            List<String> artifacts,
            String expiresAt,
            String idempotencyKey
    ) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("from", from == null || from.isBlank() ? "studio" : from.trim());
            payload.put("to", ioa.agent.AgentNames.baseName(to));
            payload.put("task", task == null ? "" : task);
            if (artifacts != null && !artifacts.isEmpty()) {
                var arr = mapper.createArrayNode();
                for (String path : artifacts) {
                    if (path != null) {
                        arr.add(path);
                    }
                }
                payload.set("artifacts", arr);
            }
            if (expiresAt != null && !expiresAt.isBlank()) {
                payload.put("expires_at", expiresAt.trim());
            }
            String body = mapper.writeValueAsString(payload);
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/admin/requests"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!adminToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + adminToken);
            }
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                builder.header("Idempotency-Key", idempotencyKey.trim());
            }
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 202 || status == 200) {
                JsonNode root = mapper.readTree(response.body());
                Long runId = root != null && root.hasNonNull("runId") ? root.get("runId").asLong() : null;
                String runStatus = text(root, "status");
                return new RequestSubmission(runId, runStatus.isEmpty() ? "RUNNING" : runStatus, null);
            }
            String detail = extractProblemDetail(response.body());
            if (detail.isEmpty()) {
                detail = "Agent Hub rejected the request (HTTP " + status + ").";
            }
            return new RequestSubmission(null, null, detail);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new RequestSubmission(null, null, "Agent Hub is not running at " + baseUrl + ".");
        }
    }

    private static String extractProblemDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode root = mapper.readTree(body);
            String detail = text(root, "detail");
            if (!detail.isBlank()) {
                return detail;
            }
            return text(root, "title");
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Lists approval items from the running Hub.
     *
     * @param status optional status filter (`"PENDING"`, or null/empty for all).
     */
    public List<BotApprovalItem> listApprovals(String status, int limit) {
        try {
            StringBuilder path = new StringBuilder("/v1/admin/approvals?limit=").append(Math.max(1, limit));
            if (status != null && !status.isBlank()) {
                path.append("&status=").append(encode(status));
            }
            var response = get(path.toString(), true);
            if (response.statusCode() == 200) {
                return parseApprovals(response.body());
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    /**
     * Resolves a single approval (`ALLOW_ONCE`, `ALWAYS_ALLOW_RULE`, `EDIT_AND_APPROVE`, or `DENY`).
     */
    public void resolveApproval(long approvalId, String decision, String editedPayloadJson, String rulePattern)
            throws IOException, InterruptedException {
        String body = mapper.writeValueAsString(Map.of(
                "decision", decision == null ? "ALLOW_ONCE" : decision,
                "editedPayloadJson", editedPayloadJson == null ? "" : editedPayloadJson,
                "rulePattern", rulePattern == null ? "" : rulePattern
        ));
        var response = postJson("/v1/admin/approvals/" + approvalId + "/resolve", body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to resolve approval (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    /**
     * Batch-resolves multiple approval items from the Batch Draft Queue.
     */
    public void batchResolveApprovals(List<Long> approvalIds, String decision, Map<String, String> editsById)
            throws IOException, InterruptedException {
        String body = mapper.writeValueAsString(Map.of(
                "approvalIds", approvalIds == null ? List.of() : approvalIds,
                "decision", decision == null ? "ALLOW_ONCE" : decision,
                "editsById", editsById == null ? Map.of() : editsById
        ));
        var response = postJson("/v1/admin/approvals/batch-resolve", body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Batch resolve failed (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    /**
     * Lists available interactive HTML Bot Pages (`GET /v1/pages`).
     */
    public List<BotPageSummary> listPages() {
        try {
            var response = get("/v1/pages", false);
            if (response.statusCode() == 200) {
                JsonNode arr = mapper.readTree(response.body());
                if (arr != null && arr.isArray()) {
                    List<BotPageSummary> list = new ArrayList<>();
                    for (JsonNode n : arr) {
                        list.add(new BotPageSummary(
                                text(n, "slug"),
                                text(n, "title"),
                                text(n, "path"),
                                text(n, "updatedAt")
                        ));
                    }
                    return list;
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    /**
     * Returns available Bot Pages from the running Agent Hub, falling back to
     * scanning {@code ${smile.home}/bot/pages/} and the standard built-in page
     * slugs when the Hub is offline.
     */
    public List<BotPageSummary> listAvailablePages() {
        if (isReady()) {
            List<BotPageSummary> online = listPages();
            if (!online.isEmpty()) {
                return online;
            }
        }
        return loadOfflinePages();
    }

    /**
     * Returns the default offline Bot Pages list, augmented with any custom
     * {@code *.html} files discovered in {@code ${smile.home}/bot/pages/}.
     */
    public static List<BotPageSummary> loadOfflinePages() {
        String home = System.getProperty("smile.home", ".");
        Path pagesDir = Path.of(home, "bot", "pages");
        Map<String, BotPageSummary> bySlug = new LinkedHashMap<>();
        bySlug.put("executive-brief", new BotPageSummary("executive-brief", "Executive Brief — @frank",
                pagesDir.resolve("executive-brief.html").toString(), ""));
        bySlug.put("sales-pipeline", new BotPageSummary("sales-pipeline", "Sales Pipeline — @mike",
                pagesDir.resolve("sales-pipeline.html").toString(), ""));
        bySlug.put("community-radar", new BotPageSummary("community-radar", "Community Radar — @julien",
                pagesDir.resolve("community-radar.html").toString(), ""));
        bySlug.put("bot-activity", new BotPageSummary("bot-activity", "Bot Activity",
                pagesDir.resolve("bot-activity.html").toString(), ""));

        if (Files.isDirectory(pagesDir)) {
            try (Stream<Path> stream = Files.list(pagesDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".html"))
                        .sorted()
                        .forEach(p -> {
                            String fileName = p.getFileName().toString();
                            String slug = fileName.substring(0, fileName.length() - 5);
                            bySlug.computeIfAbsent(slug, s -> new BotPageSummary(s, s, p.toString(), ""));
                        });
            } catch (IOException ignored) {
            }
        }
        return new ArrayList<>(bySlug.values());
    }

    /**
     * Resolves the primary Bot Page associated with a Bot persona or bot ID, if any.
     *
     * @param bot the Bot specification.
     * @return the matching {@link BotPageSummary}, if mapped.
     */
    public static Optional<BotPageSummary> pageForBot(BotsConfig.BotSpec bot) {
        if (bot == null) {
            return Optional.empty();
        }
        String slug = primaryPageSlugForBot(bot.botId(), bot.persona(), null);
        if (slug == null) {
            return Optional.empty();
        }
        final String targetSlug = slug;
        return loadOfflinePages().stream()
                .filter(p -> targetSlug.equals(p.slug()))
                .findFirst();
    }

    static String primaryPageSlugForBot(String botId, String persona, String routineId) {
        String cleanBot = normalizeBotToken(botId);
        String cleanPersona = normalizeBotToken(persona);
        String cleanRoutine = routineId == null ? "" : routineId.trim().toLowerCase();
        if (cleanBot.isEmpty() && cleanRoutine.startsWith("peer-request:")) {
            cleanBot = normalizeBotToken(cleanRoutine.substring("peer-request:".length()));
        }
        if ("frank".equals(cleanBot) || "chief-of-staff".equals(cleanPersona) || cleanRoutine.startsWith("frank-")) {
            return "executive-brief";
        }
        if ("mike".equals(cleanBot) || "sales".equals(cleanPersona) || cleanRoutine.startsWith("mike-")) {
            return "sales-pipeline";
        }
        if ("julien".equals(cleanBot) || "evangelist".equals(cleanPersona) || cleanRoutine.startsWith("julien-")) {
            return "community-radar";
        }
        if ("*".equals(cleanPersona) || cleanBot.contains("all-agents")) {
            return "bot-activity";
        }
        return null;
    }

    private static String normalizeBotToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim().toLowerCase();
        if (s.startsWith("peer-request:")) {
            s = s.substring("peer-request:".length()).trim();
        } else if (s.startsWith("peer:")) {
            s = s.substring("peer:".length()).trim();
        }
        if (s.startsWith("@")) {
            s = s.substring(1).trim();
        }
        int hashIdx = s.indexOf('#');
        if (hashIdx >= 0) {
            s = s.substring(0, hashIdx).trim();
        }
        return s;
    }

    /**
     * Returns {@code true} if the given {@link BotStreamEvent} is a {@code "page"}
     * update event that should automatically open in the system browser.
     * In particular, {@code "bot-activity"} is only auto-opened when updated by
     * {@code "frank-bot-activity-recap"}, and canonical per-bot pages
     * ({@code "executive-brief"}, {@code "sales-pipeline"}, {@code "community-radar"})
     * are only auto-opened when the event belongs to that owning bot.
     *
     * @param ev the stream event to evaluate.
     * @return {@code true} if the page should be opened in the browser.
     */
    public static boolean shouldAutoOpenPageEvent(BotStreamEvent ev) {
        if (ev == null || !"page".equals(ev.type()) || ev.pageSlug() == null || ev.pageSlug().isBlank()) {
            return false;
        }
        String slug = ev.pageSlug().trim();
        if ("bot-activity".equalsIgnoreCase(slug)) {
            return "frank-bot-activity-recap".equalsIgnoreCase(
                    ev.routineId() == null ? "" : ev.routineId().trim());
        }
        if ("executive-brief".equalsIgnoreCase(slug)
                || "sales-pipeline".equalsIgnoreCase(slug)
                || "community-radar".equalsIgnoreCase(slug)) {
            String expectedSlug = primaryPageSlugForBot(ev.botId(), null, ev.routineId());
            if (expectedSlug != null && !expectedSlug.equalsIgnoreCase(slug)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Opens the specified Bot Page in the system's default web browser.
     * Uses {@code /v1/pages/<slug>} when Agent Hub is running, or falls back to
     * the local HTML file on disk when offline.
     *
     * @param parent optional parent component for information dialogs.
     * @param page   the Bot Page summary to open.
     */
    public void openPageInBrowser(Component parent, BotPageSummary page) {
        if (page == null) {
            return;
        }
        Thread.ofPlatform().name("bot-page-open").daemon(true).start(() -> {
            try {
                URI targetUri = null;
                if (isReady()) {
                    targetUri = URI.create(baseUrl + "/v1/pages/" + encode(page.slug()));
                } else {
                    Path localPath = page.path() != null && !page.path().isBlank()
                            ? Path.of(page.path())
                            : Path.of(System.getProperty("smile.home", "."), "bot", "pages", page.slug() + ".html");
                    if (Files.isRegularFile(localPath)) {
                        targetUri = localPath.toUri();
                    }
                }

                if (targetUri != null && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(targetUri);
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            parent,
                            "Start Agent Hub to generate and view '" + page.title() + "'.",
                            "Agent Hub",
                            JOptionPane.INFORMATION_MESSAGE));
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Fetches the HTML content of a Bot Page (`GET /v1/pages/{slug}`).
     */
    public String getPageHtml(String slug) throws IOException, InterruptedException {
        var response = get("/v1/pages/" + encode(slug), false);
        if (response.statusCode() != 200) {
            throw new IOException("Failed to load Bot Page '" + slug + "' (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    /**
     * Connects to the Server-Sent Events stream ({@code GET /v1/admin/events/stream})
     * and dispatches each event to {@code consumer} until the stream closes or the
     * calling thread is interrupted.
     *
     * @param consumer callback invoked for each received {@link BotStreamEvent}.
     * @throws IOException          if an I/O or HTTP error occurs.
     * @throws InterruptedException if interrupted while streaming.
     */
    public void streamEvents(java.util.function.Consumer<BotStreamEvent> consumer)
            throws IOException, InterruptedException {
        if (consumer == null) {
            return;
        }
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/admin/events/stream"))
                .header("Accept", "text/event-stream")
                .GET();
        if (!adminToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + adminToken);
        }
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() != 200) {
            throw new IOException("SSE connection failed (HTTP " + response.statusCode() + ")");
        }
        try (Stream<String> lines = response.body()) {
            String eventName = null;
            StringBuilder dataBuf = new StringBuilder();
            var it = lines.iterator();
            while (it.hasNext() && !Thread.currentThread().isInterrupted()) {
                String line = it.next();
                if (line.isEmpty()) {
                    if (!dataBuf.isEmpty()) {
                        BotStreamEvent ev = parseStreamEvent(eventName, dataBuf.toString().trim());
                        if (ev != null) {
                            consumer.accept(ev);
                        }
                        dataBuf.setLength(0);
                    }
                    eventName = null;
                } else if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (!dataBuf.isEmpty()) {
                        dataBuf.append('\n');
                    }
                    dataBuf.append(line.substring(5).trim());
                }
            }
        }
    }

    static BotStreamEvent parseStreamEvent(String eventName, String data) {
        if (data == null || data.isBlank()) {
            return eventName != null && !eventName.isBlank()
                    ? new BotStreamEvent(eventName, null, null, null, "")
                    : null;
        }
        try {
            JsonNode n = mapper.readTree(data);
            String type = text(n, "type");
            if (type.isBlank() && eventName != null) {
                type = eventName;
            }
            Long runId = n.hasNonNull("runId") ? n.get("runId").asLong() : null;
            return new BotStreamEvent(
                    type,
                    runId,
                    text(n, "botId"),
                    text(n, "routineId"),
                    text(n, "pageSlug"),
                    text(n, "timestamp")
            );
        } catch (Exception ex) {
            return new BotStreamEvent(
                    eventName != null && !eventName.isBlank() ? eventName : "message",
                    null, "", "", "", "");
        }
    }

    public HttpResponse<String> get(String path, boolean auth) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (auth && !adminToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + adminToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> postJson(String path, String jsonBody) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
        if (!adminToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + adminToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> putJson(String path, String jsonBody) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
        if (!adminToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + adminToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static BotsOverview parseOverview(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        JsonNode cfgNode = root.get("config");
        BotsConfig cfg = cfgNode != null && !cfgNode.isNull()
                ? mapper.treeToValue(cfgNode, BotsConfig.class)
                : BotsConfig.load();
        List<RoutineRuntimeMeta> metaList = new ArrayList<>();
        JsonNode metaArr = root.get("runtimeMetadata");
        if (metaArr != null && metaArr.isArray()) {
            for (JsonNode m : metaArr) {
                metaList.add(new RoutineRuntimeMeta(
                        text(m, "routineId"),
                        text(m, "botId"),
                        text(m, "persona"),
                        text(m, "lastFiredAt"),
                        m.hasNonNull("lastRunId") ? m.get("lastRunId").asLong() : null,
                        text(m, "lastRunStatus"),
                        text(m, "lastRunAt"),
                        m.hasNonNull("consecutiveFailures") ? m.get("consecutiveFailures").asInt() : 0,
                        text(m, "lastError")
                ));
            }
        }
        int pending = root.hasNonNull("pendingApprovalCount") ? root.get("pendingApprovalCount").asInt() : 0;
        return new BotsOverview(cfg, metaList, pending);
    }

    private static List<BotRunSummary> parseRuns(String body) throws IOException {
        JsonNode arr = mapper.readTree(body);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<BotRunSummary> list = new ArrayList<>();
        for (JsonNode n : arr) {
            list.add(new BotRunSummary(
                    n.hasNonNull("id") ? n.get("id").asLong() : null,
                    text(n, "botId"),
                    text(n, "routineId"),
                    text(n, "persona"),
                    text(n, "modelId"),
                    n.hasNonNull("eventRecordId") ? n.get("eventRecordId").asLong() : null,
                    text(n, "status"),
                    text(n, "startedAt"),
                    text(n, "completedAt"),
                    text(n, "conversationPath"),
                    text(n, "summary"),
                    text(n, "error")
            ));
        }
        return list;
    }

    private static List<BotApprovalItem> parseApprovals(String body) throws IOException {
        JsonNode arr = mapper.readTree(body);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<BotApprovalItem> list = new ArrayList<>();
        for (JsonNode n : arr) {
            list.add(new BotApprovalItem(
                    n.hasNonNull("id") ? n.get("id").asLong() : null,
                    n.hasNonNull("runId") ? n.get("runId").asLong() : null,
                    text(n, "botId"),
                    text(n, "routineId"),
                    text(n, "persona"),
                    text(n, "kind"),
                    text(n, "channel"),
                    text(n, "recipient"),
                    text(n, "subject"),
                    text(n, "payloadJson"),
                    text(n, "previousPayloadJson"),
                    text(n, "status"),
                    text(n, "resolutionJson"),
                    text(n, "expiresAt"),
                    text(n, "createdAt"),
                    text(n, "resolvedAt")
            ));
        }
        return list;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return "";
        }
        return node.get(field).asString("");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
