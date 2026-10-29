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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ioa.agent.email.EmailConfig;
import ioa.agent.email.EmailServices;
import ioa.agent.email.SharedSecrets;
import photos.sluice.secrets.SecretStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UOW-EMAIL-5 in SMILE Studio: {@link EmailOAuthFlow} PKCE
 * loopback authorization, CSRF state validation, and {@link SharedSecrets} +
 * {@link EmailConfig} integration.
 */
class EmailOAuthAndSettingsTest {

    @TempDir
    Path tempDir;

    private HttpServer providerServer;
    private String providerBaseUrl;

    @BeforeEach
    void setUp() throws Exception {
        providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerServer.start();
        providerBaseUrl = "http://127.0.0.1:" + providerServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (providerServer != null) {
            providerServer.stop(0);
        }
        System.clearProperty("ioa.email.config");
    }

    private SecretStore newTestSecretStore() {
        return SecretStore.forApplication("SmileStudioEmailTest")
                .inNamespace("ai.smile.studio.email.test")
                .onOperatingSystem("none")
                .withCredentialFilesIn(tempDir.resolve("credentials"))
                .open();
    }

    @Test
    void pkceLoopbackFlowExchangesCodeAndVerifierForRefreshToken() throws Exception {
        AtomicReference<String> tokenFormReceived = new AtomicReference<>();

        providerServer.createContext("/oauth2/v2.0/token", ex -> {
            tokenFormReceived.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] json = """
                    {"access_token":"ya29.access-1","refresh_token":"1//refresh-token-pkce-999","expires_in":3600}
                    """.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, json.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(json);
            }
        });

        EmailConfig.AccountSpec account = EmailConfig.AccountSpec.gmail(
                "personal-gmail", "Personal Gmail", "user@gmail.com", "test-google-client-id");
        HttpClient httpClient = HttpClient.newHttpClient();

        EmailOAuthFlow.OAuthResult result = EmailOAuthFlow.authorizePkceLoopback(
                account,
                null,
                providerBaseUrl + "/oauth2/v2/auth",
                providerBaseUrl + "/oauth2/v2.0/token",
                authUri -> {
                    // Simulate browser redirect back to the loopback listener with code and matching state
                    Map<String, String> params = parseQuery(authUri.getRawQuery());
                    assertEquals("code", params.get("response_type"));
                    assertEquals("test-google-client-id", params.get("client_id"));
                    assertEquals("S256", params.get("code_challenge_method"));
                    assertNotNull(params.get("code_challenge"));
                    String redirectUri = params.get("redirect_uri");
                    String state = params.get("state");

                    Thread.ofVirtual().start(() -> {
                        try {
                            URI callback = URI.create(redirectUri + "?code=auth-code-777&state=" + state);
                            httpClient.send(
                                    HttpRequest.newBuilder(callback).GET().build(),
                                    HttpResponse.BodyHandlers.ofString()
                            );
                        } catch (Exception ignored) {
                        }
                    });
                },
                httpClient,
                Duration.ofSeconds(10)
        );

        assertEquals("1//refresh-token-pkce-999", result.refreshToken());
        assertEquals("ya29.access-1", result.accessToken());
        assertNotNull(tokenFormReceived.get());
        assertTrue(tokenFormReceived.get().contains("grant_type=authorization_code"));
        assertTrue(tokenFormReceived.get().contains("code=auth-code-777"));
        assertTrue(tokenFormReceived.get().contains("code_verifier="));
    }

    @Test
    void pkceLoopbackFlowRejectsMismatchedStateParameter() {
        EmailConfig.AccountSpec account = EmailConfig.AccountSpec.msGraph(
                "work-outlook", "Work Outlook", "user@outlook.com", "ms-client-id", "common");
        HttpClient httpClient = HttpClient.newHttpClient();

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                EmailOAuthFlow.authorizePkceLoopback(
                        account,
                        null,
                        providerBaseUrl + "/authorize",
                        providerBaseUrl + "/token",
                        authUri -> {
                            Map<String, String> params = parseQuery(authUri.getRawQuery());
                            String redirectUri = params.get("redirect_uri");
                            Thread.ofVirtual().start(() -> {
                                try {
                                    URI callback = URI.create(redirectUri + "?code=auth-code-777&state=forged-state");
                                    httpClient.send(
                                            HttpRequest.newBuilder(callback).GET().build(),
                                            HttpResponse.BodyHandlers.ofString()
                                    );
                                } catch (Exception ignored) {
                                }
                            });
                        },
                        httpClient,
                        Duration.ofSeconds(5)
                ));
        assertInstanceOf(SecurityException.class, ex.getCause());
    }

    @Test
    void savesAndResolvesEmailAccountSecretsInStudioSecretStore() {
        Path emailConfigPath = tempDir.resolve("conf").resolve("email.json");
        System.setProperty("ioa.email.config", emailConfigPath.toString());
        SecretStore secrets = newTestSecretStore();

        EmailConfig.AccountSpec spec = EmailConfig.AccountSpec.msGraph(
                "hotmail-personal", "Personal Hotmail", "alice@hotmail.com", "ms-cid", "common");
        EmailConfig cfg = EmailConfig.empty().withUpsertedAccount(spec);
        cfg.save(emailConfigPath);

        assertTrue(SharedSecrets.saveEmailSecret(secrets, "hotmail-personal", "ms-refresh-token-secret"));
        assertEquals(
                Optional.of("ms-refresh-token-secret"),
                SharedSecrets.resolveEmailSecret(secrets, "hotmail-personal")
        );

        EmailServices services = new EmailServices();
        services.reload(EmailConfig.load(emailConfigPath), secrets);
        assertTrue(services.client("hotmail-personal").isPresent());
        assertEquals(EmailConfig.Provider.MSGRAPH, services.client("hotmail-personal").get().account().provider());

        SharedSecrets.deleteEmailSecret(secrets, "hotmail-personal");
        assertTrue(SharedSecrets.resolveEmailSecret(secrets, "hotmail-personal").isEmpty());

        // Regression test: account IDs with dots/@ (e.g. haifeng.li@aihalo.dev) save without IllegalArgumentException
        assertTrue(SharedSecrets.saveEmailSecret(secrets, "haifeng.li@aihalo.dev", "dot-secret-token"));
        assertEquals(
                Optional.of("dot-secret-token"),
                SharedSecrets.resolveEmailSecret(secrets, "haifeng.li@aihalo.dev")
        );
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
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
}
