/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
    Optional<String> googleClientId;

    @ConfigProperty(name = "smile.auth.google.client-secret")
    Optional<String> googleClientSecret;

    @ConfigProperty(name = "smile.auth.google.redirect-uri")
    Optional<String> googleRedirectUri;

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

    public int guestMergeHours() {
        return guestMergeHours;
    }
}
