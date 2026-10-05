/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Integration tests for {@link OnnxResource}.
 */
@QuarkusTest
public class OnnxResourceTest {

    @Test
    public void testGetOnnxModelInfo() {
        given()
            .when().get("/api/v1/onnx/squeezenet")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is("squeezenet"))
                .body("inputs", notNullValue())
                .body("outputs", notNullValue());
    }

    @Test
    public void testGetOnnxModelHealth() {
        given()
            .when().get("/api/v1/onnx/squeezenet/health")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is("squeezenet"))
                .body("status", is("ACTIVE"))
                .body("in_flight_requests", is(0))
                .body("uptime_seconds", notNullValue());
    }

    @Test
    public void testGetOnnxModelMetrics() {
        given()
            .when().get("/api/v1/onnx/squeezenet/metrics")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("total_requests", notNullValue())
                .body("successful_requests", notNullValue())
                .body("in_flight_requests", is(0))
                .body("uptime_seconds", greaterThanOrEqualTo(0));
    }

    @Test
    public void testReloadOnnxModel() {
        given()
            .when().post("/api/v1/onnx/squeezenet/reload")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("reloaded"))
                .body("id", is("squeezenet"))
                .body("path", notNullValue());
    }

    @Test
    public void testUnloadAndReloadOnnxModel() {
        // Unload the ONNX model
        given()
            .when().post("/api/v1/onnx/squeezenet/unload?timeout=5")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("unloaded"))
                .body("id", is("squeezenet"))
                .body("drained", is(true));

        // Subsequent info request should return 404
        given()
            .when().get("/api/v1/onnx/squeezenet")
            .then()
                .statusCode(404);

        // Subsequent health request should return 404
        given()
            .when().get("/api/v1/onnx/squeezenet/health")
            .then()
                .statusCode(404);

        // Reload unloaded model from disk should restore it
        given()
            .when().post("/api/v1/onnx/squeezenet/reload")
            .then()
                .statusCode(200)
                .body("status", is("reloaded"))
                .body("id", is("squeezenet"));

        // Verify model is accessible again
        given()
            .when().get("/api/v1/onnx/squeezenet")
            .then()
                .statusCode(200)
                .body("id", is("squeezenet"));
    }

    @Test
    public void testGetNonExistentOnnxModelReturns404() {
        given()
            .when().get("/api/v1/onnx/non_existent_model")
            .then()
                .statusCode(404);
    }
}
