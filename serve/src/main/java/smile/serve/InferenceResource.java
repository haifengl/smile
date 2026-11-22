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
import smile.feature.importance.KernelSHAP;
import smile.model.Prediction;
import smile.serve.model.ModelRegistry;

/**
 * REST resource exposing classic SMILE model inference, versioning, aliases,
 * and lifecycle management under {@code /v1/smile}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET    /v1/smile/{slug}[/versions/{ver}|/aliases/{alias}]} – model metadata.</li>
 *   <li>{@code GET    /v1/smile/{slug}/versions} – list loaded versions and aliases.</li>
 *   <li>{@code GET    /v1/smile/{slug}[/versions/{ver}|/aliases/{alias}]/health} – health status.</li>
 *   <li>{@code POST   /v1/smile/{slug}[/versions/{ver}|/aliases/{alias}]} – single JSON inference.</li>
 *   <li>{@code POST   /v1/smile/{slug}[/versions/{ver}|/aliases/{alias}]/stream} – streaming inference.</li>
 *   <li>{@code POST   /v1/smile/{slug}[/versions/{ver}]/reload} – reload from disk (localhost only).</li>
 *   <li>{@code POST   /v1/smile/{slug}[/versions/{ver}]/unload} – unload from memory (localhost only).</li>
 *   <li>{@code POST   /v1/smile/{slug}[/versions/{ver}|/aliases/{alias}]/update[/stream]} – online update (localhost only).</li>
 *   <li>{@code PUT    /v1/smile/{slug}/aliases/{alias}} – assign/promote alias (localhost only).</li>
 *   <li>{@code DELETE /v1/smile/{slug}/aliases/{alias}} – remove alias (localhost only).</li>
 * </ul>
 *
 * <p>Inference and metadata responses include {@code X-Model-Id} and
 * {@code X-Model-Version} headers identifying the concrete artifact served.
 *
 * @author Haifeng Li
 */
@Path("/v1/smile")
public class InferenceResource {

    /** Response header carrying the resolved model slug. */
    public static final String HEADER_MODEL_ID = "X-Model-Id";

    /** Response header carrying the concrete served model version. */
    public static final String HEADER_MODEL_VERSION = "X-Model-Version";

    @Inject
    InferenceService service;

    // --------------------------------------------------------------- metadata & versions

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<ModelMetadata> get(@PathParam("id") String id) {
        return metadataResponse(service.getModel(id));
    }

    @GET
    @Path("/{id}/versions/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<ModelMetadata> getVersion(@PathParam("id") String id,
                                                  @PathParam("version") String version) {
        return metadataResponse(service.getModelVersion(id, version));
    }

    @GET
    @Path("/{id}/aliases/{alias}")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<ModelMetadata> getAlias(@PathParam("id") String id,
                                                @PathParam("alias") String alias) {
        return metadataResponse(service.getModelAlias(id, alias));
    }

    @GET
    @Path("/{id}/versions")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> versions(@PathParam("id") String id) {
        return versionsPayload(service.getVersions(id));
    }

    private RestResponse<ModelMetadata> metadataResponse(InferenceModel model) {
        return RestResponse.ResponseBuilder.ok(service.metadataFor(model))
                .header(HEADER_MODEL_ID, model.slug())
                .header(HEADER_MODEL_VERSION, model.version())
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

    private static Map<String, Object> healthPayload(InferenceModel model) {
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

    private static Map<String, Object> reloadPayload(InferenceModel reloaded) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "reloaded");
        map.put("id", reloaded.slug());
        map.put("version", reloaded.version());
        map.put("path", reloaded.path().toString());
        map.put("algorithm", reloaded.metadata().algorithm());
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

    // --------------------------------------------------------------- online updates

    @POST
    @Path("/{id}/update")
    @LocalhostOnly
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> update(@QueryParam("persist") boolean persist,
                                      @PathParam("id") String id,
                                      JsonObject request) {
        return doUpdate(service.getModel(id), request, persist);
    }

    @POST
    @Path("/{id}/versions/{version}/update")
    @LocalhostOnly
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateVersion(@QueryParam("persist") boolean persist,
                                             @PathParam("id") String id,
                                             @PathParam("version") String version,
                                             JsonObject request) {
        return doUpdate(service.getModelVersion(id, version), request, persist);
    }

    @POST
    @Path("/{id}/aliases/{alias}/update")
    @LocalhostOnly
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateAlias(@QueryParam("persist") boolean persist,
                                           @PathParam("id") String id,
                                           @PathParam("alias") String alias,
                                           JsonObject request) {
        return doUpdate(service.getModelAlias(id, alias), request, persist);
    }

    private Map<String, Object> doUpdate(InferenceModel model, JsonObject request, boolean persist) {
        service.update(model, request, persist);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "updated");
        map.put("id", model.slug());
        map.put("version", model.version());
        map.put("updated", 1);
        map.put("persisted", persist);
        map.put("timestamp", Instant.now().getEpochSecond());
        return map;
    }

