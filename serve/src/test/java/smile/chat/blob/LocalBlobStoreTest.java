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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for hardened {@link LocalBlobStore}.
 */
public class LocalBlobStoreTest {

    @TempDir
    Path tempDir;

    @Test
    public void testGivenPutWhenGetThenReturnsBytes() throws Exception {
        LocalBlobStore store = new LocalBlobStore(tempDir);
        String key = BlobKeys.of("users/google-0123456789abcdef01234567", 7L, null,
                "11111111-1111-1111-1111-111111111111");
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        store.put(key, data, "text/plain");

        assertArrayEquals(data, store.get(key).orElseThrow());
        assertTrue(Files.isRegularFile(store.root().resolve(key)));
        assertTrue(key.contains("/pending/"));
    }

    @Test
    public void testGivenPrefixWhenDeletePrefixThenRemovesTree() throws Exception {
        LocalBlobStore store = new LocalBlobStore(tempDir);
        String ns = "shared/2026/11/19";
        String key = BlobKeys.of(ns, 42L, 9L, "22222222-2222-2222-2222-222222222222");
        store.put(key, new byte[] {1, 2, 3}, "application/octet-stream");

        store.deletePrefix(BlobKeys.conversationPrefix(ns, 42L));

        assertTrue(store.get(key).isEmpty());
        assertFalse(Files.exists(store.root().resolve(ns).resolve("42")));
    }

    @Test
    public void testGivenPathTraversalOrBroadPrefixWhenCalledThenRejected() {
        LocalBlobStore store = new LocalBlobStore(tempDir);
        assertThrows(IOException.class, () -> store.put("../escape", new byte[] {1}, null));
        assertThrows(IOException.class, () -> store.put("/abs/key", new byte[] {1}, null));
        assertThrows(IOException.class, () -> store.deletePrefix(""));
        assertThrows(IOException.class, () -> store.deletePrefix("users/"));
        assertThrows(IOException.class, () -> store.deletePrefix("shared/"));
    }

    @Test
    public void testGivenOversizedPayloadWhenPutOrGetThenRejected() throws Exception {
        LocalBlobStore store = new LocalBlobStore(tempDir, 16);
        String key = BlobKeys.of("shared/2026/11/19", 1L, null, "33333333-3333-3333-3333-333333333333");

        assertThrows(IOException.class, () -> store.put(key, new byte[17], "text/plain"));
        assertThrows(IOException.class, () -> store.put(
                key, new ByteArrayInputStream(new byte[17]), -1, "text/plain"));

        // Plant an oversized file directly on disk to verify bounded read guard.
        Path planted = store.root().resolve(key);
        Files.createDirectories(planted.getParent());
        Files.write(planted, new byte[32]);
        assertThrows(IOException.class, () -> store.get(key));
    }

    @Test
    public void testGivenSymlinkEscapeWhenGetOrPutThenRejected(@TempDir Path outsideDir) throws Exception {
        LocalBlobStore store = new LocalBlobStore(tempDir);
        Path secret = outsideDir.resolve("secret.txt");
        Files.writeString(secret, "top-secret");

        Path linkDir = store.root().resolve("shared/2026/11/19/99/pending");
        Files.createDirectories(linkDir);
        Path symlink = linkDir.resolve("44444444-4444-4444-4444-444444444444");
        try {
            Files.createSymbolicLink(symlink, secret);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
            // Skip on Windows environments without Developer Mode / SeCreateSymbolicLinkPrivilege.
            return;
        }

        String key = "shared/2026/11/19/99/pending/44444444-4444-4444-4444-444444444444";
        assertThrows(IOException.class, () -> store.get(key));
        assertThrows(IOException.class, () -> store.put(key, new byte[] {1, 2}, "text/plain"));
    }
}
