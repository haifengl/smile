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
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OpenAI-compatible {@code GET /models}.
 *
 * @author Haifeng Li
 */
@QuarkusTest
public class ModelsResourceTest {

    @Test
    public void testGivenNoChatModelWhenListThenEmptyDataArray() {
        // %test profile leaves ChatService unavailable
        given()
            .when().get("/api/v1/models")
            .then()
                .statusCode(200)
                .body("object", equalTo("list"))
                .body("data", empty());
    }

    @Test
    public void testGivenHuggingFaceIdWhenOwnerDerivedThenUsesFirstSegment() {
        assertEquals("meta-llama", ChatService.ownerFromHuggingFaceId("meta-llama/Llama-3.1-8B-Instruct"));
        assertEquals("Qwen", ChatService.ownerFromHuggingFaceId("Qwen/Qwen2.5-7B-Instruct"));
        assertEquals("unknown", ChatService.ownerFromHuggingFaceId(null));
    }

    @Test
    public void testGivenFamilyWhenOwnerDerivedThenUsesFirstSegment() {
        assertEquals("meta", ChatService.ownerFromFamily("meta/llama3"));
        assertEquals("acme", ChatService.ownerFromFamily("acme"));
        assertEquals("unknown", ChatService.ownerFromFamily(""));
    }
}
