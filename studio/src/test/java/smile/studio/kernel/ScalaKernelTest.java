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
package smile.studio.kernel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import smile.studio.text.OutputArea;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ScalaKernel}.
 *
 * <p>The kernel launches {@code dotty.tools.repl.Main} in a child JVM and drives
 * it over stdin/stdout, so these tests are integration-level: they spawn a real
 * Scala 3 REPL process and evaluate real Scala snippets. They are tagged
 * {@code integration} because starting the REPL is slow, and CI runs Gradle only
 * (Studio is an sbt module), so the tag keeps them off the default fast path.
 * The process is started once for the whole class, as startup is expensive.
 *
 * <p>Every snippet is hard-coded in this class as a text block rather than read
 * from a {@code .sc} example file. The examples under
 * {@code studio/src/universal/examples} are user-facing and may drift from what
 * these tests assert; inlining the snippets keeps the tests self-contained and
 * lets them use small synthetic data that finishes in seconds instead of the
 * minutes the full examples take.
 *
 * @author Haifeng Li
 */
public class ScalaKernelTest {
    /**
     * A t-SNE snippet on 200 synthetic points. It mirrors the shape of the
     * {@code tsne.sc} example (fit a model, then plot its coordinates) but uses
     * a small in-memory data set and the minimum 250 iterations, so it finishes
     * in seconds. It deliberately stops short of {@code canvas.window()}, which
     * would open a Swing window and hang a headless test JVM.
     */
    private static final String TSNE_SNIPPET = """
            import smile.manifold.*
            import smile.plot.swing.*
            val X = Array.tabulate(200, 10)((i, j) => math.sin(i * 0.1 + j) + math.cos(j * 0.3))
            val model = tsne(X, 2, 20, 200, 12, 250)
            val canvas = plot(model.coordinates(), '*')
            """;

    /**
     * A classification snippet on 200 synthetic two-class samples. It mirrors
     * the shape of the {@code usps.sc} example (read train/test frames, build a
     * formula, validate a random forest) but uses a tiny in-memory data set and
     * a 5-tree forest, so it finishes in seconds.
     */
    private static final String CLASSIFICATION_SNIPPET = """
            import smile.*
            import smile.data.*
            import smile.data.formula.*
            import smile.data.vector.*
            import smile.classification.*
            import smile.validation.*

            val rng = new java.util.Random(42)
            val n = 200
            val x = Array.tabulate(n, 4)((i, j) => rng.nextGaussian() + (if (i % 2 == 0) 1.0 else -1.0))
            val y = Array.tabulate(n)(i => i % 2)
            val zipTrain = DataFrame.of(x, "x1", "x2", "x3", "x4").merge(new DataFrame(new IntVector("class", y)))
            val zipTest = zipTrain
            val formula: Formula = "class" ~ "."
            val metrics = validate.classification(formula, zipTrain, zipTest) { (formula, data) =>
              randomForest(formula, data, ntrees = 5)
            }
            """;

    private static ScalaKernel kernel;
    private static OutputArea output;
    private static String originalSmileHome;

    @BeforeAll
    public static void setUpClass() {
        // The child REPL inherits smile.home from this JVM and resolves test
        // data through smile.io.Paths. Another test in the same forked JVM
        // (smile.MainTest) may have set smile.home to ".", which makes
        // Paths.getTestData resolve to ./data/... and the snippets fail with
        // NoSuchFileException. Point it at the real test resources first.
        originalSmileHome = System.getProperty("smile.home");
        Path resources = Path.of("base", "src", "test", "resources").toAbsolutePath();
        if (Files.isDirectory(resources.resolve("data"))) {
            System.setProperty("smile.home", resources.toString());
        }

        output = new OutputArea();
        kernel = new ScalaKernel();
        kernel.setOutputArea(output);
    }

