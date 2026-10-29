/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import ioa.agent.email.EmailConfig;
import smile.util.Strings;

/**
 * Desktop OAuth 2.0 PKCE loopback authorization flow (RFC 7636 + RFC 8252) for
 * Google Gmail ({@link EmailConfig.Provider#GMAIL}) and Microsoft Graph
 * ({@link EmailConfig.Provider#MSGRAPH}).
 *
 * <p>Starts an ephemeral loopback HTTP listener on {@code 127.0.0.1:0}, opens
 * the provider's authorization URL in the user's browser with a SHA-256 PKCE
 * {@code code_challenge} and anti-CSRF {@code state} parameter, receives the
 * authorization code redirect, and exchanges it at the token endpoint for an
 * OAuth 2.0 {@code refresh_token}.
 *
 * @author Haifeng Li
 */
public final class EmailOAuthFlow {

    /** Google OAuth 2.0 authorization endpoint. */
    public static final String GOOGLE_AUTH_ENDPOINT =
            "https://accounts.google.com/o/oauth2/v2/auth";
    /** Google OAuth 2.0 token endpoint. */
    public static final String GOOGLE_TOKEN_ENDPOINT =
            "https://oauth2.googleapis.com/token";
    /** Google Gmail scopes for reading, drafting, and sending email. */
    public static final String GOOGLE_SCOPES =
            "https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/gmail.compose";

    /** Microsoft Entra v2.0 authorization endpoint template (`{tenant}`). */
    public static final String MS_AUTH_ENDPOINT_TEMPLATE =
            "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize";
    /** Microsoft Entra v2.0 token endpoint template (`{tenant}`). */
    public static final String MS_TOKEN_ENDPOINT_TEMPLATE =
            "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token";
    /** Microsoft Graph scopes for personal Outlook.com/Hotmail and M365 accounts. */
    public static final String MS_SCOPES =
            "offline_access https://graph.microsoft.com/Mail.Read https://graph.microsoft.com/Mail.ReadWrite https://graph.microsoft.com/Mail.Send https://graph.microsoft.com/User.Read";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Tokens returned upon completing the OAuth 2.0 authorization code exchange.
     *
     * @param refreshToken long-lived OAuth 2.0 refresh token to store in {@code SecretStore}.
     * @param accessToken initial short-lived bearer access token.
     */
    public record OAuthResult(
            String refreshToken,
            String accessToken
    ) { }

    private EmailOAuthFlow() {
    }

