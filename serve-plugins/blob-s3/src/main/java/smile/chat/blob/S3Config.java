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

import java.util.Optional;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * S3 backend configuration ({@code smile.blob.s3.*}), used when
 * {@code smile.blob.storage=s3}.
 *
 * @author Haifeng Li
 */
@ConfigMapping(prefix = "smile.blob.s3")
public interface S3Config {
    /** S3 bucket name (required). */
    Optional<String> bucket();

    /** AWS region (required). */
    Optional<String> region();

    /**
     * Optional custom endpoint (MinIO / S3-compatible). When unset, the AWS
     * regional endpoint is used.
     */
    Optional<String> endpoint();

    /** Optional static access key; when unset, the default AWS credential chain is used. */
    @WithName("access-key")
    Optional<String> accessKey();

    /** Optional static secret key paired with {@link #accessKey()}. */
    @WithName("secret-key")
    Optional<String> secretKey();
}
