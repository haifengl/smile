/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
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

