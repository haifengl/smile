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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import smile.studio.text.OutputArea;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link KotlinKernel}.
 *
 * <p>The Kotlin scripting engine compiles snippets in-process, so these tests
 * are slower than plain unit tests. Each test creates a fresh kernel to avoid
 * state leaking between tests.
 *
 * @author Haifeng Li
 */
public class KotlinKernelTest {
    private KotlinKernel kernel;
    private OutputArea output;

    @BeforeEach
    public void setUp() {
        kernel = new KotlinKernel();
        output = new OutputArea();
        kernel.setOutputArea(output);
    }

    @AfterEach
    public void tearDown() {
        kernel.close();
    }

    // ------------------------------------------------------------------
    // eval – basic expressions
    // ------------------------------------------------------------------

    @Test
    public void testEvalDeclaration() {
        System.out.println("KotlinKernel: eval a val declaration");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("val x = 42", values);
        assertTrue(ok, "eval should succeed for a valid declaration");
        assertTrue(values.isEmpty(), "A declaration has no value");
    }

    @Test
    public void testEvalExpressionReturnsValue() {
        System.out.println("KotlinKernel: eval an expression returns its value");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("1 + 1", values);
        assertTrue(ok);
        assertEquals(1, values.size(), "An expression should yield a value");
        assertEquals(2, values.getFirst());
    }

    @Test
    public void testEvalExpressionType() {
        System.out.println("KotlinKernel: eval reports the result type");
        List<Object> values = new ArrayList<>();
        kernel.eval("\"hello\".length", values);
        assertEquals(1, values.size());
        assertEquals(5, values.getFirst());
    }

    @Test
    public void testEvalConvenienceReturnsLastValue() {
        System.out.println("KotlinKernel: eval convenience overload returns the last value");
        Object result = kernel.eval("2 * 21");
        assertEquals(42, result);
    }

    @Test
    public void testEvalConvenienceReturnsNullForDeclaration() {
        System.out.println("KotlinKernel: eval convenience overload returns null for a declaration");
        Object result = kernel.eval("val y = 1");
        assertNull(result, "A declaration should not yield a value");
    }

    @Test
    public void testEvalEmptyCodeSucceeds() {
        System.out.println("KotlinKernel: eval blank code succeeds with no values");
        List<Object> values = new ArrayList<>();
        boolean ok = kernel.eval("   ", values);
        assertTrue(ok);
        assertTrue(values.isEmpty());
    }

    // ------------------------------------------------------------------
    // state shared between snippets
    // ------------------------------------------------------------------

    @Test
    public void testStateSharedAcrossEvals() {
        System.out.println("KotlinKernel: later snippets see earlier declarations");
        kernel.eval("val base = 10");
        Object result = kernel.eval("base + 5");
        assertEquals(15, result);
    }

    @Test
    public void testImportAvailableToLaterSnippet() {
        System.out.println("KotlinKernel: an import is visible to later snippets");
        assertTrue(evalSucceeds("import java.util.Locale"));
        Object result = kernel.eval("Locale.US.toString()");
        assertEquals("en_US", result);
    }

