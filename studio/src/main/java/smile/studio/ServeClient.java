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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A minimal HTTP client for the SMILE Serve REST API.
 *
 * <p>The client targets the engine at the configured {@code host:port}, so it can
 * talk to a locally launched serve instance or to a shared/remote one. Note that
 * the model-management endpoints ({@code /v1/models/load},
 * {@code /v1/smile/{id}/reload}, {@code /v1/smile/{id}/unload}) are {@code @LocalhostOnly} on the
 * server: a remote engine answers {@code 403}, which this class reports as
 * {@link ServeManagementForbiddenException} rather than a generic failure.
 *
 * @author Haifeng Li
 */
public final class ServeClient {
    private static final ObjectMapper mapper = new ObjectMapper();
    /** Connect timeout for a full request such as loading a model. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Read timeout for a full request. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    /**
     * Probe timeouts. A periodic probe runs every few seconds against a host that
     * may be an unresponsive remote machine, so it must give up quickly rather
     * than tie up a thread for the full request timeout.
     */
    private static final Duration PROBE_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private final String baseUrl;
    /** Client for full requests. */
    private final HttpClient client;
    /** Client and read timeout used by the liveness/readiness probes. */
    private final HttpClient probeClient;
    private final Duration probeTimeout;

    /**
     * Constructor that targets loopback.
     *
     * @param port the serve HTTP port.
     */
    public ServeClient(int port) {
        this("127.0.0.1", port);
    }

    /**
     * Constructor.
     *
     * <p>A wildcard bind address is redirected to loopback: {@code 0.0.0.0} is a
     * listen-everywhere instruction, not a destination. Any explicitly configured
     * remote host is used as written.
     *
     * @param host the serve host.
     * @param port the serve HTTP port.
     */
    public ServeClient(String host, int port) {
        String h = LocalHosts.connectHost(host);
        // URL literals need brackets around IPv6 addresses.
        if (h.contains(":") && !h.startsWith("[")) {
            h = "[" + h + "]";
        }
        this.baseUrl = "http://" + h + ":" + port;
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.probeClient = HttpClient.newBuilder()
                .connectTimeout(PROBE_CONNECT_TIMEOUT)
                .build();
        this.probeTimeout = PROBE_TIMEOUT;
    }

    /**
     * Returns the base URL used for requests.
     *
     * @return the base URL.
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Sends a GET request.
     *
     * @param path the request path (e.g. {@code /q/health/ready}).
     * @return the response.
     * @throws IOException          on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends a short-timeout GET used by the health probes.
     *
     * @param path the probe path.
     * @return the response, or empty when the engine did not answer in time.
     */
    private Optional<HttpResponse<String>> probe(String path) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(probeTimeout)
                    .GET()
                    .build();
            return Optional.of(probeClient.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    /**
     * Sends a POST request with a JSON body.
     *
     * @param path the request path.
     * @param body the JSON body.
     * @return the response.
     * @throws IOException          on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public HttpResponse<String> post(String path, Map<String, Object> body)
            throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Loads a model into the running serve instance.
     *
     * <p>A {@code 409 Conflict} means a model with the same id is already
     * loaded; that is treated as success.
     *
     * @param modelPath the model file or directory path.
     * @return the loaded model id, or {@code null} when the response carries none.
     * @throws ServeManagementForbiddenException when the server rejects a
     *         non-loopback caller ({@code 403}).
     * @throws IOException          on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public String loadModel(String modelPath) throws IOException, InterruptedException {
        var response = post("/v1/models/load", Map.of("model", modelPath, "kind", "auto"));
        int status = response.statusCode();
        if (status == 409) {
            return null;
        }
        if (status == 403) {
            throw forbidden("load a model");
        }
        if (status < 200 || status >= 300) {
            throw new IOException("Failed to load model (HTTP " + status + "): " + response.body());
        }
        JsonNode node = mapper.readTree(response.body());
        if (node.has("id")) {
            return node.get("id").asString();
        }
        if (node.has("ids") && node.get("ids").isArray() && !node.get("ids").isEmpty()) {
            return node.get("ids").get(0).asString();
        }
        return null;
    }

    /**
     * Unloads a model, trying the SMILE endpoint first and the ONNX endpoint second.
     *
     * @param id the model id.
     * @return {@code true} when the model was unloaded.
     * @throws ServeManagementForbiddenException when the server rejects a
     *         non-loopback caller ({@code 403}).
     * @throws IOException          on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public boolean unloadModel(String id) throws IOException, InterruptedException {
        var response = post("/v1/smile/" + id + "/unload", Map.of());
        if (response.statusCode() == 404) {
            response = post("/v1/onnx/" + id + "/unload", Map.of());
        }
        if (response.statusCode() == 403) {
            throw forbidden("unload a model");
        }
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /**
     * Reloads a model from disk, trying the SMILE endpoint first and the ONNX endpoint second.
     *
     * @param id the model id.
     * @return {@code true} when the model was reloaded.
     * @throws ServeManagementForbiddenException when the server rejects a
     *         non-loopback caller ({@code 403}).
     * @throws IOException          on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public boolean reloadModel(String id) throws IOException, InterruptedException {
        var response = post("/v1/smile/" + id + "/reload", Map.of());
        if (response.statusCode() == 404) {
            response = post("/v1/onnx/" + id + "/reload", Map.of());
        }
        if (response.statusCode() == 403) {
            throw forbidden("reload a model");
        }
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /**
     * Returns the ids of the models currently available in the engine.
     *
     * <p>An unreachable engine and an empty catalog both produce an empty list.
     * Use {@link #fetchModels()} where the difference matters — for example a
     * drift check must not mistake a transient failure for "no models loaded".
     *
     * @return the model ids, or an empty list when the catalog cannot be read.
     */
    public List<String> listModels() {
        return fetchModels().orElseGet(List::of);
    }

    /**
     * Fetches the model catalog, distinguishing an empty catalog from a failure.
     *
     * @return the model ids (possibly empty), or empty when the engine did not
     *         answer or returned a non-200 status.
     */
    public Optional<List<String>> fetchModels() {
        try {
            var response = get("/v1/models");
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.of(parseModelIds(response.body()));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /**
     * Parses model ids from an OpenAI-shaped {@code {object: "list", data: [...]}} body.
     *
     * @param jsonBody the response body.
     * @return the model ids in catalog order.
     */
    static List<String> parseModelIds(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode data = mapper.readTree(jsonBody).get("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (JsonNode item : data) {
                if (item.has("id")) {
                    ids.add(item.get("id").asString());
                }
            }
            return List.copyOf(ids);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /**
     * Returns {@code true} when the serve process is up and answering the
     * liveness probe, regardless of whether any model is loaded.
     *
     * <p>Liveness — not readiness — is the existence test used before attaching,
     * because an engine started with {@code allow-empty} is a legitimate target
     * even with an empty catalog: Studio loads models into it on demand.
     *
     * @return {@code true} when the server is alive.
     */
    public boolean isAlive() {
        return probe("/q/health/live")
                .map(response -> response.statusCode() == 200 && response.body().contains("\"UP\""))
                .orElse(false);
    }

    /**
     * Returns {@code true} when the serve readiness probe reports UP.
     *
     * @return {@code true} when the server is ready.
     */
    public boolean isReady() {
        return probe("/q/health/ready")
                .map(response -> response.statusCode() == 200 && response.body().contains("\"UP\""))
                .orElse(false);
    }

    private ServeManagementForbiddenException forbidden(String action) {
        return new ServeManagementForbiddenException(
                "Cannot " + action + " at " + baseUrl
                        + ": model management endpoints are only accessible from localhost.");
    }
}
