/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.auth;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import smile.chat.ChatCompletionResource;
import smile.chat.agent.InternalAgentAuth;
import smile.chat.blob.BlobNamespaceResolver;
import smile.serve.LocalhostFilter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the open-source OIDC SPI, {@link NoOpOidcAuthProvider} fail-closed
 * guard, {@link InternalAgentAuth}, {@link AuthFilter} OIDC delegation, and
 * {@link LocalhostFilter} admin-role delegation.
 */
public class OidcSpiAndSecurityUnitTest {

    @Test
    public void testNoOpProviderFailsClosedWhenOidcConfigured() {
        NoOpOidcAuthProvider noOp = new NoOpOidcAuthProvider();
        assertFalse(noOp.isActive());
        assertEquals("SSO", noOp.displayName());
        assertFalse(noOp.hasAdminRoleConfigured());
        assertTrue(noOp.checkBootstrapAccess().isEmpty());
        assertTrue(noOp.buildLogoutRedirect("https://serve.local").isEmpty());

        assertDoesNotThrow(() -> NoOpOidcAuthProvider.assertNotConfigured(null, "", "   "));
        assertThrows(IllegalStateException.class,
                () -> NoOpOidcAuthProvider.assertNotConfigured("https://idp.corp.example", null));
        assertThrows(IllegalStateException.class,
                () -> NoOpOidcAuthProvider.assertNotConfigured(null, "/etc/smile/jwks.json"));
    }

    @Test
    public void testInternalAgentAuthEnforcesLoopbackPathMethodAndSecret() {
        RoutingContext loopback = mockRoutingContext("127.0.0.1");
        RoutingContext remote = mockRoutingContext("10.0.0.42");
        String secret = InternalAgentAuth.token();
        assertNotNull(secret);
        assertTrue(secret.length() >= 43);

        // Valid secret + loopback + POST /chat/completions -> allowed
        assertTrue(InternalAgentAuth.isInternalChatCompletionHop(
                loopback, "POST", "/chat/completions", secret, null));
        assertTrue(InternalAgentAuth.isInternalChatCompletionHop(
                loopback, "POST", "/v1/chat/completions", secret, null));
        assertTrue(InternalAgentAuth.isInternalChatCompletionHop(
                loopback, "POST", "/api/v1/chat/completions", null, secret));

        // Spoofed static "true" -> rejected even on loopback
        assertFalse(InternalAgentAuth.isInternalChatCompletionHop(
                loopback, "POST", "/chat/completions", null, "true"));
        assertFalse(InternalAgentAuth.isInternalChatCompletionHop(
                loopback, "POST", "/chat/completions", "true", "true"));

        // Valid secret from remote IP -> rejected
        assertFalse(InternalAgentAuth.isInternalChatCompletionHop(
                remote, "POST", "/chat/completions", secret, null));

        // Valid secret on non-chat-completions endpoint or wrong method -> rejected
        assertFalse(InternalAgentAuth.isInternalChatCompletionHop(
                loopback, "GET", "/chat/completions", secret, null));
        assertFalse(InternalAgentAuth.isInternalChatCompletionHop(
                loopback, "POST", "/models/load", secret, null));
        assertFalse(InternalAgentAuth.isInternalChatCompletionHop(
                loopback, "POST", "/smile/iris", secret, null));
    }

