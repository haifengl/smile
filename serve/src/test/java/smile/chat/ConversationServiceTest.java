/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.chat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for conversation title helpers.
 */
public class ConversationServiceTest {

    @Test
    public void testGivenCreatedAtWhenDefaultTitleThenHumanReadable() {
        // Given a fixed instant
        Instant createdAt = Instant.parse("2026-08-27T20:30:00Z");

        // When formatting the default title
        String title = ConversationService.defaultTitle(createdAt);

        // Then it is a non-empty timestamp string (not "New chat")
        assertNotNull(title);
        assertFalse(title.isBlank());
        assertNotEquals("New chat", title);
        assertTrue(title.contains("2026"));
    }

    @Test
    public void testGivenUserMessageWhenTitleFromFirstMessageThenUsesFirstWords() {
        // Given a long user prompt
        String prompt = "Explain gradient descent for neural networks in simple terms please";

        // When deriving the sidebar title
        String title = ConversationService.titleFromFirstMessage(prompt);

        // Then only the first few words are used
        assertEquals("Explain gradient descent for neural networks", title);
    }

    @Test
    public void testGivenBlankMessageWhenTitleFromFirstMessageThenNull() {
        assertNull(ConversationService.titleFromFirstMessage("   "));
    }
}
