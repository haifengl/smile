/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import java.util.Map;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Server liveness probe endpoint at {@code /api/v1/health}.
 *
 * <p>Confirms that the serve API server process is alive and responding.
 * Suitable for Kubernetes {@code livenessProbe} configurations to detect
 * deadlocks or unresponsive server states.
 *
 * @author Haifeng Li
 */
@Path("/health")
public class HealthResource {

    private static final Map<String, String> RESPONSE_UP = Map.of("status", "UP");

    /**
     * Liveness check confirming that the server process is alive and responding.
     *
     * @return HTTP 200 OK with {@code {"status": "UP"}}.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> health() {
        return RESPONSE_UP;
    }

    /**
     * Header-only liveness check.
     *
     * @return HTTP 200 OK without body.
     */
    @HEAD
    public Response head() {
        return Response.ok().build();
    }
}