    @Test
    public void testIsScalaClasspathEntry() {
        System.out.println("KotlinKernel: isScalaClasspathEntry identifies Scala entries");
        assertTrue(KotlinKernel.isScalaClasspathEntry("com.github.haifengl.smile-scala-6.3.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("smile-scala_3-6.3.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("smile-scala-6.3.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("C:\\code\\smile\\target\\out\\jvm\\scala-3.9.0\\smile-scala\\classes"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("C:/code/smile/scala/build/classes/scala/main"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("/home/user/smile/scala/bin/classes"));

        // Scala compiler & tooling jars
        assertTrue(KotlinKernel.isScalaClasspathEntry("org.scala-lang.scala3-compiler_3-3.9.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("org.scala-lang.scala3-repl_3-3.9.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("org.scala-lang.scala3-directives-parser_3-3.9.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("org.scala-lang.scala3-interfaces-3.9.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("org.scala-sbt.compiler-interface-1.12.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("org.scala-sbt.util-interface-1.11.5.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("org.scala-lang.tasty-core_3-3.9.0.jar"));
        assertTrue(KotlinKernel.isScalaClasspathEntry("org.scala-lang.modules.scala-asm-9.9.0-scala-1.jar"));

        // Kotlin compiler jars should not be filtered out by isScalaClasspathEntry
        assertFalse(KotlinKernel.isScalaClasspathEntry("org.jetbrains.kotlin.kotlin-compiler-embeddable-2.4.20.jar"));
        assertFalse(KotlinKernel.isScalaClasspathEntry("org.jetbrains.kotlin.kotlin-daemon-embeddable-2.4.20.jar"));
        assertFalse(KotlinKernel.isScalaClasspathEntry("org.jetbrains.kotlin.kotlin-scripting-compiler-embeddable-2.4.20.jar"));
        assertFalse(KotlinKernel.isScalaClasspathEntry("com.github.haifengl.smile-kotlin-6.3.0.jar"));
        assertFalse(KotlinKernel.isScalaClasspathEntry("smile-kotlin-6.3.0.jar"));
        assertFalse(KotlinKernel.isScalaClasspathEntry("kotlin-stdlib-2.4.20.jar"));
        assertFalse(KotlinKernel.isScalaClasspathEntry(null));
        assertFalse(KotlinKernel.isScalaClasspathEntry(""));
    }

    @Test
    public void testSmileClassesAreResolvable() {
        System.out.println("KotlinKernel: the SMILE libraries are on the script classpath");
        // The scripting engine derives its classpath from the hosting
        // application, so the SMILE API must be visible to the scripts.
        assertTrue(evalSucceeds("import smile.math.MathEx"));
        Object result = kernel.eval("MathEx.log2(8.0)");
        assertEquals(3.0, ((Number) result).doubleValue(), 1e-10);
    }

    @Test
    public void testSmileDataFrameInScript() {
        System.out.println("KotlinKernel: a SMILE DataFrame can be created in a script");
        output.clear();
        boolean ok = evalSucceeds("""
                import smile.data.DataFrame
                val df = DataFrame.of(
                    arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0), doubleArrayOf(5.0, 6.0)),
                    "x", "y"
                )""");
        assertTrue(ok, "Script failed:\n" + output.buffer());
        Object result = kernel.eval("df.nrow()");
        assertEquals(3, result);
    }

    @Test
    public void testSmileReadCsvInScript() {
        System.out.println("KotlinKernel: smile.read.csv can be invoked in a script");
        output.clear();
        boolean ok = evalSucceeds("""
                val df = smile.read.csv(smile.io.Paths.getTestData("classification/breastcancer.csv").toString())
                """);
        assertTrue(ok, "Script failed:\n" + output.buffer());
        Object result = kernel.eval("df.nrow()");
        assertEquals(569, result);
    }

    /**
     * The end-to-end scenario of a Kotlin notebook: load a dataset, fit a
     * model, and read the fit metrics, in the successive cells of a session.
     * This mirrors the shipped kotlin.ipynb.
     */
    @Test
    public void testNotebookWorkflow() {
        System.out.println("KotlinKernel: the end-to-end notebook workflow");

        // Cell 1: imports.
        output.clear();
        assertTrue(evalSucceeds("""
                import smile.data.formula.Formula
                import smile.io.Read
                import smile.classification.RandomForest
                import smile.io.Paths"""), "Cell 1 failed:\n" + output.buffer());

        // Cell 2: load the iris dataset.
        output.clear();
        assertTrue(evalSucceeds("val iris = Read.arff(Paths.getTestData(\"weka/iris.arff\"))"),
                "Cell 2 failed:\n" + output.buffer());
        assertEquals(150, kernel.eval("iris.nrow()"));

        // Cell 3: fit a random forest, using the iris declaration of cell 2.
        output.clear();
        assertTrue(evalSucceeds("val rf = RandomForest.fit(Formula.lhs(\"class\"), iris)"),
                "Cell 3 failed:\n" + output.buffer());
        Object accuracy = kernel.eval("rf.metrics().accuracy()");
        assertInstanceOf(Number.class, accuracy, "The model should expose its accuracy");
        assertTrue(((Number) accuracy).doubleValue() > 0.8, "Accuracy should be high for iris");

        // The model is visible to the kernel explorer like any other variable.
        var names = kernel.variables().stream().map(Variable::name).toList();
        assertTrue(names.contains("iris"), "iris should be listed");
        assertTrue(names.contains("rf"), "rf should be listed");
    }

    // ------------------------------------------------------------------
    // eval – errors
    // ------------------------------------------------------------------

    @Test
    public void testEvalUnresolvedReference() {
        System.out.println("KotlinKernel: an unresolved reference returns false");
        assertFalse(evalSucceeds("noSuchFunction()"));
    }

    @Test
    public void testEvalRuntimeException() {
        System.out.println("KotlinKernel: a runtime exception returns false");
        assertFalse(evalSucceeds("throw RuntimeException(\"boom\")"));
    }

    @Test
    public void testEvalSyntaxError() {
        System.out.println("KotlinKernel: a syntax error returns false");
        assertFalse(evalSucceeds("val bad = "));
    }

    @Test
    public void testRecoveryAfterError() {
        System.out.println("KotlinKernel: the session recovers after an error");
        assertFalse(evalSucceeds("throw RuntimeException(\"boom\")"));
        Object result = kernel.eval("1 + 2");
        assertEquals(3, result, "The kernel should still evaluate after an error");
    }

    // ------------------------------------------------------------------
    // output redirection
    // ------------------------------------------------------------------

    @Test
    public void testPrintlnCapturedInOutputArea() {
        System.out.println("KotlinKernel: println is captured in the output area");
        output.clear();
        assertTrue(evalSucceeds("println(\"hello from kotlin\")"));
        assertTrue(output.buffer().toString().contains("hello from kotlin"),
                "The printed text should be redirected to the output area");
    }

    @Test
    public void testSystemOutRestoredAfterEval() {
        System.out.println("KotlinKernel: System.out is restored after eval");
        var original = System.out;
        kernel.eval("println(\"captured\")");
        assertSame(original, System.out, "System.out must be restored after eval");
    }

    @Test
    public void testSystemOutRestoredAfterError() {
        System.out.println("KotlinKernel: System.out is restored after a failed eval");
        var original = System.out;
        kernel.eval("throw RuntimeException(\"boom\")");
        assertSame(original, System.out, "System.out must be restored even on failure");
    }

    // ------------------------------------------------------------------
    // variables()
    // ------------------------------------------------------------------

    @Test
    public void testVariablesReflectDeclarations() {
        System.out.println("KotlinKernel: variables() reflects declared variables");
        kernel.eval("val pi = 3.14");
        kernel.eval("val greeting = \"SMILE\"");
        var names = kernel.variables().stream().map(Variable::name).toList();
        assertTrue(names.contains("pi"), "pi should be listed");
        assertTrue(names.contains("greeting"), "greeting should be listed");
    }

    @Test
    public void testVariablesExcludeSyntheticResultFields() {
        System.out.println("KotlinKernel: variables() excludes the synthetic result fields");
        kernel.eval("1 + 1");
        var names = kernel.variables().stream().map(Variable::name).toList();
        assertTrue(names.stream().noneMatch(n -> n.startsWith("res")),
                "The synthetic res<N> fields must not be listed");
    }

    @Test
    public void testVariablesEmptyInitially() {
        System.out.println("KotlinKernel: variables() is empty before any eval");
        assertTrue(kernel.variables().isEmpty());
    }

    // ------------------------------------------------------------------
    // isRunning state
    // ------------------------------------------------------------------

    @Test
    public void testIsRunningInitiallyFalse() {
        System.out.println("KotlinKernel: isRunning() is false before any eval");
        assertFalse(kernel.isRunning());
    }

    @Test
    public void testSetRunning() {
        System.out.println("KotlinKernel: setRunning() changes isRunning state");
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
        System.out.println("KotlinKernel: reset() clears all variables");
        kernel.eval("val z = 99");
        assertFalse(kernel.variables().isEmpty(), "Variable should exist before reset");
        kernel.reset();
        assertTrue(kernel.variables().isEmpty(), "Variables should be empty after reset");
    }

    @Test
    public void testResetKeepsKernelUsable() {
        System.out.println("KotlinKernel: the kernel still evaluates after reset()");
        kernel.eval("val z = 99");
        kernel.reset();
        assertEquals(5, kernel.eval("2 + 3"));
    }

    @Test
    public void testRestartClearsState() {
        System.out.println("KotlinKernel: restart() clears previously declared variables");
        kernel.eval("val beforeRestart = 1");
        assertFalse(kernel.variables().isEmpty());
        kernel.restart();
        assertTrue(kernel.variables().isEmpty(), "Variables must be empty after restart");
        assertFalse(evalSucceeds("beforeRestart"), "The old declaration must be gone after restart");
    }

    @Test
    public void testDoubleCloseIsIdempotent() {
        System.out.println("KotlinKernel: calling close() twice is safe");
        assertDoesNotThrow(() -> {
            kernel.close();
            kernel.close();
        });
    }

    @Test
    public void testEvalAfterCloseFailsGracefully() {
        System.out.println("KotlinKernel: eval after close() fails without throwing");
        kernel.close();
        assertFalse(evalSucceeds("1 + 1"), "eval should report failure on a closed kernel");
        assertTrue(kernel.variables().isEmpty());
    }

    @Test
    public void testRestartAfterClose() {
        System.out.println("KotlinKernel: restart() revives a closed kernel");
        kernel.close();
        kernel.restart();
        assertEquals(7, kernel.eval("3 + 4"));
    }

    @Test
    public void testStopDoesNotThrow() {
        System.out.println("KotlinKernel: stop() is a no-op that does not throw");
        assertDoesNotThrow(() -> kernel.stop());
    }

    /**
     * Evaluates the code and returns whether it succeeded.
     * @param code the code to evaluate.
     * @return true if the code evaluated without errors.
     */
    private boolean evalSucceeds(String code) {
        return kernel.eval(code, new ArrayList<>());
    }
}
