/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.kernel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import smile.studio.text.OutputArea;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link ScalaKernel}.
 *
 * <p>The kernel drives {@code scala-cli repl} as a subprocess, so these tests
 * are integration-level and require {@code scala-cli} on the {@code PATH}.
 * They are tagged {@code integration} because scala-cli may download the Scala
 * compiler on first use, which makes a cold run take minutes, and because the
 * binary is not part of the build — CI runs without it and excludes the tag.
 * When scala-cli is absent the tests also skip cleanly via
 * {@link org.junit.jupiter.api.Assumptions#assumeTrue(boolean)}, so a plain
 * local run does not fail just because the tool is missing. The process is
 * started once for the whole class, as startup is expensive.
 *
 * @author Haifeng Li
 */
@Tag("integration")
@Timeout(value = 20, unit = TimeUnit.MINUTES)
public class ScalaKernelTest {
    private static ScalaKernel kernel;
    private static OutputArea output;

    @BeforeAll
    public static void setUpClass() {
        assumeTrue(scalaCliAvailable(), "scala-cli is not installed; skipping ScalaKernel tests");
        output = new OutputArea();
        kernel = new ScalaKernel();
        kernel.setOutputArea(output);
    }

    @AfterAll
    public static void tearDownClass() {
        if (kernel != null) {
            kernel.close();
        }
    }

    @BeforeEach
    public void setUp() {
        if (kernel != null) {
            kernel.reset();
        }
    }

    /**
     * Evaluates the code and returns whether it succeeded.
     * @param code the code to evaluate.
     * @return true if the code evaluated without errors.
     */
    private boolean evalSucceeds(String code) {
        return kernel.eval(code, new ArrayList<>());
    }

    /**
     * Returns whether scala-cli can be launched.
     * @return true if scala-cli is on the PATH.
     */
    private static boolean scalaCliAvailable() {
        try {
            Process process = new ProcessBuilder("scala-cli", "version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(3, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // eval – basic expressions
    // ------------------------------------------------------------------

    @Test
    public void testEvalDeclaration() {
        System.out.println("ScalaKernel: eval a val declaration");
        List<Object> values = new ArrayList<>();
        assertTrue(kernel.eval("val x = 42", values), "eval should succeed for a valid declaration");
    }

    @Test
    public void testEvalExpression() {
        System.out.println("ScalaKernel: eval an expression");
        assertTrue(evalSucceeds("1 + 1"));
    }

    @Test
    public void testEvalMultipleLines() {
        System.out.println("ScalaKernel: eval a multi-line cell");
        // Every line of the cell must be evaluated, not just the first.
        assertTrue(evalSucceeds("val a = 1\nval b = 2\na + b"));
    }

    @Test
    public void testEvalMultiLineDefinition() {
        System.out.println("ScalaKernel: eval a multi-line class definition");
        assertTrue(evalSucceeds("class Foo {\n  def bar: Int = 41\n}"));
        assertTrue(evalSucceeds("Foo().bar"), "the class must be usable in a later cell");
    }

    @Test
    public void testEvalEmptyCodeSucceeds() {
        System.out.println("ScalaKernel: eval blank code succeeds");
        List<Object> values = new ArrayList<>();
        assertTrue(kernel.eval("   ", values));
        assertTrue(values.isEmpty());
    }

    // ------------------------------------------------------------------
    // state shared between cells
    // ------------------------------------------------------------------

    @Test
    public void testStateSharedAcrossEvals() {
        System.out.println("ScalaKernel: later cells see earlier declarations");
        assertTrue(evalSucceeds("val base = 10"));
        assertTrue(evalSucceeds("val derived = base + 5"));
    }

    @Test
    public void testImportAvailableToLaterCell() {
        System.out.println("ScalaKernel: an import is visible to later cells");
        assertTrue(evalSucceeds("import java.util.Locale"));
        assertTrue(evalSucceeds("Locale.US.toString()"));
    }

    // ------------------------------------------------------------------
    // SMILE classpath
    // ------------------------------------------------------------------

    @Test
    public void testSmileClassesAreResolvable() {
        System.out.println("ScalaKernel: the SMILE libraries are on the script classpath");
        // The kernel passes the application classpath to the REPL, so the
        // SMILE API must be visible to Scala scripts.
        assertTrue(evalSucceeds("import smile.math.MathEx"));
        assertTrue(evalSucceeds("MathEx.log2(8.0)"));
    }

    @Test
    public void testSmileDataFrameInScript() {
        System.out.println("ScalaKernel: a SMILE DataFrame can be created in a script");
        assertTrue(evalSucceeds("import smile.data.DataFrame"));
        assertTrue(evalSucceeds(
                "val df = DataFrame.of(Array(Array(1.0, 2.0), Array(3.0, 4.0)), \"x\", \"y\")"));
        assertTrue(evalSucceeds("df.nrow()"));
    }

    // ------------------------------------------------------------------
    // eval – errors
    // ------------------------------------------------------------------

    @Test
    public void testEvalUnresolvedReference() {
        System.out.println("ScalaKernel: an unresolved reference returns false");
        assertFalse(evalSucceeds("noSuchFunction()"));
    }

    @Test
    public void testEvalRuntimeException() {
        System.out.println("ScalaKernel: a runtime exception returns false");
        assertFalse(evalSucceeds("throw new RuntimeException(\"boom\")"));
    }

    @Test
    public void testRecoveryAfterError() {
        System.out.println("ScalaKernel: the session recovers after an error");
        assertFalse(evalSucceeds("noSuchFunction()"));
        assertTrue(evalSucceeds("1 + 1"), "the kernel should still evaluate after an error");
    }

    // ------------------------------------------------------------------
    // output
    // ------------------------------------------------------------------

    @Test
    public void testPrintlnCapturedInOutputArea() {
        System.out.println("ScalaKernel: println is captured in the output area");
        output.clear();
        assertTrue(evalSucceeds("println(\"hello from scala\")"));
        assertTrue(output.buffer().toString().contains("hello from scala"),
                "The printed text should be redirected to the output area");
    }

    @Test
    public void testOutputHasNoAnsiEscapes() {
        System.out.println("ScalaKernel: the output area has no ANSI escape codes");
        output.clear();
        evalSucceeds("println(\"plain text\")");
        // scala-cli emits ANSI colors on some diagnostics even with
        // --color never, so the kernel must strip them.
        assertFalse(output.buffer().toString().contains("\u001B["),
                "ANSI escape sequences must be stripped from the output");
    }

    // ------------------------------------------------------------------
    // variables()
    // ------------------------------------------------------------------

    @Test
    public void testVariablesReflectDeclarations() {
        System.out.println("ScalaKernel: variables() reflects declared variables");
        assertTrue(evalSucceeds("val pi = 3.14"));
        assertTrue(evalSucceeds("val greeting = \"SMILE\""));
        var names = kernel.variables().stream().map(Variable::name).toList();
        assertTrue(names.contains("pi"), "pi should be listed");
        assertTrue(names.contains("greeting"), "greeting should be listed");
    }

    @Test
    public void testVariablesExcludeSyntheticResults() {
        System.out.println("ScalaKernel: variables() excludes the synthetic res<N> fields");
        assertTrue(evalSucceeds("1 + 1"));
        var names = kernel.variables().stream().map(Variable::name).toList();
        assertTrue(names.stream().noneMatch(n -> n.startsWith("res")),
                "The synthetic res<N> fields must not be listed");
    }

    // ------------------------------------------------------------------
    // isRunning state
    // ------------------------------------------------------------------

    @Test
    public void testIsRunningInitiallyFalse() {
        System.out.println("ScalaKernel: isRunning() is false before any eval");
        assertFalse(kernel.isRunning());
    }

    @Test
    public void testSetRunning() {
        System.out.println("ScalaKernel: setRunning() changes isRunning state");
        kernel.setRunning(true);
        assertTrue(kernel.isRunning());
        kernel.setRunning(false);
        assertFalse(kernel.isRunning());
    }

    // ------------------------------------------------------------------
    // reset / restart / close
    // ------------------------------------------------------------------

    @Test
    public void testResetClearsVariables() {
        System.out.println("ScalaKernel: reset() clears all variables");
        assertTrue(evalSucceeds("val z = 99"));
        assertFalse(kernel.variables().isEmpty(), "Variable should exist before reset");
        kernel.reset();
        assertTrue(kernel.variables().isEmpty(), "Variables should be empty after reset");
    }

    @Test
    public void testRestartKeepsKernelUsable() {
        System.out.println("ScalaKernel: the kernel still evaluates after restart()");
        assertTrue(evalSucceeds("val beforeRestart = 1"));
        kernel.restart();
        assertTrue(evalSucceeds("1 + 1"));
    }

    @Test
    public void testDoubleCloseIsIdempotent() {
        System.out.println("ScalaKernel: calling close() twice is safe");
        assertDoesNotThrow(() -> {
            kernel.close();
            kernel.close();
        });
        // Recreate the kernel for the remaining tests.
        kernel = new ScalaKernel();
        kernel.setOutputArea(output);
    }

    @Test
    public void testStopDoesNotThrow() {
        System.out.println("ScalaKernel: stop() does not throw");
        assertDoesNotThrow(() -> kernel.stop());
    }
}
