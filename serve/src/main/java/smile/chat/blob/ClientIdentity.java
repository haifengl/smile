/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat.blob;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import jakarta.ws.rs.core.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

/**
 * Derives a stable blob-namespace {@code user_id} from the client IP when no
 * authenticated user is available.
 *
 * <p>Raw IPs never appear in storage keys: the value is a truncated SHA-256
 * hex digest. This is <em>not</em> authorization — access control relies on
 * opaque {@code content_id} values and API checks.
 *
 * @author Haifeng Li
 */
public final class ClientIdentity {
    private ClientIdentity() {}

    /**
     * Resolves client IP (ignoring {@code X-Forwarded-For} unless trusted proxy
     * mode is enabled) and returns a 16-character hex prefix of
     * {@code SHA-256(normalized_ip)}.
     *
     * @param routingContext Vert.x routing context.
     * @param headers        JAX-RS headers.
     * @return hashed user id for blob keys.
     */
    public static String fromRequest(RoutingContext routingContext, HttpHeaders headers) {
        return fromRequest(routingContext, headers, false);
    }

    /**
     * Resolves client IP (honouring {@code X-Forwarded-For} only when
     * {@code trustForwardedHeaders} is {@code true}) and returns a 16-character
     * hex prefix of {@code SHA-256(normalized_ip)}.
     *
     * @param routingContext        Vert.x routing context.
     * @param headers               JAX-RS headers.
     * @param trustForwardedHeaders whether to trust {@code X-Forwarded-For} from a reverse proxy.
     * @return hashed client identifier.
     */
    public static String fromRequest(RoutingContext routingContext,
                                     HttpHeaders headers,
                                     boolean trustForwardedHeaders) {
        return hashIp(normalizeIp(resolveClientIp(routingContext, headers, trustForwardedHeaders)));
    }

    /**
     * Resolves client IP from the TCP peer address without trusting
     * {@code X-Forwarded-For}.
     *
     * @param routingContext Vert.x routing context.
     * @param headers        JAX-RS headers.
     * @return client IP string.
     */
    public static String resolveClientIp(RoutingContext routingContext, HttpHeaders headers) {
        return resolveClientIp(routingContext, headers, false);
    }

    /**
     * Resolves client IP, honouring {@code X-Forwarded-For} only when
     * {@code trustForwardedHeaders} is {@code true} (i.e. behind a trusted reverse proxy).
     *
     * @param routingContext        Vert.x routing context.
     * @param headers               JAX-RS headers.
     * @param trustForwardedHeaders whether to trust {@code X-Forwarded-For}.
     * @return client IP string.
     */
    public static String resolveClientIp(RoutingContext routingContext,
                                         HttpHeaders headers,
                                         boolean trustForwardedHeaders) {
        String clientIP = routingContext.request().remoteAddress().hostAddress();
        if (trustForwardedHeaders && headers != null) {
            String forwardedFor = headers.getHeaderString("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                clientIP = forwardedFor.split(",")[0].trim();
            }
        }
        return clientIP;
    }

    /**
     * Normalizes IPv4-mapped IPv6 ({@code ::ffff:a.b.c.d} → {@code a.b.c.d}).
     *
     * @param ip raw address, or {@code null}.
     * @return normalized address, or {@code "unknown"}.
     */
    public static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        String trimmed = ip.trim();
        if (trimmed.regionMatches(true, 0, "::ffff:", 0, 7)) {
            return trimmed.substring(7);
        }
        return trimmed;
    }

    /**
     * @param ip normalized IP string.
     * @return first 16 hex characters of SHA-256.
     */
    public static String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
