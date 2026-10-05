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

/**
 * Integration tests for the SmallRye Health probes exposed under {@code /q/health}.
 */
@QuarkusTest
public class HealthCheckTest {

    @Test
    public void testHealthAggregate() {
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("UP"));
    }

    @Test
    public void testLivenessProbe() {
        given()
            .when().get("/q/health/live")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("UP"));
    }

    @Test
    public void testReadinessProbe() {
        given()
            .when().get("/q/health/ready")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("UP"));
    }
}
