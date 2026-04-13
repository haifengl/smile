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
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the {@code smile predict} CLI command.
 *
 * <p>The strategy is:
 * <ol>
 *   <li>Train a model with {@link Train} (via picocli) to create a {@code .sml} file.</li>
 *   <li>Run {@link Predict} on the same data file and assert output lines.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class PredictTest {

    private Path tempDir;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outCapture;
    private ByteArrayOutputStream errCapture;

    /** ARFF file with the iris dataset (150 rows, "class" as response). */
    private static final Path IRIS_ARFF = Path.of("base/src/test/resources/data/weka/iris.arff");
    /** ARFF file for regression (Longley, 16 rows). */
    private static final Path LONGLEY_ARFF = Path.of("base/src/test/resources/data/weka/regression/longley.arff");

    @BeforeAll
    public static void setUpClass() {
        MathEx.setSeed(19650218);
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("smile-predict-test");
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
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    // ------------------------------------------------------------------
    // Helper: train a model and return its path
    // ------------------------------------------------------------------

    private Path trainClassifier(String... subCmdAndArgs) throws Exception {
        Path modelFile = tempDir.resolve("model.sml");
        String[] head = {"-d", IRIS_ARFF.toString(), "-m", modelFile.toString()};
        String[] full = new String[head.length + subCmdAndArgs.length];
        System.arraycopy(head, 0, full, 0, head.length);
        System.arraycopy(subCmdAndArgs, 0, full, head.length, subCmdAndArgs.length);
        int exit = new CommandLine(new Train()).execute(full);
        assertEquals(0, exit, "Training should succeed");
        return modelFile;
    }

    private Path trainRegressor(String... subCmdAndArgs) throws Exception {
        Path modelFile = tempDir.resolve("model.sml");
        String[] head = {"-d", LONGLEY_ARFF.toString(), "--formula", "employed ~ .", "-m", modelFile.toString()};
        String[] full = new String[head.length + subCmdAndArgs.length];
        System.arraycopy(head, 0, full, 0, head.length);
        System.arraycopy(subCmdAndArgs, 0, full, head.length, subCmdAndArgs.length);
        int exit = new CommandLine(new Train()).execute(full);
        assertEquals(0, exit, "Training should succeed");
        return modelFile;
    }

    // ------------------------------------------------------------------
    // Classification prediction
    // ------------------------------------------------------------------

    @Test
    public void testPredictClassificationProducesOneLabelPerRow() throws Exception {
        System.out.println("Predict classification: one label per input row");
        Path model = trainClassifier("random-forest", "--trees", "50");

        // Reset capture after training
        outCapture.reset();

        int exit = new CommandLine(new Predict()).execute(
            IRIS_ARFF.toString(), "--model", model.toString()
        );
        assertEquals(0, exit, "Expected exit code 0");

        String[] lines = outCapture.toString().trim().split("\\r?\\n");
        // iris.arff has 150 rows → 150 prediction lines
        assertEquals(150, lines.length,
                "Should produce exactly 150 prediction lines, got " + lines.length);
    }

    @Test
    public void testPredictClassificationLabelsAreNonEmpty() throws Exception {
        System.out.println("Predict classification: labels are non-empty strings");
        Path model = trainClassifier("random-forest", "--trees", "50");
        outCapture.reset();

        new CommandLine(new Predict()).execute(
            IRIS_ARFF.toString(), "--model", model.toString()
        );

        for (String line : outCapture.toString().trim().split("\\r?\\n")) {
            assertFalse(line.isBlank(), "Each prediction line must not be blank");
        }
    }

    @Test
    public void testPredictClassificationWithProbability() throws Exception {
        System.out.println("Predict classification: --probability flag appends class probabilities");
        Path model = trainClassifier("random-forest", "--trees", "50");
        outCapture.reset();

        int exit = new CommandLine(new Predict()).execute(
            IRIS_ARFF.toString(), "--model", model.toString(), "--probability"
        );
        assertEquals(0, exit);

        String[] lines = outCapture.toString().trim().split("\\r?\\n");
        assertEquals(150, lines.length, "Should still produce 150 lines");
        // With probabilities each line has: <label> <p0> <p1> <p2>
        // Check at least one space (more than just the label) in every line.
        for (String line : lines) {
            assertTrue(line.contains(" "),
                    "Probability output should contain spaces: " + line);
        }
    }

    @Test
    public void testPredictLdaClassification() throws Exception {
        System.out.println("Predict LDA classification");
        Path model = trainClassifier("lda");
        outCapture.reset();

        int exit = new CommandLine(new Predict()).execute(
            IRIS_ARFF.toString(), "--model", model.toString()
        );
        assertEquals(0, exit);
        String[] lines = outCapture.toString().trim().split("\\r?\\n");
        assertEquals(150, lines.length);
    }

    // ------------------------------------------------------------------
    // Regression prediction
    // ------------------------------------------------------------------

    @Test
    public void testPredictRegressionProducesOneValuePerRow() throws Exception {
        System.out.println("Predict regression: one numeric value per row");
        Path model = trainRegressor("ols");
        outCapture.reset();

        int exit = new CommandLine(new Predict()).execute(
            LONGLEY_ARFF.toString(), "--model", model.toString()
        );
        assertEquals(0, exit);

        String[] lines = outCapture.toString().trim().split("\\r?\\n");
        // longley.arff has 16 rows
        assertEquals(16, lines.length,
                "Should produce 16 prediction lines, got " + lines.length);
    }

    @Test
    public void testPredictRegressionValuesAreNumeric() throws Exception {
        System.out.println("Predict regression: output values are numeric");
        Path model = trainRegressor("ols");
        outCapture.reset();

        new CommandLine(new Predict()).execute(
            LONGLEY_ARFF.toString(), "--model", model.toString()
        );

        for (String line : outCapture.toString().trim().split("\\r?\\n")) {
            assertDoesNotThrow(() -> Double.parseDouble(line.trim()),
                    "Each prediction line should be a parseable double: " + line);
        }
    }

    @Test
    public void testPredictRidgeRegression() throws Exception {
        System.out.println("Predict Ridge regression");
        Path model = trainRegressor("ridge", "--lambda", "0.1");
        outCapture.reset();

        int exit = new CommandLine(new Predict()).execute(
            LONGLEY_ARFF.toString(), "--model", model.toString()
        );
        assertEquals(0, exit);
        String[] lines = outCapture.toString().trim().split("\\r?\\n");
        assertEquals(16, lines.length);
    }

    // ------------------------------------------------------------------
    // Error handling
    // ------------------------------------------------------------------

    @Test
    public void testPredictMissingModelFileProducesError() throws Exception {
        System.out.println("Predict: missing model file produces non-zero exit");
        int exit = new CommandLine(new Predict()).execute(
            IRIS_ARFF.toString(), "--model", tempDir.resolve("nonexistent.sml").toString()
        );
        assertNotEquals(0, exit, "Should fail with missing model file");
    }

    @Test
    public void testPredictMissingDataFileProducesError() throws Exception {
        System.out.println("Predict: missing data file is reported as an error");
        Path model = trainClassifier("random-forest", "--trees", "10");
        outCapture.reset();

        int exit = new CommandLine(new Predict()).execute(
            tempDir.resolve("nodata.arff").toString(), "--model", model.toString()
        );
        assertNotEquals(0, exit, "Should fail when data file does not exist");
    }
}

