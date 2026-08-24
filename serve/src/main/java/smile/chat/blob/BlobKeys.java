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
package smile.chat.blob;

/**
 * Helpers for blob storage key layout
 * {@code {user_id}/{conversation_id}/{message_id}/{content_id}}.
 *
 * @author Haifeng Li
 */
public final class BlobKeys {
    /** Message-id segment used before a {@link smile.chat.ConversationItem} exists. */
    public static final String PENDING_MESSAGE = "pending";

    private BlobKeys() {}

    /**
     * Builds a storage key.
     *
     * @param userId         hashed client identity (or authenticated user id).
     * @param conversationId numeric conversation primary key.
     * @param messageId      message primary key, or {@code null} for pending uploads.
     * @param contentId      opaque content UUID.
     * @return the storage key.
     */
    public static String of(String userId, long conversationId, Long messageId, String contentId) {
        String msg = messageId == null ? PENDING_MESSAGE : Long.toString(messageId);
        return userId + "/" + conversationId + "/" + msg + "/" + contentId;
    }

    /**
     * Prefix covering all blobs for a conversation (for cascade delete).
     *
     * @param userId         hashed client identity.
     * @param conversationId numeric conversation primary key.
     * @return prefix ending with {@code /}.
     */
    public static String conversationPrefix(String userId, long conversationId) {
        return userId + "/" + conversationId + "/";
    }
}
