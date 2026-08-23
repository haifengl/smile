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
package smile.chat;

import java.util.Optional;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Multimedia blob storage configuration ({@code smile.blob.*}).
 *
 * <p>Default backend is local filesystem under {@link #localPath()}. Set
 * {@link #storage()} to {@code s3} and provide bucket/region (and optional
 * endpoint / static credentials) for S3-compatible object storage.
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.blob")
public interface BlobStorageConfig {
    /**
     * Storage backend: {@code local} (default) or {@code s3}.
     */
    @WithDefault("local")
    String storage();

    /**
     * Root directory for the local backend (mirrored object key paths).
     */
    @WithDefault("/data/blob")
    @WithName("local.path")
    String localPath();

    /** S3 bucket name (required when {@link #storage()} is {@code s3}). */
    @WithName("s3.bucket")
    Optional<String> s3Bucket();

    /** AWS region (required when {@link #storage()} is {@code s3}). */
    @WithName("s3.region")
    Optional<String> s3Region();

    /**
     * Optional custom endpoint (MinIO / S3-compatible). When unset, the AWS
     * regional endpoint is used.
     */
    @WithName("s3.endpoint")
    Optional<String> s3Endpoint();

    /** Optional static access key; when unset, the default AWS credential chain is used. */
    @WithName("s3.access-key")
    Optional<String> s3AccessKey();

    /** Optional static secret key paired with {@link #s3AccessKey()}. */
    @WithName("s3.secret-key")
    Optional<String> s3SecretKey();

    /**
     * Maximum upload size in bytes (default 20 MiB).
     */
    @WithDefault("20971520")
    @WithName("max-upload-bytes")
    long maxUploadBytes();
}
