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
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Integration tests for the unified Prometheus scrape endpoint at {@code /q/metrics}.
 */
@QuarkusTest
public class MetricsEndpointTest {

    @Test
    public void testMetricsEndpointExposesClassicModelSeries() {
        given()
            .when().get("/q/metrics")
            .then()
                .statusCode(200)
                .body(containsString("serve_smile_requests_total"))
                .body(containsString("model_id=\"iris_random_forest-1\""));
    }

    @Test
    public void testMetricsEndpointExposesLlmSeries() {
        given()
            .when().get("/q/metrics")
            .then()
                .statusCode(200)
                .body(containsString("serve_llm_prompt_tokens_total"))
                .body(containsString("serve_llm_time_to_first_token_seconds"));
    }
}
