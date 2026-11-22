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
import java.util.LinkedHashMap;
import java.util.Map;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestStreamElementType;
import smile.serve.model.ModelRegistry;

/**
 * REST resource exposing ONNX Runtime model inference, versioning, aliases,
 * and lifecycle management under {@code /v1/onnx}.
 *
 * <p>Inference and metadata responses include {@code X-Model-Id} and
 * {@code X-Model-Version} headers identifying the concrete artifact served.
 *
 * @author Haifeng Li
 */
@Path("/v1/onnx")
public class OnnxResource {

    @Inject
    OnnxService service;

    // --------------------------------------------------------------- info & versions

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<OnnxModelInfo> info(@PathParam("id") String id) {
        return infoResponse(service.getModel(id));
    }

    @GET
    @Path("/{id}/versions/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<OnnxModelInfo> infoVersion(@PathParam("id") String id,
                                                   @PathParam("version") String version) {
        return infoResponse(service.getModelVersion(id, version));
    }

    @GET
    @Path("/{id}/aliases/{alias}")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<OnnxModelInfo> infoAlias(@PathParam("id") String id,
                                                 @PathParam("alias") String alias) {
        return infoResponse(service.getModelAlias(id, alias));
    }

    @GET
    @Path("/{id}/versions")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> versions(@PathParam("id") String id) {
        return versionsPayload(service.getVersions(id));
    }

    private RestResponse<OnnxModelInfo> infoResponse(OnnxModel model) {
        return RestResponse.ResponseBuilder.ok(service.infoFor(model))
                .header(InferenceResource.HEADER_MODEL_ID, model.slug())
                .header(InferenceResource.HEADER_MODEL_VERSION, model.version())
                .build();
    }

    // --------------------------------------------------------------- health

    @GET
    @Path("/{id}/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> health(@PathParam("id") String id) {
        return healthPayload(service.getModel(id));
    }

    @GET
    @Path("/{id}/versions/{version}/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> healthVersion(@PathParam("id") String id,
                                             @PathParam("version") String version) {
        return healthPayload(service.getModelVersion(id, version));
    }

    @GET
    @Path("/{id}/aliases/{alias}/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> healthAlias(@PathParam("id") String id,
                                           @PathParam("alias") String alias) {
        return healthPayload(service.getModelAlias(id, alias));
    }

    private static Map<String, Object> healthPayload(OnnxModel model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", model.slug());
        map.put("version", model.version());
        map.put("status", model.state().name());
        map.put("in_flight_requests", model.metrics().inFlightRequests());
        map.put("uptime_seconds", model.metrics().uptimeSeconds());
        return map;
    }

    // --------------------------------------------------------------- alias management

