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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalBlobStore}.
 */
public class LocalBlobStoreTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGivenPutWhenGetThenReturnsBytes() throws Exception {
        // Given
        LocalBlobStore store = new LocalBlobStore(tempDir);
        String key = BlobKeys.of("abc123", 7L, null, "11111111-1111-1111-1111-111111111111");
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        // When
        store.put(key, data, "text/plain");

        // Then
        assertArrayEquals(data, store.get(key).orElseThrow());
        assertTrue(Files.isRegularFile(tempDir.resolve(key)));
        assertTrue(key.contains("/pending/"));
    }

    @Test
    public void testGivenPrefixWhenDeletePrefixThenRemovesTree() throws Exception {
        LocalBlobStore store = new LocalBlobStore(tempDir);
        String key = BlobKeys.of("user1", 42L, 9L, "22222222-2222-2222-2222-222222222222");
        store.put(key, new byte[] {1, 2, 3}, "application/octet-stream");

        store.deletePrefix(BlobKeys.conversationPrefix("user1", 42L));

        assertTrue(store.get(key).isEmpty());
        assertFalse(Files.exists(tempDir.resolve("user1").resolve("42")));
    }

    @Test
    public void testGivenPathTraversalWhenPutThenRejected() {
        LocalBlobStore store = new LocalBlobStore(tempDir);
        assertThrows(Exception.class, () -> store.put("../escape", new byte[] {1}, null));
    }
}
