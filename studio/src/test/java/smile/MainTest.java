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
package smile;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import smile.Main;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Main} entry point routing.
 *
 * @author Haifeng Li
 */
public class MainTest {

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outCapture;
    private ByteArrayOutputStream errCapture;

    @BeforeAll
    public static void setUpClass() {
        // Ensure smile.home points to a valid location so path normalization works.
        if (System.getProperty("smile.home") == null) {
            System.setProperty("smile.home", ".");
        }
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
        originalOut = System.out;
        originalErr = System.err;
        outCapture = new ByteArrayOutputStream();
        errCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(errCapture));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ------------------------------------------------------------------
    // smile.home normalization
    // ------------------------------------------------------------------

    @Test
    public void testSmileHomeIsNormalized() {
        System.out.println("smile.home normalization");
        // Set a path with redundant components.
        System.setProperty("smile.home", "./foo/../bar");
        Main.main(new String[]{"train", "--help"});
        String normalized = System.getProperty("smile.home");
        // The normalized path must not contain "..".
        assertFalse(normalized.contains(".."),
                "smile.home should be normalized, but was: " + normalized);
    }

    @Test
    public void testSmileHomeDefaultDoesNotContainTrailingSpace() {
        System.out.println("smile.home default has no trailing space");
        System.clearProperty("smile.home");
        Main.main(new String[]{"train", "--help"});
        String home = System.getProperty("smile.home");
        assertNotNull(home);
        // The bug ". " (dot-space) must be gone.
        assertFalse(home.endsWith(" "),
                "smile.home must not end with a space, but was: '" + home + "'");
    }

    // ------------------------------------------------------------------
    // CLI routing – verify picocli help exits cleanly (exit code 0).
    // ------------------------------------------------------------------

    @Test
    public void testTrainHelpExitsCleanly() {
        System.out.println("train --help exits cleanly");
        // picocli exits via System.exit; capture it with a SecurityManager isn't
        // practical here, but we at least verify no unexpected exception bubbles up.
        assertDoesNotThrow(() -> Main.main(new String[]{"train", "--help"}));
    }

    @Test
    public void testPredictHelpExitsCleanly() {
        System.out.println("predict --help exits cleanly");
        assertDoesNotThrow(() -> Main.main(new String[]{"predict", "--help"}));
    }

    @Test
    public void testServeHelpExitsCleanly() {
        System.out.println("serve --help exits cleanly");
        assertDoesNotThrow(() -> Main.main(new String[]{"serve", "--help"}));
    }

    // ------------------------------------------------------------------
    // smile.home system property is always set after Main.main()
    // ------------------------------------------------------------------

    @Test
    public void testSmileHomeSystemPropertyIsSet() {
        System.out.println("smile.home system property is set after main()");
        System.clearProperty("smile.home");
        Main.main(new String[]{"train", "--help"});
        assertNotNull(System.getProperty("smile.home"),
                "smile.home system property must be set by Main.main()");
    }

    // ------------------------------------------------------------------
    // Path.of normalization contract (unit-level, no process spawn)
    // ------------------------------------------------------------------

    @Test
    public void testPathNormalizationContract() {
        System.out.println("Path.of normalization contract");
        Path p = Path.of("./foo/../bar/../baz").normalize();
        // Normalized form must equal "baz" (relative) on all platforms.
        assertEquals("baz", p.toString());
    }
}

