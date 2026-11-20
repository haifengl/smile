/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.chat;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import smile.auth.AuthConfig;
import smile.auth.AuthContext;
import smile.auth.AuthMeResponse;
import smile.auth.AuthProviderKind;
import smile.auth.GoogleOAuthService;
import smile.auth.MicrosoftOAuthService;
import smile.auth.OidcPrincipal;
import smile.auth.User;
import smile.chat.agent.ChatAgentBridge;
import smile.chat.agent.InternalAgentAuth;
import smile.chat.agent.NoOpChatAgentBridge;
import smile.llm.ChatCompletion;
import smile.llm.Message;
import smile.llm.Role;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Google/Microsoft OAuth 2.0 + S256 PKCE flows and the
 * strict {@code REQ-KIRIN-03} OAuth activation gate in {@link ChatCompletionResource}.
 */
public class OAuthAndAgentGateTest {

    @Test
    public void testAuthContextIsOAuthUserGate() {
        AuthContext ctx = new AuthContext();
        assertFalse(ctx.isLoggedIn());
        assertFalse(ctx.isOAuthUser());

        // Localhost auto-login 'me' user must never qualify as an OAuth user
        User localMe = new User();
        localMe.id = 1L;
        localMe.externalId = "me";
        localMe.displayName = "Me";
        localMe.authProvider = AuthProviderKind.LOCAL;
        ctx.setUser(localMe, true);
        assertTrue(ctx.isLoggedIn());
        assertTrue(ctx.isLocalMeAuto());
        assertFalse(ctx.isOAuthUser());

        // Even if localMeAuto is false (e.g. session cookie for 'me'), LOCAL provider is not OAuth
        ctx.setUser(localMe, false);
        assertFalse(ctx.isOAuthUser());

        // Google OAuth user qualifies
        User googleUser = new User();
        googleUser.id = 2L;
        googleUser.externalId = "google-sub-123";
        googleUser.email = "alice@gmail.com";
        googleUser.displayName = "Alice";
        googleUser.authProvider = AuthProviderKind.GOOGLE;
        ctx.setUser(googleUser, false);
        assertTrue(ctx.isLoggedIn());
        assertFalse(ctx.isLocalMeAuto());
        assertTrue(ctx.isOAuthUser());

        // Microsoft OAuth user qualifies
        User msUser = new User();
        msUser.id = 3L;
        msUser.externalId = "ms:bob-id-456";
        msUser.email = "bob@outlook.com";
        msUser.displayName = "Bob";
        msUser.authProvider = AuthProviderKind.MICROSOFT;
        ctx.setUser(msUser, false);
        assertTrue(ctx.isLoggedIn());
        assertTrue(ctx.isOAuthUser());

        // Browser OIDC session user qualifies for kirin (4-tool scoped mode)
        User oidcUser = new User();
        oidcUser.id = 4L;
        oidcUser.externalId = "oidc:0123456789abcdef:carol";
        oidcUser.email = "carol@corp.example";
        oidcUser.displayName = "Carol";
        oidcUser.authProvider = AuthProviderKind.OIDC;
        OidcPrincipal browserPrincipal = new OidcPrincipal(
                "https://idp.corp.example", "carol", oidcUser.externalId,
                oidcUser.email, oidcUser.displayName, null, Set.of("analyst"),
                false, 4L, Instant.now().plusSeconds(3600), false);
        ctx.setOidcPrincipal(browserPrincipal, oidcUser);
        assertTrue(ctx.isLoggedIn());
        assertTrue(ctx.isOidcAuthenticated());
        assertFalse(ctx.isBearerTokenAuth());
        assertTrue(ctx.isOAuthUser());

        // M2M / Studio Bearer JWT caller does NOT qualify for server-side kirin
        OidcPrincipal bearerPrincipal = new OidcPrincipal(
                "https://idp.corp.example", "svc-studio", oidcUser.externalId,
                oidcUser.email, oidcUser.displayName, null, Set.of("smile-admin"),
                true, null, Instant.now().plusSeconds(3600), true);
        ctx.setOidcPrincipal(bearerPrincipal, oidcUser);
        assertTrue(ctx.isLoggedIn());
        assertTrue(ctx.isBearerTokenAuth());
        assertTrue(ctx.hasAdminRole());
        assertFalse(ctx.isOAuthUser());
    }

