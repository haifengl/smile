/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression;

import java.util.Properties;
import smile.datasets.*;
import smile.io.Read;
import smile.io.Write;
import smile.math.kernel.GaussianKernel;
import smile.math.MathEx;
import smile.validation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SVMTest {

    public SVMTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");
        var longley = new Longley();
        var options = new SVM.Options(2.0, 10.0, 1E-3);
        RegressionMetrics metrics = LOOCV.regression(longley.x(), longley.y(), (x, y) -> SVM.fit(x, y, options));

        System.out.println("LOOCV RMSE = " + metrics.rmse());
        assertEquals(1.6140, metrics.rmse(), 1E-4);

        Regression<double[]> model = SVM.fit(longley.x(), longley.y(), options);
        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testCPU() throws Exception {
        System.out.println("CPU");
        var cpu = new CPU();
        double[][] x = cpu.x();
        double[] y = cpu.y();
        MathEx.standardize(x);

        RegressionValidations<Regression<double[]>> result = CrossValidation.regression(10, x, y,
                (xi, yi) -> SVM.fit(xi, yi, new SVM.Options(40.0, 10.0, 1E-3)));

        System.out.println(result);
        assertEquals(49.8908, result.avg().rmse(), 1E-4);
    }

    @Test
    public void tesProstate() throws Exception {
        System.out.println("Prostate");
        var prostate = new ProstateCancer();
        GaussianKernel kernel = new GaussianKernel(6.0);

        RegressionValidation<Regression<double[]>> result = RegressionValidation.of(prostate.x(), prostate.y(),
                prostate.testx(), prostate.testy(), (x, y) -> SVM.fit(x, y, kernel, new SVM.Options(0.5, 5, 1E-3)));

        System.out.println(result);
        assertEquals(0.9112183360712871, result.metrics().rmse(), 1E-4);
    }

    @Test
    public void tesAbalone() throws Exception {
        System.out.println("Abalone");
        var abalone = new Abalone();
        GaussianKernel kernel = new GaussianKernel(5.0);
        var result = RegressionValidation.of(abalone.x(), abalone.y(), abalone.testx(), abalone.testy(),
                (x, y) -> SVM.fit(x, y, kernel, new SVM.Options(1.5, 100, 1E-3)));

        System.out.println(result);
        assertEquals(2.1092, result.metrics().rmse(), 1E-4);
    }

    @Test
    public void tesDiabetes() throws Exception {
        System.out.println("Diabetes");

        var diabetes = new Diabetes();
        GaussianKernel kernel = new GaussianKernel(5.0);
        RegressionValidations<Regression<double[]>> result = CrossValidation.regression(10, diabetes.x(), diabetes.y(),
                (x, y) -> SVM.fit(x, y, kernel, new SVM.Options(50, 1000, 1E-3)));

        System.out.println(result);
        assertEquals(61.5184, result.avg().rmse(), 1E-4);
    }

    // ── constructor validation ─────────────────────────────────────────────────

    @Test
    void givenValidParameters_whenConstruct_thenNoException() {
        assertDoesNotThrow(() -> new SVM.Options(1.0, 5.0, 1E-3));
        assertDoesNotThrow(() -> new SVM.Options(0.01, 1000.0));
    }

    @Test
    void givenZeroEpsilon_whenConstruct_thenThrows() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SVM.Options(0.0, 1.0, 1E-3));
        assertTrue(ex.getMessage().contains("epsilon") || ex.getMessage().contains("eps"),
                "Error message should mention epsilon, got: " + ex.getMessage());
    }

    @Test
    void givenNegativeEpsilon_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SVM.Options(-1.0, 1.0, 1E-3));
    }

    @Test
    void givenNegativeC_whenConstruct_thenThrowsWithCorrectMessage() {
        // Bug fix: the error message used to say "Invalid maximum number of iterations"
        // for the C parameter. After the fix it should mention the penalty.
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SVM.Options(1.0, -1.0, 1E-3));

        // Must NOT say "iterations" — that belongs to a different parameter.
        assertFalse(ex.getMessage().toLowerCase().contains("iterations"),
                "Error message for invalid C must not mention 'iterations': " + ex.getMessage());

        // Must clearly describe the penalty parameter.
        assertTrue(
                ex.getMessage().toLowerCase().contains("penalty")
                        || ex.getMessage().toLowerCase().contains("margin")
                        || ex.getMessage().toLowerCase().contains("soft"),
                "Error message should describe the C parameter: " + ex.getMessage());
    }

    @Test
    void givenZeroTolerance_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SVM.Options(1.0, 5.0, 0.0));
    }

    @Test
    void givenZeroC_whenConstruct_thenDoesNotThrow() {
        // C = 0 is the boundary value; the check is C < 0.
        assertDoesNotThrow(() -> new SVM.Options(1.0, 0.0, 1E-3));
    }

    // ── Properties round-trip ─────────────────────────────────────────────────

    @Test
    void givenOptions_whenRoundTripViaProperties_thenValuesPreserved() {
        SVM.Options original = new SVM.Options(2.0, 50.0, 1E-4);
        Properties props = original.toProperties();

        SVM.Options restored = SVM.Options.of(props);

        assertEquals(original.eps(), restored.eps(), 1E-15);
        assertEquals(original.C(),   restored.C(),   1E-15);
        assertEquals(original.tol(), restored.tol(), 1E-15);
    }

    @Test
    void givenEmptyProperties_whenOf_thenReturnsDefaults() {
        SVM.Options defaults = SVM.Options.of(new Properties());

        // Default values from the Options.of() implementation
        assertEquals(1.0,  defaults.eps(), 1E-15);
        assertEquals(1.0,  defaults.C(),   1E-15);
        assertEquals(1E-3, defaults.tol(), 1E-15);
    }

    @Test
    void givenPartialProperties_whenOf_thenUsesProvidedValues() {
        Properties props = new Properties();
        props.setProperty("smile.svm.epsilon", "3.0");
        // C and tol left at defaults

        SVM.Options opts = SVM.Options.of(props);
        assertEquals(3.0,  opts.eps(), 1E-15);
        assertEquals(1.0,  opts.C(),   1E-15);  // default
        assertEquals(1E-3, opts.tol(), 1E-15);  // default
    }

    @Test
    void givenOptions_whenToProperties_thenAllKeysPresent() {
        SVM.Options opts = new SVM.Options(1.5, 10.0, 5E-4);
        Properties props = opts.toProperties();

        assertNotNull(props.getProperty("smile.svm.epsilon"));
        assertNotNull(props.getProperty("smile.svm.C"));
        assertNotNull(props.getProperty("smile.svm.tolerance"));
    }
}
