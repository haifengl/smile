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

    /**
     * Comma-separated allowlist of permitted attachment MIME types. Active web
     * formats ({@code text/html}, {@code image/svg+xml}, JavaScript, XML) are
     * excluded by default to prevent stored XSS.
     */
    @WithDefault("image/png,image/jpeg,image/webp,image/gif,video/mp4,video/webm,video/quicktime,audio/mpeg,audio/wav,audio/ogg,audio/mp4,audio/flac,audio/webm,application/pdf,text/plain,text/markdown,text/csv,application/json")
    @WithName("allowed-mime-types")
    String allowedMimeTypes();

    /**
     * Enables application-layer AES-256-GCM envelope encryption at rest with
     * key-bound AAD across whichever {@link smile.chat.blob.BlobStore} backend
     * is active.
     */
    @WithDefault("false")
    @WithName("encryption.enabled")
    boolean encryptionEnabled();

    /**
     * Optional inline 256-bit AES key encoded as Base64, Base64URL, or 64-char hex.
     * Falls back to {@code SMILE_BLOB_KEY} environment variable and then
     * {@link #encryptionKeyFile()}.
     */
    @WithName("encryption.key")
    Optional<String> encryptionKey();

    /**
     * Path to the 256-bit AES key file (auto-generated with {@code 0600} permissions
     * if absent on first boot). Defaults to {@code <local.path>/.blob.key} when
     * using the local backend.
     */
    @WithName("encryption.key-file")
    Optional<String> encryptionKeyFile();

    /**
     * When {@code true}, allows reading legacy unencrypted blobs that lack the
     * {@code SMILEBLB} header while still writing all new blobs encrypted.
     * Defaults to {@code false} (fail closed).
     */
    @WithDefault("false")
    @WithName("encryption.allow-plaintext-read")
    boolean encryptionAllowPlaintextRead();

    /**
     * Per-OAuth-user storage quota in bytes (default 1 GiB; {@code 0} disables).
     */
    @WithDefault("1073741824")
    @WithName("max-user-bytes")
    long maxUserBytes();

    /**
     * Daily shared-space ({@code shared/YYYY/MM/DD/}) storage quota in bytes
     * (default 512 MiB; {@code 0} disables).
     */
    @WithDefault("536870912")
    @WithName("shared.max-daily-bytes")
    long sharedMaxDailyBytes();

    /**
     * Maximum unauthenticated guest uploads per client IP per minute
     * (default 10; {@code 0} disables).
     */
    @WithDefault("10")
    @WithName("guest-uploads-per-minute")
    int guestUploadsPerMinute();

    /**
     * Retention TTL in hours for unlinked {@code pending/} uploads
     * ({@code messageId IS NULL}; default 24 hours; {@code 0} disables).
     */
    @WithDefault("24")
    @WithName("pending-ttl-hours")
    int pendingTtlHours();

    /**
     * Retention TTL in days for {@code shared/YYYY/MM/DD/} attachments
     * (default 7 days; {@code 0} disables).
     */
    @WithDefault("7")
    @WithName("shared-retention-days")
    int sharedRetentionDays();

    /**
     * Interval in minutes for the background retention &amp; orphan sweeper
     * (default 60 minutes; {@code 0} disables periodic background sweeping).
     */
    @WithDefault("60")
    @WithName("sweep-interval-minutes")
    int sweepIntervalMinutes();

    /**
     * When {@code true} (default), verifies {@code SHA-256(bytes)} against the
     * recorded {@code ConversationContent.sha256} on every read and fails closed
     * if out-of-band tampering or corruption is detected.
     */
    @WithDefault("true")
    @WithName("verify-checksum-on-read")
    boolean verifyChecksumOnRead();

    /**
     * Maximum decoded pixel count ({@code width * height}) permitted for image
     * attachments to prevent decompression-bomb OOMs (default 64 megapixels;
     * {@code 0} disables).
     */
    @WithDefault("67108864")
    @WithName("max-image-pixels")
    long maxImagePixels();

    /**
     * Maximum image width or height in pixels (default 16384; {@code 0} disables).
     */
    @WithDefault("16384")
    @WithName("max-image-dimension")
    int maxImageDimension();

    /**
     * When {@code true} (default), losslessly strips EXIF, GPS, XMP, IPTC, and
     * comment metadata segments/chunks from uploaded PNG, JPEG, and WebP images
     * without re-encoding pixel data.
     */
    @WithDefault("true")
    @WithName("strip-image-metadata")
    boolean stripImageMetadata();
}
