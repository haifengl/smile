/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

/**
 * Google OAuth 2.0 authorization-code flow (no Quarkus OIDC tenant required).
 */
@ApplicationScoped
public class GoogleOAuthService {
    private static final Logger LOG = Logger.getLogger(GoogleOAuthService.class);
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    @Inject
    AuthConfig config;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    /** {@code true} when client id and secret are configured. */
    public boolean isEnabled() {
        return config.googleClientId() != null
                && !config.googleClientId().isBlank()
                && config.googleClientSecret() != null
                && !config.googleClientSecret().isBlank();
    }

    /**
     * Builds the Google authorization redirect URL.
     *
     * @param origin request origin (scheme + host + port).
     * @param state  CSRF state token.
     * @return redirect URI.
     */
    public URI authorizationRedirect(String origin, String state) {
        String redirect = resolveRedirectUri(origin);
        String query = "client_id=" + enc(config.googleClientId())
                + "&redirect_uri=" + enc(redirect)
                + "&response_type=code"
                + "&scope=" + enc("openid email profile")
                + "&state=" + enc(state)
                + "&access_type=online"
                + "&prompt=select_account";
        return URI.create(AUTH_URL + "?" + query);
    }

    /**
     * Exchanges an authorization code for profile claims.
     *
     * @param origin request origin.
     * @param code   authorization code.
     * @return Google profile fields: sub, email, name, picture.
     */
    public GoogleProfile exchangeCode(String origin, String code) {
        if (!isEnabled()) {
            throw new IllegalStateException("Google OAuth is not configured");
        }
        String redirect = resolveRedirectUri(origin);
        String body = "code=" + enc(code)
                + "&client_id=" + enc(config.googleClientId())
                + "&client_secret=" + enc(config.googleClientSecret())
                + "&redirect_uri=" + enc(redirect)
                + "&grant_type=authorization_code";
        try {
            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> tokenRes = http.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            if (tokenRes.statusCode() / 100 != 2) {
                LOG.warnf("Google token exchange failed: %s", tokenRes.body());
                throw new IllegalStateException("Google token exchange failed");
            }
            JsonNode tokenJson = objectMapper.readTree(tokenRes.body());
            String accessToken = tokenJson.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("Missing access_token");
            }

            HttpRequest userReq = HttpRequest.newBuilder(URI.create(USERINFO_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> userRes = http.send(userReq, HttpResponse.BodyHandlers.ofString());
            if (userRes.statusCode() / 100 != 2) {
                LOG.warnf("Google userinfo failed: %s", userRes.body());
                throw new IllegalStateException("Google userinfo failed");
            }
            JsonNode userJson = objectMapper.readTree(userRes.body());
            return new GoogleProfile(
                    userJson.path("sub").asText(),
                    userJson.path("email").asText(null),
                    userJson.path("name").asText(null),
                    userJson.path("picture").asText(null));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Google OAuth failed", e);
        }
    }

    /** Creates a random CSRF state token. */
    public String newState() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveRedirectUri(String origin) {
        if (config.googleRedirectUri() != null && !config.googleRedirectUri().isBlank()) {
            return config.googleRedirectUri();
        }
        return origin + "/api/v1/auth/callback/google";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Google OpenID profile subset. */
    public record GoogleProfile(String sub, String email, String name, String picture) {}
}