    @Test
    public void testShouldRouteToKirinAgentEnforcesStrictOAuthGate() {
        ChatCompletionResource resource = new ChatCompletionResource();
        AuthContext authContext = new AuthContext();
        resource.authContext = authContext;

        ChatAgentBridge activeBridge = new ChatAgentBridge() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Optional<OAuthAppCredentials> resolveOAuthCredentials(
                    AuthProviderKind provider, String id, String secret, String tenant) {
                return Optional.empty();
            }

            @Override
            public void onOAuthLogin(User user, OAuthLoginPayload payload) {
            }

            @Override
            public ChatCompletion complete(
                    User user, CompletionRequest req, int port, String model, Consumer<String> sink) {
                return null;
            }
        };
        resource.agentBridge = activeBridge;

        CompletionRequest plainRequest = new CompletionRequest();
        plainRequest.messages = new Message[] { new Message(Role.user, "Check my calendar today") };
        HttpHeaders normalHeaders = stubHeaders(null);

        // 1. Unauthenticated guest -> false
        assertFalse(resource.shouldRouteToKirinAgent(normalHeaders, plainRequest));

        // 2. Localhost auto-login 'me' -> false
        User localMe = new User();
        localMe.id = 1L;
        localMe.externalId = "me";
        localMe.authProvider = AuthProviderKind.LOCAL;
        authContext.setUser(localMe, true);
        assertFalse(resource.shouldRouteToKirinAgent(normalHeaders, plainRequest));

        // 3. Google OAuth user -> true
        User googleUser = new User();
        googleUser.id = 2L;
        googleUser.externalId = "g-123";
        googleUser.email = "alice@gmail.com";
        googleUser.authProvider = AuthProviderKind.GOOGLE;
        authContext.setUser(googleUser, false);
        assertTrue(resource.shouldRouteToKirinAgent(normalHeaders, plainRequest));

        // 4. Hardened internal loopback hop:
        //    - Spoofed bare "true" MUST NOT be treated as an internal hop
        resource.routingContext = mockRoutingContext("127.0.0.1");
        HttpHeaders spoofedHeaders = stubHeaders(null, "true");
        assertFalse(resource.isInternalAgentHop(spoofedHeaders));
        assertTrue(resource.shouldRouteToKirinAgent(spoofedHeaders, plainRequest));

        //    - Valid per-process secret on remote IP MUST NOT be treated as internal hop
        resource.routingContext = mockRoutingContext("10.0.0.99");
        HttpHeaders validTokenHeaders = stubHeaders(InternalAgentAuth.token(), null);
        assertFalse(resource.isInternalAgentHop(validTokenHeaders));
        assertTrue(resource.shouldRouteToKirinAgent(validTokenHeaders, plainRequest));

        //    - Valid per-process secret on loopback IS treated as internal hop (prevents recursion)
        resource.routingContext = mockRoutingContext("127.0.0.1");
        assertTrue(resource.isInternalAgentHop(validTokenHeaders));
        assertFalse(resource.shouldRouteToKirinAgent(validTokenHeaders, plainRequest));

        // 5. Microsoft OAuth user -> true
        User msUser = new User();
        msUser.id = 3L;
        msUser.externalId = "ms:456";
        msUser.email = "bob@contoso.com";
        msUser.authProvider = AuthProviderKind.MICROSOFT;
        authContext.setUser(msUser, false);
        assertTrue(resource.shouldRouteToKirinAgent(normalHeaders, plainRequest));

