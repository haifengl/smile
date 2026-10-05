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
import smile.model.Prediction;

/**
 * REST resource exposing the classic SMILE model inference and control API at
 * {@code /api/v1/smile}.
 *
 * <ul>
 *   <li>{@code GET  /smile/{id}}         – retrieve model metadata.</li>
 *   <li>{@code GET  /smile/{id}/health}  – inspect model operational status.</li>
 *   <li>{@code GET  /smile/{id}/metrics} – inspect inference throughput and latency metrics.</li>
 *   <li>{@code POST /smile/{id}}         – single JSON inference request.</li>
 *   <li>{@code POST /smile/{id}/stream}  – streaming inference (JSON lines or CSV).</li>
 *   <li>{@code POST /smile/{id}/reload}  – reload model from disk (localhost only).</li>
 *   <li>{@code POST /smile/{id}/unload}  – unload model from memory (localhost only).</li>
 * </ul>
 *
 * <p>The unified model catalog is {@code GET /api/v1/models}.
 *
 * @author Haifeng Li
 */
@Path("/smile")
public class InferenceResource {

    @Inject
    InferenceService service;

    @Inject
    RoutingContext routingContext;

    /**
     * Returns the metadata of a single model.
     *
     * @param id the model ID.
     * @return the model metadata (404 if not found).
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ModelMetadata get(@PathParam("id") String id) {
        return service.getModel(id).metadata();
    }

    /**
     * Returns the health and readiness status of a model.
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
     * Returns operational metrics, throughput, and latency statistics for a model.
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
     * Reloads a model from disk into active memory. Accessible only from localhost.
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
                "algorithm", reloaded.metadata().algorithm(),
                "timestamp", Instant.now().getEpochSecond()
        );
    }

    /**
     * Unloads a model from active memory, gracefully waiting for in-flight requests.
     * Accessible only from localhost.
     *
     * @param id           the model ID.
     * @param timeoutSecs  grace period in seconds to wait for in-flight requests (default: 10s).
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
     * Performs a single inference on JSON-encoded feature values with optional explanations.
     *
     * @param explainQuery optional query parameter {@code ?explain=true}.
     * @param id           the model ID.
     * @param request      JSON object whose keys are feature names and optional {@code enableExplanations}.
     * @return the prediction with optional probabilities and explanations.
     */
    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Prediction predict(@QueryParam("explain") boolean explainQuery,
                              @PathParam("id") String id,
                              JsonObject request) {
        boolean explain = explainQuery || Boolean.TRUE.equals(request != null ? request.getBoolean("enableExplanations") : null);
        return service.predict(id, request, explain);
    }

    /**
     * Performs streaming inference over a multi-line request body.
     * Each non-blank line is treated as a separate sample:
     * either a JSON object (if {@code Content-Type: application/json}) or
     * a comma-separated row of values (if {@code Content-Type: text/plain}).
     * Results are emitted as a server-sent stream of JSON objects.
     *
     * @param explainQuery optional query parameter {@code ?explain=true}.
     * @param contentType  the MIME type of each input line.
     * @param id           the model ID.
     * @param input        the request body input stream.
     * @return a reactive stream of JSON prediction strings.
     */
    @POST
    @Path("/{id}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> stream(@QueryParam("explain") boolean explainQuery,
                                @HeaderParam("Content-Type") String contentType,
                                @PathParam("id") String id,
                                InputStream input) {
        var model = service.getModel(id);
        // Treat any content-type that starts with "application/json" as JSON,
        // including "application/json; charset=utf-8".
        boolean json = contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON);
        return Multi.createFrom().emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().submit(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(input))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            Prediction response;
                            if (json) {
                                var jsonObject = new JsonObject(line);
                                boolean explain = explainQuery || Boolean.TRUE.equals(jsonObject.getBoolean("enableExplanations"));
                                response = model.predict(jsonObject, explain);
                            } else {
                                response = model.predict(line, explainQuery);
                            }
                            emitter.emit(response.toJson());
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