    @Test
    public void testAuthFilterOidcEnforcesFailClosedAcrossSurfaces() {
        AuthFilter filter = new AuthFilter();
        filter.authContext = new AuthContext();
        filter.config = new AuthConfig();
        filter.userCache = new UserCache();
        filter.routingContext = mockRoutingContext("127.0.0.1");

        User localMe = new User();
        localMe.id = 1L;
        localMe.externalId = "me";
        localMe.displayName = "Me";
        localMe.authProvider = AuthProviderKind.LOCAL;
        filter.userCache.register(localMe);

        OidcPrincipal verifiedPrincipal = new OidcPrincipal(
                "https://idp.corp.example", "alice-sub", "oidc:1234567890abcdef:alice-sub",
                "alice@corp.example", "Alice", null, Set.of("smile-admin"),
                true, null, Instant.now().plusSeconds(3600), true);

        filter.oidcAuthProvider = stubOidcProvider(true, true, Optional.empty(), verifiedPrincipal);

        // 1. Unauthenticated /smile/iris even from 127.0.0.1 with Host: localhost -> 401 (no localMe fallback!)
        AtomicReference<Response> aborted = new AtomicReference<>();
        filter.filter(mockRequest("POST", "/smile/iris",
                Map.of(HttpHeaders.HOST, "localhost:8080"), Map.of(), aborted));
        assertNotNull(aborted.get());
        assertEquals(401, aborted.get().getStatus());
        assertNotNull(aborted.get().getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
        assertFalse(filter.authContext.isLoggedIn());

        // 2. Spoofed X-Smile-Internal-Agent: true on /chat/completions from 127.0.0.1 -> 401
        aborted.set(null);
        filter.filter(mockRequest("POST", "/chat/completions",
                Map.of(ChatCompletionResource.INTERNAL_AGENT_HEADER, "true"), Map.of(), aborted));
        assertNotNull(aborted.get());
        assertEquals(401, aborted.get().getStatus());

        // 3. Valid InternalAgentAuth secret on loopback POST /v1/chat/completions & /chat/completions -> allowed
        aborted.set(null);
        filter.filter(mockRequest("POST", "/chat/completions",
                Map.of(InternalAgentAuth.HEADER_TOKEN, InternalAgentAuth.token()), Map.of(), aborted));
        assertNull(aborted.get());
        aborted.set(null);
        filter.filter(mockRequest("POST", "/v1/chat/completions",
                Map.of(InternalAgentAuth.HEADER_TOKEN, InternalAgentAuth.token()), Map.of(), aborted));
        assertNull(aborted.get());

        // 4. Legacy consumer OAuth endpoints -> 403 Forbidden
        for (String legacyPath : new String[] {
                "/auth/login/google", "/auth/callback/google",
                "/auth/login/microsoft", "/auth/callback/microsoft" }) {
            aborted.set(null);
            filter.filter(mockRequest("GET", legacyPath, Map.of(), Map.of(), aborted));
            assertNotNull(aborted.get(), "Expected 403 on " + legacyPath);
            assertEquals(403, aborted.get().getStatus());
        }

        // 5. OIDC login/callback and unauthenticated /auth/me -> allowed through
        for (String bootstrapPath : new String[] { "/auth/login/oidc", "/auth/callback/oidc", "/auth/me" }) {
            aborted.set(null);
            filter.filter(mockRequest("GET", bootstrapPath, Map.of(), Map.of(), aborted));
            assertNull(aborted.get(), "Expected bootstrap pass-through on " + bootstrapPath);
        }

        // 6. Valid Bearer JWT populates AuthContext without touching DB
        aborted.set(null);
        filter.filter(mockRequest("POST", "/smile/iris",
                Map.of(HttpHeaders.AUTHORIZATION, "Bearer valid-jwt"), Map.of(), aborted));
        assertNull(aborted.get());
        assertTrue(filter.authContext.isLoggedIn());
        assertTrue(filter.authContext.isOidcAuthenticated());
        assertTrue(filter.authContext.isBearerTokenAuth());
        assertTrue(filter.authContext.hasAdminRole());
        assertNull(filter.authContext.user(), "M2M hot path must not require User entity");

        // 7. Bootstrap entitlement failure (e.g. unlicensed 402) blocks even /auth/me fail-closed
        filter.oidcAuthProvider = stubOidcProvider(
                true, false,
                Optional.of(new OidcAuthProvider.AuthResult.Rejected(402, null, "{\"error\":\"LICENSE_REQUIRED\"}")),
                null);
        aborted.set(null);
        filter.filter(mockRequest("GET", "/auth/me", Map.of(), Map.of(), aborted));
        assertNotNull(aborted.get());
        assertEquals(402, aborted.get().getStatus());
    }

    @Test
    public void testLocalhostFilterDelegatesToOidcAdminRole() throws Exception {
        LocalhostFilter filter = new LocalhostFilter();
        setField(filter, "routingContext", mockRoutingContext("10.0.0.55"));

        // 1. OIDC active, admin-role NOT configured -> still enforces localhost (403 remote)
        OidcAuthProvider noAdminRole = stubOidcProvider(true, false, Optional.empty(), null);
        AuthContext ctx = new AuthContext();
        setField(filter, "oidcAuthProvider", singleInstance(noAdminRole));
        setField(filter, "authContext", singleInstance(ctx));
        assertThrows(ForbiddenException.class, () -> filter.filter(null));

        // 2. OIDC active, admin-role configured, caller lacks role -> 403 Forbidden
        OidcAuthProvider withAdminRole = stubOidcProvider(true, true, Optional.empty(), null);
        setField(filter, "oidcAuthProvider", singleInstance(withAdminRole));
        ctx.setOidcPrincipal(new OidcPrincipal(
                "https://idp.corp.example", "bob", "oidc:abc:bob",
                "bob@corp.example", "Bob", null, Set.of("viewer"),
                false, null, Instant.now().plusSeconds(3600), true), null);
        assertThrows(ForbiddenException.class, () -> filter.filter(null));

        // 3. OIDC active, admin-role configured, caller HAS role -> allowed remotely!
        ctx.setOidcPrincipal(new OidcPrincipal(
                "https://idp.corp.example", "admin", "oidc:abc:admin",
                "admin@corp.example", "Admin", null, Set.of("smile-admin"),
                true, null, Instant.now().plusSeconds(3600), true), null);
        assertDoesNotThrow(() -> filter.filter(null));
    }

    @Test
    public void testBlobNamespaceResolverSupportsOidcUsers() {
        BlobNamespaceResolver resolver = new BlobNamespaceResolver();

        User oidcUser = new User();
        oidcUser.authProvider = AuthProviderKind.OIDC;
        oidcUser.externalId = "oidc:0123456789abcdef:alice";
        oidcUser.email = "alice@corp.example";

        AuthContext ctx = new AuthContext();
        ctx.setOidcPrincipal(new OidcPrincipal(
                "https://idp.corp.example", "alice", oidcUser.externalId,
                oidcUser.email, "Alice", null, Set.of(), false, 7L,
                Instant.now().plusSeconds(3600), false), oidcUser);

        String ns = resolver.resolve(ctx);
        assertTrue(ns.matches("users/oidc-[0-9a-f]{24}"), "unexpected OIDC blob namespace: " + ns);
        assertFalse(ns.contains("alice"));
    }

    private static OidcAuthProvider stubOidcProvider(
            boolean active,
            boolean hasAdminRole,
            Optional<OidcAuthProvider.AuthResult.Rejected> bootstrapRejection,
            OidcPrincipal acceptedPrincipal
    ) {
        return new OidcAuthProvider() {
            @Override
            public boolean isActive() { return active; }

            @Override
            public String displayName() { return "Corp SSO"; }

            @Override
            public boolean hasAdminRoleConfigured() { return hasAdminRole; }

            @Override
            public Optional<AuthResult.Rejected> checkBootstrapAccess() { return bootstrapRejection; }

            @Override
            public AuthResult authenticate(AuthRequest request) {
                if (acceptedPrincipal != null
                        && "Bearer valid-jwt".equals(request.authorizationHeader())) {
                    return new AuthResult.Authenticated(acceptedPrincipal);
                }
                return new AuthResult.Rejected(401, "Bearer realm=\"smile-serve\"", "{\"error\":\"unauthorized\"}");
            }

            @Override
            public AuthorizationStart startLogin(String origin, String returnPath) {
                return new AuthorizationStart(URI.create("https://idp.corp.example/auth"), "s", "v", "n");
            }

            @Override
            public CallbackResult completeCallback(String origin, String code, String verifier, String nonce) {
                return new CallbackResult(acceptedPrincipal, 3600);
            }

            @Override
            public String createSessionToken(long userId, OidcPrincipal principal, int maxAgeSeconds) {
                return "oidc1.stub.sig";
            }

            @Override
            public Optional<URI> buildLogoutRedirect(String origin) {
                return Optional.empty();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> singleInstance(T value) {
        return (Instance<T>) Proxy.newProxyInstance(
                Instance.class.getClassLoader(),
                new Class<?>[] { Instance.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "isResolvable" -> value != null;
                    case "isUnsatisfied" -> value == null;
                    case "isAmbiguous" -> false;
                    case "get" -> value;
                    case "iterator" -> value == null
                            ? java.util.Collections.<T>emptyIterator()
                            : java.util.List.of(value).iterator();
                    default -> null;
                });
    }

    private static ContainerRequestContext mockRequest(
            String method,
            String path,
            Map<String, String> headers,
            Map<String, Cookie> cookies,
            AtomicReference<Response> abortedRef
    ) {
        UriInfo uriInfo = (UriInfo) Proxy.newProxyInstance(
                UriInfo.class.getClassLoader(),
                new Class<?>[] { UriInfo.class },
                (proxy, m, args) -> switch (m.getName()) {
                    case "getPath" -> path;
                    case "getRequestUri" -> URI.create("http://localhost:8080/api/v1" + path);
                    default -> null;
                });
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class<?>[] { ContainerRequestContext.class },
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getUriInfo" -> uriInfo;
                    case "getHeaderString" -> headers.get(args[0]);
                    case "getCookies" -> cookies;
                    case "abortWith" -> {
                        abortedRef.set((Response) args[0]);
                        yield null;
                    }
                    default -> null;
                });
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
