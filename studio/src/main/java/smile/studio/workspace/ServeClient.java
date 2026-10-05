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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A minimal HTTP client for the SMILE Serve REST API.
 *
 * <p>Studio talks to a locally launched serve instance. Requests always target
 * loopback ({@code 127.0.0.1}) regardless of the host the server binds to,
 * because the model-management endpoints ({@code /api/v1/models/load},
 * {@code /smile/{id}/reload}, {@code /unload}) are {@code @LocalhostOnly}.
 *
 * @author Haifeng Li
 */
public final class ServeClient {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final String baseUrl;

    /**
     * Constructor.
     *
     * @param port the serve HTTP port.
     */
    public ServeClient(int port) {
        this.baseUrl = "http://127.0.0.1:" + port;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
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
     * @throws IOException          on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public String loadModel(String modelPath) throws IOException, InterruptedException {
        var response = post("/api/v1/models/load", Map.of("model", modelPath, "kind", "auto"));
        int status = response.statusCode();
        if (status == 409) {
            return null;
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
     * @throws IOException          on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public boolean unloadModel(String id) throws IOException, InterruptedException {
        var response = post("/api/v1/smile/" + id + "/unload", Map.of());
        if (response.statusCode() == 404) {
            response = post("/api/v1/onnx/" + id + "/unload", Map.of());
        }
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /**
     * Reloads a model from disk, trying the SMILE endpoint first and the ONNX endpoint second.
     *
     * @param id the model id.
     * @return {@code true} when the model was reloaded.
     * @throws IOException          on transport failure.
     * @throws InterruptedException if the request is interrupted.
     */
    public boolean reloadModel(String id) throws IOException, InterruptedException {
        var response = post("/api/v1/smile/" + id + "/reload", Map.of());
        if (response.statusCode() == 404) {
            response = post("/api/v1/onnx/" + id + "/reload", Map.of());
        }
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /**
     * Returns {@code true} when the serve readiness probe reports UP.
     *
     * @return {@code true} when the server is ready.
     */
    public boolean isReady() {
        try {
            var response = get("/q/health/ready");
            return response.statusCode() == 200 && response.body().contains("\"UP\"");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
