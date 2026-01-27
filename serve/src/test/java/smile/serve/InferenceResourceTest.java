/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
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
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class InferenceResourceTest {
    @Test
    public void testGetModelsEndpoint() {
        var response = """
                ["iris_random_forest-1"]""";
        given().when()
               .get("/api/v1/models")
               .then()
               .statusCode(200)
               .body(is(response));
    }

    @Test
    public void testGetModelEndpoint() {
        var response = """
                {"id":"iris_random_forest-1","algorithm":"random-forest","schema":{"petallength":{"type":"float","nullable":false},"petalwidth":{"type":"float","nullable":false},"sepallength":{"type":"float","nullable":false},"sepalwidth":{"type":"float","nullable":false}},"tags":{"smile.random_forest.trees":"200"}}""";
        given().when()
               .get("/api/v1/models/iris_random_forest-1")
               .then()
               .statusCode(200)
               .body(is(response));
    }

    @Test
    public void testPostModelEndpoint() {
        var request = """
                {"petallength":5.1,"petalwidth":3.5,"sepallength":1.4,"sepalwidth":0.2}""";
        var response = """
                {"prediction":2,"probabilities":[0.051,0.172,0.777]}""";
        given().contentType(ContentType.JSON) // Specify the content type of the request body
               .body(request)
               .when()
               .post("/api/v1/models/iris_random_forest-1")
               .then()
               .statusCode(200)
               .body(is(response));
    }
}
