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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
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
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;
import smile.chat.agent.ChatAgentBridge;
import smile.chat.blob.ClientIdentity;

/**
 * Authentication endpoints under {@code /api/v1/auth}.
 */
@UnlessBuildProfile("micro")
@Path("/api/v1/auth")
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

    @Inject
    OidcAuthProvider oidcAuthProvider;

    /**
     * Returns the current user, if any, along with OAuth / OIDC provider availability
     * and whether server-side {@code kirin} Agent Mode is active for this session.
     *
     * @return auth status and profile.
     */
    @GET
    @Path("/me")
    public AuthMeResponse me() {
        if (isOidcActive()) {
            String label = oidcAuthProvider.displayName();
            if (!authContext.isLoggedIn()) {
                return AuthMeResponse.guest(false, false, true, true, label);
            }
            User user = userService != null
                    ? userService.ensureAuthenticatedUser(authContext)
                    : authContext.user();
            if (user == null) {
                return AuthMeResponse.guest(false, false, true, true, label);
            }
            boolean agentMode = authContext.isOAuthUser()
                    && agentBridge != null
                    && agentBridge.isAvailable();
            return AuthMeResponse.of(user, agentMode, false, false, true, true, label);
        }

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
     * Starts Enterprise OIDC SSO Authorization Code + S256 PKCE login.
     *
     * @param requestContext request context.
     * @param returnTo       optional post-login SPA path ({@code /} or {@code /chat/}).
     * @return redirect to the configured OIDC Identity Provider.
     */
    @GET
    @Path("/login/oidc")
    public Response loginOidc(@Context ContainerRequestContext requestContext,
                              @QueryParam("return_to") String returnTo) {
        if (!isOidcActive()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("{\"error\":\"OIDC login is not configured\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        String origin = origin(requestContext);
        String returnPath = OAuthReturnPath.normalize(returnTo);
        boolean secure = isSecure(requestContext);
        try {
            OidcAuthProvider.AuthorizationStart start = oidcAuthProvider.startLogin(origin, returnPath);
            return Response.temporaryRedirect(start.authorizationUri())
                    .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, start.state(), 600, secure))
                    .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, start.codeVerifier(), 600, secure))
                    .cookie(oauthCookie(AuthFilter.OAUTH_NONCE_COOKIE, start.nonce(), 600, secure))
                    .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, returnPath, 600, secure))
                    .build();
        } catch (OidcAuthProvider.OidcUnavailableException e) {
            LOG.warnf(e, "OIDC interactive login unavailable");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("{\"error\":\"oidc_unavailable\",\"message\":\"Identity Provider is currently unreachable\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Completes Enterprise OIDC SSO callback — validates CSRF {@code state}, PKCE
     * {@code code_verifier}, and ID token {@code nonce}, upserts the OIDC user,
     * provisions their isolated {@code kirin} workspace, issues a signed OIDC
     * session cookie, and redirects to the SPA route stored at login.
     *
     * @param requestContext   request context.
     * @param code             authorization code.
     * @param state            CSRF state.
     * @param error            optional OIDC error code.
     * @param errorDescription optional OIDC error description.
     * @return redirect to the SPA.
     */
    @GET
    @Path("/callback/oidc")
    @Transactional
    public Response callbackOidc(@Context ContainerRequestContext requestContext,
                                 @QueryParam("code") String code,
                                 @QueryParam("state") String state,
                                 @QueryParam("error") String error,
                                 @QueryParam("error_description") String errorDescription) {
        boolean secure = isSecure(requestContext);
        if (!isOidcActive()) {
            return clearOidcTransientCookies(Response.status(Response.Status.SERVICE_UNAVAILABLE), secure).build();
        }
        if (error != null && !error.isBlank()) {
            LOG.warnf("OIDC callback returned error=%s description=%s", error, errorDescription);
            return clearOidcTransientCookies(
                    Response.status(Response.Status.BAD_REQUEST)
                            .type(MediaType.APPLICATION_JSON)
                            .entity("{\"error\":\"oidc_login_failed\",\"message\":\"Identity Provider rejected login\"}"),
                    secure).build();
        }
        var cookies = requestContext.getCookies();
        var stateCookie = cookies != null ? cookies.get(AuthFilter.OAUTH_STATE_COOKIE) : null;
        if (stateCookie == null || state == null || !constantTimeEquals(state, stateCookie.getValue())) {
            LOG.warn("OIDC OAuth state mismatch");
            return clearOidcTransientCookies(
                    Response.status(Response.Status.BAD_REQUEST)
                            .type(MediaType.APPLICATION_JSON)
                            .entity("{\"error\":\"invalid_state\",\"message\":\"Invalid OIDC state\"}"),
                    secure).build();
        }
        if (code == null || code.isBlank()) {
            return clearOidcTransientCookies(
                    Response.status(Response.Status.BAD_REQUEST)
                            .type(MediaType.APPLICATION_JSON)
                            .entity("{\"error\":\"missing_code\",\"message\":\"Missing authorization code\"}"),
                    secure).build();
        }
        var pkceCookie = cookies.get(AuthFilter.OAUTH_PKCE_COOKIE);
        var nonceCookie = cookies.get(AuthFilter.OAUTH_NONCE_COOKIE);
        String codeVerifier = pkceCookie != null ? pkceCookie.getValue() : null;
        String expectedNonce = nonceCookie != null ? nonceCookie.getValue() : null;
        if (codeVerifier == null || codeVerifier.isBlank() || expectedNonce == null || expectedNonce.isBlank()) {
            return clearOidcTransientCookies(
                    Response.status(Response.Status.BAD_REQUEST)
                            .type(MediaType.APPLICATION_JSON)
                            .entity("{\"error\":\"invalid_session\",\"message\":\"Missing PKCE or nonce cookie\"}"),
                    secure).build();
        }
        try {
            OidcAuthProvider.CallbackResult result = oidcAuthProvider.completeCallback(
                    origin(requestContext), code, codeVerifier, expectedNonce);
            OidcPrincipal principal = result.principal();
            String displayName = (principal.displayName() != null && !principal.displayName().isBlank())
                    ? principal.displayName()
                    : (principal.email() != null && !principal.email().isBlank()
                            ? principal.email()
                            : principal.subject());
            User user = userService.upsertOidcUser(
                    principal.externalId(), principal.email(), displayName, principal.avatarUrl());
            if (agentBridge != null) {
                agentBridge.onOAuthLogin(user, new ChatAgentBridge.OAuthLoginPayload(
                        AuthProviderKind.OIDC,
                        user.email,
                        user.displayName,
                        null,
                        null,
                        null,
                        null
                ));
            }
            String sessionToken = oidcAuthProvider.createSessionToken(
                    user.id, principal, result.sessionMaxAgeSeconds());
            AuthFilter.queueOidcSession(requestContext, sessionToken, result.sessionMaxAgeSeconds());
            return clearOidcTransientCookies(
                    Response.temporaryRedirect(postLoginUri(requestContext)), secure).build();
        } catch (OidcAuthProvider.OidcUnavailableException e) {
            LOG.warnf(e, "OIDC callback failed: Identity Provider unreachable");
            return clearOidcTransientCookies(
                    Response.status(Response.Status.SERVICE_UNAVAILABLE)
                            .type(MediaType.APPLICATION_JSON)
                            .entity("{\"error\":\"oidc_unavailable\",\"message\":\"Identity Provider unreachable\"}"),
                    secure).build();
        } catch (RuntimeException e) {
            LOG.warn("OIDC callback failed", e);
            return clearOidcTransientCookies(
                    Response.status(Response.Status.BAD_REQUEST)
                            .type(MediaType.APPLICATION_JSON)
                            .entity("{\"error\":\"oidc_login_failed\",\"message\":\"OIDC login failed\"}"),
                    secure).build();
        }
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
        if (isOidcActive()) {
            return consumerProviderDisabled();
        }
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
        boolean secure = isSecure(requestContext);
        return Response.temporaryRedirect(googleOAuth.authorizationRedirect(origin, state, codeVerifier))
                .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, state, 600, secure))
                .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, codeVerifier, 600, secure))
                .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, returnPath, 600, secure))
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
        if (isOidcActive()) {
            return consumerProviderDisabled();
        }
        var stateCookie = requestContext.getCookies().get(AuthFilter.OAUTH_STATE_COOKIE);
        if (stateCookie == null || state == null || !constantTimeEquals(state, stateCookie.getValue())) {
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
        boolean secure = isSecure(requestContext);
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
                    .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, "", 0, secure))
                    .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, "", 0, secure))
                    .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, "", 0, secure))
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
        if (isOidcActive()) {
            return consumerProviderDisabled();
        }
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
        boolean secure = isSecure(requestContext);
        return Response.temporaryRedirect(microsoftOAuth.authorizationRedirect(origin, state, codeVerifier))
                .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, state, 600, secure))
                .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, codeVerifier, 600, secure))
                .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, returnPath, 600, secure))
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
        if (isOidcActive()) {
            return consumerProviderDisabled();
        }
        var stateCookie = requestContext.getCookies().get(AuthFilter.OAUTH_STATE_COOKIE);
        if (stateCookie == null || state == null || !constantTimeEquals(state, stateCookie.getValue())) {
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
        boolean secure = isSecure(requestContext);
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
                    .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, "", 0, secure))
                    .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, "", 0, secure))
                    .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, "", 0, secure))
                    .build();
        } catch (RuntimeException e) {
            LOG.warn("Microsoft OAuth callback failed", e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Login failed").build();
        }
    }

    /**
     * Clears the session cookie and, when OIDC RP-initiated logout is available,
     * returns the IdP logout redirect URL.
     *
     * @param requestContext request context.
     * @return {@code 200 OK} with {@code logout_url} or {@code 204 No Content}.
     */
    @POST
    @Path("/logout")
    public Response logout(@Context ContainerRequestContext requestContext) {
        if (!authContext.isLoggedIn()) {
            throw new NotAuthorizedException("Not logged in");
        }
        NewCookie cleared = AuthFilter.clearedSessionCookie(isSecure(requestContext));
        if (isOidcActive() && requestContext != null) {
            Optional<URI> logoutRedirect = oidcAuthProvider.buildLogoutRedirect(origin(requestContext));
            if (logoutRedirect.isPresent()) {
                return Response.ok(Map.of(
                                "logged_out", true,
                                "logout_url", logoutRedirect.get().toString()))
                        .cookie(cleared)
                        .build();
            }
        }
        return Response.noContent().cookie(cleared).build();
    }

    private boolean isOidcActive() {
        return oidcAuthProvider != null && oidcAuthProvider.isActive();
    }

    private static Response consumerProviderDisabled() {
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"provider_disabled\","
                        + "\"message\":\"Consumer OAuth login is disabled while OIDC SSO is active\"}")
                .build();
    }

    private static Response.ResponseBuilder clearOidcTransientCookies(
            Response.ResponseBuilder builder,
            boolean secure
    ) {
        return builder
                .cookie(oauthCookie(AuthFilter.OAUTH_STATE_COOKIE, "", 0, secure))
                .cookie(oauthCookie(AuthFilter.OAUTH_PKCE_COOKIE, "", 0, secure))
                .cookie(oauthCookie(AuthFilter.OAUTH_NONCE_COOKIE, "", 0, secure))
                .cookie(oauthCookie(AuthFilter.OAUTH_RETURN_COOKIE, "", 0, secure));
    }

    private static NewCookie oauthCookie(String name, String value, int maxAge, boolean secure) {
        return new NewCookie.Builder(name)
                .value(value)
                .path("/")
                .maxAge(maxAge)
                .secure(secure)
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    private boolean isSecure(ContainerRequestContext requestContext) {
        if (requestContext == null) {
            return false;
        }
        if (requestContext.getUriInfo() != null
                && requestContext.getUriInfo().getRequestUri() != null
                && "https".equalsIgnoreCase(requestContext.getUriInfo().getRequestUri().getScheme())) {
            return true;
        }
        if (authConfig != null && authConfig.trustForwardedHeaders()) {
            String forwarded = requestContext.getHeaderString("X-Forwarded-Proto");
            return forwarded != null && forwarded.toLowerCase().contains("https");
        }
        return false;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
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
        var cookies = requestContext.getCookies();
        var returnCookie = cookies != null ? cookies.get(AuthFilter.OAUTH_RETURN_COOKIE) : null;
        String path = returnCookie != null ? returnCookie.getValue() : null;
        return URI.create(origin(requestContext) + OAuthReturnPath.normalize(path));
    }
}
