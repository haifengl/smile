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
 * Tests for {@link VersionProvider}.
 *
 * @author Haifeng Li
 */
public class VersionProviderTest {

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

    @Test
    public void testGetVersionReturnsNonNull() {
        System.out.println("VersionProvider.getVersion() is non-null");
        var provider = new VersionProvider();
        String[] version = provider.getVersion();
        assertNotNull(version, "getVersion() must not return null");
        assertEquals(1, version.length, "getVersion() must return exactly one element");
    }

    @Test
    public void testGetVersionStartsWithSmile() {
        System.out.println("VersionProvider.getVersion() starts with SMILE");
        var provider = new VersionProvider();
        String ver = provider.getVersion()[0];
        assertTrue(ver.startsWith("SMILE "),
                "Version string must start with 'SMILE ', but was: " + ver);
    }

    @Test
    public void testGetVersionNullSafe() {
        System.out.println("VersionProvider is null-safe when implementation version is absent");
        // When running from compiled classes (not a JAR) getImplementationVersion() returns null.
        // The provider must still return a valid string instead of "SMILE null".
        var provider = new VersionProvider();
        String ver = provider.getVersion()[0];
        assertFalse(ver.contains("null"),
                "Version string must not contain literal 'null', but was: " + ver);
    }

    @Test
    public void testJShellVersionConstantNullSafe() {
        System.out.println("JShell.version constant used safely by VersionProvider");
        // JShell.version may be null when running outside a packaged JAR.
        // VersionProvider must handle this gracefully.
        var provider = new VersionProvider();
        String ver = provider.getVersion()[0];
        // Whether JShell.version is null or not, the result should always be "SMILE <something>".
        assertTrue(ver.length() > "SMILE ".length(),
                "Version string must have content after 'SMILE ', but was: " + ver);
    }
}

