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
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Chat completion validation tests (audio unsupported).
 */
@QuarkusTest
public class ChatCompletionAudioTest {

    @Test
    public void testGivenAudioContentWhenCompletionThenBadRequest() {
        // Given chat service unavailable in %test, validate still runs after 503 check.
        // Audio validation happens before service call when available; with unavailable
        // service we get 503 first. Test deserializer + hasAudio via unit test instead.
        given()
            .contentType(ContentType.JSON)
            .body("""
                    {"messages":[{"role":"user","content":[
                      {"type":"audio_url","audio_url":{"url":"/api/v1/media/x"}}
                    ]}],"max_tokens":8}
                    """)
            .when().post("/api/v1/chat/completions")
            .then()
            .statusCode(400);
    }
}
