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

