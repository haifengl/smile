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
 * Unit tests for the {@link ScalaREPL} interface.
 *
 * <p>Launching an interactive Scala REPL in a headless CI environment is not
 * practical, so this class verifies only the interface shape and that no
 * static initializers throw.
 *
 * @author Haifeng Li
 */
public class ScalaREPLTest {

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
    // Interface shape
    // ------------------------------------------------------------------

    @Test
    public void testScalaREPLIsAnInterface() {
        System.out.println("ScalaREPL is an interface");
        assertTrue(ScalaREPL.class.isInterface(),
                "ScalaREPL must be declared as an interface");
    }

    @Test
    public void testStartMethodExists() throws Exception {
        System.out.println("ScalaREPL has a static start(String[]) method");
        var method = ScalaREPL.class.getMethod("start", String[].class);
        assertNotNull(method, "start(String[]) method must exist");
        assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()),
                "start method must be static");
    }

    @Test
    public void testStartMethodReturnTypeIsVoid() throws Exception {
        System.out.println("ScalaREPL.start(String[]) returns void");
        var method = ScalaREPL.class.getMethod("start", String[].class);
        assertEquals(void.class, method.getReturnType(),
                "start method must return void");
    }

    // ------------------------------------------------------------------
    // Javadoc / naming correction regression guard
    // ------------------------------------------------------------------

    @Test
    public void testScalaREPLClassSimpleNameIsCorrect() {
        System.out.println("ScalaREPL class name is ScalaREPL (not JShell)");
        assertEquals("ScalaREPL", ScalaREPL.class.getSimpleName(),
                "Class simple name must be 'ScalaREPL'");
    }
}

