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
import java.util.Optional;

/**
 * Clean-provenance SPI for optional OIDC SSO and API Bearer JWT authentication
 * providers. The default implementation ({@link NoOpOidcAuthProvider}) is
 * inactive unless superseded by an installed provider implementation.
 */
public interface OidcAuthProvider {

    /**
     * Incoming request credentials extracted by {@link AuthFilter}.
     *
     * @param authorizationHeader raw {@code Authorization} header value, if present.
     * @param sessionCookieValue  raw {@code smile_session} cookie value, if present.
     * @param requestPath         normalized request path relative to {@code /v1} or {@code /api/v1}.
     * @param httpMethod          HTTP method (e.g. {@code GET}, {@code POST}).
     */
    record AuthRequest(
            String authorizationHeader,
            String sessionCookieValue,
            String requestPath,
            String httpMethod
    ) { }

    /** Outcome of authenticating a request against the active OIDC provider. */
    sealed interface AuthResult permits AuthResult.Authenticated, AuthResult.Rejected {
        /** Authentication succeeded and resolved an {@link OidcPrincipal}. */
        record Authenticated(OidcPrincipal principal) implements AuthResult { }

        /** Authentication or authorization failed and the request must be aborted. */
        record Rejected(int httpStatus, String wwwAuthenticate, String jsonBody) implements AuthResult { }
    }

    /**
     * Parameters produced to initiate an Authorization Code + S256 PKCE redirect.
     *
     * @param authorizationUri IdP authorization endpoint URI with query parameters.
     * @param state            CSRF state value to bind in an {@code HttpOnly} cookie.
     * @param codeVerifier     PKCE code verifier to bind in an {@code HttpOnly} cookie.
     * @param nonce            OIDC ID token nonce to bind in an {@code HttpOnly} cookie.
     */
    record AuthorizationStart(
            URI authorizationUri,
            String state,
            String codeVerifier,
            String nonce
    ) { }

    /**
     * Verified result of completing an OIDC authorization code callback.
     *
     * @param principal            verified OIDC user principal.
     * @param sessionMaxAgeSeconds bounded session cookie lifetime in seconds.
     */
    record CallbackResult(
            OidcPrincipal principal,
            int sessionMaxAgeSeconds
    ) { }

    /** Exception thrown when interactive OIDC endpoints are temporarily unreachable (mapped to HTTP 503). */
    final class OidcUnavailableException extends RuntimeException {
        public OidcUnavailableException(String message) {
            super(message);
        }

        public OidcUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** {@code true} when OIDC mode is configured and active (fail-closed). */
    boolean isActive();

    /** Human-readable SSO button label shown in the Web UI (default {@code "SSO"}). */
    String displayName();

    /** {@code true} when an admin role claim is configured for {@code @LocalhostOnly} delegation. */
    boolean hasAdminRoleConfigured();

    /**
     * Optional gate evaluated on bootstrap endpoints such as {@code GET /api/v1/auth/me}
     * when OIDC is active. Returns non-empty to fail closed.
     *
     * @return optional rejection response.
     */
    Optional<AuthResult.Rejected> checkBootstrapAccess();

    /**
     * Authenticates an incoming request from a Bearer JWT or OIDC session cookie.
     *
     * @param request extracted request credentials and metadata.
     * @return authentication outcome.
     */
    AuthResult authenticate(AuthRequest request);

    /**
     * Builds the IdP Authorization Code + S256 PKCE redirect and CSRF/nonce values.
     *
     * @param origin     request origin ({@code scheme://host[:port]}).
     * @param returnPath normalized post-login SPA path.
     * @return authorization start parameters.
     */
    AuthorizationStart startLogin(String origin, String returnPath);

    /**
     * Exchanges the authorization code, verifies the ID token and nonce, and extracts identity/roles.
     *
     * @param origin        request origin ({@code scheme://host[:port]}).
     * @param code          authorization code from IdP callback.
     * @param codeVerifier  PKCE code verifier from transient cookie.
     * @param expectedNonce OIDC nonce from transient cookie.
     * @return verified callback result.
     */
    CallbackResult completeCallback(String origin, String code, String codeVerifier, String expectedNonce);

    /**
     * Creates the signed {@code smile_session} cookie value for an authenticated OIDC browser user.
     *
     * @param userId        persisted database user id.
     * @param principal     verified OIDC principal.
     * @param maxAgeSeconds bounded session lifetime in seconds.
     * @return signed session cookie value.
     */
    String createSessionToken(long userId, OidcPrincipal principal, int maxAgeSeconds);

    /**
     * Returns an optional IdP RP-initiated logout redirect URI when configured or discovered.
     *
     * @param origin request origin ({@code scheme://host[:port]}).
     * @return optional IdP logout URI.
     */
    Optional<URI> buildLogoutRedirect(String origin);
}
