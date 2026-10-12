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
package smile.chat;

import java.util.Optional;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Multimedia blob storage configuration ({@code smile.blob.*}).
 *
 * <p>Default backend is local filesystem under {@link #localPath()}. Other backends
 * (e.g. {@code s3}, configured under {@code smile.blob.s3.*}) are provided by
 * plugin jars on the classpath; see {@code serve-plugins}.
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.blob")
public interface BlobStorageConfig {
    /**
     * Storage backend: {@code local} (default) or the name of a plugin backend such as {@code s3}.
     */
    @WithDefault("local")
    String storage();

    /**
     * Root directory for the local backend (mirrored object key paths).
     */
    @WithDefault("/data/blob")
    @WithName("local.path")
    String localPath();

    /**
     * Maximum upload size in bytes (default 20 MiB).
     */
    @WithDefault("20971520")
    @WithName("max-upload-bytes")
    long maxUploadBytes();
}
