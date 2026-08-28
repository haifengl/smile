/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

    /** Sets the resolved user for this request. */
    public void setUser(User user, boolean localMeAuto) {
        this.user = user;
        this.localMeAuto = localMeAuto;
    }

    /** Clears any resolved user. */
    public void clear() {
        this.user = null;
        this.localMeAuto = false;
    }

    /** {@code true} when a user is associated with this request. */
    public boolean isLoggedIn() {
        return user != null;
    }

    /** {@code true} when the user was auto-assigned as local {@code me}. */
    public boolean isLocalMeAuto() {
        return localMeAuto;
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
