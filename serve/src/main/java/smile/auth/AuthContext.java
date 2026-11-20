/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.auth;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped authenticated user resolved by {@link AuthFilter}.
 */
@RequestScoped
public class AuthContext {
    private User user;
    private boolean localMeAuto;
    private OidcPrincipal oidcPrincipal;

    /** Sets the resolved user for this request. */
    public void setUser(User user, boolean localMeAuto) {
        this.user = user;
        this.localMeAuto = localMeAuto;
        this.oidcPrincipal = null;
    }

    /**
     * Sets the verified OIDC principal and optional cached/persisted user for this request.
     *
     * @param principal verified OIDC identity.
     * @param user      resolved {@link User} entity, or {@code null} for stateless M2M Bearer calls.
     */
    public void setOidcPrincipal(OidcPrincipal principal, User user) {
        this.oidcPrincipal = principal;
        this.user = user;
        this.localMeAuto = false;
    }

    /** Clears any resolved user or OIDC principal. */
    public void clear() {
        this.user = null;
        this.localMeAuto = false;
        this.oidcPrincipal = null;
    }

    /** {@code true} when a user or verified OIDC principal is associated with this request. */
    public boolean isLoggedIn() {
        return user != null || oidcPrincipal != null;
    }

    /** {@code true} when the user was auto-assigned as local {@code me}. */
    public boolean isLocalMeAuto() {
        return localMeAuto;
    }

    /** Returns the verified OIDC principal, or {@code null} when not OIDC-authenticated. */
    public OidcPrincipal oidcPrincipal() {
        return oidcPrincipal;
    }

    /** {@code true} when authenticated via an active OIDC provider (Bearer JWT or OIDC cookie). */
    public boolean isOidcAuthenticated() {
        return oidcPrincipal != null;
    }

    /** {@code true} when authenticated via an {@code Authorization: Bearer} JWT. */
    public boolean isBearerTokenAuth() {
        return oidcPrincipal != null && oidcPrincipal.fromBearerToken();
    }

    /** {@code true} when the OIDC principal carries the configured admin role claim. */
    public boolean hasAdminRole() {
        return oidcPrincipal != null && oidcPrincipal.adminAuthorized();
    }

    /**
     * {@code true} strictly when authenticated via browser Google, Microsoft, or OIDC
     * SSO session (never {@code true} for unauthenticated guests, localhost auto-login
     * {@code me}, or M2M {@code Authorization: Bearer} API callers).
     */
    public boolean isOAuthUser() {
        return user != null
                && !localMeAuto
                && !isBearerTokenAuth()
                && (user.authProvider == AuthProviderKind.GOOGLE
                    || user.authProvider == AuthProviderKind.MICROSOFT
                    || user.authProvider == AuthProviderKind.OIDC);
    }

    /**
     * Returns the current user.
     *
     * @return user or {@code null} for guests.
     */
    public User user() {
        return user;
    }

    /**
     * Returns the current user id when logged in.
     *
     * @return user id or {@code null}.
     */
    public Long userId() {
        return user == null ? null : user.id;
    }

    /**
     * Requires an authenticated user.
     *
     * @return current user.
     * @throws jakarta.ws.rs.NotAuthorizedException when guest.
     */
    public User requireUser() {
        if (user == null) {
            throw new jakarta.ws.rs.NotAuthorizedException("Authentication required");
        }
        return user;
    }
}
