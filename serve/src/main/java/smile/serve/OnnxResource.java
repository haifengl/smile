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
package smile.serve;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * REST resource exposing the ONNX model inference and control API at
 * {@code /api/v1/onnx}.
 *
 * <ul>
 *   <li>{@code GET  /onnx/{id}}          – retrieve ONNX model metadata.</li>
 *   <li>{@code GET  /onnx/{id}/health}   – inspect model operational status.</li>
 *   <li>{@code GET  /onnx/{id}/metrics}  – inspect inference throughput and latency metrics.</li>
 *   <li>{@code POST /onnx/{id}}          – single JSON inference request.</li>
 *   <li>{@code POST /onnx/{id}/stream}   – streaming inference (JSON lines
 *       or CSV text for single-input models).</li>
 *   <li>{@code POST /onnx/{id}/reload}   – reload ONNX model from disk (localhost only).</li>
 *   <li>{@code POST /onnx/{id}/unload}   – unload ONNX model from memory (localhost only).</li>
 * </ul>
 *
 * <p>The unified model catalog is {@code GET /api/v1/models}.
 *
 * <h2>Request format</h2>
 * <p>For single-shot inference, the request body is a JSON object mapping
 * each input name to a flat array of numeric or string values:
 * <pre>{@code
 * POST /api/v1/onnx/resnet50
 * Content-Type: application/json
 *
 * { "input": [0.1, 0.2, ..., 0.3] }
 * }</pre>
 * or for models with string inputs:
 * <pre>{@code
 * POST /api/v1/onnx/text_classifier
 * Content-Type: application/json
 *
 * { "text": ["positive", "neutral"] }
 * }</pre>
 *
 * <p>For single-input models, CSV lines are also accepted via the
 * {@code /stream} endpoint with {@code Content-Type: text/plain}.
 *
 * <h2>Response format</h2>
 * <p>Responses are JSON objects mapping each output name to a flat array of
 * numbers or strings:
 * <pre>{@code
 * { "output": [0.02, 0.95, 0.03] }
 * }</pre>
 *
 * @author Haifeng Li
 */
@Path("/onnx")
public class OnnxResource {

    @Inject
    OnnxService service;

    @Inject
    RoutingContext routingContext;

    /**
     * Returns the metadata of a single ONNX model, including its graph name,
     * version, input/output node descriptors, and any custom metadata embedded
     * in the model file.
     *
     * @param id the model ID (file stem without {@code .onnx}).
     * @return the model info, or HTTP 404 if not found.
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public OnnxModelInfo info(@PathParam("id") String id) {
        return service.getModel(id).info();
    }

    /**
     * Returns the health and readiness status of an ONNX model.
     *
     * @param id the model ID.
     * @return health status JSON object.
     */
    @GET
    @Path("/{id}/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> health(@PathParam("id") String id) {
        var model = service.getModel(id);
        return Map.of(
                "id", model.id(),
                "status", model.state().name(),
                "in_flight_requests", model.metrics().inFlightRequests(),
                "uptime_seconds", model.metrics().uptimeSeconds()
        );
    }

    /**
     * Returns operational metrics, throughput, and latency statistics for an ONNX model.
     *
     * @param id the model ID.
     * @return model metrics.
     */
    @GET
    @Path("/{id}/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public ModelMetrics metrics(@PathParam("id") String id) {
        return service.getModel(id).metrics();
    }

    /**
     * Reloads an ONNX model from disk into active memory. Accessible only from localhost.
     *
     * @param id the model ID.
     * @return reload confirmation.
     */
    @POST
    @Path("/{id}/reload")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> reload(@PathParam("id") String id) {
        LocalhostGuard.requireLocalhost(routingContext);
        var reloaded = service.reloadModel(id);
        return Map.of(
                "status", "reloaded",
                "id", reloaded.id(),
                "path", reloaded.path().toString(),
                "timestamp", Instant.now().getEpochSecond()
        );
    }

    /**
     * Unloads an ONNX model from active memory, gracefully waiting for in-flight requests.
     * Accessible only from localhost.
     *
     * @param id          the model ID.
     * @param timeoutSecs grace period in seconds to wait for in-flight requests (default: 10s).
     * @return unload confirmation.
     */
    @POST
    @Path("/{id}/unload")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> unload(@PathParam("id") String id,
                                      @QueryParam("timeout") @DefaultValue("10") long timeoutSecs) {
        LocalhostGuard.requireLocalhost(routingContext);
        boolean drained = service.unloadModel(id, Duration.ofSeconds(Math.max(1, timeoutSecs)));
        return Map.of(
                "status", "unloaded",
                "id", id,
                "drained", drained,
                "timestamp", Instant.now().getEpochSecond()
        );
    }

    /**
     * Runs a single inference with JSON-encoded inputs.
     *
     * <p>The request body must be a JSON object whose keys are the model's
     * input names and whose values are flat JSON arrays of numbers or strings.
     *
     * @param id      the model ID.
     * @param request JSON object mapping input names to flat arrays.
     * @return JSON object mapping output names to flat arrays.
     */
    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject predict(@PathParam("id") String id, JsonObject request) {
        return service.predict(id, request);
    }

    /**
     * Streams inference results over a multi-line request body.
     *
     * <p>Each non-blank line in the body is treated as a separate inference
     * request:
     * <ul>
     *   <li>If {@code Content-Type} starts with {@code application/json},
     *       each line must be a complete JSON object (JSON-lines format).</li>
     *   <li>If {@code Content-Type} starts with {@code text/plain},
     *       each line must be a comma-separated list of floats for the model's
     *       first (and only) input.</li>
     * </ul>
     * Each result is emitted as an SSE {@code data:} event containing a
     * compact JSON object.
     *
     * @param contentType the MIME type of each input line.
     * @param id          the model ID.
     * @param input       the request body input stream.
     * @return a reactive stream of JSON result strings, one per input line.
     */
    @POST
    @Path("/{id}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> stream(@HeaderParam("Content-Type") String contentType,
                                @PathParam("id") String id,
                                InputStream input) {
        var model = service.getModel(id);
        boolean json = contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON);
        return Multi.createFrom().emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().submit(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(input))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            JsonObject response = json
                                    ? model.predict(new JsonObject(line))
                                    : model.predict(line);
                            emitter.emit(response.encode());
                        }
                    }
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.fail(ex);
                }
            });
        });
    }
}

