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

import java.util.Map;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/**
 * Helpers for OpenAI-style external conversation ids ({@code conv_<id>})
 * and metadata validation.
 *
 * @author Haifeng Li
 */
public final class ConversationIds {
    /** Prefix used in external conversation identifiers. */
    public static final String PREFIX = "conv_";

    private ConversationIds() {}

    /**
     * Formats the database primary key as an OpenAI-style conversation id.
     *
     * @param id the numeric primary key.
     * @return {@code conv_<id>}.
     */
    public static String toExternalId(long id) {
        return PREFIX + id;
    }

    /**
     * Parses an external conversation id into the numeric primary key.
     *
     * <p>Accepts {@code conv_42} and bare {@code 42} for convenience.
     *
     * @param conversationId the path/body id string.
     * @return the numeric id.
     * @throws BadRequestException if the id is missing or malformed.
     */
    public static long parseRequired(String conversationId) {
        Long id = parseOptional(conversationId);
        if (id == null) {
            throw new BadRequestException("Missing conversation id");
        }
        return id;
    }

    /**
     * Parses an optional conversation id reference (e.g. from chat completions).
     *
     * @param conversationId the id string, or {@code null}/blank.
     * @return the numeric id, or {@code null} when absent.
     * @throws BadRequestException if the id is present but malformed.
     */
    public static Long parseOptional(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        String raw = conversationId.trim();
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.startsWith(PREFIX)) {
            raw = raw.substring(PREFIX.length());
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid conversation id: " + conversationId);
        }
    }

    /**
     * Loads a conversation by external id or throws {@link NotFoundException}.
     *
     * @param conversationId external id.
     * @return the entity.
     */
    public static Conversation findRequired(String conversationId) {
        long id = parseRequired(conversationId);
        Conversation conversation = Conversation.findById(id);
        if (conversation == null) {
            throw new NotFoundException("Conversation not found: " + conversationId);
        }
        return conversation;
    }

    /**
     * Validates OpenAI metadata constraints (≤16 pairs, key ≤64, value ≤512).
     *
     * @param metadata the metadata map, or {@code null}.
     * @throws BadRequestException when constraints are violated.
     */
    public static void validateMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return;
        }
        if (metadata.size() > 16) {
            throw new BadRequestException("metadata may contain at most 16 key-value pairs");
        }
        for (var entry : metadata.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.length() > 64) {
                throw new BadRequestException("metadata keys must be non-null and at most 64 characters");
            }
            if (value == null || value.length() > 512) {
                throw new BadRequestException("metadata values must be non-null and at most 512 characters");
            }
        }
    }
}
