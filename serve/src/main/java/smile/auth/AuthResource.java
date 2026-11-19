/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
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
import smile.chat.agent.ChatAgentBridge;
import smile.chat.blob.ClientIdentity;
import io.quarkus.arc.profile.UnlessBuildProfile;

/**
 * Authentication endpoints under {@code /api/v1/auth}.
 */
@UnlessBuildProfile("micro")
@Path("/auth")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    AuthConfig authConfig;

    @Inject
    AuthContext authContext;

    @Inject
    RoutingContext routingContext;

    @Inject
    UserService userService;

    @Inject
    GoogleOAuthService googleOAuth;

    @Inject
    MicrosoftOAuthService microsoftOAuth;

    @Inject
    ChatAgentBridge agentBridge;

    /**
     * Returns the current user, if any, along with OAuth provider availability
     * and whether server-side {@code kirin} Agent Mode is active for this session.
     *
     * @return auth status and profile.
     */
    @GET
    @Path("/me")
    public AuthMeResponse me() {
        boolean googleEnabled = googleOAuth != null && googleOAuth.isEnabled();
        boolean microsoftEnabled = microsoftOAuth != null && microsoftOAuth.isEnabled();
        if (!authContext.isLoggedIn()) {
            return AuthMeResponse.guest(googleEnabled, microsoftEnabled);
        }
        boolean agentMode = authContext.isOAuthUser()
                && agentBridge != null
                && agentBridge.isAvailable();
        return AuthMeResponse.of(authContext.user(), agentMode, googleEnabled, microsoftEnabled);
    }

    /**
     * Starts Google OAuth (redirects to Google). Uses configured credentials or
     * falls back to Studio's built-in {@code OAuthClientVault}.
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
        String codeVerifier = googleOAuth.newCodeVerifier();
        String returnPath = OAuthReturnPath.normalize(returnTo);
        return Response.temporaryRedirect(googleOAuth.authorizationRedirect(origin, state, codeVerifier))
                .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, state, 600))
                .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, codeVerifier, 600))
                .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, returnPath, 600))
                .build();
    }

    /**
     * Google OAuth callback — establishes a session, provisions the user's
     * {@code /smile} home directory and {@code kirin} agent credentials when
     * Agent Mode is available, and redirects to the SPA route stored at login.
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
        var stateCookie = requestContext.getCookies().get(AuthFilter.OAUTH_STATE_COOKIE);
        if (stateCookie == null || state == null || !state.equals(stateCookie.getValue())) {
            if (!googleOAuth.isEnabled()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
            }
            LOG.warn("Google OAuth state mismatch");
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid OAuth state").build();
        }
        if (!googleOAuth.isEnabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        if (code == null || code.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing code").build();
        }
        var pkceCookie = requestContext.getCookies().get(AuthFilter.OAUTH_PKCE_COOKIE);
        String codeVerifier = pkceCookie != null ? pkceCookie.getValue() : null;
        try {
            GoogleOAuthService.GoogleProfile profile =
                    googleOAuth.exchangeCode(origin(requestContext), code, codeVerifier);
            User user = userService.upsertGoogleUser(
                    profile.sub(), profile.email(), profile.name(), profile.picture());
            userService.mergeGuestConversations(user.id,
                    ClientIdentity.resolveClientIp(
                            routingContext, null, authConfig.trustForwardedHeaders()));
            if (agentBridge != null) {
                agentBridge.onOAuthLogin(user, new ChatAgentBridge.OAuthLoginPayload(
                        AuthProviderKind.GOOGLE,
                        profile.email(),
                        profile.name(),
                        profile.refreshToken(),
                        profile.accessToken(),
                        profile.customClientId(),
                        null
                ));
            }
            AuthFilter.queueSession(requestContext, user.id);
            return Response.temporaryRedirect(postLoginUri(requestContext))
                    .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, "", 0))
                    .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, "", 0))
                    .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, "", 0))
                    .build();
        } catch (RuntimeException e) {
            LOG.warn("Google OAuth callback failed", e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Login failed").build();
        }
    }

    /**
     * Starts Microsoft OAuth (redirects to Microsoft Entra / Outlook.com).
     * Uses configured credentials or falls back to Studio's built-in {@code OAuthClientVault}.
     *
     * @param requestContext request context.
     * @param returnTo       optional post-login SPA path ({@code /} or {@code /chat/}).
     * @return redirect to Microsoft.
     */
    @GET
    @Path("/login/microsoft")
    public Response loginMicrosoft(@Context ContainerRequestContext requestContext,
                                   @QueryParam("return_to") String returnTo) {
        if (!microsoftOAuth.isEnabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("{\"error\":\"Microsoft login is not configured\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        String origin = origin(requestContext);
        String state = microsoftOAuth.newState();
        String codeVerifier = googleOAuth.newCodeVerifier();
        String returnPath = OAuthReturnPath.normalize(returnTo);
        return Response.temporaryRedirect(microsoftOAuth.authorizationRedirect(origin, state, codeVerifier))
                .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, state, 600))
                .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, codeVerifier, 600))
                .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, returnPath, 600))
                .build();
    }

    /**
     * Microsoft OAuth callback — establishes a session, provisions the user's
     * {@code /smile} home directory and {@code kirin} agent credentials when
     * Agent Mode is available, and redirects to the SPA route stored at login.
     *
     * @param requestContext request context.
     * @param code           authorization code.
     * @param state          CSRF state.
     * @return redirect to chat UI.
     */
    @GET
    @Path("/callback/microsoft")
    @Transactional
    public Response callbackMicrosoft(@Context ContainerRequestContext requestContext,
                                      @QueryParam("code") String code,
                                      @QueryParam("state") String state) {
        var stateCookie = requestContext.getCookies().get(AuthFilter.OAUTH_STATE_COOKIE);
        if (stateCookie == null || state == null || !state.equals(stateCookie.getValue())) {
            if (!microsoftOAuth.isEnabled()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
            }
            LOG.warn("Microsoft OAuth state mismatch");
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid OAuth state").build();
        }
        if (!microsoftOAuth.isEnabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        if (code == null || code.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing code").build();
        }
        var pkceCookie = requestContext.getCookies().get(AuthFilter.OAUTH_PKCE_COOKIE);
        String codeVerifier = pkceCookie != null ? pkceCookie.getValue() : null;
        try {
            MicrosoftOAuthService.MicrosoftProfile profile =
                    microsoftOAuth.exchangeCode(origin(requestContext), code, codeVerifier);
            User user = userService.upsertMicrosoftUser(
                    profile.sub(), profile.email(), profile.name(), null);
            userService.mergeGuestConversations(user.id,
                    ClientIdentity.resolveClientIp(
                            routingContext, null, authConfig.trustForwardedHeaders()));
            if (agentBridge != null) {
                agentBridge.onOAuthLogin(user, new ChatAgentBridge.OAuthLoginPayload(
                        AuthProviderKind.MICROSOFT,
                        profile.email(),
                        profile.name(),
                        profile.refreshToken(),
                        profile.accessToken(),
                        profile.customClientId(),
                        profile.tenantId()
                ));
            }
            AuthFilter.queueSession(requestContext, user.id);
            return Response.temporaryRedirect(postLoginUri(requestContext))
                    .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, "", 0))
                    .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, "", 0))
                    .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, "", 0))
                    .build();
        } catch (RuntimeException e) {
            LOG.warn("Microsoft OAuth callback failed", e);
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

    private static NewCookie oauthCookie(String name, String value, int maxAge) {
        return new NewCookie.Builder(name)
                .value(value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .build();
    }

    private static String origin(ContainerRequestContext requestContext) {
        URI uri = requestContext.getUriInfo().getRequestUri();
        String scheme = uri.getScheme();
        String forwardedProto = requestContext.getHeaderString("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            scheme = forwardedProto.split(",")[0].trim();
        }
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
