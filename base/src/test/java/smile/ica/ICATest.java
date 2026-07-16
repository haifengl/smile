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
package smile.ica;

import java.util.Properties;
import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.io.CSV;
import smile.math.MathEx;
import smile.io.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the smile.ica package.
 *
 * @author Haifeng Li
 */
public class ICATest {

    static double[][] data;
    static double[][] X; // transposed: variables × samples

    @BeforeAll
    public static void setUpClass() throws Exception {
        CSVFormat format = CSVFormat.Builder.create().get();
        CSV csv = new CSV(format);
        data = csv.read(Paths.getTestData("ica/ica.csv")).toArray(false, CategoricalEncoder.DUMMY);
        X = MathEx.transpose(data);
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    // -----------------------------------------------------------------------
    // Original regression test (LogCosh, default)
    // -----------------------------------------------------------------------

    @Test
    public void testLogCoshDefault() {
        System.out.println("ICA - LogCosh (default)");
        MathEx.setSeed(19650218);

        ICA ica = ICA.fit(X, 2);
        var components = ica.components();
        assertEquals(2, components.length);
        assertEquals(data.length, components[0].length);
        assertEquals( 0.02003, components[0][0], 1E-5);
        assertEquals(-0.03275, components[1][0], 1E-5);
        assertEquals(-0.01140, components[0][1], 1E-5);
        assertEquals(-0.01084, components[1][1], 1E-5);
    }

    // -----------------------------------------------------------------------
    // Contrast function: Exp
    // -----------------------------------------------------------------------

    @Test
    public void testExpContrast() {
        System.out.println("ICA - Exp contrast function");
        MathEx.setSeed(19650218);

        ICA.Options opts = new ICA.Options(new Exp(), 200);
        ICA ica = ICA.fit(X, 2, opts);
        var components = ica.components();
        assertEquals(2, components.length);
        assertEquals(data.length, components[0].length);
        // Components must be unit vectors.
        assertEquals(1.0, MathEx.norm(components[0]), 1E-10);
        assertEquals(1.0, MathEx.norm(components[1]), 1E-10);
        // Components must be approximately orthogonal.
        double dot = MathEx.dot(components[0], components[1]);
        assertEquals(0.0, dot, 1E-4);
    }

    // -----------------------------------------------------------------------
    // Contrast function: Kurtosis
    // -----------------------------------------------------------------------

    @Test
    public void testKurtosisContrast() {
        System.out.println("ICA - Kurtosis contrast function");
        MathEx.setSeed(19650218);

        ICA.Options opts = new ICA.Options(new Kurtosis(), 200);
        ICA ica = ICA.fit(X, 2, opts);
        var components = ica.components();
        assertEquals(2, components.length);
        assertEquals(data.length, components[0].length);
        assertEquals(1.0, MathEx.norm(components[0]), 1E-10);
        assertEquals(1.0, MathEx.norm(components[1]), 1E-10);
        double dot = MathEx.dot(components[0], components[1]);
        assertEquals(0.0, dot, 1E-4);
    }

    // -----------------------------------------------------------------------
    // Options: String-name constructors and toProperties / of round-trip
    // -----------------------------------------------------------------------

    @Test
    public void testOptionsStringConstructorLogCosh() {
        ICA.Options opts = new ICA.Options("LogCosh", 50);
        assertInstanceOf(LogCosh.class, opts.contrast());
        assertEquals(50, opts.maxIter());
        assertEquals(1E-4, opts.tol(), 0.0);
    }

    @Test
    public void testOptionsStringConstructorGaussian() {
        ICA.Options opts = new ICA.Options("Gaussian", 80);
        assertInstanceOf(Exp.class, opts.contrast());
        assertEquals(80, opts.maxIter());
    }

    @Test
    public void testOptionsStringConstructorKurtosis() {
        ICA.Options opts = new ICA.Options("Kurtosis", 60);
        assertInstanceOf(Kurtosis.class, opts.contrast());
        assertEquals(60, opts.maxIter());
    }

    @Test
    public void testOptionsStringConstructorInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> new ICA.Options("UnknownFunction", 100));
    }

    @Test
    public void testOptionsToPropertiesAndOfLogCosh() throws Exception {
        ICA.Options original = new ICA.Options(new LogCosh(), 42, 1E-5);
        Properties props = original.toProperties();
        assertEquals("LogCosh", props.getProperty("smile.ica.contrast"));
        assertEquals("42", props.getProperty("smile.ica.iterations"));
        assertEquals("1.0E-5", props.getProperty("smile.ica.tolerance"));

        ICA.Options restored = ICA.Options.of(props);
        assertInstanceOf(LogCosh.class, restored.contrast());
        assertEquals(42, restored.maxIter());
        assertEquals(1E-5, restored.tol(), 0.0);
    }

    @Test
    public void testOptionsToPropertiesAndOfGaussian() throws Exception {
        ICA.Options original = new ICA.Options(new Exp(), 77, 1E-6);
        Properties props = original.toProperties();
        assertEquals("Gaussian", props.getProperty("smile.ica.contrast"));

        ICA.Options restored = ICA.Options.of(props);
        assertInstanceOf(Exp.class, restored.contrast());
        assertEquals(77, restored.maxIter());
    }

    @Test
    public void testOptionsToPropertiesAndOfKurtosis() throws Exception {
        ICA.Options original = new ICA.Options(new Kurtosis(), 33);
        Properties props = original.toProperties();
        assertEquals("Kurtosis", props.getProperty("smile.ica.contrast"));

        ICA.Options restored = ICA.Options.of(props);
        assertInstanceOf(Kurtosis.class, restored.contrast());
        assertEquals(33, restored.maxIter());
    }

    @Test
    public void testOptionsOfDefaults() throws Exception {
        ICA.Options opts = ICA.Options.of(new Properties());
        assertInstanceOf(LogCosh.class, opts.contrast());
        assertEquals(100, opts.maxIter());
        assertEquals(1E-4, opts.tol(), 0.0);
    }

    @Test
    public void testOptionsInvalidTolerance() {
        assertThrows(IllegalArgumentException.class,
                () -> new ICA.Options(new LogCosh(), 100, -1E-4));
    }

    @Test
    public void testOptionsInvalidMaxIter() {
        assertThrows(IllegalArgumentException.class,
                () -> new ICA.Options(new LogCosh(), 0, 1E-4));
    }

    // -----------------------------------------------------------------------
    // Invalid dimension p
    // -----------------------------------------------------------------------

    @Test
    public void testFitInvalidPTooSmall() {
        assertThrows(IllegalArgumentException.class,
                () -> ICA.fit(X, 0));
    }

    @Test
    public void testFitInvalidPTooLarge() {
        assertThrows(IllegalArgumentException.class,
                () -> ICA.fit(X, X.length + 1));
    }

    // -----------------------------------------------------------------------
    // Full-rank decomposition (p == number of signals)
    // -----------------------------------------------------------------------

    @Test
    public void testFitFullRank() {
        System.out.println("ICA - p == number of source signals");
        MathEx.setSeed(19650218);

        int p = X.length; // 3 for the ica.csv data
        ICA ica = ICA.fit(X, p);
        var components = ica.components();
        assertEquals(p, components.length);
        // All rows must be unit vectors.
        for (double[] row : components) {
            assertEquals(1.0, MathEx.norm(row), 1E-10);
        }
        // All pairs must be orthogonal.
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                assertEquals(0.0, MathEx.dot(components[i], components[j]), 1E-4);
            }
        }
    }


    // -----------------------------------------------------------------------
    // LogCosh contrast function: numerical properties
    // -----------------------------------------------------------------------

    @Test
    public void testLogCoshAtZero() {
        LogCosh lc = new LogCosh();
        assertEquals(0.0, lc.f(0.0), 1E-15);
        assertEquals(0.0, lc.g(0.0), 1E-15);
        assertEquals(1.0, lc.g2(0.0), 1E-15);
    }

    @Test
    public void testLogCoshSymmetry() {
        LogCosh lc = new LogCosh();
        // f is even
        for (double x : new double[]{0.5, 1.0, 2.0, 5.0, 20.0}) {
            assertEquals(lc.f(x), lc.f(-x), 1E-12);
        }
        // g is odd
        for (double x : new double[]{0.5, 1.0, 2.0, 5.0}) {
            assertEquals(lc.g(x), -lc.g(-x), 1E-12);
        }
        // g2 is even
        for (double x : new double[]{0.5, 1.0, 2.0, 5.0}) {
            assertEquals(lc.g2(x), lc.g2(-x), 1E-12);
        }
    }

    @Test
    public void testLogCoshNumericalStability() {
        // For large |x|, the old log(cosh(x)) would overflow; stable version must not.
        LogCosh lc = new LogCosh();
        double large = 1000.0;
        // Expected: |x| - log(2)  (since exp(-2|x|) ≈ 0)
        double expected = large - Math.log(2.0);
        assertEquals(expected, lc.f(large), 1E-6);
        assertEquals(expected, lc.f(-large), 1E-6);
    }

    @Test
    public void testLogCoshDerivativeConsistency() {
        // g should be the numerical derivative of f.
        LogCosh lc = new LogCosh();
        double h = 1E-7;
        for (double x : new double[]{-2.0, -0.5, 0.0, 0.5, 2.0}) {
            double numDeriv = (lc.f(x + h) - lc.f(x - h)) / (2 * h);
            assertEquals(numDeriv, lc.g(x), 1E-6);
        }
    }

    @Test
    public void testLogCoshSecondDerivativeConsistency() {
        // g2 should be the numerical derivative of g.
        LogCosh lc = new LogCosh();
        double h = 1E-6;
        for (double x : new double[]{-2.0, -0.5, 0.0, 0.5, 2.0}) {
            double numDeriv2 = (lc.g(x + h) - lc.g(x - h)) / (2 * h);
            assertEquals(numDeriv2, lc.g2(x), 1E-5);
        }
    }

    @Test
    public void testLogCoshToString() {
        assertEquals("LogCosh", new LogCosh().toString());
    }

    // -----------------------------------------------------------------------
    // Exp (Gaussian) contrast function: numerical properties
    // -----------------------------------------------------------------------

    @Test
    public void testExpAtZero() {
        Exp exp = new Exp();
        assertEquals(-1.0, exp.f(0.0), 1E-15);
        assertEquals(0.0,  exp.g(0.0), 1E-15);
        assertEquals(1.0,  exp.g2(0.0), 1E-15);
    }

    @Test
    public void testExpSymmetry() {
        Exp exp = new Exp();
        for (double x : new double[]{0.5, 1.0, 2.0}) {
            assertEquals(exp.f(x), exp.f(-x), 1E-12); // f is even
            assertEquals(exp.g(x), -exp.g(-x), 1E-12); // g is odd
            assertEquals(exp.g2(x), exp.g2(-x), 1E-12); // g2 is even
        }
    }

    @Test
    public void testExpDerivativeConsistency() {
        Exp exp = new Exp();
        double h = 1E-7;
        for (double x : new double[]{-2.0, -0.5, 0.0, 0.5, 2.0}) {
            double numDeriv = (exp.f(x + h) - exp.f(x - h)) / (2 * h);
            assertEquals(numDeriv, exp.g(x), 1E-6);
        }
    }

    @Test
    public void testExpSecondDerivativeConsistency() {
        Exp exp = new Exp();
        double h = 1E-6;
        for (double x : new double[]{-2.0, -0.5, 0.0, 0.5, 2.0}) {
            double numDeriv2 = (exp.g(x + h) - exp.g(x - h)) / (2 * h);
            assertEquals(numDeriv2, exp.g2(x), 1E-5);
        }
    }

    @Test
    public void testExpToString() {
        assertEquals("Gaussian", new Exp().toString());
    }

    // -----------------------------------------------------------------------
    // Kurtosis contrast function: numerical properties
    // -----------------------------------------------------------------------

    @Test
    public void testKurtosisAtZero() {
        Kurtosis k = new Kurtosis();
        assertEquals(0.0, k.f(0.0), 1E-15);
        assertEquals(0.0, k.g(0.0), 1E-15);
        assertEquals(0.0, k.g2(0.0), 1E-15);
    }

    @Test
    public void testKurtosisKnownValues() {
        Kurtosis k = new Kurtosis();
        assertEquals(0.25, k.f(1.0),  1E-15);
        assertEquals(1.0,  k.g(1.0),  1E-15);
        assertEquals(3.0,  k.g2(1.0), 1E-15);
        assertEquals(4.0,  k.f(-2.0), 1E-15);
        assertEquals(-8.0, k.g(-2.0), 1E-15);
        assertEquals(12.0, k.g2(-2.0),1E-15);
    }

    @Test
    public void testKurtosisDerivativeConsistency() {
        Kurtosis k = new Kurtosis();
        double h = 1E-7;
        for (double x : new double[]{-2.0, -0.5, 0.0, 0.5, 2.0}) {
            double numDeriv = (k.f(x + h) - k.f(x - h)) / (2 * h);
            assertEquals(numDeriv, k.g(x), 1E-6);
        }
    }

    @Test
    public void testKurtosisSecondDerivativeConsistency() {
        Kurtosis k = new Kurtosis();
        double h = 1E-6;
        for (double x : new double[]{-2.0, -0.5, 0.5, 2.0}) {
            double numDeriv2 = (k.g(x + h) - k.g(x - h)) / (2 * h);
            assertEquals(numDeriv2, k.g2(x), 1E-4);
        }
    }

    @Test
    public void testKurtosisToString() {
        assertEquals("Kurtosis", new Kurtosis().toString());
    }

    // -----------------------------------------------------------------------
    // Synthetic cocktail-party test (two sources, two mixtures)
    // -----------------------------------------------------------------------

    @Test
    public void testSyntheticCocktailParty() {
        System.out.println("ICA - synthetic cocktail party");
        MathEx.setSeed(12345);

        int T = 2000;
        // Source 1: sawtooth wave
        double[] s1 = new double[T];
        for (int t = 0; t < T; t++) {
            s1[t] = 2.0 * ((t % 50) / 50.0) - 1.0;
        }
        // Source 2: square wave
        double[] s2 = new double[T];
        for (int t = 0; t < T; t++) {
            s2[t] = Math.signum(Math.sin(2 * Math.PI * t / 30.0));
        }
        // Mix: x1 = 0.7*s1 + 0.3*s2, x2 = 0.4*s1 + 0.6*s2
        double[][] mixedData = new double[T][2];
        for (int t = 0; t < T; t++) {
            mixedData[t][0] = 0.7 * s1[t] + 0.3 * s2[t];
            mixedData[t][1] = 0.4 * s1[t] + 0.6 * s2[t];
        }

        // ICA expects variables × samples
        ICA ica = ICA.fit(MathEx.transpose(mixedData), 2);
        var components = ica.components();
        assertEquals(2, components.length);
        // Components should be unit vectors.
        assertEquals(1.0, MathEx.norm(components[0]), 1E-10);
        assertEquals(1.0, MathEx.norm(components[1]), 1E-10);
        // Components should be orthogonal.
        assertEquals(0.0, MathEx.dot(components[0], components[1]), 1E-4);
    }
}
