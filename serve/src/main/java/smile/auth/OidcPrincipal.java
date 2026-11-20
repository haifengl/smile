/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.auth;

import java.time.Instant;
import java.util.Set;

/**
 * Immutable authenticated identity resolved from a verified OIDC Bearer JWT
 * or OIDC browser session cookie.
 *
 * @param issuer          OIDC issuer URI ({@code iss} claim).
 * @param subject         OIDC subject identifier ({@code sub} claim).
 * @param externalId      collision-free issuer-namespaced user external id ({@code <= 128} chars).
 * @param email           optional email claim.
 * @param displayName     optional display name claim.
 * @param avatarUrl       optional avatar/picture URL claim.
 * @param roles           extracted role/group values from configured claim paths.
 * @param adminAuthorized {@code true} when the caller satisfies the configured admin role.
 * @param sessionUserId   persisted database user id when resolved from an OIDC browser session cookie.
 * @param expiresAt       expiration instant of the verified token or session.
 * @param fromBearerToken {@code true} when authenticated via {@code Authorization: Bearer}, {@code false} for session cookie.
 */
public record OidcPrincipal(
        String issuer,
        String subject,
        String externalId,
        String email,
        String displayName,
        String avatarUrl,
        Set<String> roles,
        boolean adminAuthorized,
        Long sessionUserId,
        Instant expiresAt,
        boolean fromBearerToken
) {
    public OidcPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
