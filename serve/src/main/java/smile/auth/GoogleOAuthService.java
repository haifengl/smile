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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import smile.chat.agent.ChatAgentBridge;

/**
 * Google OAuth 2.0 authorization-code + S256 PKCE flow with unified identity,
 * Gmail, and Google Calendar scopes and automatic fallback to Studio's
 * {@code OAuthClientVault} via {@link ChatAgentBridge}.
 */
@ApplicationScoped
public class GoogleOAuthService {
    private static final Logger LOG = Logger.getLogger(GoogleOAuthService.class);
    public static final String DEFAULT_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String DEFAULT_TOKEN_URL = "https://oauth2.googleapis.com/token";
    public static final String DEFAULT_USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    /** Unified Google scopes covering OpenID profile, Gmail read/compose/send, and Google Calendar events. */
    public static final String UNIFIED_SCOPES =
            "openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/gmail.compose https://www.googleapis.com/auth/calendar.events";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    AuthConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ChatAgentBridge agentBridge;

    private HttpClient http = HttpClient.newHttpClient();
    private String authUrl = DEFAULT_AUTH_URL;
    private String tokenUrl = DEFAULT_TOKEN_URL;
    private String userinfoUrl = DEFAULT_USERINFO_URL;

    /** Package-private endpoint override for deterministic unit tests. */
    void setEndpointsForTest(String authUrl, String tokenUrl, String userinfoUrl, HttpClient http) {
        this.authUrl = authUrl != null ? authUrl : DEFAULT_AUTH_URL;
        this.tokenUrl = tokenUrl != null ? tokenUrl : DEFAULT_TOKEN_URL;
        this.userinfoUrl = userinfoUrl != null ? userinfoUrl : DEFAULT_USERINFO_URL;
        if (http != null) {
            this.http = http;
        }
    }

