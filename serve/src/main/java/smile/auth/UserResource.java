/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * User profile API at {@code /api/v1/users/me}.
 */
@Path("/users/me")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    AuthContext authContext;

    /**
     * Returns the authenticated user's profile.
     *
     * @return user profile.
     */
    @GET
    public UserProfile getProfile() {
        return UserProfile.from(authContext.requireUser());
    }

    /**
     * Updates display name, avatar URL, and/or personal instructions.
     *
     * @param request patch body.
     * @return updated profile.
     */
    @PATCH
    @Transactional
    public UserProfile updateProfile(UpdateUserRequest request) {
        User user = authContext.requireUser();
        if (request == null) {
            return UserProfile.from(user);
        }
        if (request.displayName != null) {
            String name = request.displayName.trim();
            if (!name.isEmpty()) {
                user.displayName = name.length() > 128 ? name.substring(0, 128) : name;
            }
        }
        if (request.avatarUrl != null) {
            user.avatarUrl = request.avatarUrl.isBlank() ? null : request.avatarUrl.trim();
        }
        if (request.personalInstructions != null) {
            user.personalInstructions = request.personalInstructions.isBlank()
                    ? null
                    : request.personalInstructions;
        }
        return UserProfile.from(user);
    }
}