    @POST
    @Path("/{id}/update/stream")
    @LocalhostOnly
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateStream(@QueryParam("persist") boolean persist,
                                            @HeaderParam("Content-Type") String contentType,
                                            @PathParam("id") String id,
                                            InputStream input) {
        return doUpdateStream(service.getModel(id), contentType, input, persist);
    }

    @POST
    @Path("/{id}/versions/{version}/update/stream")
    @LocalhostOnly
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateStreamVersion(@QueryParam("persist") boolean persist,
                                                   @HeaderParam("Content-Type") String contentType,
                                                   @PathParam("id") String id,
                                                   @PathParam("version") String version,
                                                   InputStream input) {
        return doUpdateStream(service.getModelVersion(id, version), contentType, input, persist);
    }

    @POST
    @Path("/{id}/aliases/{alias}/update/stream")
    @LocalhostOnly
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateStreamAlias(@QueryParam("persist") boolean persist,
                                                 @HeaderParam("Content-Type") String contentType,
                                                 @PathParam("id") String id,
                                                 @PathParam("alias") String alias,
                                                 InputStream input) {
        return doUpdateStream(service.getModelAlias(id, alias), contentType, input, persist);
    }

    private Map<String, Object> doUpdateStream(InferenceModel model, String contentType, InputStream input, boolean persist) {
        if (!model.isOnline()) {
            throw new BadRequestException("Model '" + model.slug() + "' (" + model.metadata().algorithm() + ") does not support online learning");
        }
        if (persist) {
            service.checkBeforePersist(model);
        }
        boolean json = contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON);
        int count = 0;
        try (var reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    long start = System.nanoTime();
                    if (json) {
                        model.update(new JsonObject(line));
                    } else {
                        model.update(line);
                    }
                    count++;
                    if (service.hasListeners()) {
                        service.notifyListeners(InferenceEvent.onlineUpdate(
                                model.slug(),
                                model.version(),
                                line,
                                persist,
                                System.nanoTime() - start));
                    }
                }
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Failed to process online update stream: " + ex.getMessage(), ex);
        }
        if (persist && count > 0) {
            service.persistModel(model);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "updated");
        map.put("id", model.slug());
        map.put("version", model.version());
        map.put("updated", count);
        map.put("persisted", persist && count > 0);
        map.put("timestamp", Instant.now().getEpochSecond());
        return map;
    }

    // --------------------------------------------------------------- predict & stream

    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<Prediction> predict(@QueryParam("explain") boolean explainQuery,
                                            @QueryParam("samples") Integer samples,
                                            @QueryParam("ridge") Double ridge,
                                            @PathParam("id") String id,
                                            JsonObject request) {
        return doPredict(service.getModel(id), explainQuery, samples, ridge, request);
    }

    @POST
    @Path("/{id}/versions/{version}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<Prediction> predictVersion(@QueryParam("explain") boolean explainQuery,
                                                   @QueryParam("samples") Integer samples,
                                                   @QueryParam("ridge") Double ridge,
                                                   @PathParam("id") String id,
                                                   @PathParam("version") String version,
                                                   JsonObject request) {
        return doPredict(service.getModelVersion(id, version), explainQuery, samples, ridge, request);
    }

    @POST
    @Path("/{id}/aliases/{alias}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<Prediction> predictAlias(@QueryParam("explain") boolean explainQuery,
                                                 @QueryParam("samples") Integer samples,
                                                 @QueryParam("ridge") Double ridge,
                                                 @PathParam("id") String id,
                                                 @PathParam("alias") String alias,
                                                 JsonObject request) {
        return doPredict(service.getModelAlias(id, alias), explainQuery, samples, ridge, request);
    }

    private RestResponse<Prediction> doPredict(InferenceModel model,
                                               boolean explainQuery,
                                               Integer samples,
                                               Double ridge,
                                               JsonObject request) {
        boolean explain = isExplainRequested(explainQuery, request);
        KernelSHAP.Options shapOptions = resolveShapOptions(samples, ridge, request);
        Prediction prediction = service.predict(model, request, explain, shapOptions);
        return RestResponse.ResponseBuilder.ok(prediction)
                .header(HEADER_MODEL_ID, model.slug())
                .header(HEADER_MODEL_VERSION, model.version())
                .build();
    }

    @POST
    @Path("/{id}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> stream(@QueryParam("explain") boolean explainQuery,
                                @QueryParam("samples") Integer samples,
                                @QueryParam("ridge") Double ridge,
                                @HeaderParam("Content-Type") String contentType,
                                @PathParam("id") String id,
                                InputStream input) {
        return doStream(service.getModel(id), explainQuery, samples, ridge, contentType, input);
    }

    @POST
    @Path("/{id}/versions/{version}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streamVersion(@QueryParam("explain") boolean explainQuery,
                                       @QueryParam("samples") Integer samples,
                                       @QueryParam("ridge") Double ridge,
                                       @HeaderParam("Content-Type") String contentType,
                                       @PathParam("id") String id,
                                       @PathParam("version") String version,
                                       InputStream input) {
        return doStream(service.getModelVersion(id, version), explainQuery, samples, ridge, contentType, input);
    }

    @POST
    @Path("/{id}/aliases/{alias}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streamAlias(@QueryParam("explain") boolean explainQuery,
                                     @QueryParam("samples") Integer samples,
                                     @QueryParam("ridge") Double ridge,
                                     @HeaderParam("Content-Type") String contentType,
                                     @PathParam("id") String id,
                                     @PathParam("alias") String alias,
                                     InputStream input) {
        return doStream(service.getModelAlias(id, alias), explainQuery, samples, ridge, contentType, input);
    }

    private Multi<String> doStream(InferenceModel model,
                                   boolean explainQuery,
                                   Integer samples,
                                   Double ridge,
                                   String contentType,
                                   InputStream input) {
        KernelSHAP.Options queryShapOptions = resolveShapOptions(samples, ridge, null);
        boolean json = contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON);
        return Multi.createFrom().emitter(emitter -> {
            Infrastructure.getDefaultWorkerPool().submit(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(input))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            long start = System.nanoTime();
                            boolean rowExplain = explainQuery;
                            Prediction response;
                            if (json) {
                                var jsonObject = new JsonObject(line);
                                rowExplain = isExplainRequested(explainQuery, jsonObject);
                                KernelSHAP.Options rowOptions = resolveShapOptions(samples, ridge, jsonObject);
                                response = model.predict(jsonObject, rowExplain, rowOptions);
                            } else {
                                response = model.predict(line, explainQuery, queryShapOptions);
                            }
                            if (service.hasListeners()) {
                                service.notifyListeners(InferenceEvent.smilePredict(
                                        model.slug(),
                                        model.version(),
                                        InferenceEvent.Action.STREAM_PREDICT,
                                        line,
                                        response,
                                        System.nanoTime() - start,
                                        rowExplain));
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

    private static Map<String, Object> versionsPayload(ModelRegistry.SlugVersions info) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slug", info.slug());
        map.put("default", info.defaultVersion());
        map.put("latest", info.latestVersion());
        map.put("versions", info.versions());
        map.put("aliases", info.aliases());
        return map;
    }

    private static boolean isExplainRequested(boolean explainQuery, JsonObject request) {
        if (explainQuery) {
            return true;
        }
        if (request == null) {
            return false;
        }
        Object enable = request.getValue("enableExplanations");
        if (enable instanceof Boolean b && b) {
            return true;
        }
        return request.getValue("shap") instanceof JsonObject;
    }

    /**
     * Resolves optional {@link KernelSHAP.Options} from query parameters and/or JSON request fields.
     * Query parameters take precedence over JSON payload fields when both are present.
     */
    static KernelSHAP.Options resolveShapOptions(Integer querySamples, Double queryRidge, JsonObject request) {
        Integer samples = querySamples;
        Double ridge = queryRidge;

        if (request != null) {
            JsonObject shapObj = request.getValue("shap") instanceof JsonObject jo ? jo : null;
            if (samples == null) {
                Object rawSamples = request.containsKey("shapSamples")
                        ? request.getValue("shapSamples")
                        : (shapObj != null
                                ? (shapObj.containsKey("samples") ? shapObj.getValue("samples") : shapObj.getValue("numSamples"))
                                : null);
                if (rawSamples != null) {
                    samples = parseIntegerOption("samples", rawSamples);
                }
            }
            if (ridge == null) {
                Object rawRidge = request.containsKey("shapRidge")
                        ? request.getValue("shapRidge")
                        : (shapObj != null ? shapObj.getValue("ridge") : null);
                if (rawRidge != null) {
                    ridge = parseDoubleOption("ridge", rawRidge);
                }
            }
        }

        if (samples == null && ridge == null) {
            return null;
        }

        int numSamples = samples != null ? samples : 0;
        double ridgeVal = ridge != null ? ridge : KernelSHAP.DEFAULT_RIDGE;
        try {
            return new KernelSHAP.Options(numSamples, ridgeVal);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
    }

    private static Integer parseIntegerOption(String name, Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ex) {
                throw new BadRequestException("Invalid integer for '" + name + "': " + s, ex);
            }
        }
        throw new BadRequestException("Invalid integer for '" + name + "': " + value);
    }

    private static Double parseDoubleOption(String name, Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ex) {
                throw new BadRequestException("Invalid number for '" + name + "': " + s, ex);
            }
        }
        throw new BadRequestException("Invalid number for '" + name + "': " + value);
    }
}
