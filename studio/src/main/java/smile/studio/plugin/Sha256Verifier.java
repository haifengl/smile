/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Verifies the SHA-256 of a downloaded artifact against the digest a marketplace
 * entry pins. This is the integrity half of the trust model: an {@code archive}
 * source that declares {@code sha256} must match, and a mismatch aborts the
 * install rather than extracting unverified bytes.
 *
 * @author Haifeng Li
 */
final class Sha256Verifier {

    private Sha256Verifier() {
    }

    /**
     * Computes the SHA-256 of a file.
     * @param file the file to hash.
     * @return the lowercase hex digest.
     * @throws IOException if the file cannot be read.
     */
    static String hash(Path file) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream dis = new DigestInputStream(in, digest)) {
            while (dis.read(buffer) != -1) {
                // DigestInputStream updates the digest as it reads.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Verifies a file against an expected digest.
     *
     * @param file the file to check.
     * @param expected the expected hex digest, or null/blank to skip (no pin).
     * @return true when the digest matches or no pin was declared.
     * @throws IOException if the file cannot be read.
     */
    static boolean verify(Path file, String expected) throws IOException {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String normalized = expected.trim().toLowerCase(Locale.ROOT);
        return hash(file).equals(normalized);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandatory in every JVM; this cannot happen.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
