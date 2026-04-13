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

