/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonObject;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * REST resource exposing the model inference API at {@code /api/v1/models}.
 *
 * <ul>
 *   <li>{@code GET  /models}           – list all loaded model IDs.</li>
 *   <li>{@code GET  /models/{id}}      – retrieve model metadata.</li>
 *   <li>{@code POST /models/{id}}      – single JSON inference request.</li>
 *   <li>{@code POST /models/{id}/stream} – streaming inference (JSON lines or CSV).</li>
 * </ul>
 *
 * @author Haifeng Li
 */
@Path("/models")
public class InferenceResource {

    @Inject
    InferenceService service;

    /**
     * Returns the IDs of all loaded models.
     *
     * @return alphabetically sorted list of model IDs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> list() {
        return service.models();
    }

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
     * Performs a single inference on JSON-encoded feature values.
     *
     * @param id      the model ID.
     * @param request JSON object whose keys are feature names.
     * @return the inference response with prediction and optional probabilities.
     */
    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public InferenceResponse predict(@PathParam("id") String id, JsonObject request) {
        return service.predict(id, request);
    }

    /**
     * Performs streaming inference over a multi-line request body.
     * Each non-blank line is treated as a separate sample:
     * either a JSON object (if {@code Content-Type: application/json}) or
     * a comma-separated row of values (if {@code Content-Type: text/plain}).
     * Results are emitted as a server-sent stream of plain-text lines.
     *
     * @param contentType the MIME type of each input line.
     * @param id          the model ID.
     * @param input       the request body input stream.
     * @return a reactive stream of inference result strings.
     */
    @POST
    @Path("/{id}/stream")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> stream(@HeaderParam("Content-Type") String contentType,
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
                            var response = json ? model.predict(new JsonObject(line)) : model.predict(line);
                            emitter.emit(response.toString());
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
