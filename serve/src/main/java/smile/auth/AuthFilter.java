/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.auth;

import java.util.Optional;
import java.util.Set;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.vertx.ext.web.RoutingContext;
import smile.chat.ChatCompletionResource;
import smile.chat.agent.InternalAgentAuth;

/**
 * Resolves the current authenticated identity:
 * <ul>
 *   <li>When an {@link OidcAuthProvider} is active, enforces fail-closed OIDC
 *       Bearer JWT or OIDC session cookie authentication across all endpoints,
 *       disabling consumer OAuth providers, guest mode, and localhost {@code me}.</li>
 *   <li>When no OIDC provider is active, resolves the user from a signed
 *       {@link SessionToken} cookie or localhost {@code me} auto-login.</li>
 * </ul>
 */
@UnlessBuildProfile("micro")
@Provider
@Priority(1000)
public class AuthFilter implements ContainerRequestFilter, ContainerResponseFilter {
    static final String OAUTH_STATE_COOKIE = "smile_oauth_state";
    static final String OAUTH_RETURN_COOKIE = "smile_oauth_return";
    static final String OAUTH_PKCE_COOKIE = "smile_oauth_pkce";
    static final String OAUTH_NONCE_COOKIE = "smile_oauth_nonce";
    static final String PENDING_SESSION_USER = "smile.pendingSessionUserId";
    static final String PENDING_OIDC_SESSION_COOKIE = "smile.pendingOidcSessionCookie";

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");
    private static final Set<String> DISABLED_CONSUMER_OAUTH_PATHS = Set.of(
            "/auth/login/google",
            "/auth/callback/google",
            "/auth/login/microsoft",
            "/auth/callback/microsoft"
    );

    record PendingOidcSession(String token, int maxAgeSeconds) { }

    @Inject
    AuthContext authContext;

    @Inject
    AuthConfig config;

    @Inject
    UserCache userCache;

    @Inject
    RoutingContext routingContext;

