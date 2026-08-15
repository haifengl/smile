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
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for graceful chat-service failover when no LLM is configured.
 *
 * <p>The {@code %test} profile points {@code smile.chat.model} at a missing
 * local path so {@link ChatService} starts unavailable without contacting
 * Hugging Face Hub.
 *
 * @author Haifeng Li
 */
@QuarkusTest
public class ChatServiceTest {

    @Inject
    ChatService chatService;

    @Test
    public void testGivenMissingLocalModelWhenStartedThenChatServiceIsUnavailable() {
        // Given %test.smile.chat.model points at a non-existent path
        // When the application starts
        // Then chat stays unavailable (no HF download attempted)
        assertFalse(chatService.isAvailable());
    }

    @Test
    public void testGivenUnavailableModelWhenChatRequestedThenReturns503() {
        // Given the chat model is not loaded
        var body = """
                {"messages":[{"role":"user","content":"hi"}],"max_tokens":8}
                """;
        // When posting a chat completion (streaming default)
        // Then the response is HTTP 503
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/v1/chat/completions")
            .then()
                .statusCode(503);
    }

    @Test
    public void testGivenUnavailableModelWhenNonStreamingThenReturns503() {
        var body = """
                {"stream":false,"messages":[{"role":"user","content":"hi"}],"max_completion_tokens":8}
                """;
        given()
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .body(body)
            .when().post("/api/v1/chat/completions")
            .then()
                .statusCode(503);
    }

    @Test
    public void testGivenMaxCompletionTokensWhenResolvedThenTakesPrecedence() {
        CompletionRequest request = new CompletionRequest();
        assertEquals(2048, request.resolveMaxTokens());

        request.maxTokens = 100;
        assertEquals(100, request.resolveMaxTokens());

        request.maxCompletionTokens = 50;
        assertEquals(50, request.resolveMaxTokens());
    }

    @Test
    public void testGivenStreamFlagInBodyWhenParsedThenHonorsFalse() {
        assertTrue(ChatCompletionsStreamFlag.streamFlag("{}".getBytes()));
        assertTrue(ChatCompletionsStreamFlag.streamFlag("{\"stream\":true}".getBytes()));
        assertFalse(ChatCompletionsStreamFlag.streamFlag("{\"stream\":false}".getBytes()));
        assertFalse(ChatCompletionsStreamFlag.streamFlag("{\"stream\": false, \"messages\":[]}".getBytes()));
    }

    @Test
    public void testGivenHuggingFaceRepoIdWhenCheckedThenAccepted() {
        assertTrue(ChatService.looksLikeHuggingFaceRepoId("meta-llama/Llama-3.1-8B"));
        assertTrue(ChatService.looksLikeHuggingFaceRepoId("org/model-name"));
    }

    @Test
    public void testGivenFilesystemPathWhenCheckedThenRejectedAsHuggingFaceId() {
        assertFalse(ChatService.looksLikeHuggingFaceRepoId("serve/src/test/resources/no-such-model"));
        assertFalse(ChatService.looksLikeHuggingFaceRepoId("../model/Llama3.1-8B-Instruct"));
        assertFalse(ChatService.looksLikeHuggingFaceRepoId("/abs/path/model"));
        assertFalse(ChatService.looksLikeHuggingFaceRepoId("C:\\models\\llama"));
        assertFalse(ChatService.looksLikeHuggingFaceRepoId("just-a-name"));
        assertFalse(ChatService.looksLikeHuggingFaceRepoId(""));
        assertFalse(ChatService.looksLikeHuggingFaceRepoId(null));
    }

    @Test
    public void testGivenModelIdWhenMatchedThenEmptyOrExactAccepted() {
        String loaded = "meta-llama/Llama-3.1-8B-Instruct";
        assertTrue(ChatService.matchesModelId(null, loaded));
        assertTrue(ChatService.matchesModelId("", loaded));
        assertTrue(ChatService.matchesModelId("   ", loaded));
        assertTrue(ChatService.matchesModelId(loaded, loaded));
        assertTrue(ChatService.matchesModelId("  " + loaded + "  ", loaded));
        assertFalse(ChatService.matchesModelId("meta/llama3", loaded));
        assertFalse(ChatService.matchesModelId("other-model", loaded));
    }

    @Test
    public void testGivenModelSpecWhenPublicIdDerivedThenUsesRepoOrDirectoryName() {
        assertEquals("meta-llama/Llama-3.1-8B-Instruct",
                ChatService.publicModelId("meta-llama/Llama-3.1-8B-Instruct"));
        assertEquals("Qwen/Qwen2.5-7B-Instruct",
                ChatService.publicModelId("Qwen/Qwen2.5-7B-Instruct"));
        assertEquals("unknown", ChatService.publicModelId(null));
        assertEquals("unknown", ChatService.publicModelId("  "));
    }
}
