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

import java.util.List;
import jakarta.annotation.PreDestroy;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import smile.chat.BlobStorageConfig;

/**
 * Application-scoped {@link BlobStore} selected from {@link BlobStorageConfig}.
 *
 * @author Haifeng Li
 */
@Startup
@ApplicationScoped
public class ConfigurableBlobStore implements BlobStore {
    private static final Logger logger = Logger.getLogger(ConfigurableBlobStore.class);

    private final BlobStore delegate;
    private final AutoCloseable closeable;

    /**
     * @param config    blob storage configuration.
     * @param providers all backends on the classpath (built-in plus plugins).
     */
    @Inject
    public ConfigurableBlobStore(BlobStorageConfig config, @All List<BlobStoreProvider> providers) {
        String backend = config.storage().trim();
        BlobStoreProvider provider = providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(backend))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "smile.blob.storage=" + backend + " has no provider on the classpath; available: "
                                + providers.stream().map(BlobStoreProvider::name).sorted().toList()));
        this.delegate = provider.create();
        this.closeable = delegate instanceof AutoCloseable c ? c : null;
        logger.infof("Blob storage backend: %s", provider.name());
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
