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
package smile.chat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.auth.AuthConfig;
import smile.auth.SessionToken;
import smile.auth.User;
import smile.auth.UserService;
import smile.chat.blob.BlobKeys;
import smile.chat.blob.BlobStore;
import smile.chat.blob.ConfigurableBlobStore;
import smile.chat.blob.EncryptingBlobStore;
import smile.chat.blob.LocalBlobStore;
import smile.chat.blob.UploadRateLimiter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for P1 blob storage security hardening:
 * AES-256-GCM envelope encryption at rest, per-user and daily shared quotas,
 * guest upload rate limiting, and scheduled retention/orphan sweeping.
 */
@QuarkusTest
@TestProfile(BlobP1IntegrationTest.P1HardenedProfile.class)
public class BlobP1IntegrationTest {

    private static final byte[] MINIMAL_PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53
    };

    private static final String TEST_AES_KEY_B64 = Base64.getEncoder().encodeToString(new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
    });

    public static class P1HardenedProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "smile.blob.local.path", "build/test-blobs-p1",
                    "smile.blob.encryption.enabled", "true",
                    "smile.blob.encryption.key", TEST_AES_KEY_B64,
                    "smile.blob.max-user-bytes", "80",
                    "smile.blob.shared.max-daily-bytes", "80",
                    "smile.blob.guest-uploads-per-minute", "2",
                    "smile.blob.pending-ttl-hours", "24",
                    "smile.blob.shared-retention-days", "7",
                    "smile.blob.sweep-interval-minutes", "0"
            );
        }
    }

    @Inject
    BlobStore blobStore;

    @Inject
    UploadRateLimiter uploadRateLimiter;

    @Inject
    BlobRetentionSweeper sweeper;

    @Inject
    UserService userService;

    @Inject
    AuthConfig authConfig;

    @BeforeEach
    @Transactional
    void resetState() {
        uploadRateLimiter.reset();
        ConversationContent.deleteAll();
    }

    @Test
    public void testGivenEncryptionEnabledWhenUploadAndGetThenEncryptedOnDiskAndDecryptedOnRead() throws Exception {
        assertTrue(blobStore instanceof ConfigurableBlobStore cfg
                        && cfg.delegate() instanceof EncryptingBlobStore,
                "ConfigurableBlobStore must wrap delegate in EncryptingBlobStore when enabled");

        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("smile-enc-", ".png");
        Files.write(tmp, MINIMAL_PNG);
        try {
            String contentId = given()
                    .multiPart("file", tmp.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(200)
                    .extract().path("content_id");

            // Raw file on disk must start with SMILEBLB header, NOT PNG magic bytes
            ConversationContent row = ConversationContent.findById(contentId);
            assertNotNull(row);
            ConfigurableBlobStore cfg = (ConfigurableBlobStore) blobStore;
            EncryptingBlobStore enc = (EncryptingBlobStore) cfg.delegate();
            LocalBlobStore local = (LocalBlobStore) enc.delegate();
            byte[] rawOnDisk = Files.readAllBytes(local.root().resolve(row.storageKey));
            assertEquals("SMILEBLB", new String(Arrays.copyOf(rawOnDisk, 8), StandardCharsets.US_ASCII));
            assertEquals(MINIMAL_PNG.length + EncryptingBlobStore.ENVELOPE_OVERHEAD_BYTES, rawOnDisk.length);

            // HTTP download transparently decrypts and returns original PNG bytes
            byte[] fetched = given()
                    .when().get("/api/v1/media/" + contentId)
                    .then()
                    .statusCode(200)
                    .extract().asByteArray();
            assertArrayEquals(MINIMAL_PNG, fetched);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testGivenOAuthUserQuotaWhenExceededThenReturns413() throws Exception {
        User oauthUser = userService.upsertGoogleUser(
                "google-quota-sub-1", "quota@example.com", "Quota User", null);
        String cookie = SessionToken.create(oauthUser.id, authConfig.sessionSecret(), 3600);

        String convId = given()
                .cookie(SessionToken.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("smile-quota-", ".png");
        Files.write(tmp, MINIMAL_PNG); // 32 bytes; quota = 80 bytes -> 2 uploads fit (64 B), 3rd (96 B) fails
        try {
            for (int i = 0; i < 2; i++) {
                given()
                        .cookie(SessionToken.COOKIE_NAME, cookie)
                        .multiPart("file", tmp.toFile(), "image/png")
                        .when().post("/api/v1/conversations/" + convId + "/content")
                        .then()
                        .statusCode(200);
            }
            given()
                    .cookie(SessionToken.COOKIE_NAME, cookie)
                    .multiPart("file", tmp.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(413)
                    .body(containsString("quota"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testGivenSharedDailyQuotaWhenExceededThenReturns413() throws Exception {
        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("smile-shared-quota-", ".png");
        Files.write(tmp, MINIMAL_PNG); // 32 bytes; shared daily quota = 80 bytes
        try {
            for (int i = 0; i < 2; i++) {
                given()
                        .multiPart("file", tmp.toFile(), "image/png")
                        .when().post("/api/v1/conversations/" + convId + "/content")
                        .then()
                        .statusCode(200);
            }
            given()
                    .multiPart("file", tmp.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(413)
                    .body(containsString("shared"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testGivenGuestUploadsWhenExceedingRateLimitThenReturns429WithRetryAfter() throws Exception {
        // Remote Host header prevents localhost 'me' auto-login -> true guest session
        String convId = given()
                .header("Host", "guest-rate.example.com")
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("smile-guest-rl-", ".txt");
        Files.writeString(tmp, "hi"); // 2 bytes (well under 80 B quota)
        try {
            // Limit = 2 uploads/min
            for (int i = 0; i < 2; i++) {
                given()
                        .header("Host", "guest-rate.example.com")
                        .multiPart("file", tmp.toFile(), "text/plain")
                        .when().post("/api/v1/conversations/" + convId + "/content")
                        .then()
                        .statusCode(200);
            }
            given()
                    .header("Host", "guest-rate.example.com")
                    .multiPart("file", tmp.toFile(), "text/plain")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(429)
                    .header("Retry-After", notNullValue());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testGivenExpiredPendingAndSharedBlobsWhenSweptThenReclaimedWhileOAuthLinkedPreserved() throws Exception {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");

        String oldPendingId = UUID.randomUUID().toString();
        String oauthLinkedId = UUID.randomUUID().toString();
        String oldSharedId = UUID.randomUUID().toString();
        String freshSharedId = UUID.randomUUID().toString();

        String oauthNs = "users/google-1234567890abcdef12345678";
        String oldSharedNs = "shared/2026/11/01";
        String freshSharedNs = "shared/2026/11/18";

        String oldPendingKey = BlobKeys.of(oauthNs, 101L, null, oldPendingId);
        String oauthLinkedKey = BlobKeys.of(oauthNs, 101L, 5001L, oauthLinkedId);
        String oldSharedKey = BlobKeys.of(oldSharedNs, 102L, 5002L, oldSharedId);
        String freshSharedKey = BlobKeys.of(freshSharedNs, 103L, 5003L, freshSharedId);
        String orphanDiskKey = BlobKeys.of("shared/2026/10/15", 104L, null, UUID.randomUUID().toString());

        blobStore.put(oldPendingKey, "old-pending".getBytes(StandardCharsets.UTF_8), "text/plain");
        blobStore.put(oauthLinkedKey, "oauth-keep".getBytes(StandardCharsets.UTF_8), "text/plain");
        blobStore.put(oldSharedKey, "old-shared".getBytes(StandardCharsets.UTF_8), "text/plain");
        blobStore.put(freshSharedKey, "fresh-shared".getBytes(StandardCharsets.UTF_8), "text/plain");
        blobStore.put(orphanDiskKey, "orphan-disk".getBytes(StandardCharsets.UTF_8), "text/plain");

        seedRow(oldPendingId, 101L, null, oauthNs, 77L, oldPendingKey, 11, now.minus(25, ChronoUnit.HOURS));
        seedRow(oauthLinkedId, 101L, 5001L, oauthNs, 77L, oauthLinkedKey, 10, now.minus(10, ChronoUnit.DAYS));
        seedRow(oldSharedId, 102L, 5002L, oldSharedNs, null, oldSharedKey, 10, now.minus(10, ChronoUnit.DAYS));
        seedRow(freshSharedId, 103L, 5003L, freshSharedNs, null, freshSharedKey, 12, now.minus(1, ChronoUnit.DAYS));

        BlobRetentionSweeper.SweepResult result = sweeper.sweepNow(now);

        assertEquals(1, result.pendingDeleted());
        assertEquals(1, result.sharedDeleted());
        assertEquals(2, result.expiredSharedPrefixesCleaned(),
                "Both shared/2026/11/01 and orphaned shared/2026/10/15 must be purged");
        assertEquals(21L, result.bytesReclaimed());

        // Verify DB + BlobStore outcomes
        assertNull(ConversationContent.findById(oldPendingId));
        assertTrue(blobStore.get(oldPendingKey).isEmpty());

        assertNotNull(ConversationContent.findById(oauthLinkedId));
        assertTrue(blobStore.get(oauthLinkedKey).isPresent());

        assertNull(ConversationContent.findById(oldSharedId));
        assertTrue(blobStore.get(oldSharedKey).isEmpty());
        assertTrue(blobStore.get(orphanDiskKey).isEmpty());

        assertNotNull(ConversationContent.findById(freshSharedId));
        assertTrue(blobStore.get(freshSharedKey).isPresent());
    }

    @Transactional
    void seedRow(String id, long convId, Long msgId, String ns, Long ownerUserId,
                 String key, long size, Instant createdAt) {
        ConversationContent row = new ConversationContent();
        row.id = id;
        row.conversationId = convId;
        row.messageId = msgId;
        row.userId = ns;
        row.ownerUserId = ownerUserId;
        row.role = "user";
        row.mimeType = "text/plain";
        row.filename = "f.txt";
        row.sizeBytes = size;
        row.sha256 = "0".repeat(64);
        row.storageKey = key;
        row.persist();
        // Override @CreationTimestamp via JPQL update so retention cutoff tests are deterministic
        ConversationContent.getEntityManager()
                .createQuery("UPDATE ConversationContent c SET c.createdAt = :ts WHERE c.id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
