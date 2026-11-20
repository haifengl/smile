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
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Default no-op {@link OidcAuthProvider} active when no OIDC provider plugin is
 * installed. Fails closed at startup if {@code smile.auth.oidc.*} is configured
 * without a provider implementation present.
 */
@UnlessBuildProfile("micro")
@DefaultBean
@ApplicationScoped
public class NoOpOidcAuthProvider implements OidcAuthProvider {

    void onStart(@Observes StartupEvent event) {
        String issuer = resolveConfig("smile.auth.oidc.issuer", "SMILE_AUTH_OIDC_ISSUER");
        String jwksPath = resolveConfig("smile.auth.oidc.jwks-path", "SMILE_AUTH_OIDC_JWKS_PATH");
        String clientId = resolveConfig("smile.auth.oidc.client-id", "SMILE_AUTH_OIDC_CLIENT_ID");
        String audience = resolveConfig("smile.auth.oidc.audience", "SMILE_AUTH_OIDC_AUDIENCE");
        String adminRole = resolveConfig("smile.auth.oidc.admin-role", "SMILE_AUTH_OIDC_ADMIN_ROLE");
        assertNotConfigured(issuer, jwksPath, clientId, audience, adminRole);
    }

    static void assertNotConfigured(String... values) {
        if (values == null) {
            return;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                throw new IllegalStateException(
                        "OIDC authentication (smile.auth.oidc.*) is configured, "
                                + "but no OidcAuthProvider implementation is installed in this build.");
            }
        }
    }

    private static String resolveConfig(String propertyName, String envName) {
        try {
            Config config = ConfigProvider.getConfig();
            Optional<String> val = config.getOptionalValue(propertyName, String.class);
            if (val.isPresent() && !val.get().isBlank()) {
                return val.get().trim();
            }
        } catch (IllegalStateException | ExceptionInInitializerError ignored) {
            // Standalone unit test outside MicroProfile Config
        }
        String sys = System.getProperty(propertyName);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String env = System.getenv(envName);
        return env != null && !env.isBlank() ? env.trim() : null;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public String displayName() {
        return "SSO";
    }

    @Override
    public boolean hasAdminRoleConfigured() {
        return false;
    }

    @Override
    public Optional<AuthResult.Rejected> checkBootstrapAccess() {
        return Optional.empty();
    }

    @Override
    public AuthResult authenticate(AuthRequest request) {
        throw new UnsupportedOperationException("No OidcAuthProvider implementation is installed");
    }

    @Override
    public AuthorizationStart startLogin(String origin, String returnPath) {
        throw new UnsupportedOperationException("No OidcAuthProvider implementation is installed");
    }

    @Override
    public CallbackResult completeCallback(String origin, String code, String codeVerifier, String expectedNonce) {
        throw new UnsupportedOperationException("No OidcAuthProvider implementation is installed");
    }

    @Override
    public String createSessionToken(long userId, OidcPrincipal principal, int maxAgeSeconds) {
        throw new UnsupportedOperationException("No OidcAuthProvider implementation is installed");
    }

    @Override
    public Optional<URI> buildLogoutRedirect(String origin) {
        return Optional.empty();
    }
}
