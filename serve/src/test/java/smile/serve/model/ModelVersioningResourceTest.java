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
package smile.serve.model;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import smile.serve.InferenceResource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end integration tests for slug overrides, multi-version co-serving,
 * mutable aliases ({@code default}, {@code latest}, custom aliases), response
 * headers ({@code X-Model-Id}, {@code X-Model-Version}), and cross-backend conflict checks.
 */
@QuarkusTest
public class ModelVersioningResourceTest {

    private static final String IRIS_SAMPLE =
            "{\"petallength\":5.1,\"petalwidth\":3.5,\"sepallength\":1.4,\"sepalwidth\":0.2}";

    @Test
    public void testSmileMultiVersionCoServingAliasesPromotionAndRollback() {
        String slug = "iris_multi_test";
        try {
            // Given: load v1 (random forest) under explicit slug "iris_multi_test"
            given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "model": "serve/src/test/resources/model/iris_random_forest.sml",
                          "kind": "sml",
                          "slug": "iris_multi_test",
                          "version": "1",
                          "alias": "stable"
                        }
                        """)
                .when().post("/v1/models/load")
                .then()
                    .statusCode(200)
                    .body("id", equalTo(slug))
                    .body("slug", equalTo(slug))
                    .body("version", equalTo("1"))
                    .body("algorithm", equalTo("random-forest"));

            // When: load v2 (LDA) with set_default=false and alias="canary"
            given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "model": "serve/src/test/resources/model/iris_lda.sml",
                          "kind": "sml",
                          "slug": "iris_multi_test",
                          "version": "2",
                          "alias": "canary",
                          "set_default": false
                        }
                        """)
                .when().post("/v1/models/load")
                .then()
                    .statusCode(200)
                    .body("id", equalTo(slug))
                    .body("version", equalTo("2"))
                    .body("algorithm", equalTo("lda"));

            // Then: GET /v1/models lists one entry for iris_multi_test with default="1" and versions=["1","2"]
            given()
                .when().get("/v1/models")
                .then()
                    .statusCode(200)
                    .body("data.findAll { it.id == 'iris_multi_test' }", hasSize(1))
                    .body("data.find { it.id == 'iris_multi_test' }.version", equalTo("1"))
                    .body("data.find { it.id == 'iris_multi_test' }.versions", contains("1", "2"))
                    .body("data.find { it.id == 'iris_multi_test' }.kind", equalTo("random-forest"));

            // And: GET /v1/models/{slug}/versions/2 and /aliases/canary retrieve v2 (LDA)
            given()
                .when().get("/v1/models/" + slug + "/versions/2")
                .then()
                    .statusCode(200)
                    .body("id", equalTo(slug))
                    .body("version", equalTo("2"))
                    .body("kind", equalTo("lda"))
                    .body("smile.default_version", equalTo("1"))
                    .body("smile.aliases.canary", equalTo("2"));

            given()
                .when().get("/v1/models/" + slug + "/aliases/canary")
                .then()
                    .statusCode(200)
                    .body("version", equalTo("2"))
                    .body("kind", equalTo("lda"));

            // And: GET /v1/smile/{slug}/versions summarizes versions and aliases
            given()
                .when().get("/v1/smile/" + slug + "/versions")
                .then()
                    .statusCode(200)
                    .body("slug", equalTo(slug))
                    .body("default", equalTo("1"))
                    .body("latest", equalTo("2"))
                    .body("versions", contains("1", "2"))
                    .body("aliases.default", equalTo("1"))
                    .body("aliases.stable", equalTo("1"))
                    .body("aliases.canary", equalTo("2"));

            // And: default endpoint routes to v1, canary/latest/versions/2 route to v2 with headers
            given()
                .contentType(ContentType.JSON)
                .body(IRIS_SAMPLE)
                .when().post("/v1/smile/" + slug)
                .then()
                    .statusCode(200)
                    .header(InferenceResource.HEADER_MODEL_ID, equalTo(slug))
                    .header(InferenceResource.HEADER_MODEL_VERSION, equalTo("1"));

            given()
                .contentType(ContentType.JSON)
                .body(IRIS_SAMPLE)
                .when().post("/v1/smile/" + slug + "/aliases/canary")
                .then()
                    .statusCode(200)
                    .header(InferenceResource.HEADER_MODEL_ID, equalTo(slug))
                    .header(InferenceResource.HEADER_MODEL_VERSION, equalTo("2"));

            given()
                .contentType(ContentType.JSON)
                .body(IRIS_SAMPLE)
                .when().post("/v1/smile/" + slug + "/versions/2")
                .then()
                    .statusCode(200)
                    .header(InferenceResource.HEADER_MODEL_ID, equalTo(slug))
                    .header(InferenceResource.HEADER_MODEL_VERSION, equalTo("2"));

