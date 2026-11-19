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

import java.time.Instant;

/**
 * Immutable security and lifecycle audit event emitted for multimedia attachment
 * operations (metadata only — never carries raw attachment bytes or user emails).
 *
 * @param timestamp      UTC timestamp when the event occurred.
 * @param action         attachment operation or security event kind.
 * @param namespace      opaque storage namespace ({@code users/<provider>-<24hex>} or {@code shared/<YYYY>/<MM>/<DD>}).
 * @param ownerUserId    owning user id when applicable, or {@code null}.
 * @param conversationId parent conversation id when applicable, or {@code null}.
 * @param contentId      opaque attachment UUID when applicable, or {@code null}.
 * @param mimeType       verified MIME type when applicable, or {@code null}.
 * @param sizeBytes      attachment size in bytes ({@code 0} when not applicable).
 * @param sha256         SHA-256 hex digest of stored bytes when applicable, or {@code null}.
 * @param success        {@code true} when permitted/succeeded; {@code false} when blocked or failed.
 * @param reason         optional short diagnostic or security rejection reason.
 * @author Haifeng Li
 */
public record BlobAuditEvent(
        Instant timestamp,
        Action action,
        String namespace,
        Long ownerUserId,
        Long conversationId,
        String contentId,
        String mimeType,
        long sizeBytes,
        String sha256,
        boolean success,
        String reason
) {
    /**
     * Attachment security and lifecycle event kinds.
     */
    public enum Action {
        /** Attachment uploaded and committed to blob storage. */
        UPLOAD,
        /** Attachment bytes served or materialized for an authorized caller. */
        DOWNLOAD,
        /** Attachment deleted as part of conversation deletion. */
        DELETE,
        /** Expired pending or shared attachment purged by the retention sweeper. */
        SWEEP_DELETE,
        /** Unauthorized caller attempted to access another user's attachment. */
        ACCESS_DENIED,
        /** Upload blocked by a {@link BlobContentScanner} or media safety check. */
        SCAN_REJECTED,
        /** Read-time SHA-256 digest mismatch indicating out-of-band storage tampering. */
        INTEGRITY_FAILURE,
        /** Upload rejected because per-user or daily shared quota was exceeded. */
        QUOTA_EXCEEDED,
        /** Guest upload rejected by per-IP rate limiting. */
        RATE_LIMITED
    }
}