    @AfterAll
    public static void tearDownClass() {
        if (kernel != null) {
            kernel.close();
        }
        if (originalSmileHome == null) {
            System.clearProperty("smile.home");
        } else {
            System.setProperty("smile.home", originalSmileHome);
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
     * Returns the names of the variables currently bound in the session.
     * @return the variable names.
     */
    private List<String> variableNames() {
        return kernel.variables().stream().map(Variable::name).toList();
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
    public void testIsKotlinClasspathEntry() {
        System.out.println("ScalaKernel: isKotlinClasspathEntry identifies Kotlin entries");
        assertTrue(ScalaKernel.isKotlinClasspathEntry("com.github.haifengl.smile-kotlin-6.3.0.jar"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("smile-kotlin-6.3.0.jar"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("C:\\code\\smile\\target\\out\\jvm\\u\\smile-kotlin\\classes"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("C:/code/smile/kotlin/build/classes/kotlin/main"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("/home/user/smile/kotlin/bin/classes"));

        // Kotlin compiler & tooling jars
        assertTrue(ScalaKernel.isKotlinClasspathEntry("org.jetbrains.kotlin.kotlin-compiler-embeddable-2.4.20.jar"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("org.jetbrains.kotlin.kotlin-daemon-embeddable-2.4.20.jar"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("org.jetbrains.kotlin.kotlin-scripting-compiler-embeddable-2.4.20.jar"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("org.jetbrains.kotlin.kotlin-scripting-compiler-impl-embeddable-2.4.20.jar"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("org.jetbrains.kotlin.kotlin-scripting-jvm-host-2.4.20.jar"));
        assertTrue(ScalaKernel.isKotlinClasspathEntry("org.jetbrains.kotlin.kotlin-build-tools-api-2.4.20.jar"));

        // Scala jars should not be filtered out by isKotlinClasspathEntry
        assertFalse(ScalaKernel.isKotlinClasspathEntry("com.github.haifengl.smile-scala-6.3.0.jar"));
        assertFalse(ScalaKernel.isKotlinClasspathEntry("smile-scala_3-6.3.0.jar"));
        assertFalse(ScalaKernel.isKotlinClasspathEntry("org.scala-lang.scala3-compiler_3-3.9.0.jar"));
        assertFalse(ScalaKernel.isKotlinClasspathEntry("scala3-library_3-3.9.0.jar"));
        assertFalse(ScalaKernel.isKotlinClasspathEntry("org.jetbrains.kotlin.kotlin-stdlib-2.4.20.jar"));
        assertFalse(ScalaKernel.isKotlinClasspathEntry("org.jetbrains.kotlin.kotlin-reflect-1.9.25.jar"));
        assertFalse(ScalaKernel.isKotlinClasspathEntry(null));
        assertFalse(ScalaKernel.isKotlinClasspathEntry(""));
    }

    @Test
    public void testSmileClassesAreResolvable() {
        System.out.println("ScalaKernel: the SMILE libraries are on the script classpath");
        // The kernel passes the application classpath to the REPL, so the
        // SMILE API must be visible to Scala snippets.
        assertTrue(evalSucceeds("import smile.math.MathEx"));
        assertTrue(evalSucceeds("MathEx.log2(8.0)"));
    }

    @Test
    public void testSmileDataFrameInScript() {
        System.out.println("ScalaKernel: a SMILE DataFrame can be created in a snippet");
        assertTrue(evalSucceeds("import smile.data.DataFrame"));
        assertTrue(evalSucceeds(
                "val df = DataFrame.of(Array(Array(1.0, 2.0), Array(3.0, 4.0)), \"x\", \"y\")"));
        assertTrue(evalSucceeds("df.nrow()"));
    }

    @Test
    public void testSmileReadCsvInScript() {
        System.out.println("ScalaKernel: smile.read.csv can be invoked in a snippet");
        assertTrue(evalSucceeds(
                "val df = smile.read.csv(smile.io.Paths.getTestData(\"mnist/mnist2500_X.txt\").toString, delimiter=\" \", header=false)"));
        assertTrue(evalSucceeds("df.nrow() == 2500"));
    }

    @Test
    @Tag("integration")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void testTsneSnippet() {
        System.out.println("ScalaKernel: run an inline t-SNE snippet");
        assertTrue(evalSucceeds(TSNE_SNIPPET), "the t-SNE snippet should evaluate without errors");
        var names = variableNames();
        assertTrue(names.contains("model"), "model variable should be bound");
        assertTrue(names.contains("canvas"), "canvas variable should be bound");
    }

    @Test
    @Tag("integration")
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void testClassificationSnippet() {
        System.out.println("ScalaKernel: run an inline classification snippet");
        assertTrue(evalSucceeds(CLASSIFICATION_SNIPPET), "the classification snippet should evaluate without errors");
        var names = variableNames();
        assertTrue(names.contains("zipTrain"), "zipTrain variable should be bound");
        assertTrue(names.contains("zipTest"), "zipTest variable should be bound");
        assertTrue(names.contains("metrics"), "metrics variable should be bound");
    }

    @Test
    public void testNoExtraEmptyLinesForImports() {
        System.out.println("ScalaKernel: imports produce no extra empty lines");
        output.clear();
        assertTrue(evalSucceeds("import smile.io.*\nimport smile.manifold.*"));
        assertEquals("", output.buffer().toString(), "Imports should not print empty lines");
    }

    // ------------------------------------------------------------------
    // eval – errors
    // ------------------------------------------------------------------

    @Test
    public void testDetectErrorHeuristic() {
        System.out.println("ScalaKernel: test error detection heuristic");
        // Lines starting with -- Error: --
        String replError = "-- Error: ----------------------------------------------------------------------\n"
                + "1 |val x: Int = \"string\"\n"
                + "  |             ^^^^^^^^\n"
                + "  |             Found:    (\"string\" : String)\n"
                + "  |             Required: Int\n"
                + "1 error found\n";
        assertEquals("the snippet failed to compile (-- Error: --)", kernel.detectError(replError));

        String literalErrorDash = "-- Error: -- something broke\n";
        assertEquals("the snippet failed to compile (-- Error: --)", kernel.detectError(literalErrorDash));

        // Diagnostics with error code
        String diagError = "-- [E006] Not Found Error: -----------------------------------------------------\n"
                + "1 |noSuchFunction()\n"
                + "1 error found\n";
        assertEquals("the snippet failed to compile (-- [E006])", kernel.detectError(diagError));

        // Compiler summary without header
        String summaryOnly = "2 errors found\n";
        assertEquals("the snippet failed to compile (compilation failed)", kernel.detectError(summaryOnly));

        // Warnings must NOT be treated as compilation errors
        String warningMsg = "-- Warning: --------------------------------------------------------------------\n"
                + "1 |val 1 = 2\n"
                + "1 warning found\n";
        assertNull(kernel.detectError(warningMsg));

        String warningCode = "-- [E030] Match case Unreachable Warning: -------------------------------------\n"
                + "1 |case _ =>\n";
        assertNull(kernel.detectError(warningCode));

        // Runtime errors with stack trace / elision
        String runtimeError = "java.lang.ArithmeticException: / by zero\n  ... 35 elided\n";
        assertEquals("java.lang.ArithmeticException: / by zero", kernel.detectError(runtimeError));

        String matchError = "scala.MatchError: 2 (of class java.lang.Integer)\n  ... 28 elided\n";
        assertEquals("scala.MatchError: 2 (of class java.lang.Integer)", kernel.detectError(matchError));

        // Normal output must not be detected as error
        assertNull(kernel.detectError("val x: Int = 42\n"));
        assertNull(kernel.detectError("Error: something failed\n"));
        assertNull(kernel.detectError("This is not an error\n"));
    }

    @Test
    public void testStopSendingCodeOnCompilationError() {
        System.out.println("ScalaKernel: stop sending following code on compilation error");
        output.clear();
        assertFalse(evalSucceeds("val beforeErr = 100\nnoSuchFunction()\nval afterErr = 200"));
        var names = variableNames();
        assertTrue(names.contains("beforeErr"), "Code before error should be evaluated");
        assertFalse(names.contains("afterErr"), "Code after error must not be evaluated");
        assertFalse(output.buffer().toString().contains("afterErr = 200"),
                "Following code must not produce output");
        assertTrue(evalSucceeds("beforeErr == 100"), "Variables declared before error should exist in REPL");
        assertFalse(evalSucceeds("afterErr"), "Variables declared after error must not exist in REPL");
    }

    @Test
    public void testStopSendingCodeOnRuntimeError() {
        System.out.println("ScalaKernel: stop sending following code on runtime error");
        output.clear();
        assertFalse(evalSucceeds("val beforeBoom = 300\n1 / 0\nval afterBoom = 400"));
        var names = variableNames();
        assertTrue(names.contains("beforeBoom"), "Code before error should be evaluated");
        assertFalse(names.contains("afterBoom"), "Code after error must not be evaluated");
        assertFalse(output.buffer().toString().contains("afterBoom = 400"),
                "Following code must not produce output");
        assertTrue(evalSucceeds("beforeBoom == 300"), "Variables declared before error should exist in REPL");
        assertFalse(evalSucceeds("afterBoom"), "Variables declared after error must not exist in REPL");
    }

    @Test
    public void testNormalOutputNotTreatedAsError() {
        System.out.println("ScalaKernel: output mentioning error is not treated as failure");
        assertTrue(evalSucceeds("println(\"Error: something failed\")"));
        assertTrue(evalSucceeds("println(\"Not an Exception\")"));
    }

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
        // The REPL may emit ANSI colors on some diagnostics even with
        // -color never, so the kernel must strip them.
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
        var names = variableNames();
        assertTrue(names.contains("pi"), "pi should be listed");
        assertTrue(names.contains("greeting"), "greeting should be listed");
    }

    @Test
    public void testVariablesExcludeSyntheticResults() {
        System.out.println("ScalaKernel: variables() excludes the synthetic res<N> fields");
        assertTrue(evalSucceeds("1 + 1"));
        var names = variableNames();
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
        // stop() writes a raw Ctrl-C byte to the REPL's stdin. When no snippet
        // is running that byte lingers in the input buffer and corrupts the
        // next line, so restart to hand the remaining tests a clean session.
        kernel.restart();
    }
}
