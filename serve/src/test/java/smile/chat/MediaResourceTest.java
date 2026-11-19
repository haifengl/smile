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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import smile.auth.AuthConfig;
import smile.auth.SessionToken;
import smile.auth.User;
import smile.auth.UserService;
import smile.chat.blob.BlobStore;
import smile.chat.blob.ConfigurableBlobStore;
import smile.chat.blob.LocalBlobStore;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for hardened media upload, retrieval, namespace isolation,
 * IDOR protection, and XSS/MIME validation.
 *
 * @author Haifeng Li
 */
@QuarkusTest
public class MediaResourceTest {

    private static final byte[] VALID_PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    @Inject
    BlobStore blobStore;

    @Inject
    MediaService mediaService;

    @Inject
    UserService userService;

    @Inject
    AuthConfig authConfig;

    @Test
    public void testGivenLocalMeConversationWhenUploadAndGetThenStoredInSharedDateSpaceWithSecurityHeaders() throws Exception {
        // Localhost request without OAuth cookie uses local 'me' (or guest) -> shared/YYYY/MM/DD/
        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("smile-media-", ".png");
        Files.write(tmp, VALID_PNG);
        try {
            String contentId = given()
                    .multiPart("file", tmp.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(200)
                    .body("content_id", notNullValue())
                    .body("url", startsWith("/api/v1/media/"))
                    .body("mime_type", equalTo("image/png"))
                    .body("size_bytes", equalTo(VALID_PNG.length))
                    .extract().path("content_id");

            // Verify storage key lives in shared/YYYY/MM/DD/...
            ConversationContent meta = ConversationContent.findById(contentId);
            assertNotNull(meta);
            String expectedPrefix = "shared/" + DateTimeFormatter.ofPattern("yyyy/MM/dd")
                    .withZone(ZoneOffset.UTC).format(Instant.now()) + "/";
            assertTrue(meta.storageKey.startsWith(expectedPrefix),
                    "Expected shared/YYYY/MM/DD/ prefix, got: " + meta.storageKey);

            // GET returns bytes + all P0 defensive headers + inline for safe raster image
            byte[] fetched = given()
                    .when().get("/api/v1/media/" + contentId)
                    .then()
                    .statusCode(200)
                    .header("Content-Type", startsWith("image/png"))
                    .header("Content-Disposition", startsWith("inline;"))
                    .header("X-Content-Type-Options", equalTo("nosniff"))
                    .header("Content-Security-Policy", equalTo("default-src 'none'; sandbox"))
                    .header("Referrer-Policy", equalTo("no-referrer"))
                    .header("Cross-Origin-Resource-Policy", equalTo("same-origin"))
                    .header("Cache-Control", equalTo("private, no-store"))
                    .extract().asByteArray();
            assertArrayEquals(VALID_PNG, fetched);

            // ?download=true forces attachment
            given()
                    .queryParam("download", true)
                    .when().get("/api/v1/media/" + contentId)
                    .then()
                    .statusCode(200)
                    .header("Content-Disposition", startsWith("attachment;"));

            if (blobStore instanceof ConfigurableBlobStore configurable
                    && configurable.delegate() instanceof LocalBlobStore local) {
                assertTrue(Files.exists(local.root().resolve(meta.storageKey)));
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testGivenOAuthUsersWhenUploadThenIsolatedPerUserAndIdorBlocked() throws Exception {
        User alice = userService.upsertGoogleUser(
                "google-sub-alice-media-test", "alice@example.com", "Alice", null);
        User bob = userService.upsertMicrosoftUser(
                "ms:bob-media-test-oid", "bob@contoso.com", "Bob", null);

        String aliceCookie = SessionToken.create(alice.id, authConfig.sessionSecret(), 3600);
        String bobCookie = SessionToken.create(bob.id, authConfig.sessionSecret(), 3600);

        String aliceConvId = given()
                .cookie(SessionToken.COOKIE_NAME, aliceCookie)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        String bobConvId = given()
                .cookie(SessionToken.COOKIE_NAME, bobCookie)
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("smile-oauth-", ".png");
        Files.write(tmp, VALID_PNG);
        try {
            String aliceContentId = given()
                    .cookie(SessionToken.COOKIE_NAME, aliceCookie)
                    .multiPart("file", tmp.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + aliceConvId + "/content")
                    .then()
                    .statusCode(200)
                    .extract().path("content_id");

            String bobContentId = given()
                    .cookie(SessionToken.COOKIE_NAME, bobCookie)
                    .multiPart("file", tmp.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + bobConvId + "/content")
                    .then()
                    .statusCode(200)
                    .extract().path("content_id");

            ConversationContent aliceMeta = ConversationContent.findById(aliceContentId);
            ConversationContent bobMeta = ConversationContent.findById(bobContentId);
            assertTrue(aliceMeta.storageKey.matches("users/google-[0-9a-f]{24}/.+"),
                    "Expected opaque Google user prefix, got: " + aliceMeta.storageKey);
            assertTrue(bobMeta.storageKey.matches("users/microsoft-[0-9a-f]{24}/.+"),
                    "Expected opaque Microsoft user prefix, got: " + bobMeta.storageKey);
            assertEquals(alice.id, aliceMeta.ownerUserId);
            assertEquals(bob.id, bobMeta.ownerUserId);

            // Alice can fetch her own media
            given()
                    .cookie(SessionToken.COOKIE_NAME, aliceCookie)
                    .when().get("/api/v1/media/" + aliceContentId)
                    .then()
                    .statusCode(200);

            // IDOR: Bob MUST NOT be able to fetch Alice's media (404, no existence oracle)
            given()
                    .cookie(SessionToken.COOKIE_NAME, bobCookie)
                    .when().get("/api/v1/media/" + aliceContentId)
                    .then()
                    .statusCode(404);

            // IDOR: local 'me' / unauthenticated caller MUST NOT fetch Alice's media
            given()
                    .when().get("/api/v1/media/" + aliceContentId)
                    .then()
                    .statusCode(404);

            // VLM prompt resolution: Alice succeeds, Bob is rejected with NotFoundException
            String aliceUrl = "/api/v1/media/" + aliceContentId;
            Message[] msgs = new Message[] {
                    new Message(Role.user, new TextPart("describe"), new ImageUrlPart(aliceUrl))
            };
            Message[] resolved = mediaService.resolveInternalMedia(msgs, alice.id, "127.0.0.1");
            assertTrue(resolved[0].parts().get(1) instanceof ImageUrlPart img
                    && img.url().startsWith("data:image/png;base64,"));

            assertThrows(NotFoundException.class,
                    () -> mediaService.resolveInternalMedia(msgs, bob.id, "127.0.0.1"));
            assertThrows(NotFoundException.class,
                    () -> mediaService.resolveInternalMedia(msgs, null, "127.0.0.1"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testGivenDisallowedOrSpoofedUploadsWhenPostedThenRejectedWith400() throws Exception {
        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path fakePng = Files.createTempFile("smile-fake-", ".png");
        Path html = Files.createTempFile("smile-xss-", ".html");
        Path svg = Files.createTempFile("smile-xss-", ".svg");
        Files.writeString(fakePng, "not-actually-png", StandardCharsets.UTF_8);
        Files.writeString(html, "<html><script>alert(1)</script></html>", StandardCharsets.UTF_8);
        Files.writeString(svg, "<svg onload='alert(1)'/>", StandardCharsets.UTF_8);
        try {
            given()
                    .multiPart("file", fakePng.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then().statusCode(400);

            given()
                    .multiPart("file", html.toFile(), "text/html")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then().statusCode(400);

            given()
                    .multiPart("file", svg.toFile(), "image/svg+xml")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then().statusCode(400);
        } finally {
            Files.deleteIfExists(fakePng);
            Files.deleteIfExists(html);
            Files.deleteIfExists(svg);
        }
    }

    @Test
    public void testGivenTextUploadWhenFetchedThenForcesAttachmentAndDeletesCleanly() throws Exception {
        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("smile-media-", ".txt");
        Files.writeString(tmp, "bye");
        String contentId;
        try {
            contentId = given()
                    .multiPart("file", tmp.toFile(), "text/plain")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(200)
                    .extract().path("content_id");
        } finally {
            Files.deleteIfExists(tmp);
        }

        // Non-inline MIME type must force attachment even without ?download=true
        given().when().get("/api/v1/media/" + contentId)
                .then()
                .statusCode(200)
                .header("Content-Disposition", startsWith("attachment;"))
                .header("X-Content-Type-Options", equalTo("nosniff"));

        given().when().delete("/api/v1/conversations/" + convId)
                .then().statusCode(200);

        given().when().get("/api/v1/media/" + contentId)
                .then().statusCode(404);
    }

    @Test
    public void testGivenUnknownContentIdWhenGetThenNotFound() {
        given().when().get("/api/v1/media/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }
}
