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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jboss.logging.Logger;
import smile.chat.BlobStorageConfig;

/**
 * {@link BlobStore} decorator providing application-layer {@code AES-256-GCM}
 * envelope encryption at rest with key-bound Additional Authenticated Data (AAD).
 *
 * <p>Wire format:
 * <pre>
 *   [ 8 bytes: ASCII "SMILEBLB" ]
 *   [ 1 byte : version 0x01 ]
 *   [ 12 bytes: random 96-bit GCM IV ]
 *   [ N + 16 bytes: AES-256-GCM ciphertext + 128-bit authentication tag ]
 * </pre>
 *
 * <p>The GCM AAD binds {@code MAGIC || VERSION || storageKey(UTF-8)}, preventing
 * ciphertext transplantation across users or object keys even if an attacker
 * has raw filesystem or bucket access.
 *
 * @author Haifeng Li
 */
public final class EncryptingBlobStore implements BlobStore, AutoCloseable {
    private static final Logger logger = Logger.getLogger(EncryptingBlobStore.class);

    /** Environment variable holding a 256-bit AES key (Base64, Base64URL, or 64-char hex). */
    public static final String ENV_BLOB_KEY = "SMILE_BLOB_KEY";

    /** Envelope magic bytes: {@code SMILEBLB}. */
    static final byte[] MAGIC = "SMILEBLB".getBytes(StandardCharsets.US_ASCII);
    static final byte VERSION_1 = 0x01;
    static final int IV_LENGTH = 12;
    static final int TAG_BITS = 128;
    static final int TAG_BYTES = TAG_BITS / 8;
    static final int HEADER_LENGTH = MAGIC.length + 1 + IV_LENGTH;

    /** Exact ciphertext expansion in bytes (header + IV + GCM tag). */
    public static final int ENVELOPE_OVERHEAD_BYTES = HEADER_LENGTH + TAG_BYTES;

    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final String ENCRYPTED_CONTENT_TYPE = "application/octet-stream";
    private static final int AES_256_BYTES = 32;

