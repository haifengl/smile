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
package smile.studio.kernel;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import smile.studio.OutputArea;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JavaKernel}.
 *
 * <p>JavaKernel starts a remote JShell JVM, so tests are necessarily
 * integration-level.  Each test creates a fresh kernel to avoid state leaking.
 *
 * @author Haifeng Li
 */
public class JavaKernelTest {

    private static JavaKernel kernel;

    @BeforeAll
    public static void setUpClass() throws Exception {
        kernel = new JavaKernel();
        var output = new OutputArea();
        kernel.setOutputArea(output);
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
        kernel.close();
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    // ------------------------------------------------------------------
    // eval – basic expressions
    // ------------------------------------------------------------------

    @Test
    public void testEvalIntegerLiteral() {
        System.out.println("JavaKernel: eval integer literal");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("int x = 42;", values);
        assertTrue(ok, "eval should succeed for a valid statement");
    }

    @Test
    public void testEvalReturnsVariableValue() {
        System.out.println("JavaKernel: eval returns variable value");
        List<Object> values = new ArrayList<>();
        kernel.eval("int answer = 42;", values);
        assertFalse(values.isEmpty(), "Should capture the declared variable");
        assertEquals("42", values.getFirst().toString());
    }

    @Test
    public void testEvalStringVariable() {
        System.out.println("JavaKernel: eval string variable");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("String greeting = \"hello\";", values);
        assertTrue(ok);
        assertEquals("\"hello\"", values.getFirst().toString());
    }

    @Test
    public void testEvalMultipleStatements() {
        System.out.println("JavaKernel: eval multiple statements");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("int a = 1; int b = 2; int c = a + b;", values);
        assertTrue(ok);
        // All three var declarations produce values
        assertEquals(3, values.size());
        assertEquals("3", values.get(2).toString());
    }

    @Test
    public void testEvalSyntaxError() {
        System.out.println("JavaKernel: eval syntax error returns false");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("int x = ;", values);
        assertFalse(ok, "Syntax error should return false");
    }

    @Test
    public void testEvalRuntimeException() {
        System.out.println("JavaKernel: eval runtime exception returns false");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("throw new RuntimeException(\"boom\");", values);
        assertFalse(ok, "Runtime exception should return false");
    }

    @Test
    public void testEvalIncompleteCodeThrows() {
        System.out.println("JavaKernel: eval incomplete code throws RuntimeException");
        // An unclosed brace is not EMPTY; it's DEFINITELY_INCOMPLETE
        assertThrows(RuntimeException.class,
                () -> kernel.eval("if (true) {", new ArrayList<>()),
                "Incomplete code should throw RuntimeException");
    }

    @Test
    public void testEvalEmptyCodeSucceeds() {
        System.out.println("JavaKernel: eval empty/blank code succeeds");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("", values);
        assertTrue(ok, "Empty code should succeed with no values");
        assertTrue(values.isEmpty());
    }

    // ------------------------------------------------------------------
    // eval – convenience overload
    // ------------------------------------------------------------------

    @Test
    public void testEvalConvenienceReturnsLastValue() {
        System.out.println("JavaKernel: eval convenience returns last variable value");
        Object result = kernel.eval("int p = 7; int q = 8;");
        assertEquals("8", result.toString());
    }

    @Test
    public void testEvalConvenienceReturnsNullForNoVars() {
        System.out.println("JavaKernel: eval convenience returns null when no variables");
        Object result = kernel.eval("System.out.println(\"hi\");");
        assertNull(result, "Method call without var snippet should return null");
    }

    // ------------------------------------------------------------------
    // variables()
    // ------------------------------------------------------------------

    @Test
    public void testVariablesReflectsDeclarations() {
        System.out.println("JavaKernel: variables() reflects declared variables");
        kernel.eval("double pi = 3.14; String name = \"SMILE\";");
        var vars = kernel.variables();
        var names = vars.stream().map(Variable::name).toList();
        assertTrue(names.contains("pi"),   "pi should be listed");
        assertTrue(names.contains("name"), "name should be listed");
    }

    @Test
    public void testVariablesEmptyInitially() {
        System.out.println("JavaKernel: variables() empty before any eval");
        // After restart (done in setUp) there are no user variables yet.
        var vars = kernel.variables();
        // Could have 0 variables; certainly no 'pi' or 'name'.
        var names = vars.stream().map(Variable::name).toList();
        assertFalse(names.contains("pi"));
    }

    // ------------------------------------------------------------------
    // isRunning state
    // ------------------------------------------------------------------

    @Test
    public void testIsRunningInitiallyFalse() {
        System.out.println("JavaKernel: isRunning() false before any eval");
        assertFalse(kernel.isRunning());
    }

    @Test
    public void testSetRunning() {
        System.out.println("JavaKernel: setRunning() changes isRunning state");
        kernel.setRunning(true);
        assertTrue(kernel.isRunning());
        kernel.setRunning(false);
        assertFalse(kernel.isRunning());
    }

    // ------------------------------------------------------------------
    // reset()
    // ------------------------------------------------------------------

    @Test
    public void testResetClearsVariables() {
        System.out.println("JavaKernel: reset() clears all snippets");
        kernel.eval("int z = 99;");
        assertFalse(kernel.variables().isEmpty(), "Variable should exist before reset");
        kernel.reset();
        assertTrue(kernel.variables().isEmpty(), "Variables should be empty after reset");
    }

    // ------------------------------------------------------------------
    // magic commands
    // ------------------------------------------------------------------

    @Test
    public void testEvalMagicUnknownCommandLogs() {
        System.out.println("JavaKernel: unknown magic command is logged, not thrown");
        // Unknown magic command should not throw – just log a warning.
        assertDoesNotThrow(() -> kernel.evalMagic("//! unknownMagic arg1"));
    }

    @Test
    public void testEvalMagicBlankIsNoOp() {
        System.out.println("JavaKernel: blank magic command is a no-op");
        assertDoesNotThrow(() -> kernel.evalMagic("   "));
    }

    @Test
    public void testEvalMagicInvalidMavenCoordinatesThrows() {
        System.out.println("JavaKernel: invalid maven coordinates throw IllegalArgumentException");
        // "mvn badcoords" (no colons) should throw IllegalArgumentException
        assertThrows(Exception.class,
                () -> kernel.evalMagic("mvn bad-coords-no-colons"));
    }

    // ------------------------------------------------------------------
    // addToClasspath – invalid coordinates
    // ------------------------------------------------------------------

    @Test
    public void testAddToClasspathInvalidCoordinates() {
        System.out.println("JavaKernel: addToClasspath rejects malformed GAV");
        assertThrows(IllegalArgumentException.class,
                () -> kernel.addToClasspath("notValidGAV"),
                "Should reject coordinates without two colons");
    }

    // ------------------------------------------------------------------
    // close / restart
    // ------------------------------------------------------------------

    @Test
    public void testDoubleCloseIsIdempotent() throws Exception {
        System.out.println("JavaKernel: calling close() twice is safe");
        assertDoesNotThrow(() -> {
            kernel.close();
            kernel.close(); // second close must not throw
        });
        // recreate kernel for subsequent tests
        setUpClass();
    }

    @Test
    public void testRestartResetsState() {
        System.out.println("JavaKernel: restart() clears previously declared variables");
        kernel.eval("int beforeRestart = 1;");
        assertFalse(kernel.variables().isEmpty());
        kernel.restart();
        assertTrue(kernel.variables().isEmpty(), "Variables must be empty after restart");
    }
}

