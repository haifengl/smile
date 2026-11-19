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
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Authentication and session configuration ({@code smile.auth.*}).
 */
@ApplicationScoped
public class AuthConfig {

    @ConfigProperty(name = "smile.auth.local-me.enabled", defaultValue = "true")
    boolean localMeEnabled;

    @ConfigProperty(name = "smile.auth.session-secret",
            defaultValue = "dev-smile-session-secret-change-me")
    String sessionSecret;

    @ConfigProperty(name = "smile.auth.session-max-age-seconds", defaultValue = "2592000")
    int sessionMaxAgeSeconds;

    @ConfigProperty(name = "smile.auth.google.client-id")
    Optional<String> googleClientId = Optional.empty();

    @ConfigProperty(name = "smile.auth.google.client-secret")
    Optional<String> googleClientSecret = Optional.empty();

    @ConfigProperty(name = "smile.auth.google.redirect-uri")
    Optional<String> googleRedirectUri = Optional.empty();

    @ConfigProperty(name = "smile.auth.microsoft.client-id")
    Optional<String> microsoftClientId = Optional.empty();

    @ConfigProperty(name = "smile.auth.microsoft.client-secret")
    Optional<String> microsoftClientSecret = Optional.empty();

    @ConfigProperty(name = "smile.auth.microsoft.tenant-id")
    Optional<String> microsoftTenantId = Optional.empty();

    @ConfigProperty(name = "smile.auth.microsoft.redirect-uri")
    Optional<String> microsoftRedirectUri = Optional.empty();

    @ConfigProperty(name = "smile.home.users", defaultValue = "/smile/users")
    String usersHomeDir;

    @ConfigProperty(name = "smile.auth.guest-merge-hours", defaultValue = "24")
    int guestMergeHours;

    public boolean localMeEnabled() {
        return localMeEnabled;
    }

    public String sessionSecret() {
        return sessionSecret;
    }

    public int sessionMaxAgeSeconds() {
        return sessionMaxAgeSeconds;
    }

    public String googleClientId() {
        return googleClientId.orElse("");
    }

    public String googleClientSecret() {
        return googleClientSecret.orElse("");
    }

    public String googleRedirectUri() {
        return googleRedirectUri.orElse("");
    }

    public String microsoftClientId() {
        return microsoftClientId.orElse("");
    }

    public String microsoftClientSecret() {
        return microsoftClientSecret.orElse("");
    }

    public String microsoftTenantId() {
        return microsoftTenantId.orElse("");
    }

    public String microsoftRedirectUri() {
        return microsoftRedirectUri.orElse("");
    }

    public String usersHomeDir() {
        String envDir = System.getenv("SMILE_USERS_DIR");
        if (envDir != null && !envDir.isBlank()) {
            return envDir.trim();
        }
        String sysDir = System.getProperty("smile.home.users");
        if (sysDir != null && !sysDir.isBlank()) {
            return sysDir.trim();
        }
        return usersHomeDir != null && !usersHomeDir.isBlank() ? usersHomeDir.trim() : "/smile/users";
    }

    public int guestMergeHours() {
        return guestMergeHours;
    }
}
