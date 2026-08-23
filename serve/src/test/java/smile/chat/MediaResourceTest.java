/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
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
 * Integration tests for media upload and retrieval.
 *
 * @author Haifeng Li
 */
@QuarkusTest
public class MediaResourceTest {

    @Inject
    BlobStore blobStore;

    @Inject
    MediaService mediaService;

    @Test
    public void testGivenConversationWhenUploadAndGetThenRoundTripsBytes() throws Exception {
        // Given a conversation
        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        byte[] payload = "png-bytes-not-really".getBytes(StandardCharsets.UTF_8);
        Path tmp = Files.createTempFile("smile-media-", ".png");
        Files.write(tmp, payload);
        try {
            // When uploading
            String contentId = given()
                    .multiPart("file", tmp.toFile(), "image/png")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(200)
                    .body("content_id", notNullValue())
                    .body("url", startsWith("/api/v1/media/"))
                    .body("mime_type", equalTo("image/png"))
                    .body("size_bytes", equalTo(payload.length))
                    .extract().path("content_id");

            // Then GET returns the same bytes
            byte[] fetched = given()
                    .when().get("/api/v1/media/" + contentId)
                    .then()
                    .statusCode(200)
                    .header("Content-Type", startsWith("image/png"))
                    .extract().asByteArray();
            assertArrayEquals(payload, fetched);

            given()
                    .queryParam("download", true)
                    .when().get("/api/v1/media/" + contentId)
                    .then()
                    .statusCode(200)
                    .header("Content-Disposition", containsString("attachment"));

            if (blobStore instanceof ConfigurableBlobStore configurable
                    && configurable.delegate() instanceof LocalBlobStore local) {
                assertTrue(Files.exists(local.root()));
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testGivenInternalMediaUrlWhenResolvedThenBecomesDataUrl() throws Exception {
        String convId = given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/conversations")
                .then()
                .statusCode(200)
                .extract().path("id");

        Path tmp = Files.createTempFile("smile-media-", ".bin");
        Files.write(tmp, new byte[] {1, 2, 3, 4});
        try {
            String url = given()
                    .multiPart("file", tmp.toFile(), "application/octet-stream")
                    .when().post("/api/v1/conversations/" + convId + "/content")
                    .then()
                    .statusCode(200)
                    .extract().path("url");

            Message[] resolved = mediaService.resolveInternalMedia(new Message[] {
                    new Message(Role.user, new TextPart("see"), new ImageUrlPart(url))
            });
            assertTrue(resolved[0].parts().get(1) instanceof ImageUrlPart image
                    && image.url().startsWith("data:application/octet-stream;base64,"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void testGivenDeleteConversationWhenHasMediaThenBlobsRemoved() throws Exception {
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
