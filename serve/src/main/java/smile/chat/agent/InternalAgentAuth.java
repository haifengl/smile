/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.chat.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import io.vertx.ext.web.RoutingContext;
import smile.serve.LocalhostGuard;

/**
 * Per-process cryptographic loopback authenticator for internal {@code kirin}
 * LLM rounds targeting {@code POST /v1/chat/completions}.
 *
 * <p>Lazily generates a 256-bit {@link SecureRandom} secret once per JVM process
 * (held exclusively in memory, never logged or serialized) and validates incoming
 * internal hops using constant-time comparison strictly on loopback sockets.
 */
public final class InternalAgentAuth {

    /** HTTP header carrying the per-process internal loopback secret token. */
    public static final String HEADER_TOKEN = "X-Smile-Internal-Agent-Token";

    private record Secret(String token, byte[] bytes) { }

    private static final AtomicReference<Secret> SECRET = new AtomicReference<>();

    private InternalAgentAuth() {
    }

    private static Secret currentSecret() {
        Secret existing = SECRET.get();
        if (existing != null) {
            return existing;
        }
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Secret created = new Secret(encoded, encoded.getBytes(StandardCharsets.UTF_8));
        if (SECRET.compareAndSet(null, created)) {
            return created;
        }
        return SECRET.get();
    }

    /**
     * Returns the per-process secret token to attach on internal loopback calls.
     *
     * @return Base64URL-encoded 256-bit secret.
     */
    public static String token() {
        return currentSecret().token();
    }

    /**
     * Validates that the incoming request originates from a direct loopback
     * socket and presents a constant-time match for the per-process secret.
     *
     * @param routingContext Vert.x routing context for direct peer IP verification.
     * @param headerValue    candidate token header value.
     * @return {@code true} only if loopback and secret matches in constant time.
     */
    public static boolean isValidInternalHop(RoutingContext routingContext, String headerValue) {
        if (headerValue == null || headerValue.isEmpty()) {
            return false;
        }
        if (!LocalhostGuard.isLocalhost(routingContext)) {
            return false;
        }
        byte[] candidate = headerValue.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(candidate, currentSecret().bytes());
    }

    /**
     * Validates an internal loopback hop checking both {@link #HEADER_TOKEN}
     * and the legacy {@code X-Smile-Internal-Agent} header for the per-process
     * 256-bit secret strictly on loopback. Static values such as {@code "true"}
     * are always rejected.
     *
     * @param routingContext Vert.x routing context.
     * @param tokenHeader    value of {@link #HEADER_TOKEN}, or {@code null}.
     * @param legacyHeader   value of {@code X-Smile-Internal-Agent}, or {@code null}.
     * @return {@code true} only when loopback and one of the headers matches the secret.
     */
    public static boolean isValidInternalHop(
            RoutingContext routingContext,
            String tokenHeader,
            String legacyHeader
    ) {
        return isValidInternalHop(routingContext, tokenHeader)
                || isValidInternalHop(routingContext, legacyHeader);
    }

    /**
     * Validates that a request is an authorized internal loopback hop scoped
     * strictly to {@code POST /chat/completions} or {@code /v1/chat/completions}.
     *
     * @param routingContext Vert.x routing context.
     * @param method         HTTP method.
     * @param path           request path.
     * @param tokenHeader    value of {@link #HEADER_TOKEN}, or {@code null}.
     * @param legacyHeader   value of {@code X-Smile-Internal-Agent}, or {@code null}.
     * @return {@code true} only for {@code POST /chat/completions} from loopback with valid secret.
     */
    public static boolean isInternalChatCompletionHop(
            RoutingContext routingContext,
            String method,
            String path,
            String tokenHeader,
            String legacyHeader
    ) {
        if (!"POST".equalsIgnoreCase(method) || path == null) {
            return false;
        }
        String normalized = normalizePath(path);
        if (!"/chat/completions".equals(normalized)
                && !"/v1/chat/completions".equals(normalized)) {
            return false;
        }
        return isValidInternalHop(routingContext, tokenHeader, legacyHeader);
    }

    private static String normalizePath(String path) {
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