    @PUT
    @Path("/{id}/aliases/{alias}")
    @LocalhostOnly
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> setAlias(@PathParam("id") String id,
                                        @PathParam("alias") String alias,
                                        JsonObject request) {
        if (request == null || request.getString("version") == null || request.getString("version").isBlank()) {
            throw new BadRequestException("Request body must contain non-blank 'version'");
        }
        String version = request.getString("version").strip();
        ModelRegistry.SlugVersions updated = service.setAlias(id, alias, version);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "updated");
        resp.put("slug", updated.slug());
        resp.put("alias", alias);
        resp.put("version", version);
        resp.put("default", updated.defaultVersion());
        resp.put("latest", updated.latestVersion());
        resp.put("versions", updated.versions());
        resp.put("aliases", updated.aliases());
        return resp;
    }

    @DELETE
    @Path("/{id}/aliases/{alias}")
    @LocalhostOnly
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> deleteAlias(@PathParam("id") String id,
                                           @PathParam("alias") String alias) {
        ModelRegistry.SlugVersions updated = service.deleteAlias(id, alias);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "deleted");
        resp.put("slug", updated.slug());
        resp.put("alias", alias);
        resp.put("default", updated.defaultVersion());
        resp.put("latest", updated.latestVersion());
        resp.put("versions", updated.versions());
        resp.put("aliases", updated.aliases());
        return resp;
    }

    // --------------------------------------------------------------- reload & unload

    @POST
    @Path("/{id}/reload")
    @LocalhostOnly
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> reload(@PathParam("id") String id) {
        return reloadPayload(service.reloadModel(id));
    }

    @POST
    @Path("/{id}/versions/{version}/reload")
    @LocalhostOnly
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> reloadVersion(@PathParam("id") String id,
                                             @PathParam("version") String version) {
        return reloadPayload(service.reloadModelVersion(id, version));
    }

    private static Map<String, Object> reloadPayload(OnnxModel reloaded) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "reloaded");
        map.put("id", reloaded.slug());
        map.put("version", reloaded.version());
        map.put("path", reloaded.path().toString());
        map.put("timestamp", Instant.now().getEpochSecond());
        return map;
    }

    @POST
    @Path("/{id}/unload")
    @LocalhostOnly
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> unload(@PathParam("id") String id,
                                      @QueryParam("timeout") @DefaultValue("10") long timeoutSecs) {
        boolean drained = service.unloadModel(id, Duration.ofSeconds(Math.max(1, timeoutSecs)));
        return Map.of(
                "status", "unloaded",
                "id", id,
                "drained", drained,
                "timestamp", Instant.now().getEpochSecond()
        );
    }

    @POST
    @Path("/{id}/versions/{version}/unload")
    @LocalhostOnly
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> unloadVersion(@PathParam("id") String id,
                                             @PathParam("version") String version,
                                             @QueryParam("timeout") @DefaultValue("10") long timeoutSecs) {
        boolean drained = service.unloadModelVersion(id, version, Duration.ofSeconds(Math.max(1, timeoutSecs)));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "unloaded");
        map.put("id", id);
        map.put("version", version);
        map.put("drained", drained);
        map.put("timestamp", Instant.now().getEpochSecond());
        return map;
    }

    // --------------------------------------------------------------- predict & stream

    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<JsonObject> predict(@PathParam("id") String id, JsonObject request) {
        return doPredict(service.getModel(id), request);
    }

    @POST
    @Path("/{id}/versions/{version}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<JsonObject> predictVersion(@PathParam("id") String id,
                                                   @PathParam("version") String version,
                                                   JsonObject request) {
        return doPredict(service.getModelVersion(id, version), request);
    }

    @POST
    @Path("/{id}/aliases/{alias}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<JsonObject> predictAlias(@PathParam("id") String id,
                                                 @PathParam("alias") String alias,
                                                 JsonObject request) {
        return doPredict(service.getModelAlias(id, alias), request);
    }

    private RestResponse<JsonObject> doPredict(OnnxModel model, JsonObject request) {
        JsonObject result = service.predict(model, request);
        return RestResponse.ResponseBuilder.ok(result)
                .header(InferenceResource.HEADER_MODEL_ID, model.slug())
                .header(InferenceResource.HEADER_MODEL_VERSION, model.version())
                .build();
    }

    @POST
    @Path("/{id}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> stream(@HeaderParam("Content-Type") String contentType,
                                @PathParam("id") String id,
                                InputStream input) {
        return doStream(service.getModel(id), contentType, input);
    }

    @POST
    @Path("/{id}/versions/{version}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streamVersion(@HeaderParam("Content-Type") String contentType,
                                       @PathParam("id") String id,
                                       @PathParam("version") String version,
                                       InputStream input) {
        return doStream(service.getModelVersion(id, version), contentType, input);
    }

    @POST
    @Path("/{id}/aliases/{alias}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streamAlias(@HeaderParam("Content-Type") String contentType,
                                     @PathParam("id") String id,
                                     @PathParam("alias") String alias,
                                     InputStream input) {
        return doStream(service.getModelAlias(id, alias), contentType, input);
    }

    private Multi<String> doStream(OnnxModel model, String contentType, InputStream input) {
        boolean json = contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON);
        return Multi.createFrom().emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().submit(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(input))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            long start = System.nanoTime();
                            JsonObject response = json
                                    ? model.predict(new JsonObject(line))
                                    : model.predict(line);
                            String encoded = response.encode();
                            if (service.hasListeners()) {
                                service.notifyListeners(InferenceEvent.onnxPredict(
                                        model.slug(),
                                        model.version(),
                                        InferenceEvent.Action.STREAM_PREDICT,
                                        line,
                                        encoded,
                                        System.nanoTime() - start));
                            }
                            emitter.emit(encoded);
                        }
                    }
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.fail(ex);
                }
            });
        });
    }

    private static Map<String, Object> versionsPayload(ModelRegistry.SlugVersions info) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slug", info.slug());
        map.put("default", info.defaultVersion());
        map.put("latest", info.latestVersion());
        map.put("versions", info.versions());
        map.put("aliases", info.aliases());
        return map;
    }
}
