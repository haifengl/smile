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

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * OpenAI-compatible conversation CRUD tests.
 *
 * @author Haifeng Li
 */
@QuarkusTest
public class ConversationResourceTest {

    @Test
    public void testGivenEmptyBodyWhenCreateThenReturnsConversationObject() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/v1/conversations")
            .then()
                .statusCode(200)
                .body("object", equalTo("conversation"))
                .body("id", startsWith("conv_"))
                .body("created_at", notNullValue())
                .body("metadata", anEmptyMap());
    }

    @Test
    public void testGivenMetadataAndItemsWhenCreateThenPersistsAndRetrieveWorks() {
        String id = given()
            .contentType(ContentType.JSON)
            .body("""
                    {
                      "metadata": {"topic": "demo"},
                      "items": [
                        {"type": "message", "role": "user", "content": "Hello!"}
                      ]
                    }
                    """)
            .when().post("/api/v1/conversations")
            .then()
                .statusCode(200)
                .body("object", equalTo("conversation"))
                .body("metadata.topic", equalTo("demo"))
                .extract().path("id");

        given()
            .when().get("/api/v1/conversations/" + id)
            .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("object", equalTo("conversation"))
                .body("metadata.topic", equalTo("demo"));

        given()
            .when().get("/api/v1/conversations/" + id + "/items")
            .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].role", equalTo("user"))
                .body("[0].content", equalTo("Hello!"));
    }

    @Test
    public void testGivenExistingConversationWhenUpdateThenMetadataReplaced() {
        String id = given()
            .contentType(ContentType.JSON)
            .body("{\"metadata\":{\"topic\":\"old\"}}")
            .when().post("/api/v1/conversations")
            .then()
                .statusCode(200)
                .extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"metadata\":{\"topic\":\"project-x\"}}")
            .when().post("/api/v1/conversations/" + id)
            .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("metadata.topic", equalTo("project-x"))
                .body("metadata", aMapWithSize(1));
    }

    @Test
    public void testGivenExistingConversationWhenDeleteThenReturnsDeletedResource() {
        String id = given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/v1/conversations")
            .then()
                .statusCode(200)
                .extract().path("id");

        given()
            .when().delete("/api/v1/conversations/" + id)
            .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("deleted", equalTo(true))
                .body("object", equalTo("conversation.deleted"));

        given()
            .when().get("/api/v1/conversations/" + id)
            .then()
                .statusCode(404);
    }

    @Test
    public void testGivenTooManyMetadataPairsWhenCreateThenBadRequest() {
        var metadata = new StringBuilder("{\"metadata\":{");
        for (int i = 0; i < 17; i++) {
            if (i > 0) metadata.append(',');
            metadata.append("\"k").append(i).append("\":\"v\"");
        }
        metadata.append("}}");

        given()
            .contentType(ContentType.JSON)
            .body(metadata.toString())
            .when().post("/api/v1/conversations")
            .then()
                .statusCode(400);
    }

    @Test
    public void testGivenUnknownIdWhenGetThenNotFound() {
        given()
            .when().get("/api/v1/conversations/conv_999999999")
            .then()
                .statusCode(404);
    }
}
