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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Filesystem {@link BlobStore} under a configurable root directory.
 *
 * @author Haifeng Li
 */
public final class LocalBlobStore implements BlobStore {
    private static final Logger logger = Logger.getLogger(LocalBlobStore.class);

    private final Path root;

    /**
     * @param rootDir absolute or relative root for blob files.
     */
    public LocalBlobStore(Path rootDir) {
        this.root = rootDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create blob root: " + this.root, e);
        }
        logger.infof("Local blob store root=%s", this.root);
    }

    /** @return absolute blob root directory. */
    public Path root() {
        return root;
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        Path path = resolve(key);
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    }

    @Override
    public void put(String key, InputStream data, long length, String contentType) throws IOException {
        Path path = resolve(key);
        Files.createDirectories(path.getParent());
        Files.copy(data, path, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Optional<byte[]> get(String key) throws IOException {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(path));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public void deletePrefix(String prefix) throws IOException {
        if (prefix == null || prefix.isBlank() || prefix.contains("..")) {
            throw new IOException("Invalid blob prefix: " + prefix);
        }
        String normalized = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        Path dir = root.resolve(normalized).normalize();
        if (!dir.startsWith(root)) {
            throw new IOException("Blob prefix escapes root: " + prefix);
        }
        if (Files.isDirectory(dir)) {
            deleteTree(dir);
        }
    }

    private Path resolve(String key) throws IOException {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw new IOException("Invalid blob key: " + key);
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IOException("Blob key escapes root: " + key);
        }
        return path;
    }

    private static void deleteTree(Path dir) throws IOException {
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
