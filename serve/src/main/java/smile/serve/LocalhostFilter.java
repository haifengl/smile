/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS request filter enforcing that endpoints annotated with {@link LocalhostOnly}
 * are only accessed from localhost/loopback socket connections.
 *
 * @author Haifeng Li
 */
@LocalhostOnly
@Provider
@Priority(Priorities.AUTHORIZATION)
public class LocalhostFilter implements ContainerRequestFilter {

    @Inject
    RoutingContext routingContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        LocalhostGuard.requireLocalhost(routingContext);
    }
}
