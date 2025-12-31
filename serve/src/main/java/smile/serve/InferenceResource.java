/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * Model REST API.
 * @author Haifeng Li
 */
@Path("/models")
public class InferenceResource {

    @Inject
    InferenceService service;

    @Inject
    ObjectMapper objectMapper; // Inject the Quarkus-provided Jackson ObjectMapper
    TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {};

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> list() {
        return service.models();
    }

    @GET
    @Path("/{modelId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ModelMetadata get(@PathParam("modelId") String id) {
        return service.getModel(id).metadata();
    }

    @POST
    @Path("/{modelId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public InferenceResponse predict(@PathParam("modelId") String id, Map<String, Object> request) {
        return service.predict(id, request);
    }

    @POST
    @Path("/{modelId}/jsonl")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<InferenceResponse> jsonl(@PathParam("modelId") String id, Multi<Map<String, Object>> lines) {
        var model = service.getModel(id);
        return lines.onItem().transform(line -> model.predict(line));
    }

    @POST
    @Path("/{modelId}/csv")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Multi<String> csv(@PathParam("modelId") String id, Multi<String> lines) {
        var model = service.getModel(id);
        return lines.onItem().transform(line -> model.predict(line).toString());
    }
}
