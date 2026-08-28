/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed session cookie value: {@code base64(userId:expEpochSec).base64(hmac)}.
 */
public final class SessionToken {
    public static final String COOKIE_NAME = "smile_session";

    private SessionToken() {}

    /**
     * Creates a signed token for the given user.
     *
     * @param userId     database user id.
     * @param secret     HMAC secret.
     * @param maxAgeSecs cookie lifetime.
     * @return signed token string.
     */
    public static String create(long userId, String secret, int maxAgeSecs) {
        long exp = Instant.now().getEpochSecond() + maxAgeSecs;
        String payload = userId + ":" + exp;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sig = sign(encoded, secret);
        return encoded + "." + sig;
    }

    /**
     * Verifies and parses a session token.
     *
     * @param token  cookie value.
     * @param secret HMAC secret.
     * @return user id, or {@code null} when invalid/expired.
     */
    public static Long verify(String token, String secret) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return null;
        }
        String encoded = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        if (!MessageDigest.isEqual(
                sign(encoded, secret).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int colon = payload.indexOf(':');
            if (colon <= 0) {
                return null;
            }
            long userId = Long.parseLong(payload.substring(0, colon));
            long exp = Long.parseLong(payload.substring(colon + 1));
            if (Instant.now().getEpochSecond() > exp) {
                return null;
            }
            return userId;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String sign(String encoded, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(encoded.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }
}
