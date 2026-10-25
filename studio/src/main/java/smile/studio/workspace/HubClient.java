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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        String h = (host == null || host.isBlank() || "0.0.0.0".equals(host.trim())) ? "127.0.0.1" : host.trim();
        this.baseUrl = "http://" + h + ":" + port;
        this.adminToken = adminToken == null ? "dev-admin-token" : adminToken.trim();
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
     * Fetches the HTML content of a Bot Page (`GET /v1/pages/{slug}`).
     */
    public String getPageHtml(String slug) throws IOException, InterruptedException {
        var response = get("/v1/pages/" + encode(slug), false);
        if (response.statusCode() != 200) {
            throw new IOException("Failed to load Bot Page '" + slug + "' (HTTP " + response.statusCode() + ")");
        }
        return response.body();
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
