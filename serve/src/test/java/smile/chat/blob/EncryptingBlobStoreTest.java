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
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EncryptingBlobStore}.
 */
public class EncryptingBlobStoreTest {

    @TempDir
    Path tempDir;

    private static byte[] randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return k;
    }

    @Test
    public void testGivenPlaintextWhenPutAndGetThenEncryptedOnDiskAndRoundTrips() throws Exception {
        long maxPlain = 1024;
        LocalBlobStore raw = new LocalBlobStore(tempDir, maxPlain + EncryptingBlobStore.ENVELOPE_OVERHEAD_BYTES);
        EncryptingBlobStore store = new EncryptingBlobStore(raw, randomKey(), maxPlain, false);

        String key = BlobKeys.of("users/google-0123456789abcdef01234567", 42L, null,
                "11111111-1111-1111-1111-111111111111");
        byte[] plaintext = "top-secret-attachment-content".getBytes(StandardCharsets.UTF_8);

        store.put(key, plaintext, "text/plain");

        // Raw bytes on disk must carry the SMILEBLB header and must not contain plaintext
        byte[] onDisk = raw.get(key).orElseThrow();
        assertEquals(plaintext.length + EncryptingBlobStore.ENVELOPE_OVERHEAD_BYTES, onDisk.length);
        assertEquals("SMILEBLB", new String(Arrays.copyOf(onDisk, 8), StandardCharsets.US_ASCII));
        assertEquals(EncryptingBlobStore.VERSION_1, onDisk[8]);
        assertFalse(new String(onDisk, StandardCharsets.ISO_8859_1).contains("top-secret"));

        // Decrypted round-trip matches exactly
        assertArrayEquals(plaintext, store.get(key).orElseThrow());

        // Stream put produces fresh IV / different ciphertext and round-trips
        String key2 = BlobKeys.of("users/google-0123456789abcdef01234567", 42L, null,
                "22222222-2222-2222-2222-222222222222");
        store.put(key2, new ByteArrayInputStream(plaintext), plaintext.length, "text/plain");
        assertArrayEquals(plaintext, store.get(key2).orElseThrow());
        assertFalse(Arrays.equals(onDisk, raw.get(key2).orElseThrow()),
                "Fresh random IV must produce distinct ciphertexts");
    }

    @Test
    public void testGivenTransplantedCiphertextAcrossUsersWhenGetThenAadRejects() throws Exception {
        LocalBlobStore raw = new LocalBlobStore(tempDir, 4096);
        EncryptingBlobStore store = new EncryptingBlobStore(raw, randomKey(), 4096, false);

        String aliceKey = BlobKeys.of("users/google-aaaaaaaaaaaaaaaaaaaaaaaa", 1L, 1L,
                "11111111-1111-1111-1111-111111111111");
        String bobKey = BlobKeys.of("users/google-bbbbbbbbbbbbbbbbbbbbbbbb", 2L, 1L,
                "22222222-2222-2222-2222-222222222222");

        store.put(aliceKey, "alice-private".getBytes(StandardCharsets.UTF_8), "text/plain");

        // Simulate attacker copying Alice's raw ciphertext into Bob's prefix
        byte[] aliceCiphertext = raw.get(aliceKey).orElseThrow();
        raw.put(bobKey, aliceCiphertext, "application/octet-stream");

        assertThrows(IOException.class, () -> store.get(bobKey),
                "Key-bound GCM AAD must block cross-user ciphertext transplantation");
    }

    @Test
    public void testGivenTamperedCiphertextOrWrongKeyWhenGetThenRejected() throws Exception {
        LocalBlobStore raw = new LocalBlobStore(tempDir, 4096);
        byte[] keyA = randomKey();
        byte[] keyB = randomKey();
        EncryptingBlobStore storeA = new EncryptingBlobStore(raw, keyA, 4096, false);
        EncryptingBlobStore storeB = new EncryptingBlobStore(raw, keyB, 4096, false);

        String key = BlobKeys.of("shared/2026/11/19", 7L, null, "33333333-3333-3333-3333-333333333333");
        storeA.put(key, "integrity-check".getBytes(StandardCharsets.UTF_8), "text/plain");

        // Wrong key fails
        assertThrows(IOException.class, () -> storeB.get(key));

        // Flip a bit in ciphertext
        byte[] tampered = raw.get(key).orElseThrow();
        tampered[tampered.length - 1] ^= 0x01;
        raw.put(key, tampered, "application/octet-stream");
        assertThrows(IOException.class, () -> storeA.get(key));
    }

    @Test
    public void testGivenLegacyPlaintextBlobWhenAllowPlaintextFalseVsTrueThenEnforced() throws Exception {
        LocalBlobStore raw = new LocalBlobStore(tempDir, 4096);
        byte[] key = randomKey();
        String blobKey = BlobKeys.of("shared/2026/11/19", 8L, null, "44444444-4444-4444-4444-444444444444");
        byte[] legacy = "legacy-unencrypted".getBytes(StandardCharsets.UTF_8);
        raw.put(blobKey, legacy, "text/plain");

        EncryptingBlobStore strict = new EncryptingBlobStore(raw, key, 4096, false);
        assertThrows(IOException.class, () -> strict.get(blobKey),
                "Strict mode must fail closed on unencrypted blobs");

        EncryptingBlobStore migration = new EncryptingBlobStore(raw, key, 4096, true);
        assertArrayEquals(legacy, migration.get(blobKey).orElseThrow());
    }

    @Test
    public void testGivenExactMaxPlaintextBytesWhenPutThenSucceedsWithEnvelopeOverhead() throws Exception {
        int maxPlain = 256;
        LocalBlobStore raw = new LocalBlobStore(tempDir, maxPlain + EncryptingBlobStore.ENVELOPE_OVERHEAD_BYTES);
        EncryptingBlobStore store = new EncryptingBlobStore(raw, randomKey(), maxPlain, false);

        String key = BlobKeys.of("shared/2026/11/19", 9L, null, "55555555-5555-5555-5555-555555555555");
        byte[] exact = new byte[maxPlain];
        Arrays.fill(exact, (byte) 'A');
        store.put(key, exact, "text/plain");
        assertArrayEquals(exact, store.get(key).orElseThrow());

        byte[] tooBig = new byte[maxPlain + 1];
        assertThrows(IOException.class, () -> store.put(key, tooBig, "text/plain"));
        assertThrows(IOException.class, () -> store.put(key, new ByteArrayInputStream(tooBig), -1, "text/plain"));
    }

    @Test
    public void testGivenKeyParsingAndKeyFileProvisioningThenStableAcrossRestarts() throws Exception {
        byte[] raw = randomKey();
        assertArrayEquals(raw, EncryptingBlobStore.parseKey(HexFormat.of().formatHex(raw)));
        assertArrayEquals(raw, EncryptingBlobStore.parseKey(Base64.getEncoder().encodeToString(raw)));
        assertArrayEquals(raw, EncryptingBlobStore.parseKey(Base64.getUrlEncoder().withoutPadding().encodeToString(raw)));
        assertThrows(IllegalArgumentException.class, () -> EncryptingBlobStore.parseKey("short"));

        Path keyFile = tempDir.resolve("keys").resolve(".blob.key");
        byte[] created = EncryptingBlobStore.loadOrCreateKeyFile(keyFile);
        assertEquals(32, created.length);
        assertTrue(Files.isRegularFile(keyFile));

        byte[] reloaded = EncryptingBlobStore.loadOrCreateKeyFile(keyFile);
        assertArrayEquals(created, reloaded);
    }
}
