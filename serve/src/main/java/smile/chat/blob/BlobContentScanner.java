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

import java.io.IOException;

/**
 * Pluggable pre-storage content scanner SPI for malware (ClamAV / ICAP) or DLP
 * inspection of multimedia attachment uploads.
 *
 * <p>All CDI beans implementing {@code BlobContentScanner} are invoked by
 * {@link smile.chat.MediaService} <em>before</em> attachment bytes are written to
 * {@link BlobStore}. Any scanner returning {@link ScanResult#clean() false} blocks
 * the upload with HTTP {@code 422 Unprocessable Entity}; scanner I/O failures fail
 * closed with HTTP {@code 503 Service Unavailable}.
 *
 * @author Haifeng Li
 */
public interface BlobContentScanner {

    /**
     * Metadata and payload presented to a scanner before storage commit.
     *
     * @param namespace      resolved storage namespace ({@code users/...} or {@code shared/...}).
     * @param ownerUserId    owning user id, or {@code null} for unauthenticated guests.
     * @param conversationId parent conversation primary key.
     * @param filename       sanitized filename.
     * @param mimeType       magic-byte verified MIME type.
     * @param data           sanitized payload bytes to be stored.
     */
    record ScanContext(
            String namespace,
            Long ownerUserId,
            long conversationId,
            String filename,
            String mimeType,
            byte[] data
    ) {}

    /**
     * Outcome returned by a content scanner.
     *
     * @param clean   {@code true} when the attachment is permitted.
     * @param verdict short machine-readable verdict (e.g. {@code "CLEAN"}, {@code "MALWARE"}, {@code "DLP_BLOCKED"}).
     * @param reason  optional human-readable reason (never echo sensitive payload bytes).
     */
    record ScanResult(boolean clean, String verdict, String reason) {
        private static final ScanResult OK = new ScanResult(true, "CLEAN", null);

        /** @return clean / permitted scan result. */
        public static ScanResult ok() {
            return OK;
        }

        /**
         * Creates a rejecting scan result.
         *
         * @param verdict short verdict code.
         * @param reason  reason for rejection.
         * @return rejecting result.
         */
        public static ScanResult rejected(String verdict, String reason) {
            String v = (verdict == null || verdict.isBlank()) ? "REJECTED" : verdict.trim();
            return new ScanResult(false, v, reason);
        }
    }

    /**
     * Scans an uploaded attachment before it is committed to blob storage.
     *
     * @param context upload metadata and sanitized bytes.
     * @return scan verdict.
     * @throws IOException if the scanning backend is unreachable (fail closed).
     */
    ScanResult scan(ScanContext context) throws IOException;
}
