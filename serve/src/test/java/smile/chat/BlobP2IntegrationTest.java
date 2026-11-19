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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.auth.AuthConfig;
import smile.auth.SessionToken;
import smile.auth.User;
import smile.auth.UserService;
import smile.chat.blob.BlobAuditEvent;
import smile.chat.blob.BlobAuditListener;
import smile.chat.blob.BlobContentScanner;
import smile.chat.blob.BlobStore;
import smile.chat.blob.ConfigurableBlobStore;
import smile.chat.blob.ImageSafetyProcessorTest;
import smile.chat.blob.LocalBlobStore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for P2 multimedia attachment security controls:
 * <ul>
 *   <li>Read-time SHA-256 integrity verification (tampering fails closed)</li>
 *   <li>Image decompression-bomb guard and lossless EXIF/GPS metadata stripping</li>
 *   <li>Pluggable {@link BlobContentScanner} SPI (malware reject 422, scanner error fail-closed 503)</li>
 *   <li>Metadata-only {@link BlobAuditListener} event emission across upload/download/IDOR/delete/integrity failure</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(BlobP2IntegrationTest.P2TestProfile.class)
public class BlobP2IntegrationTest {

    public static class P2TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "smile.blob.verify-checksum-on-read", "true",
                    "smile.blob.max-image-pixels", "1000000",
                    "smile.blob.max-image-dimension", "2000",
                    "smile.blob.strip-image-metadata", "true",
                    "smile.blob.sweep-interval-minutes", "0"
            );
        }
    }

    @ApplicationScoped
    public static class TestEicarScanner implements BlobContentScanner {
        static final AtomicBoolean FAIL_UNAVAILABLE = new AtomicBoolean(false);

        @Override
        public ScanResult scan(ScanContext context) throws IOException {
            if (FAIL_UNAVAILABLE.get()) {
                throw new IOException("Simulated ClamAV daemon timeout");
            }
            String text = new String(context.data(), StandardCharsets.ISO_8859_1);
            if (text.contains("EICAR-TEST-SIGNATURE")) {
                return ScanResult.rejected("MALWARE", "Matched EICAR test signature");
            }
            return ScanResult.ok();
        }
    }

    @ApplicationScoped
    public static class CapturingBlobAuditListener implements BlobAuditListener {
        static final List<BlobAuditEvent> EVENTS = new CopyOnWriteArrayList<>();

        @Override
        public void onBlobEvent(BlobAuditEvent event) {
            EVENTS.add(event);
        }
    }

    @Inject
    BlobStore blobStore;

    @Inject
    UserService userService;

    @Inject
    AuthConfig authConfig;

    @BeforeEach
    public void resetTestDoubles() {
        TestEicarScanner.FAIL_UNAVAILABLE.set(false);
        CapturingBlobAuditListener.EVENTS.clear();
    }

    @Test
    public void testImageMetadataStrippedAndDecompressionBombRejected() throws Exception {
        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then().statusCode(200)
                .extract().path("id");

        // 1. PNG with GPS tEXt and eXIf metadata is stripped before storage and passes SHA-256 verification on read
        byte[] dirtyPng = ImageSafetyProcessorTest.buildPng(32, 32, true, true);
        Path tmpDirty = Files.createTempFile("p2-dirty-", ".png");
        Files.write(tmpDirty, dirtyPng);
        try {
            String contentId = given()
                    .multiPart("file", tmpDirty.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then().statusCode(200)
                    .extract().path("content_id");

            byte[] fetched = given()
                    .when().get("/api/v1/media/" + contentId)
                    .then().statusCode(200)
                    .extract().asByteArray();
            String fetchedStr = new String(fetched, StandardCharsets.ISO_8859_1);
            assertFalse(fetchedStr.contains("GPSLatitude"), "GPS text chunk must be stripped");
            assertFalse(fetchedStr.contains("eXIf"), "eXIf chunk must be stripped");
            assertTrue(fetchedStr.contains("sRGB"), "safe color chunk preserved");

            assertTrue(CapturingBlobAuditListener.EVENTS.stream()
                    .anyMatch(e -> e.action() == BlobAuditEvent.Action.UPLOAD && contentId.equals(e.contentId())));
            assertTrue(CapturingBlobAuditListener.EVENTS.stream()
                    .anyMatch(e -> e.action() == BlobAuditEvent.Action.DOWNLOAD && contentId.equals(e.contentId())));
        } finally {
            Files.deleteIfExists(tmpDirty);
        }

        // 2. Decompression-bomb PNG (3000x3000 > max-image-dimension 2000) is rejected with 400 + SCAN_REJECTED audit
        byte[] bombPng = ImageSafetyProcessorTest.buildPng(3000, 3000, false, false);
        Path tmpBomb = Files.createTempFile("p2-bomb-", ".png");
        Files.write(tmpBomb, bombPng);
        try {
            given()
                    .multiPart("file", tmpBomb.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then().statusCode(400);

            assertTrue(CapturingBlobAuditListener.EVENTS.stream()
                    .anyMatch(e -> e.action() == BlobAuditEvent.Action.SCAN_REJECTED && !e.success()));
        } finally {
            Files.deleteIfExists(tmpBomb);
        }
    }

    @Test
    public void testContentScannerRejectsMalwareAndFailsClosedWhenUnavailable() throws Exception {
        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then().statusCode(200)
                .extract().path("id");

        Path tmpMalware = Files.createTempFile("p2-eicar-", ".txt");
        Files.writeString(tmpMalware, "benign prefix EICAR-TEST-SIGNATURE suffix");
        try {
            given()
                    .multiPart("file", tmpMalware.toFile(), "text/plain")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(422)
                    .body("verdict", equalTo("MALWARE"));

            assertTrue(CapturingBlobAuditListener.EVENTS.stream()
                    .anyMatch(e -> e.action() == BlobAuditEvent.Action.SCAN_REJECTED
                            && !e.success()
                            && e.reason() != null && e.reason().contains("MALWARE")));
        } finally {
            Files.deleteIfExists(tmpMalware);
        }

        // Scanner I/O failure must fail closed with HTTP 503
        TestEicarScanner.FAIL_UNAVAILABLE.set(true);
        Path tmpClean = Files.createTempFile("p2-clean-", ".txt");
        Files.writeString(tmpClean, "totally clean note");
        try {
            given()
                    .multiPart("file", tmpClean.toFile(), "text/plain")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then().statusCode(503);
        } finally {
            TestEicarScanner.FAIL_UNAVAILABLE.set(false);
            Files.deleteIfExists(tmpClean);
        }
    }

    @Test
    public void testReadTimeSha256IntegrityDetectsTamperingAndAuditCapturesIdorAndDelete() throws Exception {
        User alice = userService.upsertGoogleUser("p2-google-alice", "alice-p2@example.com", "Alice P2", null);
        User bob = userService.upsertGoogleUser("p2-google-bob", "bob-p2@example.com", "Bob P2", null);
        String aliceCookie = SessionToken.create(alice.id, authConfig.sessionSecret(), 3600);
        String bobCookie = SessionToken.create(bob.id, authConfig.sessionSecret(), 3600);

        String convId = given()
                .cookie(SessionToken.COOKIE_NAME, aliceCookie)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then().statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("p2-integrity-", ".txt");
        Files.writeString(tmp, "original trusted attachment");
        String contentId;
        try {
            contentId = given()
                    .cookie(SessionToken.COOKIE_NAME, aliceCookie)
                    .multiPart("file", tmp.toFile(), "text/plain")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then().statusCode(200)
                    .extract().path("content_id");
        } finally {
            Files.deleteIfExists(tmp);
        }

        // Bob tries to read Alice's attachment -> 404 + ACCESS_DENIED audit event
        given()
                .cookie(SessionToken.COOKIE_NAME, bobCookie)
                .when().get("/api/v1/media/" + contentId)
                .then().statusCode(404);
        assertTrue(CapturingBlobAuditListener.EVENTS.stream()
                .anyMatch(e -> e.action() == BlobAuditEvent.Action.ACCESS_DENIED
                        && contentId.equals(e.contentId())
                        && !e.success()));

        // Tamper the underlying file directly on disk to simulate out-of-band storage corruption
        ConversationContent row = ConversationContent.findById(contentId);
        assertNotNull(row);
        assertInstanceOf(ConfigurableBlobStore.class, blobStore);
        LocalBlobStore local = (LocalBlobStore) ((ConfigurableBlobStore) blobStore).delegate();
        Path diskFile = local.root().resolve(row.storageKey);
        Files.writeString(diskFile, "tampered malicious attachment!");

        // Read must fail closed with HTTP 500 and emit INTEGRITY_FAILURE audit event
        given()
                .cookie(SessionToken.COOKIE_NAME, aliceCookie)
                .when().get("/api/v1/media/" + contentId)
                .then().statusCode(500);

        assertTrue(CapturingBlobAuditListener.EVENTS.stream()
                .anyMatch(e -> e.action() == BlobAuditEvent.Action.INTEGRITY_FAILURE
                        && contentId.equals(e.contentId())
                        && !e.success()));

        // Deleting conversation emits DELETE audit event
        given()
                .cookie(SessionToken.COOKIE_NAME, aliceCookie)
                .when().delete("/api/v1/conversations/" + convId)
                .then().statusCode(200);
        assertTrue(CapturingBlobAuditListener.EVENTS.stream()
                .anyMatch(e -> e.action() == BlobAuditEvent.Action.DELETE
                        && contentId.equals(e.contentId())
                        && e.success()));
    }
}