    /**
     * Runs the interactive browser PKCE loopback OAuth 2.0 flow for {@code account}
     * using default provider endpoints and {@link Desktop#browse(URI)}.
     *
     * @param account email account specification (`GMAIL` or `MSGRAPH`).
     * @param clientSecret optional OAuth client secret (null or empty for public native clients).
     * @param timeout max wait duration for the user to complete sign-in in the browser.
     * @return the acquired {@link OAuthResult}.
     * @throws Exception if authorization is denied, times out, or token exchange fails.
     */
    public static OAuthResult authorizePkceLoopback(
            EmailConfig.AccountSpec account,
            String clientSecret,
            Duration timeout
    ) throws Exception {
        String tenant = Strings.isNullOrBlank(account.tenantId()) ? "common" : account.tenantId().trim();
        String authEndpoint = account.provider() == EmailConfig.Provider.GMAIL
                ? GOOGLE_AUTH_ENDPOINT
                : MS_AUTH_ENDPOINT_TEMPLATE.replace("{tenant}", urlEncode(tenant));
        String tokenEndpoint = account.provider() == EmailConfig.Provider.GMAIL
                ? GOOGLE_TOKEN_ENDPOINT
                : MS_TOKEN_ENDPOINT_TEMPLATE.replace("{tenant}", urlEncode(tenant));

        return authorizePkceLoopback(
                account,
                clientSecret,
                authEndpoint,
                tokenEndpoint,
                uri -> {
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(uri);
                        } else {
                            throw new IllegalStateException("System browser launch is not supported; open URL manually: " + uri);
                        }
                    } catch (IOException ex) {
                        throw new RuntimeException("Failed to open browser: " + ex.getMessage(), ex);
                    }
                },
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                timeout
        );
    }

    /**
     * Runs the PKCE loopback OAuth 2.0 flow with custom endpoints and browser launcher
     * (used in deterministic unit tests).
     *
     * @param account email account specification.
     * @param clientSecret optional OAuth client secret.
     * @param authEndpoint OAuth authorization endpoint URL.
     * @param tokenEndpoint OAuth token endpoint URL.
     * @param browserLauncher callback receiving the full authorization {@link URI}.
     * @param httpClient HTTP client for token exchange.
     * @param timeout timeout waiting for loopback redirect.
     * @return the acquired {@link OAuthResult}.
     * @throws Exception if the flow fails or times out.
     */
    public static OAuthResult authorizePkceLoopback(
            EmailConfig.AccountSpec account,
            String clientSecret,
            String authEndpoint,
            String tokenEndpoint,
            Consumer<URI> browserLauncher,
            HttpClient httpClient,
            Duration timeout
    ) throws Exception {
        if (Strings.isNullOrBlank(account.clientId())) {
            throw new IllegalArgumentException(
                    "OAuth Client ID is required for " + account.provider() + " account '" + account.emailAddress() + "'.");
        }

        String codeVerifier = generateCodeVerifier();
        String codeChallenge = computeS256Challenge(codeVerifier);
        String expectedState = generateRandomToken(16);

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        // Use root "/" so a single registered Redirect URI ("http://127.0.0.1" or
        // "http://localhost") in Azure Portal / Google Cloud matches without needing
        // a "/oauth/callback" path suffix.
        String redirectUri = "http://127.0.0.1:" + port;

        server.createContext("/", exchange -> {
            try {
                String reqPath = exchange.getRequestURI().getPath();
                if (reqPath != null && reqPath.startsWith("/favicon")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
                String error = queryParams.get("error");
                if (!Strings.isNullOrBlank(error)) {
                    String desc = queryParams.getOrDefault("error_description", error);
                    writeHtml(exchange, 400, "<h3>OAuth Authorization Failed</h3><p>" + escapeHtml(desc) + "</p>");
                    codeFuture.completeExceptionally(new IOException("OAuth error: " + desc));
                    return;
                }
                String state = queryParams.get("state");
                if (!expectedState.equals(state)) {
                    writeHtml(exchange, 400, "<h3>Invalid OAuth State</h3><p>State mismatch.</p>");
                    codeFuture.completeExceptionally(new SecurityException("OAuth state parameter mismatch."));
                    return;
                }
                String code = queryParams.get("code");
                if (Strings.isNullOrBlank(code)) {
                    writeHtml(exchange, 400, "<h3>Missing Authorization Code</h3>");
                    codeFuture.completeExceptionally(new IOException("Missing 'code' in OAuth callback."));
                    return;
                }
                writeHtml(exchange, 200,
                        "<h3>SMILE Studio Email Connected</h3><p>Authentication succeeded. You may close this browser tab.</p>");
                codeFuture.complete(code);
            } finally {
                exchange.close();
            }
        });

        server.start();
        try {
            String scopes = account.provider() == EmailConfig.Provider.GMAIL ? GOOGLE_SCOPES : MS_SCOPES;
            StringBuilder authUrl = new StringBuilder(authEndpoint)
                    .append("?response_type=code")
                    .append("&client_id=").append(urlEncode(account.clientId().trim()))
                    .append("&redirect_uri=").append(urlEncode(redirectUri))
                    .append("&scope=").append(urlEncode(scopes))
                    .append("&state=").append(urlEncode(expectedState))
                    .append("&code_challenge=").append(urlEncode(codeChallenge))
                    .append("&code_challenge_method=S256");
            if (account.provider() == EmailConfig.Provider.GMAIL) {
                authUrl.append("&access_type=offline&prompt=consent");
            }
            if (!Strings.isNullOrBlank(account.emailAddress())) {
                authUrl.append("&login_hint=").append(urlEncode(account.emailAddress().trim()));
            }

            browserLauncher.accept(URI.create(authUrl.toString()));

            long waitMs = timeout != null && !timeout.isNegative() && !timeout.isZero()
                    ? timeout.toMillis()
                    : 180_000L;
            String authCode = codeFuture.get(waitMs, TimeUnit.MILLISECONDS);

            StringBuilder form = new StringBuilder()
                    .append("grant_type=authorization_code")
                    .append("&client_id=").append(urlEncode(account.clientId().trim()))
                    .append("&code=").append(urlEncode(authCode))
                    .append("&redirect_uri=").append(urlEncode(redirectUri))
                    .append("&code_verifier=").append(urlEncode(codeVerifier));
            if (!Strings.isNullOrBlank(clientSecret)) {
                form.append("&client_secret=").append(urlEncode(clientSecret.trim()));
            }

            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> tokenResp = httpClient.send(
                    tokenReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (tokenResp.statusCode() < 200 || tokenResp.statusCode() >= 300) {
                throw new IOException("OAuth token exchange failed (HTTP "
                        + tokenResp.statusCode() + "): " + tokenResp.body());
            }

            JsonNode root = MAPPER.readTree(tokenResp.body());
            String refreshToken = root.has("refresh_token") ? root.get("refresh_token").asString("").trim() : "";
            String accessToken = root.has("access_token") ? root.get("access_token").asString("").trim() : "";
            if (refreshToken.isEmpty() && accessToken.isEmpty()) {
                throw new IOException("OAuth token response did not include refresh_token or access_token.");
            }
            return new OAuthResult(
                    refreshToken.isEmpty() ? "bearer:" + accessToken : refreshToken,
                    accessToken
            );
        } finally {
            server.stop(0);
        }
    }

    static String generateCodeVerifier() {
        return generateRandomToken(32);
    }

    static String computeS256Challenge(String codeVerifier) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String generateRandomToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (Strings.isNullOrBlank(rawQuery)) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static void writeHtml(com.sun.net.httpserver.HttpExchange ex, int status, String bodyHtml) throws IOException {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>SMILE Studio OAuth</title></head>"
                + "<body style=\"font-family:sans-serif;padding:24px;\">" + bodyHtml + "</body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
