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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Provider for the {@code s3} backend.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class S3BlobStoreProvider implements BlobStoreProvider {
    private final S3Config config;

    /** @param config S3 settings. */
    @Inject
    public S3BlobStoreProvider(S3Config config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "s3";
    }

    @Override
    public BlobStore create() {
        return new S3BlobStore(config);
    }
}