    /**
     * Resolves the effective Google OAuth 2.0 application credentials.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code smile.auth.google.client-id} / {@code smile.auth.google.client-secret}</li>
     *   <li>{@link ChatAgentBridge#resolveOAuthCredentials} (env vars + built-in {@code OAuthClientVault})</li>
     * </ol>
     */
    public Optional<ChatAgentBridge.OAuthAppCredentials> resolveCredentials() {
        String customId = config != null ? config.googleClientId() : "";
        String customSecret = config != null ? config.googleClientSecret() : "";
        if (agentBridge != null) {
            Optional<ChatAgentBridge.OAuthAppCredentials> resolved =
                    agentBridge.resolveOAuthCredentials(AuthProviderKind.GOOGLE, customId, customSecret, null);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        if (customId != null && !customId.isBlank() && customSecret != null && !customSecret.isBlank()) {
            return Optional.of(new ChatAgentBridge.OAuthAppCredentials(
                    customId.trim(), customSecret.trim(), null, false));
        }
        return Optional.empty();
    }

    /** {@code true} when Google OAuth credentials are available (explicitly or via built-in vault). */
    public boolean isEnabled() {
        return resolveCredentials().isPresent();
    }

    /**
     * Builds the Google authorization redirect URL without PKCE.
     *
     * @param origin request origin (scheme + host + port).
     * @param state  CSRF state token.
     * @return redirect URI.
     */
    public URI authorizationRedirect(String origin, String state) {
        return authorizationRedirect(origin, state, null);
    }

    /**
     * Builds the Google authorization redirect URL with optional S256 PKCE challenge
     * and unified identity + Gmail + Google Calendar scopes.
     *
     * @param origin       request origin (scheme + host + port).
     * @param state        CSRF state token.
     * @param codeVerifier optional PKCE code verifier.
     * @return redirect URI.
     */
    public URI authorizationRedirect(String origin, String state, String codeVerifier) {
        ChatAgentBridge.OAuthAppCredentials creds = resolveCredentials()
                .orElseThrow(() -> new IllegalStateException("Google OAuth is not configured"));
        String redirect = resolveRedirectUri(origin);
        boolean requestAgentScopes = agentBridge != null && agentBridge.isAvailable();
        String scopes = requestAgentScopes ? UNIFIED_SCOPES : "openid email profile";
        String accessType = requestAgentScopes ? "offline" : "online";
        String prompt = requestAgentScopes ? "consent select_account" : "select_account";

        StringBuilder query = new StringBuilder()
                .append("client_id=").append(enc(creds.clientId()))
                .append("&redirect_uri=").append(enc(redirect))
                .append("&response_type=code")
                .append("&scope=").append(enc(scopes))
                .append("&state=").append(enc(state))
                .append("&access_type=").append(enc(accessType))
                .append("&prompt=").append(enc(prompt));
        if (codeVerifier != null && !codeVerifier.isBlank()) {
            query.append("&code_challenge=").append(enc(computeS256Challenge(codeVerifier)))
                 .append("&code_challenge_method=S256");
        }
        return URI.create(authUrl + "?" + query);
    }

    /**
     * Exchanges an authorization code for profile claims and OAuth tokens.
     *
     * @param origin request origin.
     * @param code   authorization code.
     * @return Google profile fields and OAuth tokens.
     */
    public GoogleProfile exchangeCode(String origin, String code) {
        return exchangeCode(origin, code, null);
    }

    /**
     * Exchanges an authorization code (with optional PKCE verifier) for profile claims and OAuth tokens.
     *
     * @param origin       request origin.
     * @param code         authorization code.
     * @param codeVerifier optional PKCE code verifier.
     * @return Google profile fields and OAuth tokens.
     */
    public GoogleProfile exchangeCode(String origin, String code, String codeVerifier) {
        ChatAgentBridge.OAuthAppCredentials creds = resolveCredentials()
                .orElseThrow(() -> new IllegalStateException("Google OAuth is not configured"));
        String redirect = resolveRedirectUri(origin);
        StringBuilder body = new StringBuilder()
                .append("code=").append(enc(code))
                .append("&client_id=").append(enc(creds.clientId()))
                .append("&redirect_uri=").append(enc(redirect))
                .append("&grant_type=authorization_code");
        if (creds.clientSecret() != null && !creds.clientSecret().isBlank()) {
            body.append("&client_secret=").append(enc(creds.clientSecret()));
        }
        if (codeVerifier != null && !codeVerifier.isBlank()) {
            body.append("&code_verifier=").append(enc(codeVerifier));
        }

        try {
            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
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
            String refreshToken = tokenJson.path("refresh_token").asText(null);
            if (refreshToken == null || refreshToken.isBlank()) {
                refreshToken = "bearer:" + accessToken;
            }

            HttpRequest userReq = HttpRequest.newBuilder(URI.create(userinfoUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> userRes = http.send(userReq, HttpResponse.BodyHandlers.ofString());
            if (userRes.statusCode() / 100 != 2) {
                LOG.warnf("Google userinfo failed: %s", userRes.body());
                throw new IllegalStateException("Google userinfo failed");
            }
            JsonNode userJson = objectMapper.readTree(userRes.body());
            String customClientId = creds.builtIn() ? null : creds.clientId();
            return new GoogleProfile(
                    userJson.path("sub").asText(),
                    userJson.path("email").asText(null),
                    userJson.path("name").asText(null),
                    userJson.path("picture").asText(null),
                    refreshToken,
                    accessToken,
                    customClientId);
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

    /** Creates a 32-byte Base64URL PKCE code verifier. */
    public String newCodeVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Computes an RFC 7636 S256 PKCE code challenge from {@code codeVerifier}. */
    public static String computeS256Challenge(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String resolveRedirectUri(String origin) {
        if (config != null && config.googleRedirectUri() != null && !config.googleRedirectUri().isBlank()) {
            return config.googleRedirectUri();
        }
        return origin + "/api/v1/auth/callback/google";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** Google OpenID profile and OAuth token subset. */
    public record GoogleProfile(
            String sub,
            String email,
            String name,
            String picture,
            String refreshToken,
            String accessToken,
            String customClientId
    ) {
        public GoogleProfile(String sub, String email, String name, String picture) {
            this(sub, email, name, picture, null, null, null);
        }
    }
}
