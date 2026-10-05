/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve.model;

import java.util.Map;
import java.util.Properties;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OpenAI-compatible {@code GET /models}.
 */
@QuarkusTest
public class ModelsResourceTest {

    @Test
    public void testGivenLoadedSmileAndOnnxWhenListThenIncludesThem() {
        // %test profile: chat unavailable; iris .sml (+ optional onnx) present
        given()
            .when().get("/api/v1/models")
            .then()
                .statusCode(200)
                .body("object", equalTo("list"))
                .body("data.id", hasItem("iris_random_forest-1"))
                .body("data.find { it.id == 'iris_random_forest-1' }.object", equalTo("model"))
                .body("data.find { it.id == 'iris_random_forest-1' }.owned_by", equalTo("Unknown"))
                .body("data.find { it.id == 'iris_random_forest-1' }.kind", equalTo("random-forest"));
    }

    @Test
    public void testGivenLoadedSmileWhenRetrieveThenReturnsModelObject() {
        given()
            .when().get("/api/v1/models/iris_random_forest-1")
            .then()
                .statusCode(200)
                .body("id", equalTo("iris_random_forest-1"))
                .body("object", equalTo("model"))
                .body("owned_by", equalTo("Unknown"))
                .body("kind", equalTo("random-forest"))
                .body("shutdown_date", nullValue())
                .body("smile.schema.petallength.type", equalTo("float"))
                .body("smile.train.accuracy", notNullValue())
                .body("smile.tags", notNullValue())
                .body("onnx", nullValue())
                .body("llm", nullValue());
    }

    @Test
    public void testGivenListWhenSmileModelThenOmitsDetailBlocks() {
        given()
            .when().get("/api/v1/models")
            .then()
                .statusCode(200)
                .body("data.find { it.id == 'iris_random_forest-1' }.smile", nullValue())
                .body("data.find { it.id == 'iris_random_forest-1' }.onnx", nullValue())
                .body("data.find { it.id == 'iris_random_forest-1' }.llm", nullValue());
    }

    @Test
    public void testGivenUnknownIdWhenRetrieveThenReturns404() {
        given()
            .when().get("/api/v1/models/does-not-exist")
            .then()
                .statusCode(404);
    }

    @Test
    public void testGivenHuggingFaceIdWhenOwnerDerivedThenUsesFirstSegment() {
        assertEquals("meta-llama", ModelObject.ownedByFromHuggingFaceId("meta-llama/Llama-3.1-8B-Instruct"));
        assertEquals("Qwen", ModelObject.ownedByFromHuggingFaceId("Qwen/Qwen2.5-7B-Instruct"));
        assertEquals(ModelObject.UNKNOWN_OWNER, ModelObject.ownedByFromHuggingFaceId(null));
    }

    @Test
    public void testGivenFamilyWhenOwnerDerivedThenUsesFirstSegment() {
        assertEquals("meta", ModelObject.ownedByFromFamily("meta/llama3"));
        assertEquals("acme", ModelObject.ownedByFromFamily("acme"));
        assertEquals(ModelObject.UNKNOWN_OWNER, ModelObject.ownedByFromFamily(""));
    }

    @Test
    public void testGivenSmileTagsWhenOwnedByResolvedThenPrefersAuthorThenOwner() {
        Properties tags = new Properties();
        assertEquals("Unknown", ModelObject.ownedByFromTags(tags));

        tags.setProperty("owner", "team-a");
        assertEquals("team-a", ModelObject.ownedByFromTags(tags));

        tags.setProperty("author", "alice");
        assertEquals("alice", ModelObject.ownedByFromTags(tags));

        assertEquals("bob", ModelObject.ownedByFromMap(Map.of("Owner", "bob")));
        assertEquals("Unknown", ModelObject.ownedByFromMap(Map.of()));
    }

    // --------------------------------------------------------------- POST /models/load

    @Test
    public void testGivenAlreadyLoadedModelWhenLoadThenReturns409Conflict() {
        // iris_random_forest-1 is already loaded at startup
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"serve/src/test/resources/model/iris_random_forest.sml\"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(409);
    }

    @Test
    public void testGivenBlankModelWhenLoadThenReturns400BadRequest() {
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"   \"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(400);

        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"kind\":\"sml\"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(400);
    }

    @Test
    public void testGivenNonExistentModelWhenLoadThenReturns404NotFound() {
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"serve/src/test/resources/model/non_existent.sml\",\"kind\":\"sml\"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(404);
    }

    @Test
    public void testGivenUnloadedModelWhenDynamicLoadWithSmileAliasThenSucceeds() {
        // Unload iris_random_forest-1
        given()
            .when().post("/api/v1/smile/iris_random_forest-1/unload?timeout=5")
            .then()
                .statusCode(200);

        // Dynamically load using kind="smile"
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"serve/src/test/resources/model/iris_random_forest.sml\",\"kind\":\"smile\"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(200)
                .body("status", equalTo("loaded"))
                .body("id", equalTo("iris_random_forest-1"))
                .body("kind", equalTo("sml"));

        // Loading again returns 409 Conflict
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"serve/src/test/resources/model/iris_random_forest.sml\",\"kind\":\"sml\"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(409);
    }

    @Test
    public void testGivenUnloadedOnnxWhenDynamicLoadThenSucceeds() {
        // Unload squeezenet if loaded
        given()
            .when().post("/api/v1/onnx/squeezenet/unload?timeout=5")
            .then();

        // Dynamically load using kind="onnx"
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"serve/src/test/resources/model/squeezenet.onnx\",\"kind\":\"onnx\"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(200)
                .body("status", equalTo("loaded"))
                .body("id", equalTo("squeezenet"))
                .body("kind", equalTo("onnx"));

        // Loading again returns 409 Conflict
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"serve/src/test/resources/model/squeezenet.onnx\"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(409);
    }

    @Test
    public void testGivenUnsupportedKindWhenLoadThenReturns400BadRequest() {
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"dummy\",\"kind\":\"unsupported\"}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(400);
    }

    @Test
    public void testGivenNonExistentChatModelWhenLoadThenFailsGracefully() {
        // kind="chat" or kind="llm" with non-existent path
        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"serve/src/test/resources/no-such-model\",\"kind\":\"chat\",\"config\":{\"max_batch_size\":8}}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(400);

        given()
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"model\":\"serve/src/test/resources/no-such-model\",\"kind\":\"llm\",\"config\":{\"devices\":\"0\"}}")
            .when().post("/api/v1/models/load")
            .then()
                .statusCode(400);
    }
}
