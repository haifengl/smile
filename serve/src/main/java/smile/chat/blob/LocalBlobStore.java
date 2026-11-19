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
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Hardened filesystem {@link BlobStore} under a configurable root directory.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>Strict key and prefix validation via {@link BlobKeys}.</li>
 *   <li>POSIX {@code 0700} directories and {@code 0600} files when supported by the OS.</li>
 *   <li>Symlink escape rejection on every read, write, delete, and prefix purge.</li>
 *   <li>Atomic write-then-move to prevent partial or torn attachment reads.</li>
 *   <li>Bounded reads and streaming writes capped at {@code maxBytes}.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public final class LocalBlobStore implements BlobStore {
    private static final Logger logger = Logger.getLogger(LocalBlobStore.class);
    private static final long DEFAULT_MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<PosixFilePermission> DIR_PERMS = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMS = PosixFilePermissions.fromString("rw-------");

    private final Path root;
    private final Path realRoot;
    private final long maxBytes;
    private final boolean posixSupported;

    /**
     * @param rootDir absolute or relative root for blob files.
     */
    public LocalBlobStore(Path rootDir) {
        this(rootDir, DEFAULT_MAX_BYTES);
    }

    /**
     * @param rootDir  absolute or relative root for blob files.
     * @param maxBytes maximum permitted object size in bytes for reads and streaming writes.
     */
    public LocalBlobStore(Path rootDir, long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive, got: " + maxBytes);
        }
        this.root = rootDir.toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.posixSupported = this.root.getFileSystem().supportedFileAttributeViews().contains("posix");
        try {
            Files.createDirectories(this.root);
            applyPosixPermissions(this.root, DIR_PERMS);
            this.realRoot = this.root.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create blob root: " + this.root, e);
        }
        logger.infof("Local blob store root=%s maxBytes=%d", this.realRoot, this.maxBytes);
    }

    /** @return canonical absolute blob root directory. */
    public Path root() {
        return realRoot;
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        if (data == null) {
            throw new IOException("Blob data must not be null");
        }
        if (data.length > maxBytes) {
            throw new IOException("Blob size " + data.length + " exceeds maxBytes " + maxBytes);
        }
        Path path = resolveKey(key);
        Path parent = ensureSafeDirectories(path.getParent());
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Refusing to write through symbolic link: " + key);
        }
        Path tmp = createTempFile(parent);
        try {
            Files.write(tmp, data);
            applyPosixPermissions(tmp, FILE_PERMS);
            moveAtomically(tmp, path);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    @Override
    public void put(String key, InputStream data, long length, String contentType) throws IOException {
        if (data == null) {
            throw new IOException("Blob stream must not be null");
        }
        if (length > maxBytes) {
            throw new IOException("Declared blob length " + length + " exceeds maxBytes " + maxBytes);
        }
        Path path = resolveKey(key);
        Path parent = ensureSafeDirectories(path.getParent());
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Refusing to write through symbolic link: " + key);
        }
        Path tmp = createTempFile(parent);
        try {
            long written = 0;
            byte[] buf = new byte[8192];
            try (OutputStream out = Files.newOutputStream(tmp)) {
                int n;
                while ((n = data.read(buf)) != -1) {
                    written += n;
                    if (written > maxBytes) {
                        throw new IOException("Blob stream exceeds maxBytes " + maxBytes);
                    }
                    out.write(buf, 0, n);
                }
            }
            applyPosixPermissions(tmp, FILE_PERMS);
            moveAtomically(tmp, path);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    @Override
    public Optional<byte[]> get(String key) throws IOException {
        Path path = resolveKey(key);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to read non-regular or symlink blob: " + key);
        }
        Path realFile = path.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            throw new IOException("Blob real path escapes root: " + key);
        }
        long size = Files.size(realFile);
        if (size > maxBytes) {
            throw new IOException("Blob size " + size + " exceeds maxBytes " + maxBytes);
        }
        try (InputStream in = Files.newInputStream(realFile, LinkOption.NOFOLLOW_LINKS)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(size, 65536));
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("Blob read exceeds maxBytes " + maxBytes);
                }
                out.write(buf, 0, n);
            }
            return Optional.of(out.toByteArray());
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Path path = resolveKey(key);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        ensureAncestorsWithinRoot(path.getParent());
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Refusing to follow symbolic link on delete: " + key);
        }
        Files.deleteIfExists(path);
    }

    @Override
    public void deletePrefix(String prefix) throws IOException {
        String validated;
        try {
            validated = BlobKeys.validatePrefix(prefix);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        String normalized = validated.substring(0, validated.length() - 1);
        Path dir = realRoot.resolve(normalized).normalize();
        if (!dir.startsWith(realRoot) || dir.equals(realRoot)) {
            throw new IOException("Blob prefix escapes or equals root: " + prefix);
        }
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(dir) || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to delete non-directory or symlink prefix: " + prefix);
        }
        if (!dir.toRealPath().startsWith(realRoot)) {
            throw new IOException("Blob prefix real path escapes root: " + prefix);
        }
        deleteTree(dir);
    }

    private Path resolveKey(String key) throws IOException {
        String validated;
        try {
            validated = BlobKeys.validateKey(key);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        Path path = realRoot.resolve(validated).normalize();
        if (!path.startsWith(realRoot) || path.equals(realRoot)) {
            throw new IOException("Blob key escapes root: " + key);
        }
        return path;
    }

    private Path ensureSafeDirectories(Path targetDir) throws IOException {
        Path relative = realRoot.relativize(targetDir);
        Path current = realRoot;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing to traverse non-directory or symlink: " + part);
                }
            } else {
                Files.createDirectory(current);
                applyPosixPermissions(current, DIR_PERMS);
            }
        }
        Path realTarget = current.toRealPath();
        if (!realTarget.startsWith(realRoot)) {
            throw new IOException("Target directory escapes root");
        }
        return realTarget;
    }

    private void ensureAncestorsWithinRoot(Path dir) throws IOException {
        if (dir == null || dir.equals(realRoot)) {
            return;
        }
        Path relative = realRoot.relativize(dir);
        Path current = realRoot;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing to traverse symlink ancestor: " + part);
                }
            }
        }
        if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS) && !dir.toRealPath().startsWith(realRoot)) {
            throw new IOException("Directory ancestor escapes root");
        }
    }

    private Path createTempFile(Path parent) throws IOException {
        if (posixSupported) {
            return Files.createTempFile(parent, ".blob-", ".tmp",
                    PosixFilePermissions.asFileAttribute(FILE_PERMS));
        }
        return Files.createTempFile(parent, ".blob-", ".tmp");
    }

    private static void moveAtomically(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void applyPosixPermissions(Path path, Set<PosixFilePermission> perms) {
        if (!posixSupported) {
            return;
        }
        try {
            Files.setPosixFilePermissions(path, perms);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX mount (e.g. CIFS/NTFS mounted inside Linux container)
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        // Files.walk does not follow symbolic links by default.
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioe) {
                throw ioe;
            }
            throw e;
        }
    }
}