    @Inject
    OidcAuthProvider oidcAuthProvider;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        authContext.clear();
        if (oidcAuthProvider != null && oidcAuthProvider.isActive()) {
            filterOidc(requestContext);
            return;
        }
        User user = resolveFromCookie(requestContext);
        boolean localMe = false;
        if (user == null && isLocalHost(requestContext) && config.localMeEnabled()) {
            user = userCache.localMe();
            if (user != null) {
                localMe = true;
                queueSession(requestContext, user.id);
            }
        }
        if (user != null) {
            authContext.setUser(user, localMe);
        }
    }

    private void filterOidc(ContainerRequestContext requestContext) {
        // 1. Fail-closed entitlement/bootstrap check (never falls back to guest/local-me)
        Optional<OidcAuthProvider.AuthResult.Rejected> bootstrapRejection =
                oidcAuthProvider.checkBootstrapAccess();
        if (bootstrapRejection.isPresent()) {
            requestContext.abortWith(toResponse(bootstrapRejection.get()));
            return;
        }

        String path = normalizeRequestPath(requestContext);
        String method = requestContext.getMethod();

        // 2. Disable legacy consumer OAuth endpoints when OIDC SSO is active
        if (DISABLED_CONSUMER_OAUTH_PATHS.contains(path)) {
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"provider_disabled\","
                            + "\"message\":\"Consumer OAuth login is disabled while OIDC SSO is active\"}")
                    .build());
            return;
        }

        // 3. Exempt verified internal kirin loopback hop strictly on POST /chat/completions
        if (InternalAgentAuth.isInternalChatCompletionHop(
                routingContext,
                method,
                path,
                requestContext.getHeaderString(InternalAgentAuth.HEADER_TOKEN),
                requestContext.getHeaderString(ChatCompletionResource.INTERNAL_AGENT_HEADER))) {
            return;
        }

        // 4. Exempt OIDC PKCE login & callback bootstrap paths
        if ("GET".equalsIgnoreCase(method)
                && ("/auth/login/oidc".equals(path) || "/auth/callback/oidc".equals(path))) {
            return;
        }

        // 5. Authenticate via Bearer JWT or OIDC session cookie
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        Cookie sessionCookie = requestContext.getCookies() != null
                ? requestContext.getCookies().get(SessionToken.COOKIE_NAME)
                : null;
        String cookieVal = sessionCookie != null ? sessionCookie.getValue() : null;

        boolean isAuthMe = "GET".equalsIgnoreCase(method) && "/auth/me".equals(path);
        if (isAuthMe && (authHeader == null || authHeader.isBlank())
                && (cookieVal == null || cookieVal.isBlank())) {
            return;
        }

        OidcAuthProvider.AuthRequest authReq = new OidcAuthProvider.AuthRequest(
                authHeader, cookieVal, path, method);
        OidcAuthProvider.AuthResult result = oidcAuthProvider.authenticate(authReq);

        switch (result) {
            case OidcAuthProvider.AuthResult.Authenticated(OidcPrincipal principal) -> {
                User user = resolveCachedOidcUser(principal);
                authContext.setOidcPrincipal(principal, user);
            }
            case OidcAuthProvider.AuthResult.Rejected rejected -> {
                if (isAuthMe && rejected.httpStatus() == 401) {
                    // Allow unauthenticated GET /api/v1/auth/me to report sso_required=true
                    return;
                }
                requestContext.abortWith(toResponse(rejected));
            }
        }
    }

    private User resolveCachedOidcUser(OidcPrincipal principal) {
        if (userCache == null || principal == null) {
            return null;
        }
        if (principal.sessionUserId() != null) {
            User byId = userCache.getById(principal.sessionUserId());
            if (byId != null && byId.authProvider == AuthProviderKind.OIDC) {
                return byId;
            }
        }
        if (principal.externalId() != null && !principal.externalId().isBlank()) {
            User byExt = userCache.getByExternalId(principal.externalId());
            if (byExt != null && byExt.authProvider == AuthProviderKind.OIDC) {
                return byExt;
            }
        }
        return null;
    }

    private static Response toResponse(OidcAuthProvider.AuthResult.Rejected rejected) {
        Response.ResponseBuilder builder = Response.status(rejected.httpStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(rejected.jsonBody() != null ? rejected.jsonBody() : "{\"error\":\"unauthorized\"}");
        if (rejected.wwwAuthenticate() != null && !rejected.wwwAuthenticate().isBlank()) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, rejected.wwwAuthenticate());
        }
        return builder.build();
    }

    static String normalizeRequestPath(ContainerRequestContext requestContext) {
        if (requestContext == null || requestContext.getUriInfo() == null) {
            return "/";
        }
        String raw = requestContext.getUriInfo().getPath();
        if (raw == null || raw.isBlank()) {
            return "/";
        }
        String path = raw.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.startsWith("/api/v1/")) {
            path = path.substring("/api/v1".length());
        } else if ("/api/v1".equals(path)) {
            path = "/";
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object pendingOidc = requestContext.getProperty(PENDING_OIDC_SESSION_COOKIE);
        if (pendingOidc instanceof PendingOidcSession oidcSession) {
            NewCookie cookie = new NewCookie.Builder(SessionToken.COOKIE_NAME)
                    .value(oidcSession.token())
                    .path("/")
                    .maxAge(oidcSession.maxAgeSeconds())
                    .secure(isSecureRequest(requestContext))
                    .httpOnly(true)
                    .sameSite(NewCookie.SameSite.LAX)
                    .build();
            responseContext.getHeaders().add(HttpHeaders.SET_COOKIE, cookie);
            return;
        }
        Object pending = requestContext.getProperty(PENDING_SESSION_USER);
        if (pending instanceof Long userId) {
            String token = SessionToken.create(userId, config.sessionSecret(), config.sessionMaxAgeSeconds());
            NewCookie cookie = new NewCookie.Builder(SessionToken.COOKIE_NAME)
                    .value(token)
                    .path("/")
                    .maxAge(config.sessionMaxAgeSeconds())
                    .secure(isSecureRequest(requestContext))
                    .httpOnly(true)
                    .sameSite(NewCookie.SameSite.LAX)
                    .build();
            responseContext.getHeaders().add(HttpHeaders.SET_COOKIE, cookie);
        }
    }

    /**
     * Queues a legacy session cookie to be set on the response.
     *
     * @param requestContext current request.
     * @param userId         authenticated user id.
     */
    public static void queueSession(ContainerRequestContext requestContext, long userId) {
        requestContext.setProperty(PENDING_SESSION_USER, userId);
    }

    /**
     * Queues a pre-signed OIDC session cookie to be set on the response.
     *
     * @param requestContext current request.
     * @param token          signed OIDC session token value.
     * @param maxAgeSeconds  bounded cookie lifetime in seconds.
     */
    public static void queueOidcSession(
            ContainerRequestContext requestContext,
            String token,
            int maxAgeSeconds
    ) {
        requestContext.setProperty(PENDING_OIDC_SESSION_COOKIE, new PendingOidcSession(token, maxAgeSeconds));
    }

    /**
     * Builds a cookie that clears the signed session.
     *
     * @return expired session cookie for {@link jakarta.ws.rs.core.Response#cookie(NewCookie)}.
     */
    public static NewCookie clearedSessionCookie() {
        return clearedSessionCookie(false);
    }

    /**
     * Builds a cookie that clears the signed session with explicit {@code Secure} flag.
     *
     * @param secure {@code true} when clearing over HTTPS.
     * @return expired session cookie.
     */
    public static NewCookie clearedSessionCookie(boolean secure) {
        return new NewCookie.Builder(SessionToken.COOKIE_NAME)
                .value("")
                .path("/")
                .maxAge(0)
                .secure(secure)
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    /**
     * Clears the session cookie on the response.
     *
     * @param responseContext current response.
     */
    public static void clearSession(ContainerResponseContext responseContext) {
        responseContext.getHeaders().add(HttpHeaders.SET_COOKIE, clearedSessionCookie());
    }

    private User resolveFromCookie(ContainerRequestContext requestContext) {
        if (requestContext.getCookies() == null) {
            return null;
        }
        Cookie cookie = requestContext.getCookies().get(SessionToken.COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }
        Long userId = SessionToken.verify(cookie.getValue(), config.sessionSecret());
        if (userId == null) {
            return null;
        }
        return userCache.getById(userId);
    }

    private boolean isLocalHost(ContainerRequestContext requestContext) {
        String host = hostFromRequest(requestContext);
        return host != null && LOCAL_HOSTS.contains(host.toLowerCase());
    }

    private static String hostFromRequest(ContainerRequestContext requestContext) {
        String host = requestContext.getHeaderString(HttpHeaders.HOST);
        if (host == null || host.isBlank()) {
            return null;
        }
        host = host.trim();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(1, end) : host;
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    boolean isSecureRequest(ContainerRequestContext requestContext) {
        if (requestContext == null) {
            return false;
        }
        if (requestContext.getUriInfo() != null
                && requestContext.getUriInfo().getRequestUri() != null
                && "https".equalsIgnoreCase(requestContext.getUriInfo().getRequestUri().getScheme())) {
            return true;
        }
        if (config != null && config.trustForwardedHeaders()) {
            String forwarded = requestContext.getHeaderString("X-Forwarded-Proto");
            return forwarded != null && forwarded.toLowerCase().contains("https");
        }
        return false;
    }
}
