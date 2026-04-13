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

import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import picocli.CommandLine;
import smile.io.Read;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@code smile train} CLI command.
 *
 * <p>Each test:
 * <ol>
 *   <li>Writes a temporary model file.</li>
 *   <li>Invokes picocli's {@link CommandLine} on a {@link Train} instance.</li>
 *   <li>Asserts the exit code is 0 and the model file was created.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class TrainTest {

    private Path tempDir;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outCapture;
    private ByteArrayOutputStream errCapture;

    @BeforeAll
    public static void setUpClass() {
        MathEx.setSeed(19650218); // repeatable results
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("smile-train-test");
        originalOut = System.out;
        originalErr = System.err;
        outCapture = new ByteArrayOutputStream();
        errCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(errCapture));
    }

    @AfterEach
    public void tearDown() throws Exception {
        System.setOut(originalOut);
        System.setErr(originalErr);
        // Clean up temp files
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /**
     * Runs the train sub-command and returns the picocli exit code.
     */
    private int runTrain(String dataFile, String subCmd, String... extraArgs) throws Exception {
        Path modelFile = tempDir.resolve("model.sml");
        String[] baseArgs = new String[]{
            "-d", dataFile,
            "-m", modelFile.toAbsolutePath().toString()
        };
        // Merge base args + sub-command + extra args
        String[] fullArgs = new String[baseArgs.length + 1 + extraArgs.length];
        System.arraycopy(baseArgs, 0, fullArgs, 0, baseArgs.length);
        fullArgs[baseArgs.length] = subCmd;
        System.arraycopy(extraArgs, 0, fullArgs, baseArgs.length + 1, extraArgs.length);
        return new CommandLine(new Train()).execute(fullArgs);
    }

    private Path irisArff() {
        return Path.of("base/src/test/resources/data/weka/iris.arff");
    }

    private Path longleyArff() {
        return Path.of("base/src/test/resources/data/weka/regression/longley.arff");
    }

    // ------------------------------------------------------------------
    // Classification: iris data
    // ------------------------------------------------------------------

    @Test
    public void testTrainRandomForestClassification() throws Exception {
        System.out.println("Train Random Forest (classification)");
        Path modelFile = tempDir.resolve("rf.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "random-forest", "--trees", "50"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        assertTrue(Files.size(modelFile) > 0, "Model file should be non-empty");
        // Verify the serialized object is a ClassificationModel
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainCartClassification() throws Exception {
        System.out.println("Train CART (classification)");
        Path modelFile = tempDir.resolve("cart.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "cart", "--max-depth", "10"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainLogisticClassification() throws Exception {
        System.out.println("Train Logistic Regression (classification)");
        Path modelFile = tempDir.resolve("logistic.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "logistic", "--lambda", "1.0"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainLdaClassification() throws Exception {
        System.out.println("Train LDA (classification)");
        Path modelFile = tempDir.resolve("lda.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "lda"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainQdaClassification() throws Exception {
        System.out.println("Train QDA (classification)");
        Path modelFile = tempDir.resolve("qda.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "qda"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainRdaClassification() throws Exception {
        System.out.println("Train RDA (classification)");
        Path modelFile = tempDir.resolve("rda.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "rda", "--alpha", "0.5"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainAdaBoostClassification() throws Exception {
        System.out.println("Train AdaBoost (classification)");
        Path modelFile = tempDir.resolve("ada.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "ada-boost", "--trees", "50"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainGradientBoostClassification() throws Exception {
        System.out.println("Train Gradient Boost (classification)");
        Path modelFile = tempDir.resolve("gbt.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "gradient-boost", "--trees", "50", "--shrinkage", "0.1"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    // ------------------------------------------------------------------
    // Helper: train with captured picocli error output
    // ------------------------------------------------------------------

    /**
     * Runs picocli Train command, capturing its own error writer.
     * Returns [exitCode, stderrContent].
     */
    private Object[] runTrainWithCapture(String... args) {
        var errBuf = new ByteArrayOutputStream();
        var errWriter = new java.io.PrintWriter(errBuf);
        int exit = new CommandLine(new Train())
                .setErr(errWriter)
                .execute(args);
        errWriter.flush();
        return new Object[]{exit, errBuf.toString()};
    }

    @Test
    public void testTrainFisherClassification() throws Exception {
        System.out.println("Train Fisher (classification)");
        Path modelFile = tempDir.resolve("fisher.sml");
        Object[] result = runTrainWithCapture(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "fisher"
        );
        int exit = (int) result[0];
        String stderr = (String) result[1];
        System.out.println("Fisher exit=" + exit + " stderr=" + stderr);
        assertEquals(0, exit, "Fisher should succeed. stderr=" + stderr);
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainRbfClassification() throws Exception {
        System.out.println("Train RBF (classification)");
        Path modelFile = tempDir.resolve("rbf.sml");
        Object[] result = runTrainWithCapture(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "rbf", "--neurons", "10"
        );
        int exit = (int) result[0];
        String stderr = (String) result[1];
        System.out.println("RBF exit=" + exit + " stderr=" + stderr);
        assertEquals(0, exit, "RBF should succeed. stderr=" + stderr);
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    @Test
    public void testTrainSvmClassification() throws Exception {
        System.out.println("Train SVM (classification)");
        Path modelFile = tempDir.resolve("svm.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "svm", "--kernel", "Gaussian(1.0)", "-C", "10"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.ClassificationModel.class, obj);
    }

    // ------------------------------------------------------------------
    // Regression: Longley data
    // ------------------------------------------------------------------

    @Test
    public void testTrainOlsRegression() throws Exception {
        System.out.println("Train OLS (regression)");
        Path modelFile = tempDir.resolve("ols.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", longleyArff().toString(),
            "--formula", "employed ~ .",
            "-m", modelFile.toString(),
            "ols"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.RegressionModel.class, obj);
    }

    @Test
    public void testTrainRidgeRegression() throws Exception {
        System.out.println("Train Ridge Regression");
        Path modelFile = tempDir.resolve("ridge.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", longleyArff().toString(),
            "--formula", "employed ~ .",
            "-m", modelFile.toString(),
            "ridge", "--lambda", "0.1"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.RegressionModel.class, obj);
    }

    @Test
    public void testTrainLassoRegression() throws Exception {
        System.out.println("Train LASSO Regression");
        Path modelFile = tempDir.resolve("lasso.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", longleyArff().toString(),
            "--formula", "employed ~ .",
            "-m", modelFile.toString(),
            "lasso", "--lambda", "1.0"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.RegressionModel.class, obj);
    }

    @Test
    public void testTrainElasticNetRegression() throws Exception {
        System.out.println("Train Elastic Net Regression");
        Path modelFile = tempDir.resolve("en.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", longleyArff().toString(),
            "--formula", "employed ~ .",
            "-m", modelFile.toString(),
            "elastic-net", "--lambda1", "0.5", "--lambda2", "0.5"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.RegressionModel.class, obj);
    }

    @Test
    public void testTrainCartRegression() throws Exception {
        System.out.println("Train CART (regression)");
        Path modelFile = tempDir.resolve("cart-reg.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", longleyArff().toString(),
            "--formula", "employed ~ .",
            "-m", modelFile.toString(),
            "cart", "--regression", "--max-depth", "5"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.RegressionModel.class, obj);
    }

    @Test
    public void testTrainRandomForestRegression() throws Exception {
        System.out.println("Train Random Forest (regression)");
        Path modelFile = tempDir.resolve("rf-reg.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", longleyArff().toString(),
            "--formula", "employed ~ .",
            "-m", modelFile.toString(),
            "random-forest", "--regression", "--trees", "50"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.RegressionModel.class, obj);
    }

    @Test
    public void testTrainGradientBoostRegression() throws Exception {
        System.out.println("Train Gradient Boost (regression)");
        Path modelFile = tempDir.resolve("gbt-reg.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", longleyArff().toString(),
            "--formula", "employed ~ .",
            "-m", modelFile.toString(),
            "gradient-boost", "--regression", "--trees", "50", "--shrinkage", "0.1"
        );
        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        var obj = Read.object(modelFile);
        assertInstanceOf(smile.model.RegressionModel.class, obj);
    }

    // ------------------------------------------------------------------
    // Model metadata
    // ------------------------------------------------------------------

    @Test
    public void testTrainSetsModelIdAndVersion() throws Exception {
        System.out.println("Train sets model id and version");
        Path modelFile = tempDir.resolve("rf-meta.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "--model-id", "iris-rf",
            "--model-version", "1.0.0",
            "random-forest", "--trees", "10"
        );
        assertEquals(0, exit);
        var model = (smile.model.ClassificationModel) Read.object(modelFile);
        assertEquals("iris-rf", model.getTag(smile.model.Model.ID));
        assertEquals("1.0.0", model.getTag(smile.model.Model.VERSION));
    }

    // ------------------------------------------------------------------
    // Cross-validation
    // ------------------------------------------------------------------

    @Test
    public void testTrainKFoldCrossValidation() throws Exception {
        System.out.println("Train k-fold cross-validation");
        Path modelFile = tempDir.resolve("rf-cv.sml");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "-k", "5",
            "random-forest", "--trees", "50"
        );
        assertEquals(0, exit, "Expected exit code 0 with k-fold CV");
        assertTrue(Files.exists(modelFile), "Model file should be created");
        // Captured output should mention validation metrics.
        String out = outCapture.toString();
        assertTrue(out.contains("Validation metrics"),
                "Output should contain validation metrics for k-fold CV");
    }

    // ------------------------------------------------------------------
    // Shrinkage type fix (was int, now double)
    // ------------------------------------------------------------------

    @Test
    public void testGradientBoostAcceptsDecimalShrinkage() throws Exception {
        System.out.println("gradient-boost --shrinkage accepts decimal value (regression guard)");
        Path modelFile = tempDir.resolve("gbt-shrinkage.sml");
        // If shrinkage were still declared as int, picocli would reject "0.05".
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString(),
            "-m", modelFile.toString(),
            "gradient-boost", "--trees", "20", "--shrinkage", "0.05"
        );
        assertEquals(0, exit,
                "gradient-boost should accept fractional shrinkage; exit=" + exit
                + "\nerr=" + errCapture.toString());
    }

    // ------------------------------------------------------------------
    // Missing required options produce non-zero exit code
    // ------------------------------------------------------------------

    @Test
    public void testMissingModelFileProducesError() throws Exception {
        System.out.println("Missing --model file produces non-zero exit code");
        int exit = new CommandLine(new Train()).execute(
            "-d", irisArff().toString()
        );
        assertNotEquals(0, exit, "Should fail without --model");
    }

    @Test
    public void testMissingDataFileProducesError() throws Exception {
        System.out.println("Missing --data file produces non-zero exit code");
        Path modelFile = tempDir.resolve("should-not-exist.sml");
        int exit = new CommandLine(new Train()).execute(
            "-m", modelFile.toString()
        );
        assertNotEquals(0, exit, "Should fail without --data");
    }
}