            // When: promote default to v2 atomically via PUT /aliases/default
            given()
                .contentType(ContentType.JSON)
                .body("{\"version\":\"2\"}")
                .when().put("/v1/smile/" + slug + "/aliases/default")
                .then()
                    .statusCode(200)
                    .body("default", equalTo("2"));

            // Then: default inference now serves v2
            given()
                .contentType(ContentType.JSON)
                .body(IRIS_SAMPLE)
                .when().post("/v1/smile/" + slug)
                .then()
                    .statusCode(200)
                    .header(InferenceResource.HEADER_MODEL_VERSION, equalTo("2"));

            // Reserved alias 'latest' cannot be mutated
            given()
                .contentType(ContentType.JSON)
                .body("{\"version\":\"1\"}")
                .when().put("/v1/smile/" + slug + "/aliases/latest")
                .then()
                    .statusCode(400);

            // Cross-backend conflict: loading an ONNX model under the same slug returns 409
            given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "model": "serve/src/test/resources/model/logreg_iris.onnx",
                          "kind": "onnx",
                          "slug": "iris_multi_test",
                          "version": "1"
                        }
                        """)
                .when().post("/v1/models/load")
                .then()
                    .statusCode(409);

            // Delete canary alias and unload only v1 -> v2 remains active
            given()
                .when().delete("/v1/smile/" + slug + "/aliases/canary")
                .then()
                    .statusCode(200);

            given()
                .when().get("/v1/smile/" + slug + "/aliases/canary")
                .then()
                    .statusCode(404);

            given()
                .when().post("/v1/smile/" + slug + "/versions/1/unload?timeout=5")
                .then()
                    .statusCode(200)
                    .body("version", equalTo("1"));

            given()
                .when().get("/v1/smile/" + slug + "/versions/1")
                .then()
                    .statusCode(404);

            given()
                .when().get("/v1/smile/" + slug)
                .then()
                    .statusCode(200)
                    .header(InferenceResource.HEADER_MODEL_VERSION, equalTo("2"))
                    .body("version", equalTo("2"))
                    .body("versions", contains("2"));
        } finally {
            given().when().post("/v1/smile/" + slug + "/unload?timeout=5");
        }
    }

    @Test
    public void testOnnxMultiVersionCoServingAndAliases() {
        String slug = "onnx_multi_test";
        try {
            // Load v1.0 and v2.0 under the same ONNX slug
            given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "model": "serve/src/test/resources/model/logreg_iris.onnx",
                          "kind": "onnx",
                          "slug": "onnx_multi_test",
                          "version": "1.0"
                        }
                        """)
                .when().post("/v1/models/load")
                .then()
                    .statusCode(200)
                    .body("id", equalTo(slug))
                    .body("version", equalTo("1.0"));

            given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "model": "serve/src/test/resources/model/logreg_iris.onnx",
                          "kind": "onnx",
                          "slug": "onnx_multi_test",
                          "version": "2.0",
                          "alias": "beta",
                          "set_default": false
                        }
                        """)
                .when().post("/v1/models/load")
                .then()
                    .statusCode(200)
                    .body("id", equalTo(slug))
                    .body("version", equalTo("2.0"));

            // Default routes to 1.0, beta & latest route to 2.0
            given()
                .when().get("/v1/onnx/" + slug)
                .then()
                    .statusCode(200)
                    .header(InferenceResource.HEADER_MODEL_ID, equalTo(slug))
                    .header(InferenceResource.HEADER_MODEL_VERSION, equalTo("1.0"))
                    .body("id", equalTo(slug))
                    .body("version", equalTo("1.0"))
                    .body("versions", contains("1.0", "2.0"));

            given()
                .when().get("/v1/onnx/" + slug + "/aliases/beta")
                .then()
                    .statusCode(200)
                    .header(InferenceResource.HEADER_MODEL_VERSION, equalTo("2.0"))
                    .body("version", equalTo("2.0"));

            // Unpin default -> automatically switches to latest (2.0)
            given()
                .when().delete("/v1/onnx/" + slug + "/aliases/default")
                .then()
                    .statusCode(200)
                    .body("default", equalTo("2.0"));

            given()
                .when().get("/v1/onnx/" + slug)
                .then()
                    .statusCode(200)
                    .header(InferenceResource.HEADER_MODEL_VERSION, equalTo("2.0"));
        } finally {
            given().when().post("/v1/onnx/" + slug + "/unload?timeout=5");
        }
    }
}
