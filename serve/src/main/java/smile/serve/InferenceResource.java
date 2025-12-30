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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/infer")
public class InferenceResource {

    @Inject
    InferenceService inferenceService;

    @Inject
    ObjectMapper objectMapper; // Inject the Quarkus-provided Jackson ObjectMapper

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{modelId}")
    public Response predict(@PathParam("modelId") String modelId, InferenceRequest request) {
        if (request == null || request.data == null) {
            throw new BadRequestException();
        }

        // Run the inference using the service
        Map<String, Object> result = inferenceService.predict(request);

        // Return the result as JSON
        return Response.ok(result).build();
    }

    @POST
    @Path("/jsonl")
    @Consumes("application/jsonl") // custom media type for JSON Lines
    public Response stream(InputStream requestBody) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody))) {
            reader.lines().forEach(line -> {
                try {
                    // Deserialize each line into your Java object
                    InferenceRequest data = objectMapper.readValue(line, InferenceRequest.class);
                    // Process the data object (e.g., save to DB, queue for processing)
                    System.out.println("Processed JSONL item: " + data);
                } catch (Exception e) {
                    // Handle errors for individual lines
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            return Response.serverError().entity("Error processing JSONL stream").build();
        }

        return Response.accepted().entity("JSONL stream processed").build();
    }
}
