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
package smile.shell;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JShell} constants and static accessors.
 *
 * <p>Starting an actual JShell session is an interactive operation and cannot
 * be done in a headless CI environment, so these tests focus on the constants
 * and the contract guarantees of the interface.
 *
 * @author Haifeng Li
 */
public class JShellTest {

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    // ------------------------------------------------------------------
    // Logo constant
    // ------------------------------------------------------------------

    @Test
    public void testLogoIsNonNull() {
        System.out.println("JShell.logo is non-null");
        assertNotNull(JShell.logo, "JShell.logo should not be null");
    }

    @Test
    public void testLogoIsNonEmpty() {
        System.out.println("JShell.logo is non-empty");
        assertFalse(JShell.logo.isBlank(), "JShell.logo should not be blank");
    }

    @Test
    public void testLogoContainsSmileAsciiArt() {
        System.out.println("JShell.logo contains ASCII art");
        // The logo text block contains the distinctive SMILE ASCII art characters.
        assertTrue(JShell.logo.contains("::"),
                "Logo should contain '::' ASCII art characters");
    }

    // ------------------------------------------------------------------
    // Version constant (may be null outside a JAR, must not throw)
    // ------------------------------------------------------------------

    @Test
    public void testVersionAccessDoesNotThrow() {
        System.out.println("JShell.version access does not throw");
        // Just accessing the constant must not throw, regardless of null/non-null.
        assertDoesNotThrow(() -> {
            @SuppressWarnings("unused")
            String v = JShell.version;
        });
    }

    @Test
    public void testVersionIsNullOrNonEmpty() {
        System.out.println("JShell.version is null or a non-empty string");
        String ver = JShell.version;
        if (ver != null) {
            assertFalse(ver.isBlank(),
                    "If non-null, JShell.version must not be blank");
        }
        // null is acceptable when running from compiled classes (not JAR).
    }

    // ------------------------------------------------------------------
    // VersionProvider uses JShell.version safely
    // ------------------------------------------------------------------

    @Test
    public void testVersionProviderHandlesNullJShellVersion() {
        System.out.println("VersionProvider produces non-null output even when JShell.version is null");
        // JShell.version is null in test environments (no Implementation-Version in MANIFEST).
        var provider = new VersionProvider();
        String[] result = provider.getVersion();
        assertNotNull(result);
        assertEquals(1, result.length);
        // Must not contain the literal string "null".
        assertFalse(result[0].contains("null"),
                "Version string must not contain literal 'null': " + result[0]);
    }
}

