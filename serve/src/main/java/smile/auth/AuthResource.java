/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import java.net.URI;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;
import smile.chat.blob.ClientIdentity;

/**
 * Authentication endpoints under {@code /api/v1/auth}.
 */
@Path("/auth")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    AuthContext authContext;

    @Inject
    RoutingContext routingContext;

    @Inject
    UserService userService;

    @Inject
    GoogleOAuthService googleOAuth;

    /**
     * Returns the current user, if any.
     *
     * @return auth status and profile.
     */
    @GET
    @Path("/me")
    public AuthMeResponse me() {
        if (!authContext.isLoggedIn()) {
            return AuthMeResponse.guest();
        }
        return AuthMeResponse.of(authContext.user());
    }

    /**
     * Starts Google OAuth (redirects to Google). Requires OAuth credentials.
     *
     * @param requestContext request context.
     * @param returnTo       optional post-login SPA path ({@code /} or {@code /chat/}).
     * @return redirect to Google.
     */
    @GET
    @Path("/login/google")
    public Response loginGoogle(@Context ContainerRequestContext requestContext,
                                @QueryParam("return_to") String returnTo) {
        if (!googleOAuth.isEnabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("{\"error\":\"Google login is not configured\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        String origin = origin(requestContext);
        String state = googleOAuth.newState();
        String returnPath = OAuthReturnPath.normalize(returnTo);
        NewCookie stateCookie = new NewCookie.Builder(AuthFilter.OAUTH_STATE_COOKIE)
                .value(state)
                .path("/")
                .maxAge(600)
                .httpOnly(true)
                .build();
        NewCookie returnCookie = new NewCookie.Builder(AuthFilter.OAUTH_RETURN_COOKIE)
                .value(returnPath)
                .path("/")
                .maxAge(600)
                .httpOnly(true)
                .build();
        return Response.temporaryRedirect(googleOAuth.authorizationRedirect(origin, state))
                .cookie(stateCookie)
                .cookie(returnCookie)
                .build();
    }

    /**
     * Google OAuth callback — establishes a session and redirects to the SPA route
     * stored at login ({@code /} for the inference shell, {@code /chat/} for chat).
     *
     * @param requestContext request context.
     * @param code           authorization code.
     * @param state          CSRF state.
     * @return redirect to chat UI.
     */
    @GET
    @Path("/callback/google")
    @Transactional
    public Response callbackGoogle(@Context ContainerRequestContext requestContext,
                                   @QueryParam("code") String code,
                                   @QueryParam("state") String state) {
        if (!googleOAuth.isEnabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        var stateCookie = requestContext.getCookies().get(AuthFilter.OAUTH_STATE_COOKIE);
        if (stateCookie == null || state == null || !state.equals(stateCookie.getValue())) {
            LOG.warn("Google OAuth state mismatch");
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid OAuth state").build();
        }
        if (code == null || code.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing code").build();
        }
        try {
            GoogleOAuthService.GoogleProfile profile =
                    googleOAuth.exchangeCode(origin(requestContext), code);
            User user = userService.upsertGoogleUser(
                    profile.sub(), profile.email(), profile.name(), profile.picture());
            userService.mergeGuestConversations(user.id,
                    ClientIdentity.resolveClientIp(routingContext, null));
            AuthFilter.queueSession(requestContext, user.id);
            NewCookie clearedState = new NewCookie.Builder(AuthFilter.OAUTH_STATE_COOKIE)
                    .value("")
                    .path("/")
                    .maxAge(0)
                    .httpOnly(true)
                    .build();
            NewCookie clearedReturn = new NewCookie.Builder(AuthFilter.OAUTH_RETURN_COOKIE)
                    .value("")
                    .path("/")
                    .maxAge(0)
                    .httpOnly(true)
                    .build();
            return Response.temporaryRedirect(postLoginUri(requestContext))
                    .cookie(clearedState)
                    .cookie(clearedReturn)
                    .build();
        } catch (RuntimeException e) {
            LOG.warn("Google OAuth callback failed", e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Login failed").build();
        }
    }

    /**
     * Clears the session cookie.
     *
     * @return empty ok.
     */
    @POST
    @Path("/logout")
    public Response logout() {
        if (!authContext.isLoggedIn()) {
            throw new NotAuthorizedException("Not logged in");
        }
        return Response.noContent().cookie(AuthFilter.clearedSessionCookie()).build();
    }

    private static String origin(ContainerRequestContext requestContext) {
        URI uri = requestContext.getUriInfo().getRequestUri();
        String scheme = uri.getScheme();
        String host = requestContext.getHeaderString(HttpHeaders.HOST);
        if (host == null || host.isBlank()) {
            host = uri.getHost();
            int port = uri.getPort();
            if (port > 0 && port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }
        return scheme + "://" + host;
    }

    /**
     * Absolute URI for the Quinoa-hosted SPA route chosen at login.
     */
    private static URI postLoginUri(ContainerRequestContext requestContext) {
        var returnCookie = requestContext.getCookies().get(AuthFilter.OAUTH_RETURN_COOKIE);
        String path = returnCookie != null ? returnCookie.getValue() : null;
        return URI.create(origin(requestContext) + OAuthReturnPath.normalize(path));
    }
}
