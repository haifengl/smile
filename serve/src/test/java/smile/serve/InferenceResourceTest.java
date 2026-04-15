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
package smile.serve;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
/**
 * Integration tests for {@link InferenceResource}.
 *
 * <p>These tests use the Quarkus test runtime with the {@code %test} profile,
 * which loads a small pre-trained Random Forest model from
 * {@code serve/src/test/resources/model/iris_random_forest.sml}.
 */
@QuarkusTest
public class InferenceResourceTest {
    // ------------------------------------------------------------------ list
    /**
     * GET /models should return the list of loaded model IDs.
     */
    @Test
    public void testListModels() {
        // Given a running server with one loaded model
        // When listing all models
        // Then the response contains exactly the expected model ID
        given()
            .when().get("/api/v1/models")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(is("[\"iris_random_forest-1\"]"));
    }
    // --------------------------------------------------------------- get metadata
    /**
     * GET /models/{id} should return the full metadata for a known model.
     */
    @Test
    public void testGetModelMetadata() {
        // Given the iris_random_forest-1 model is loaded
        // When fetching its metadata
        // Then the response contains algorithm, schema and tags
        given()
            .when().get("/api/v1/models/iris_random_forest-1")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is("iris_random_forest-1"))
                .body("algorithm", is("random-forest"))
                .body("schema", notNullValue())
                .body("schema.petallength.type", is("float"))
                .body("schema.petallength.nullable", is(false))
                .body("schema.petalwidth.type", is("float"))
                .body("schema.sepalwidth.type", is("float"))
                .body("schema.sepallength.type", is("float"));
    }
    /**
     * GET /models/{id} for an unknown model should return HTTP 404.
     */
    @Test
    public void testGetUnknownModelReturns404() {
        // Given no model with id "nonexistent-1" is loaded
        // When fetching its metadata
        // Then the response is 404
        given()
            .when().get("/api/v1/models/nonexistent-1")
            .then()
                .statusCode(404);
    }
    // --------------------------------------------------------------- predict (JSON)
    /**
     * POST /models/{id} with a valid JSON body should return a prediction and
     * posterior probabilities for a soft classifier.
     */
    @Test
    public void testPredictJsonReturnsPredictionAndProbabilities() {
        // Given valid iris feature values
        var request = "{\"petallength\":5.1,\"petalwidth\":3.5,\"sepallength\":1.4,\"sepalwidth\":0.2}";
        var expected = "{\"prediction\":2,\"probabilities\":[0.052,0.187,0.761]}";
        // When posting to the inference endpoint
        // Then the response contains prediction = 2 with probabilities
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/api/v1/models/iris_random_forest-1")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(is(expected));
    }
    /**
     * POST /models/{id} with all-zero features should still return a valid
     * prediction (boundary / edge-case input).
     */
    @Test
    public void testPredictJsonWithZeroFeaturesReturnsValidPrediction() {
        // Given all-zero feature values
        var request = "{\"petallength\":0.0,\"petalwidth\":0.0,\"sepallength\":0.0,\"sepalwidth\":0.0}";
        // When posting to the inference endpoint
        // Then the response has a numeric prediction field
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/api/v1/models/iris_random_forest-1")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("prediction", notNullValue());
    }
    /**
     * POST /models/{id} with a missing required field should return HTTP 400.
     */
    @Test
    public void testPredictJsonMissingFieldReturns400() {
        // Given a JSON body that is missing the "petalwidth" field
        var request = "{\"petallength\":5.1,\"sepallength\":1.4,\"sepalwidth\":0.2}";
        // When posting to the inference endpoint
        // Then the response is 400 Bad Request
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/api/v1/models/iris_random_forest-1")
            .then()
                .statusCode(400);
    }
    /**
     * POST /models/{id} for an unknown model ID should return HTTP 404.
     */
    @Test
    public void testPredictUnknownModelReturns404() {
        // Given no model "unknown-model-1" is loaded
        var request = "{\"petallength\":5.1,\"petalwidth\":3.5,\"sepallength\":1.4,\"sepalwidth\":0.2}";
        // When posting a prediction request
        // Then the response is 404
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when().post("/api/v1/models/unknown-model-1")
            .then()
                .statusCode(404);
    }
    // --------------------------------------------------------------- stream (CSV)
    /**
     * POST /models/{id}/stream with a CSV body should stream one result per
     * non-blank input line.
     *
     * <p>The endpoint uses SSE (server-sent events) so each emitted item is
     * prefixed with {@code "data:"} by the Quarkus RESTEasy Reactive runtime.
     * Each data value begins with a space (added by the resource to prevent
     * SSE clients eating the first character) followed by the predicted class
     * digit and optional probabilities.
     */
    @Test
    public void testStreamCsvReturnsPredictions() {
        // Given three CSV rows for the iris model
        // Column order matches the model schema (alphabetical): petallength, petalwidth, sepallength, sepalwidth
        var csvBody = "5.1,3.5,1.4,0.2\n6.7,3.0,5.2,2.3\n5.8,2.7,4.1,1.0\n";
        // When posting to the stream endpoint with text/plain
        // Then the response body contains three SSE data lines
        String body = given()
            .contentType(ContentType.TEXT)
            .body(csvBody)
            .when().post("/api/v1/models/iris_random_forest-1/stream")
            .then()
                .statusCode(200)
                .extract().body().asString();
        // Filter to non-blank lines; each SSE chunk looks like "data: <prediction>"
        var dataLines = body.lines()
                .filter(l -> !l.isBlank() && l.startsWith("data:"))
                .toList();
        Assertions.assertEquals(3, dataLines.size(),
                "Expected 3 SSE data lines but got: " + body);
        // Each "data:" line must carry a digit (the class label) after the "data: " prefix
        for (var line : dataLines) {
            String payload = line.substring("data:".length()).trim();
            Assertions.assertTrue(
                    Character.isDigit(payload.charAt(0)),
                    "Expected prediction digit at start of payload: " + payload);
        }
    }
    /**
     * POST /models/{id}/stream with a JSON-lines body should stream one result
     * per non-blank JSON object.
     */
    @Test
    public void testStreamJsonLinesReturnsPredictions() {
        // Given two JSON-lines rows for the iris model
        var jsonLines = "{\"petallength\":5.1,\"petalwidth\":3.5,\"sepallength\":1.4,\"sepalwidth\":0.2}\n"
                + "{\"petallength\":6.7,\"petalwidth\":3.0,\"sepallength\":5.2,\"sepalwidth\":2.3}\n";
        // When posting to the stream endpoint with application/json
        // Then the response contains two SSE data lines
        String body = given()
            .contentType(ContentType.JSON)
            .body(jsonLines)
            .when().post("/api/v1/models/iris_random_forest-1/stream")
            .then()
                .statusCode(200)
                .extract().body().asString();
        var dataLines = body.lines()
                .filter(l -> !l.isBlank() && l.startsWith("data:"))
                .toList();
        Assertions.assertEquals(2, dataLines.size(),
                "Expected 2 SSE data lines but got: " + body);
    }
    /**
     * POST /models/{id}/stream with a CSV that has too few columns will fail
     * mid-stream. Because HTTP headers are committed before the stream body
     * is sent, the status code is 200 but the connection is closed early by
     * the server without emitting any {@code data:} lines.
     *
     * <p>Note: SSE streams cannot propagate HTTP error codes after the headers
     * have been flushed; the server signals the error by closing the stream.
     */
    @Test
    public void testStreamCsvTooFewColumnsEmitsNoPredictions() {
        // Given a CSV row with only 2 columns (model needs 4)
        var badCsv = "5.1,3.5\n";
        // When posting to the stream endpoint
        // Then the server returns 200 (headers already flushed) but closes the stream
        // immediately on error without emitting any prediction data lines.
        // RestAssured may throw ConnectionClosedException; we catch it to verify the invariant.
        try {
            String body = given()
                .contentType(ContentType.TEXT)
                .body(badCsv)
                .when().post("/api/v1/models/iris_random_forest-1/stream")
                .then()
                .extract().body().asString();
            // If we get a body at all, it must contain no valid data lines.
            long dataLineCount = body.lines()
                    .filter(l -> l.startsWith("data:"))
                    .count();
            Assertions.assertEquals(0, dataLineCount,
                    "Stream should not emit data lines for bad input, but got: " + body);
        } catch (Exception e) {
            // ConnectionClosedException or similar is expected when the server
            // closes the stream early due to the processing error.
            // The exact message varies by HTTP client version, e.g.:
            //   "Premature end of chunk coded message body: closing chunk expected"
            //   "Connection reset"
            //   "Remote connection closed"
            boolean isExpected = e.getClass().getSimpleName().contains("Closed")
                    || e.getClass().getSimpleName().contains("Connection")
                    || (e.getMessage() != null && (
                            e.getMessage().contains("Connection") ||
                            e.getMessage().contains("closed") ||
                            e.getMessage().contains("closing") ||
                            e.getMessage().contains("reset") ||
                            e.getMessage().contains("Premature") ||
                            e.getMessage().contains("stream")));
            Assertions.assertTrue(isExpected, "Unexpected exception: " + e);
        }
    }
    /**
     * POST /models/{id}/stream for an unknown model should return HTTP 404
     * before any stream is established.
     */
    @Test
    public void testStreamUnknownModelReturns404() {
        // Given no model "ghost-model-1" is loaded
        // When posting to its stream endpoint
        // Then the response is 404
        given()
            .contentType(ContentType.TEXT)
            .body("5.1,3.5,1.4,0.2\n")
            .when().post("/api/v1/models/ghost-model-1/stream")
            .then()
                .statusCode(404);
    }
}