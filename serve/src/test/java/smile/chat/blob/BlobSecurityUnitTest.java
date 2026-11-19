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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import smile.auth.AuthContext;
import smile.auth.AuthProviderKind;
import smile.auth.User;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BlobNamespaceResolver}, {@link BlobKeys}, and {@link MediaTypeValidator}.
 */
public class BlobSecurityUnitTest {

    private static final Set<String> DEFAULT_ALLOWED = MediaTypeValidator.parseAllowlist(
            "image/png,image/jpeg,image/webp,image/gif,video/mp4,video/webm,video/quicktime,"
                    + "audio/mpeg,audio/wav,audio/ogg,audio/mp4,audio/flac,audio/webm,"
                    + "application/pdf,text/plain,text/markdown,text/csv,application/json");

    @Test
    public void testOAuthUsersReceiveOpaqueIsolatedNamespaces() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-19T15:00:00Z"), ZoneOffset.UTC);
        BlobNamespaceResolver resolver = new BlobNamespaceResolver(fixedClock);

        User google = new User();
        google.authProvider = AuthProviderKind.GOOGLE;
        google.externalId = "108234567890123456789";
        google.email = "alice@example.com";

        AuthContext googleCtx = new AuthContext();
        googleCtx.setUser(google, false);

        String googleNs = resolver.resolve(googleCtx);
        assertTrue(googleNs.matches("users/google-[0-9a-f]{24}"), "unexpected namespace: " + googleNs);
        assertFalse(googleNs.contains("alice"), "email/PII must never appear in storage namespace");

        // Changing email must NOT change the namespace (keyed on immutable externalId)
        google.email = "alice.new@example.com";
        assertEquals(googleNs, resolver.resolve(googleCtx));

        User ms = new User();
        ms.authProvider = AuthProviderKind.MICROSOFT;
        ms.externalId = "ms:99887766-5544-3322-1100-aabbccddeeff";
        ms.email = "bob@contoso.com";

        AuthContext msCtx = new AuthContext();
        msCtx.setUser(ms, false);

        String msNs = resolver.resolve(msCtx);
        assertTrue(msNs.matches("users/microsoft-[0-9a-f]{24}"), "unexpected namespace: " + msNs);
        assertNotEquals(googleNs, msNs);
    }

    @Test
    public void testGuestAndLocalMeUseUtcDateGroupedSharedSpace() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-19T23:59:59Z"), ZoneOffset.UTC);
        BlobNamespaceResolver resolver = new BlobNamespaceResolver(fixedClock);

        // Unauthenticated guest
        AuthContext guestCtx = new AuthContext();
        assertEquals("shared/2026/11/19", resolver.resolve(guestCtx));
        assertEquals("shared/2026/11/19", resolver.resolve(null));

        // Localhost auto-login 'me' (both initial auto-login and cookie-returned LOCAL session)
        User localMe = new User();
        localMe.authProvider = AuthProviderKind.LOCAL;
        localMe.externalId = "me";

        AuthContext localAutoCtx = new AuthContext();
        localAutoCtx.setUser(localMe, true);
        assertEquals("shared/2026/11/19", resolver.resolve(localAutoCtx));

        AuthContext localCookieCtx = new AuthContext();
        localCookieCtx.setUser(localMe, false);
        assertEquals("shared/2026/11/19", resolver.resolve(localCookieCtx));
    }

    @Test
    public void testBlobKeyAndPrefixValidation() {
        assertEquals("users/google-0123456789abcdef01234567/42/pending/uuid",
                BlobKeys.of("users/google-0123456789abcdef01234567", 42L, null, "uuid"));
        assertEquals("shared/2026/11/19/7/",
                BlobKeys.conversationPrefix("shared/2026/11/19", 7L));

        assertThrows(IllegalArgumentException.class, () -> BlobKeys.validateKey(""));
        assertThrows(IllegalArgumentException.class, () -> BlobKeys.validateKey("/leading/slash"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeys.validateKey("a/../b"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeys.validateKey("a/b\\c"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeys.validatePrefix("users/"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeys.validatePrefix("shared/"));
        assertThrows(IllegalArgumentException.class, () -> BlobKeys.validatePrefix("a//b/"));
    }

    @Test
    public void testMediaTypeValidatorBlocksXssAndSpoofedMagicBytes() {
        byte[] validPng = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        byte[] validJpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] validPdf = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        byte[] validText = "hello, world\n".getBytes(StandardCharsets.UTF_8);

        assertEquals("image/png", MediaTypeValidator.validate("image/png", validPng, DEFAULT_ALLOWED));
        assertEquals("image/jpeg", MediaTypeValidator.validate("IMAGE/JPEG; charset=binary", validJpeg, DEFAULT_ALLOWED));
        assertEquals("application/pdf", MediaTypeValidator.validate("application/pdf", validPdf, DEFAULT_ALLOWED));
        assertEquals("text/plain", MediaTypeValidator.validate("text/plain; charset=utf-8", validText, DEFAULT_ALLOWED));

        // Disallowed active formats
        assertThrows(IllegalArgumentException.class, () ->
                MediaTypeValidator.validate("text/html", "<script>alert(1)</script>".getBytes(), DEFAULT_ALLOWED));
        assertThrows(IllegalArgumentException.class, () ->
                MediaTypeValidator.validate("image/svg+xml", "<svg onload=alert(1)/>".getBytes(), DEFAULT_ALLOWED));
        assertThrows(IllegalArgumentException.class, () ->
                MediaTypeValidator.validate("application/javascript", "alert(1)".getBytes(), DEFAULT_ALLOWED));
        assertThrows(IllegalArgumentException.class, () ->
                MediaTypeValidator.validate("application/octet-stream", new byte[] {1, 2, 3}, DEFAULT_ALLOWED));

        // Spoofed magic bytes
        assertThrows(IllegalArgumentException.class, () ->
                MediaTypeValidator.validate("image/png", "<html>not-a-png</html>".getBytes(), DEFAULT_ALLOWED));
        assertThrows(IllegalArgumentException.class, () ->
                MediaTypeValidator.validate("application/pdf", "not-a-pdf".getBytes(), DEFAULT_ALLOWED));
        assertThrows(IllegalArgumentException.class, () ->
                MediaTypeValidator.validate("text/plain", new byte[] {0x41, 0x00, 0x42}, DEFAULT_ALLOWED));

        // Safe inline vs forced attachment
        assertTrue(MediaTypeValidator.isSafeInline("image/png"));
        assertTrue(MediaTypeValidator.isSafeInline("video/mp4"));
        assertTrue(MediaTypeValidator.isSafeInline("audio/mpeg"));
        assertFalse(MediaTypeValidator.isSafeInline("application/pdf"));
        assertFalse(MediaTypeValidator.isSafeInline("text/plain"));
        assertFalse(MediaTypeValidator.isSafeInline("text/html"));
        assertFalse(MediaTypeValidator.isSafeInline("image/svg+xml"));

        // Filename sanitization
        assertEquals("report.pdf", MediaTypeValidator.sanitizeFilename("../../etc/report.pdf", "fallback"));
        assertEquals("evil.png", MediaTypeValidator.sanitizeFilename("C:\\temp\\evil\";\r\n.png", "fallback")
                .replace(";", "").replace("\"", ""));
        assertEquals("fallback", MediaTypeValidator.sanitizeFilename("...", "fallback"));
    }
}
