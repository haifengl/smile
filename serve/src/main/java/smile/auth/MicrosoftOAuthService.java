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
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import smile.chat.agent.ChatAgentBridge;

/**
 * Microsoft Entra ID / Outlook.com OAuth 2.0 authorization-code + S256 PKCE flow
 * supporting unified identity, Microsoft Graph Mail, and Calendar scopes with
 * automatic fallback to Studio's {@code OAuthClientVault} via {@link ChatAgentBridge}.
 */
@ApplicationScoped
public class MicrosoftOAuthService {
    private static final Logger LOG = Logger.getLogger(MicrosoftOAuthService.class);
    public static final String DEFAULT_AUTH_URL_TEMPLATE =
            "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize";
    public static final String DEFAULT_TOKEN_URL_TEMPLATE =
            "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token";
    public static final String DEFAULT_USERINFO_URL =
            "https://graph.microsoft.com/v1.0/me?$select=id,displayName,mail,userPrincipalName";

    /** Unified Microsoft Graph scopes covering user profile, Outlook/M365 Mail, and Calendar events. */
    public static final String UNIFIED_SCOPES =
            "offline_access https://graph.microsoft.com/User.Read https://graph.microsoft.com/Mail.Read https://graph.microsoft.com/Mail.ReadWrite https://graph.microsoft.com/Mail.Send https://graph.microsoft.com/Calendars.ReadWrite";

    @Inject
    AuthConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ChatAgentBridge agentBridge;

    private HttpClient http = HttpClient.newHttpClient();
    private String authUrlTemplate = DEFAULT_AUTH_URL_TEMPLATE;
    private String tokenUrlTemplate = DEFAULT_TOKEN_URL_TEMPLATE;
    private String userinfoUrl = DEFAULT_USERINFO_URL;

    /** Package-private endpoint override for deterministic unit tests. */
    void setEndpointsForTest(String authUrlTemplate, String tokenUrlTemplate, String userinfoUrl, HttpClient http) {
        this.authUrlTemplate = authUrlTemplate != null ? authUrlTemplate : DEFAULT_AUTH_URL_TEMPLATE;
        this.tokenUrlTemplate = tokenUrlTemplate != null ? tokenUrlTemplate : DEFAULT_TOKEN_URL_TEMPLATE;
        this.userinfoUrl = userinfoUrl != null ? userinfoUrl : DEFAULT_USERINFO_URL;
        if (http != null) {
            this.http = http;
        }
    }