    private static final Set<PosixFilePermission> DIR_PERMS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> FILE_PERMS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final BlobStore delegate;
    private final SecretKey secretKey;
    private final long maxPlaintextBytes;
    private final boolean allowPlaintextRead;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Wraps {@code delegate} with AES-256-GCM encryption.
     *
     * @param delegate           underlying blob store.
     * @param rawKey             32-byte AES-256 key.
     * @param maxPlaintextBytes  maximum plaintext bytes per object ({@code <= 0} disables cap).
     * @param allowPlaintextRead when {@code true}, legacy unencrypted blobs without the
     *                           {@code SMILEBLB} header are returned verbatim on read.
     */
    public EncryptingBlobStore(BlobStore delegate,
                               byte[] rawKey,
                               long maxPlaintextBytes,
                               boolean allowPlaintextRead) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (rawKey == null || rawKey.length != AES_256_BYTES) {
            throw new IllegalArgumentException(
                    "Blob encryption key must be exactly 32 bytes (256 bits)");
        }
        this.secretKey = new SecretKeySpec(Arrays.copyOf(rawKey, AES_256_BYTES), "AES");
        this.maxPlaintextBytes = maxPlaintextBytes;
        this.allowPlaintextRead = allowPlaintextRead;
    }

    /**
     * Resolves or provisions a 256-bit AES key from {@link BlobStorageConfig} and
     * {@value #ENV_BLOB_KEY}.
     *
     * @param config blob storage configuration.
     * @return 32-byte AES-256 key.
     */
    public static byte[] resolveOrCreateKey(BlobStorageConfig config) {
        Optional<String> inline = config.encryptionKey().filter(s -> !s.isBlank());
        if (inline.isPresent()) {
            return parseKey(inline.get());
        }
        String env = System.getenv(ENV_BLOB_KEY);
        if (env != null && !env.isBlank()) {
            return parseKey(env);
        }
        Optional<String> configuredFile = config.encryptionKeyFile().filter(s -> !s.isBlank());
        Path keyPath;
        if (configuredFile.isPresent()) {
            keyPath = Path.of(configuredFile.get()).toAbsolutePath().normalize();
        } else if ("local".equalsIgnoreCase(config.storage().trim())) {
            keyPath = Path.of(config.localPath()).toAbsolutePath().normalize().resolve(".blob.key");
        } else {
            throw new IllegalStateException(
                    "smile.blob.encryption.enabled=true with storage=" + config.storage()
                            + " requires smile.blob.encryption.key, " + ENV_BLOB_KEY
                            + ", or smile.blob.encryption.key-file");
        }
        return loadOrCreateKeyFile(keyPath);
    }

    /**
     * Parses a 256-bit key encoded as 64-character hex, standard Base64, or Base64URL.
     *
     * @param encoded encoded key string.
     * @return 32-byte raw key.
     */
    public static byte[] parseKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("Blob encryption key must not be blank");
        }
        String trimmed = encoded.trim();
        byte[] raw;
        if (trimmed.length() == 64 && trimmed.matches("[0-9a-fA-F]{64}")) {
            raw = HexFormat.of().parseHex(trimmed);
        } else {
            try {
                raw = Base64.getDecoder().decode(trimmed);
            } catch (IllegalArgumentException e) {
                raw = Base64.getUrlDecoder().decode(trimmed);
            }
        }
        if (raw.length != AES_256_BYTES) {
            throw new IllegalArgumentException(
                    "Blob encryption key must decode to 32 bytes (256 bits), got " + raw.length);
        }
        return raw;
    }

    /**
     * Loads an existing key file or atomically generates a new 256-bit key file
     * with {@code 0600} permissions.
     *
     * @param keyPath target key file path.
     * @return 32-byte raw key.
     */
    static byte[] loadOrCreateKeyFile(Path keyPath) {
        try {
            if (Files.exists(keyPath, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(keyPath) || !Files.isRegularFile(keyPath, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("Blob key file must be a regular non-symlink file: " + keyPath);
                }
                String content = Files.readString(keyPath, StandardCharsets.UTF_8);
                return parseKey(content);
            }
            Path parent = keyPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                setPosixPermsQuietly(parent, DIR_PERMS);
            }
            byte[] key = new byte[AES_256_BYTES];
            new SecureRandom().nextBytes(key);
            String encoded = Base64.getEncoder().encodeToString(key) + "\n";

            Path tmp = Files.createTempFile(parent != null ? parent : Path.of("."), ".blob-key-", ".tmp");
            try {
                setPosixPermsQuietly(tmp, FILE_PERMS);
                Files.writeString(tmp, encoded, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, keyPath, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, keyPath);
                }
                setPosixPermsQuietly(keyPath, FILE_PERMS);
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                // Handle concurrent first-boot race cleanly
                if (Files.isRegularFile(keyPath, LinkOption.NOFOLLOW_LINKS)) {
                    return parseKey(Files.readString(keyPath, StandardCharsets.UTF_8));
                }
                throw e;
            }
            logger.infof("Generated new AES-256 blob encryption key at %s", keyPath);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load or create blob encryption key: " + keyPath, e);
        }
    }

    /** @return wrapped underlying blob store. */
    public BlobStore delegate() {
        return delegate;
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        BlobKeys.validateKey(key);
        if (data == null) {
            throw new IOException("Blob data must not be null");
        }
        if (maxPlaintextBytes > 0 && data.length > maxPlaintextBytes) {
            throw new IOException("Blob exceeds max size of " + maxPlaintextBytes + " bytes");
        }
        byte[] encrypted = encrypt(key, data);
        delegate.put(key, encrypted, ENCRYPTED_CONTENT_TYPE);
    }

    @Override
    public void put(String key, InputStream data, long length, String contentType) throws IOException {
        BlobKeys.validateKey(key);
        if (data == null) {
            throw new IOException("Blob input stream must not be null");
        }
        if (maxPlaintextBytes > 0 && length > maxPlaintextBytes) {
            throw new IOException("Blob exceeds max size of " + maxPlaintextBytes + " bytes");
        }
        byte[] plaintext = readBounded(data, maxPlaintextBytes);
        byte[] encrypted = encrypt(key, plaintext);
        delegate.put(key, encrypted, ENCRYPTED_CONTENT_TYPE);
    }

    @Override
    public Optional<byte[]> get(String key) throws IOException {
        BlobKeys.validateKey(key);
        Optional<byte[]> stored = delegate.get(key);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        byte[] raw = stored.get();
        if (!hasEnvelopeHeader(raw)) {
            if (allowPlaintextRead) {
                if (maxPlaintextBytes > 0 && raw.length > maxPlaintextBytes) {
                    throw new IOException("Blob exceeds max size of " + maxPlaintextBytes + " bytes");
                }
                return Optional.of(raw);
            }
            throw new IOException("Stored blob is not encrypted with SMILEBLB envelope: " + key);
        }
        byte[] plaintext = decrypt(key, raw);
        if (maxPlaintextBytes > 0 && plaintext.length > maxPlaintextBytes) {
            throw new IOException("Decrypted blob exceeds max size of " + maxPlaintextBytes + " bytes");
        }
        return Optional.of(plaintext);
    }

    @Override
    public void delete(String key) throws IOException {
        BlobKeys.validateKey(key);
        delegate.delete(key);
    }

    @Override
    public void deletePrefix(String prefix) throws IOException {
        BlobKeys.validatePrefix(prefix);
        delegate.deletePrefix(prefix);
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable c) {
            c.close();
        }
    }

    private byte[] encrypt(String key, byte[] plaintext) throws IOException {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(buildAad(key));
            byte[] cipherAndTag = cipher.doFinal(plaintext);

            byte[] out = new byte[HEADER_LENGTH + cipherAndTag.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            out[MAGIC.length] = VERSION_1;
            System.arraycopy(iv, 0, out, MAGIC.length + 1, IV_LENGTH);
            System.arraycopy(cipherAndTag, 0, out, HEADER_LENGTH, cipherAndTag.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to encrypt blob key=" + key, e);
        }
    }

    private byte[] decrypt(String key, byte[] envelope) throws IOException {
        if (envelope.length < ENVELOPE_OVERHEAD_BYTES) {
            throw new IOException("Corrupt encrypted blob (truncated envelope): " + key);
        }
        byte version = envelope[MAGIC.length];
        if (version != VERSION_1) {
            throw new IOException("Unsupported encrypted blob version " + version + " for key=" + key);
        }
        byte[] iv = Arrays.copyOfRange(envelope, MAGIC.length + 1, HEADER_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(buildAad(key));
            return cipher.doFinal(envelope, HEADER_LENGTH, envelope.length - HEADER_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IOException("Blob decryption or integrity verification failed for key=" + key, e);
        }
    }

    private static boolean hasEnvelopeHeader(byte[] data) {
        if (data.length < MAGIC.length + 1) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] buildAad(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] aad = new byte[MAGIC.length + 1 + keyBytes.length];
        System.arraycopy(MAGIC, 0, aad, 0, MAGIC.length);
        aad[MAGIC.length] = VERSION_1;
        System.arraycopy(keyBytes, 0, aad, MAGIC.length + 1, keyBytes.length);
        return aad;
    }

    private static byte[] readBounded(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (maxBytes > 0 && total > maxBytes) {
                throw new IOException("Blob stream exceeds max size of " + maxBytes + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void setPosixPermsQuietly(Path path, Set<PosixFilePermission> perms) {
        try {
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem (e.g. Windows)
        }
    }
}
