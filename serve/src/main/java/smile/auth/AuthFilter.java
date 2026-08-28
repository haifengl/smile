/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import java.util.Set;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.ext.Provider;
import smile.chat.blob.ClientIdentity;
import io.vertx.ext.web.RoutingContext;

/**
 * Resolves the current user from a signed session cookie or localhost auto-login.
 */
@Provider
@Priority(1000)
public class AuthFilter implements ContainerRequestFilter, ContainerResponseFilter {
    static final String OAUTH_STATE_COOKIE = "smile_oauth_state";
    static final String PENDING_SESSION_USER = "smile.pendingSessionUserId";

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

    @Inject
    AuthContext authContext;

    @Inject
    UserService userService;

    @Inject
    AuthConfig config;

    @Inject
    RoutingContext routingContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        authContext.clear();
        User user = resolveFromCookie(requestContext);
        boolean localMe = false;
        if (user == null && isLocalHost(requestContext) && config.localMeEnabled()) {
            user = userService.findOrCreateMe();
            localMe = true;
            queueSession(requestContext, user.id);
        }
        if (user != null) {
            authContext.setUser(user, localMe);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object pending = requestContext.getProperty(PENDING_SESSION_USER);
        if (pending instanceof Long userId) {
            String token = SessionToken.create(userId, config.sessionSecret(), config.sessionMaxAgeSeconds());
            NewCookie cookie = new NewCookie(
                    SessionToken.COOKIE_NAME,
                    token,
                    "/",
                    null,
                    null,
                    config.sessionMaxAgeSeconds(),
                    isSecureRequest(requestContext),
                    true);
            responseContext.getHeaders().add(HttpHeaders.SET_COOKIE, cookie);
        }
    }

    /**
     * Queues a session cookie to be set on the response.
     *
     * @param requestContext current request.
     * @param userId         authenticated user id.
     */
    public static void queueSession(ContainerRequestContext requestContext, long userId) {
        requestContext.setProperty(PENDING_SESSION_USER, userId);
    }

    /**
     * Clears the session cookie on the response.
     *
     * @param responseContext current response.
     */
    public static void clearSession(ContainerResponseContext responseContext) {
        NewCookie cookie = new NewCookie(SessionToken.COOKIE_NAME, "", "/", null, null, 0, false, true);
        responseContext.getHeaders().add(HttpHeaders.SET_COOKIE, cookie);
    }

    private User resolveFromCookie(ContainerRequestContext requestContext) {
        Cookie cookie = requestContext.getCookies().get(SessionToken.COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }
        Long userId = SessionToken.verify(cookie.getValue(), config.sessionSecret());
        if (userId == null) {
            return null;
        }
        return userService.findById(userId);
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

    private boolean isSecureRequest(ContainerRequestContext requestContext) {
        if ("https".equalsIgnoreCase(requestContext.getUriInfo().getRequestUri().getScheme())) {
            return true;
        }
        String forwarded = requestContext.getHeaderString("X-Forwarded-Proto");
        return forwarded != null && forwarded.toLowerCase().contains("https");
    }
}
