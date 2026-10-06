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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security negative tests: the trust policy and the path guard must refuse the
 * inputs that would let a plugin escape its sandbox or execute code (ADR-008).
 *
 * @author Haifeng Li
 */
public class TrustPolicyTest {

    @Test
    public void testCommandSourceIsRefused() {
        // Given
        PluginSource source = new PluginSource.Command("rm -rf /", 60, "copy");

        // When
        TrustPolicy.Decision decision = TrustPolicy.check(source);

        // Then
        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("command"));
    }

    @Test
    public void testArchiveRequiresHttps() {
        // Given
        PluginSource source = new PluginSource.Archive("http://example.com/x.zip", null);

        // When
        TrustPolicy.Decision decision = TrustPolicy.check(source);

        // Then
        assertFalse(decision.allowed());
    }

    @Test
    public void testArchiveRejectsMetadataHost() {
        // Given
        PluginSource source = new PluginSource.Archive("https://169.254.169.254/x.zip", null);

        // When
        TrustPolicy.Decision decision = TrustPolicy.check(source);

        // Then
        assertFalse(decision.allowed());
    }

    @Test
    public void testOrdinarySourcesAreAllowed() {
        // Then: a relative path and a legitimate archive are allowed.
        assertTrue(TrustPolicy.check(new PluginSource.RelativePath("./plugins/x")).allowed());
        assertTrue(TrustPolicy.check(
                new PluginSource.Archive("https://example.com/x.zip", null)).allowed());
    }

    @Test
    public void testPathGuardRejectsTraversal(@TempDir Path root) {
        // Then: a .. segment, an absolute path, and a drive path are all refused.
        assertThrows(SecurityException.class, () -> PathGuard.resolve(root, "../etc/passwd"));
        assertThrows(SecurityException.class, () -> PathGuard.resolve(root, "/etc/passwd"));
        assertThrows(SecurityException.class, () -> PathGuard.resolve(root, "C:/Windows/system32"));
    }

    @Test
    public void testPathGuardAcceptsNestedRelative(@TempDir Path root) {
        // Then
        Path resolved = PathGuard.resolve(root, "plugins/formatter");
        assertTrue(resolved.startsWith(root.normalize()));
    }

    @Test
    public void testSha256Verifies(@TempDir Path dir) throws IOException {
        // Given: a file whose digest we compute.
        Path file = dir.resolve("a.bin");
        Files.writeString(file, "hello plugin");
        String digest = Sha256Verifier.hash(file);

        // Then
        assertTrue(Sha256Verifier.verify(file, digest));
        assertTrue(Sha256Verifier.verify(file, digest.toUpperCase()));
        assertFalse(Sha256Verifier.verify(file, "00".repeat(32)));
        assertTrue(Sha256Verifier.verify(file, null));
    }

    @Test
    public void testPluginIdValidation() {
        // Then
        assertEquals("p@m", PluginId.parse("p@m").toString());
        assertThrows(IllegalArgumentException.class, () -> PluginId.parse("no-at-sign"));
        assertThrows(IllegalArgumentException.class, () -> new PluginId("../evil", "m"));
    }

    @Test
    public void testNamespacing() {
        // Given
        PluginId id = new PluginId("commit-commands", "claude-plugins-official");

        // Then
        assertEquals("commit-commands--commit", id.namespace("commit"));
        // A nested command path is flattened with -- and sanitized.
        assertEquals("my-plugin--db-migrate", new PluginId("my-plugin", "m").namespace("db/migrate"));
    }
}
