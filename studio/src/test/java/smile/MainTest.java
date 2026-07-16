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
}

