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
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import smile.auth.AuthContext;
import smile.auth.OidcAuthProvider;

/**
 * JAX-RS request filter enforcing {@link LocalhostOnly} endpoints:
 * <ul>
 *   <li>When OIDC is active and an admin role is configured, requires the
 *       authenticated OIDC caller to carry that role (allowing remote admin
 *       access and rejecting non-admin callers with {@code 403 Forbidden}).</li>
 *   <li>Otherwise (including the {@code micro} profile where {@code smile.auth.*}
 *       is excluded), requires a direct localhost/loopback socket connection.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
@LocalhostOnly
@Provider
@Priority(Priorities.AUTHORIZATION)
public class LocalhostFilter implements ContainerRequestFilter {

    @Inject
    RoutingContext routingContext;

    @Inject
    Instance<OidcAuthProvider> oidcAuthProvider;

    @Inject
    Instance<AuthContext> authContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        OidcAuthProvider oidc = resolveOidcProvider();
        if (oidc != null && oidc.isActive() && oidc.hasAdminRoleConfigured()) {
            AuthContext ctx = resolveAuthContext();
            if (ctx == null || !ctx.hasAdminRole()) {
                throw new ForbiddenException("Forbidden: OIDC admin role required");
            }
            return;
        }
        LocalhostGuard.requireLocalhost(routingContext);
    }

    private OidcAuthProvider resolveOidcProvider() {
        return oidcAuthProvider != null && oidcAuthProvider.isResolvable()
                ? oidcAuthProvider.get()
                : null;
    }

    private AuthContext resolveAuthContext() {
        return authContext != null && authContext.isResolvable()
                ? authContext.get()
                : null;
    }
}
