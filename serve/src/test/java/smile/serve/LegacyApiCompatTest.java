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
package smile.serve;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies both canonical {@code /v1/*} inference endpoints and the temporary
 * {@link LegacyApiCompatFilter} routing legacy {@code /api/v1/{chat,models,smile,onnx}}
 * requests to {@code /v1/*}, while keeping application endpoints under {@code /api/v1/*}.
 */
@QuarkusTest
public class LegacyApiCompatTest {

    @Test
    public void testRewritePathUnitRules() {
        assertEquals("/v1/chat/completions", LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/chat/completions"));
        assertEquals("/v1/models", LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/models"));
        assertEquals("/v1/models/load", LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/models/load"));
        assertEquals("/v1/smile/iris-1", LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/smile/iris-1"));
        assertEquals("/v1/onnx/squeezenet", LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/onnx/squeezenet"));

        // Non-inference /api/v1/* paths must remain untouched
        assertNull(LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/auth/me"));
        assertNull(LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/users/me"));
        assertNull(LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/conversations"));
        assertNull(LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/media/123"));
        assertNull(LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/audit/status"));
        assertNull(LegacyApiCompatFilter.rewriteLegacyInferencePath("/api/v1/models-extra"));
        assertNull(LegacyApiCompatFilter.rewriteLegacyInferencePath("/v1/models"));
    }

    @Test
    public void testCanonicalAndLegacyModelsEndpointsMatch() {
        // Canonical /v1/models
        given()
            .when().get("/v1/models")
            .then()
                .statusCode(200)
                .body("object", equalTo("list"))
                .body("data.id", hasItem("iris_random_forest-1"));

        // Legacy /api/v1/models transparently rerouted to /v1/models
        given()
            .when().get("/api/v1/models")
            .then()
                .statusCode(200)
                .body("object", equalTo("list"))
                .body("data.id", hasItem("iris_random_forest-1"));

        given()
            .when().get("/api/v1/models/iris_random_forest-1")
            .then()
                .statusCode(200)
                .body("id", equalTo("iris_random_forest-1"));
    }

    @Test
    public void testLegacySmileInferencePreservesPostBodyAndQueryString() {
        String sample = """
                {"sepallength":5.1,"sepalwidth":3.5,"petallength":1.4,"petalwidth":0.2}
                """;

        // Canonical /v1/smile/{id}?explain=true
        given()
            .contentType(ContentType.JSON)
            .body(sample)
            .when().post("/v1/smile/iris_random_forest-1?explain=true")
            .then()
                .statusCode(200)
                .body("prediction", notNullValue())
                .body("explanations.shap", notNullValue());

        // Legacy /api/v1/smile/{id}?explain=true must preserve both JSON body and query params
        given()
            .contentType(ContentType.JSON)
            .body(sample)
            .when().post("/api/v1/smile/iris_random_forest-1?explain=true")
            .then()
                .statusCode(200)
                .body("prediction", notNullValue())
                .body("explanations.shap", notNullValue());
    }

    @Test
    public void testLegacyOnnxAndChatCompletionsReroute() {
        given()
            .when().get("/api/v1/onnx/squeezenet")
            .then()
                .statusCode(200)
                .body("id", equalTo("squeezenet"));

        // Chat service is unavailable in %test -> 503 proves ChatCompletionResource was reached
        given()
            .contentType(ContentType.JSON)
            .body("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":4}")
            .when().post("/api/v1/chat/completions")
            .then()
                .statusCode(503);
    }

    @Test
    public void testApplicationEndpointsRemainUnderApiV1() {
        given()
            .when().get("/api/v1/auth/me")
            .then()
                .statusCode(200);

        given()
            .when().get("/api/v1/conversations")
            .then()
                .statusCode(200);
    }
}