        // 6. Non-GPU build (NoOpChatAgentBridge) -> false even for OAuth user
        resource.agentBridge = new NoOpChatAgentBridge();
        assertFalse(resource.shouldRouteToKirinAgent(normalHeaders, plainRequest));
    }

    @Test
    public void testGoogleAndMicrosoftOAuthPkceAuthorizationUrls() throws Exception {
        ChatAgentBridge mockBridge = new ChatAgentBridge() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Optional<OAuthAppCredentials> resolveOAuthCredentials(
                    AuthProviderKind provider, String id, String secret, String tenant) {
                if (provider == AuthProviderKind.GOOGLE) {
                    return Optional.of(new OAuthAppCredentials("built-in-google-id", "built-in-secret", null, true));
                }
                if (provider == AuthProviderKind.MICROSOFT) {
                    return Optional.of(new OAuthAppCredentials("built-in-ms-id", null, "common", true));
                }
                return Optional.empty();
            }

            @Override
            public void onOAuthLogin(User user, OAuthLoginPayload payload) {
            }

            @Override
            public ChatCompletion complete(
                    User user, CompletionRequest req, int port, String model, Consumer<String> sink) {
                return null;
            }
        };

        AuthConfig config = new AuthConfig();
        ObjectMapper mapper = new ObjectMapper();

        GoogleOAuthService google = new GoogleOAuthService();
        setField(google, "config", config);
        setField(google, "objectMapper", mapper);
        setField(google, "agentBridge", mockBridge);

        assertTrue(google.isEnabled());
        String verifier = google.newCodeVerifier();
        URI googleUri = google.authorizationRedirect("http://localhost:8080", "state123", verifier);
        String googleQuery = googleUri.getQuery();
        assertTrue(googleQuery.contains("client_id=built-in-google-id"));
        assertTrue(googleQuery.contains("code_challenge_method=S256"));
        assertTrue(googleQuery.contains("access_type=offline"));
        assertTrue(googleQuery.contains("gmail.readonly"));
        assertTrue(googleQuery.contains("calendar.events"));

        MicrosoftOAuthService microsoft = new MicrosoftOAuthService();
        setField(microsoft, "config", config);
        setField(microsoft, "objectMapper", mapper);
        setField(microsoft, "agentBridge", mockBridge);

        assertTrue(microsoft.isEnabled());
        URI msUri = microsoft.authorizationRedirect("http://localhost:8080", "state456", verifier);
        assertTrue(msUri.toString().startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?"));
        String msQuery = msUri.getQuery();
        assertTrue(msQuery.contains("client_id=built-in-ms-id"));
        assertTrue(msQuery.contains("code_challenge_method=S256"));
        assertTrue(msQuery.contains("Mail.Read"));
        assertTrue(msQuery.contains("Calendars.ReadWrite"));
    }

    @Test
    public void testAuthMeResponseFlags() {
        User user = new User();
        user.id = 10L;
        user.externalId = "google-10";
        user.displayName = "Alice";
        user.email = "alice@gmail.com";
        user.authProvider = AuthProviderKind.GOOGLE;

        AuthMeResponse resp = AuthMeResponse.of(user, true, true, true);
        assertTrue(resp.loggedIn());
        assertEquals(Boolean.TRUE, resp.agentMode());
        assertEquals(Boolean.TRUE, resp.googleEnabled());
        assertEquals(Boolean.TRUE, resp.microsoftEnabled());
        assertEquals("google", resp.user().authProvider());
    }

    private static HttpHeaders stubHeaders(String internalAgentHeader) {
        return stubHeaders(null, internalAgentHeader);
    }

    private static HttpHeaders stubHeaders(String internalTokenHeader, String internalAgentHeader) {
        MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
        if (internalTokenHeader != null) {
            map.putSingle(InternalAgentAuth.HEADER_TOKEN, internalTokenHeader);
        }
        if (internalAgentHeader != null) {
            map.putSingle(ChatCompletionResource.INTERNAL_AGENT_HEADER, internalAgentHeader);
        }
        return new HttpHeaders() {
            @Override
            public List<String> getRequestHeader(String name) {
                return map.get(name);
            }

            @Override
            public String getHeaderString(String name) {
                return map.getFirst(name);
            }

            @Override
            public MultivaluedMap<String, String> getRequestHeaders() {
                return map;
            }

            @Override
            public List<MediaType> getAcceptableMediaTypes() {
                return List.of();
            }

            @Override
            public List<Locale> getAcceptableLanguages() {
                return List.of();
            }

            @Override
            public MediaType getMediaType() {
                return MediaType.APPLICATION_JSON_TYPE;
            }

            @Override
            public Locale getLanguage() {
                return Locale.US;
            }

            @Override
            public Map<String, Cookie> getCookies() {
                return Map.of();
            }

            @Override
            public Date getDate() {
                return new Date();
            }

            @Override
            public int getLength() {
                return 0;
            }
        };
    }

    private static RoutingContext mockRoutingContext(String remoteHost) {
        SocketAddress addr = (SocketAddress) Proxy.newProxyInstance(
                SocketAddress.class.getClassLoader(),
                new Class<?>[] { SocketAddress.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "hostAddress" -> remoteHost;
                    case "isDomainSocket" -> false;
                    case "isInetSocket" -> true;
                    default -> null;
                });
        HttpServerRequest req = (HttpServerRequest) Proxy.newProxyInstance(
                HttpServerRequest.class.getClassLoader(),
                new Class<?>[] { HttpServerRequest.class },
                (proxy, method, args) -> "remoteAddress".equals(method.getName()) ? addr : null);
        return (RoutingContext) Proxy.newProxyInstance(
                RoutingContext.class.getClassLoader(),
                new Class<?>[] { RoutingContext.class },
                (proxy, method, args) -> "request".equals(method.getName()) ? req : null);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
