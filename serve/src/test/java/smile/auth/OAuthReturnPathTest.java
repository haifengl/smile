/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuthReturnPathTest {

    @Test
    public void testGivenNullWhenNormalizeThenChat() {
        // Given / When / Then
        assertEquals(OAuthReturnPath.CHAT, OAuthReturnPath.normalize(null));
    }

    @Test
    public void testGivenInferPathsWhenNormalizeThenRoot() {
        // Given / When / Then
        assertEquals(OAuthReturnPath.INFER, OAuthReturnPath.normalize("/"));
        assertEquals(OAuthReturnPath.INFER, OAuthReturnPath.normalize("/index.html"));
    }

    @Test
    public void testGivenChatPathsWhenNormalizeThenChat() {
        // Given / When / Then
        assertEquals(OAuthReturnPath.CHAT, OAuthReturnPath.normalize("/chat"));
        assertEquals(OAuthReturnPath.CHAT, OAuthReturnPath.normalize("/chat/"));
        assertEquals(OAuthReturnPath.CHAT, OAuthReturnPath.normalize("/chat/index.html"));
    }

    @Test
    public void testGivenUnsafePathWhenNormalizeThenChat() {
        // Given / When / Then
        assertEquals(OAuthReturnPath.CHAT, OAuthReturnPath.normalize("//evil.example/"));
        assertEquals(OAuthReturnPath.CHAT, OAuthReturnPath.normalize("/api/v1/chat"));
        assertEquals(OAuthReturnPath.CHAT, OAuthReturnPath.normalize("/chat/../admin"));
    }
}
