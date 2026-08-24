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

import java.nio.file.Path;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import smile.chat.BlobStorageConfig;

/**
 * Application-scoped {@link BlobStore} selected from {@link BlobStorageConfig}.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class ConfigurableBlobStore implements BlobStore {
    private static final Logger logger = Logger.getLogger(ConfigurableBlobStore.class);

    private final BlobStore delegate;
    private final AutoCloseable closeable;

    /**
     * @param config blob storage configuration.
     */
    @Inject
    public ConfigurableBlobStore(BlobStorageConfig config) {
        String backend = config.storage() == null ? "local" : config.storage().trim();
        if ("s3".equalsIgnoreCase(backend)) {
            S3BlobStore s3 = new S3BlobStore(config);
            this.delegate = s3;
            this.closeable = s3;
        } else {
            if (!"local".equalsIgnoreCase(backend)) {
                logger.warnf("Unknown smile.blob.storage=%s; falling back to local", backend);
            }
            this.delegate = new LocalBlobStore(Path.of(config.localPath()));
            this.closeable = null;
        }
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws java.io.IOException {
        delegate.put(key, data, contentType);
    }

    @Override
    public void put(String key, java.io.InputStream data, long length, String contentType)
            throws java.io.IOException {
        delegate.put(key, data, length, contentType);
    }

    @Override
    public java.util.Optional<byte[]> get(String key) throws java.io.IOException {
        return delegate.get(key);
    }

    @Override
    public void delete(String key) throws java.io.IOException {
        delegate.delete(key);
    }

    @Override
    public void deletePrefix(String prefix) throws java.io.IOException {
        delegate.deletePrefix(prefix);
    }

    /** @return underlying store (for tests). */
    public BlobStore delegate() {
        return delegate;
    }

    @PreDestroy
    void shutdown() {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.warn("Error closing blob store", e);
            }
        }
    }
}
