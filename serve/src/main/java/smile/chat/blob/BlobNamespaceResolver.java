/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat.blob;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.profile.UnlessBuildProfile;
import smile.auth.AuthContext;
import smile.auth.AuthProviderKind;
import smile.auth.User;

/**
 * Resolves the storage namespace prefix / local subfolder for multimedia attachments:
 * <ul>
 *   <li>Google or Microsoft OAuth users ({@link AuthContext#isOAuthUser()}):
 *       opaque per-user namespace {@code users/<provider>-<24-hex-sha256>},
 *       keeping user emails/PII out of cloud object keys and access logs while
 *       remaining invariant to email address changes.</li>
 *   <li>Unauthenticated guests and localhost auto-login {@code me} users:
 *       UTC date-grouped shared space {@code shared/<YYYY>/<MM>/<DD>} for
 *       straightforward retention and lifecycle cleanup.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
@UnlessBuildProfile("micro")
@ApplicationScoped
public class BlobNamespaceResolver {
    /** Prefix for OAuth-authenticated per-user storage namespaces. */
    public static final String USERS_PREFIX = "users/";
    /** Prefix for UTC date-grouped shared storage namespaces. */
    public static final String SHARED_PREFIX = "shared/";

    private static final DateTimeFormatter UTC_DATE =
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final Clock clock;

    /** Default CDI constructor using UTC system clock. */
    public BlobNamespaceResolver() {
        this(Clock.systemUTC());
    }

    /** Package-private constructor for deterministic testing. */
    BlobNamespaceResolver(Clock clock) {
        this.clock = clock;
    }

    /**
     * Resolves the storage namespace for the current request's {@link AuthContext}.
     *
     * @param authContext request-scoped auth context (may be {@code null}).
     * @return storage namespace prefix without leading or trailing slash.
     */
    public String resolve(AuthContext authContext) {
        if (authContext != null && authContext.isOAuthUser()) {
            return resolve(authContext.user(), clock.instant());
        }
        return sharedNamespace(clock.instant());
    }

    /**
     * Resolves the storage namespace for a user and instant.
     *
     * @param user authenticated user, or {@code null} for guest.
     * @param now  timestamp for UTC date partitioning when shared.
     * @return storage namespace prefix without leading or trailing slash.
     */
    public static String resolve(User user, Instant now) {
        if (user != null
                && (user.authProvider == AuthProviderKind.GOOGLE
                    || user.authProvider == AuthProviderKind.MICROSOFT
                    || user.authProvider == AuthProviderKind.OIDC)
                && user.externalId != null
                && !user.externalId.isBlank()) {
            return oauthUserNamespace(user.authProvider, user.externalId);
        }
        return sharedNamespace(now != null ? now : Instant.now());
    }

    /**
     * Computes the opaque per-user namespace {@code users/<provider>-<24hex>}.
     *
     * @param provider   {@link AuthProviderKind#GOOGLE}, {@link AuthProviderKind#MICROSOFT}, or {@link AuthProviderKind#OIDC}.
     * @param externalId immutable OAuth/OIDC subject or external ID.
     * @return opaque namespace string.
     */
    public static String oauthUserNamespace(AuthProviderKind provider, String externalId) {
        if (provider != AuthProviderKind.GOOGLE
                && provider != AuthProviderKind.MICROSOFT
                && provider != AuthProviderKind.OIDC) {
            throw new IllegalArgumentException("OAuth provider must be GOOGLE, MICROSOFT, or OIDC, got: " + provider);
        }
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("OAuth externalId must not be blank");
        }
        String p = provider.name().toLowerCase(Locale.ROOT);
        String seed = p + ":" + externalId.trim();
        return BlobKeys.USERS_ROOT + "/" + p + "-" + sha256HexPrefix(seed, 24);
    }

    /**
     * Computes the UTC date-partitioned shared namespace {@code shared/YYYY/MM/DD}.
     *
     * @param instant upload timestamp.
     * @return shared namespace string.
     */
    public static String sharedNamespace(Instant instant) {
        Instant ts = instant != null ? instant : Instant.now();
        return BlobKeys.SHARED_ROOT + "/" + UTC_DATE.format(ts);
    }

    private static String sha256HexPrefix(String input, int hexChars) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, hexChars);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
