/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Public user profile returned by auth and user APIs.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfile(
        long id,
        String externalId,
        String email,
        String displayName,
        String avatarUrl,
        String personalInstructions,
        String authProvider) {

    public static UserProfile from(User user) {
        return new UserProfile(
                user.id,
                user.externalId,
                user.email,
                user.displayName,
                user.avatarUrl,
                user.personalInstructions,
                user.authProvider.name().toLowerCase());
    }
}
