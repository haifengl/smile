/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Auth API tests (local {@code me} auto-login via {@code Host: localhost}).
 */
@QuarkusTest
public class AuthResourceTest {

    @Test
    public void testGivenLocalHostWhenMeThenLoggedInAsMe() {
        given()
            .header("Host", "localhost:8081")
            .when().get("/api/v1/auth/me")
            .then()
                .statusCode(200)
                .body("logged_in", equalTo(true))
                .body("user.external_id", equalTo("me"))
                .body("user.display_name", equalTo("Me"));
    }

    @Test
    public void testGivenRemoteHostWhenMeThenGuest() {
        given()
            .header("Host", "example.com")
            .when().get("/api/v1/auth/me")
            .then()
                .statusCode(200)
                .body("logged_in", equalTo(false));
    }

    @Test
    public void testGivenGoogleCallbackWithoutStateCookieWhenCallbackThenBadRequest() {
        given()
            .header("Host", "example.com")
            .queryParam("code", "unused")
            .queryParam("state", "unused")
            .when().get("/api/v1/auth/callback/google")
            .then()
                .statusCode(503);
    }
}
