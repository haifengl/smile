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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Object storage for conversation multimedia (bytes off the relational DB).
 *
 * <p>Keys use the layout
 * {@code {user_id}/{conversation_id}/{message_id}/{content_id}} where
 * {@code message_id} may be {@code pending} until a message row exists.
 *
 * @author Haifeng Li
 */
public interface BlobStore {
    /**
     * Writes object bytes at {@code key}.
     *
     * @param key         storage key.
     * @param data        object bytes.
     * @param contentType MIME type, or {@code null}.
     * @throws IOException if the write fails.
     */
    void put(String key, byte[] data, String contentType) throws IOException;

    /**
     * Writes object bytes from a stream at {@code key}.
     *
     * @param key         storage key.
     * @param data        object stream (closed by the caller).
     * @param length      content length when known, or {@code -1}.
     * @param contentType MIME type, or {@code null}.
     * @throws IOException if the write fails.
     */
    void put(String key, InputStream data, long length, String contentType) throws IOException;

    /**
     * Reads the object at {@code key}.
     *
     * @param key storage key.
     * @return object bytes, or empty when missing.
     * @throws IOException if the read fails.
     */
    Optional<byte[]> get(String key) throws IOException;

    /**
     * Deletes the object at {@code key} if it exists.
     *
     * @param key storage key.
     * @throws IOException if the delete fails.
     */
    void delete(String key) throws IOException;

    /**
     * Deletes all objects whose keys start with {@code prefix}
     * (typically {@code userId/conversationId/}).
     *
     * @param prefix key prefix including trailing slash when desired.
     * @throws IOException if listing or delete fails.
     */
    void deletePrefix(String prefix) throws IOException;
}