    /**
     * Resolves the effective Microsoft OAuth 2.0 application credentials.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code smile.auth.microsoft.client-id} / {@code tenant-id}</li>
     *   <li>{@link ChatAgentBridge#resolveOAuthCredentials} (env vars + built-in {@code OAuthClientVault})</li>
     * </ol>
     */
    public Optional<ChatAgentBridge.OAuthAppCredentials> resolveCredentials() {
        String customId = config != null ? config.microsoftClientId() : "";
        String customSecret = config != null ? config.microsoftClientSecret() : "";
        String customTenant = config != null ? config.microsoftTenantId() : "";
        if (agentBridge != null) {
            Optional<ChatAgentBridge.OAuthAppCredentials> resolved =
                    agentBridge.resolveOAuthCredentials(AuthProviderKind.MICROSOFT, customId, customSecret, customTenant);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        if (customId != null && !customId.isBlank()) {
            String secret = (customSecret != null && !customSecret.isBlank()) ? customSecret.trim() : null;
            String tenant = (customTenant != null && !customTenant.isBlank()) ? customTenant.trim() : "common";
            return Optional.of(new ChatAgentBridge.OAuthAppCredentials(
                    customId.trim(), secret, tenant, false));
        }
        return Optional.empty();
    }

    /** {@code true} when Microsoft OAuth credentials are available (explicitly or via built-in vault). */
    public boolean isEnabled() {
        return resolveCredentials().isPresent();
    }

    /**
     * Builds the Microsoft Entra v2.0 authorization redirect URL with S256 PKCE.
     *
     * @param origin       request origin (scheme + host + port).
     * @param state        CSRF state token.
     * @param codeVerifier S256 PKCE code verifier.
     * @return redirect URI.
     */
    public URI authorizationRedirect(String origin, String state, String codeVerifier) {
        ChatAgentBridge.OAuthAppCredentials creds = resolveCredentials()
                .orElseThrow(() -> new IllegalStateException("Microsoft OAuth is not configured"));
        String tenant = (creds.tenantId() != null && !creds.tenantId().isBlank()) ? creds.tenantId() : "common";
        String authUrl = authUrlTemplate.replace("{tenant}", enc(tenant));
        String redirect = resolveRedirectUri(origin);
        boolean requestAgentScopes = agentBridge != null && agentBridge.isAvailable();
        String scopes = requestAgentScopes ? UNIFIED_SCOPES : "openid profile email https://graph.microsoft.com/User.Read";

        StringBuilder query = new StringBuilder()
                .append("client_id=").append(enc(creds.clientId()))
                .append("&redirect_uri=").append(enc(redirect))
                .append("&response_type=code")
                .append("&scope=").append(enc(scopes))
                .append("&state=").append(enc(state))
                .append("&prompt=select_account");
        if (codeVerifier != null && !codeVerifier.isBlank()) {
            query.append("&code_challenge=").append(enc(GoogleOAuthService.computeS256Challenge(codeVerifier)))
                 .append("&code_challenge_method=S256");
        }
        return URI.create(authUrl + "?" + query);
    }

    /**
     * Exchanges an authorization code (with PKCE verifier) for Microsoft Graph profile claims and OAuth tokens.
     *
     * @param origin       request origin.
     * @param code         authorization code.
     * @param codeVerifier PKCE code verifier.
     * @return Microsoft profile fields and OAuth tokens.
     */
    public MicrosoftProfile exchangeCode(String origin, String code, String codeVerifier) {
        ChatAgentBridge.OAuthAppCredentials creds = resolveCredentials()
                .orElseThrow(() -> new IllegalStateException("Microsoft OAuth is not configured"));
        String tenant = (creds.tenantId() != null && !creds.tenantId().isBlank()) ? creds.tenantId() : "common";
        String tokenUrl = tokenUrlTemplate.replace("{tenant}", enc(tenant));
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
                LOG.warnf("Microsoft token exchange failed: %s", tokenRes.body());
                throw new IllegalStateException("Microsoft token exchange failed");
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
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> userRes = http.send(userReq, HttpResponse.BodyHandlers.ofString());
            if (userRes.statusCode() / 100 != 2) {
                LOG.warnf("Microsoft userinfo failed: %s", userRes.body());
                throw new IllegalStateException("Microsoft userinfo failed");
            }
            JsonNode userJson = objectMapper.readTree(userRes.body());
            String rawId = userJson.path("id").asText(null);
            String email = userJson.path("mail").asText(null);
            if (email == null || email.isBlank()) {
                email = userJson.path("userPrincipalName").asText(null);
            }
            if (email == null || email.isBlank()) {
                email = userJson.path("email").asText(null);
            }
            String displayName = userJson.path("displayName").asText(null);
            if (displayName == null || displayName.isBlank()) {
                displayName = userJson.path("name").asText(null);
            }
            String subject = (rawId != null && !rawId.isBlank())
                    ? "ms:" + rawId.trim()
                    : (email != null && !email.isBlank() ? "ms:" + email.trim() : "ms:" + UUID.randomUUID());
            String customClientId = creds.builtIn() ? null : creds.clientId();
            return new MicrosoftProfile(
                    subject,
                    email,
                    displayName,
                    refreshToken,
                    accessToken,
                    customClientId,
                    tenant);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Microsoft OAuth failed", e);
        }
    }

    /** Creates a random CSRF state token. */
    public String newState() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveRedirectUri(String origin) {
        if (config != null && config.microsoftRedirectUri() != null && !config.microsoftRedirectUri().isBlank()) {
            return config.microsoftRedirectUri();
        }
        return origin + "/api/v1/auth/callback/microsoft";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** Microsoft Graph user profile and OAuth token subset. */
    public record MicrosoftProfile(
            String sub,
            String email,
            String name,
            String refreshToken,
            String accessToken,
            String customClientId,
            String tenantId
    ) { }
}
