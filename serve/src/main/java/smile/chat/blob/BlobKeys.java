/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat.blob;

/**
 * Helpers and security validators for blob storage key layouts:
 * <ul>
 *   <li>OAuth users: {@code users/<provider>-<24hex>/<conversation_id>/<message_id|pending>/<content_id>}</li>
 *   <li>Shared (guests &amp; localhost {@code me}): {@code shared/<YYYY>/<MM>/<DD>/<conversation_id>/<message_id|pending>/<content_id>}</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public final class BlobKeys {
    /** Message-id segment used before a {@link smile.chat.ConversationItem} exists. */
    public static final String PENDING_MESSAGE = "pending";

    /** Root prefix for OAuth-authenticated user namespaces. */
    public static final String USERS_ROOT = "users";

    /** Root prefix for date-partitioned shared (guest and local {@code me}) namespaces. */
    public static final String SHARED_ROOT = "shared";

    /** Maximum permitted blob key or prefix length. */
    public static final int MAX_KEY_LENGTH = 1024;

    private BlobKeys() {}

    /**
     * Builds a validated storage key.
     *
     * @param namespace      storage namespace (e.g. {@code users/google-<24hex>} or {@code shared/2026/11/19}).
     * @param conversationId numeric conversation primary key.
     * @param messageId      message primary key, or {@code null} for pending uploads.
     * @param contentId      opaque content UUID.
     * @return the validated storage key.
     */
    public static String of(String namespace, long conversationId, Long messageId, String contentId) {
        String msg = messageId == null ? PENDING_MESSAGE : Long.toString(messageId);
        return validateKey(namespace + "/" + conversationId + "/" + msg + "/" + contentId);
    }

    /**
     * Prefix covering all blobs for a conversation within a namespace (for cascade delete).
     *
     * @param namespace      storage namespace.
     * @param conversationId numeric conversation primary key.
     * @return validated prefix ending with {@code /}.
     */
    public static String conversationPrefix(String namespace, long conversationId) {
        return validatePrefix(namespace + "/" + conversationId + "/");
    }

    /**
     * Validates a blob object key against traversal, control characters, and
     * malformed segments.
     *
     * @param key candidate blob key.
     * @return the validated key.
     * @throws IllegalArgumentException if the key is invalid.
     */
    public static String validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid blob key: " + key);
        }
        if (key.startsWith("/") || key.endsWith("/")) {
            throw new IllegalArgumentException("Blob key must not start or end with '/': " + key);
        }
        checkSegments(key, 1, "key");
        return key;
    }

    /**
     * Validates a blob prefix for {@link BlobStore#deletePrefix(String)}.
     *
     * <p>Requires at least two non-empty path segments so a bug cannot wipe an
     * entire bucket, {@code users/}, or {@code shared/} root.
     *
     * @param prefix candidate prefix.
     * @return normalized prefix ending with {@code /}.
     * @throws IllegalArgumentException if the prefix is invalid or too broad.
     */
    public static String validatePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || prefix.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid blob prefix: " + prefix);
        }
        if (prefix.startsWith("/")) {
            throw new IllegalArgumentException("Blob prefix must not start with '/': " + prefix);
        }
        String trimmed = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Blob prefix must not be root: " + prefix);
        }
        checkSegments(trimmed, 2, "prefix");
        return trimmed + "/";
    }

    private static void checkSegments(String value, int minSegments, String kind) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-' || c == '/';
            if (!allowed) {
                throw new IllegalArgumentException("Illegal character in blob " + kind + ": " + value);
            }
        }
        String[] segments = value.split("/", -1);
        if (segments.length < minSegments) {
            throw new IllegalArgumentException(
                    "Blob " + kind + " requires at least " + minSegments + " segments: " + value);
        }
        for (String seg : segments) {
            if (seg.isEmpty() || ".".equals(seg) || "..".equals(seg)) {
                throw new IllegalArgumentException("Illegal path segment in blob " + kind + ": " + value);
            }
        }
    }
}
