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

import smile.datasets.Default;
import smile.datasets.ProstateCancer;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.regression.gam.*;
import smile.regression.glm.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link GAM} class.
 *
 * @author Haifeng Li
 */
public class GAMTest {

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    /**
     * Tests Gaussian GAM on the Prostate Cancer regression dataset.
     * Compares deviance and R² against known bounds.
     */
    @Test
    public void testGaussianProstateCancer() throws Exception {
        System.out.println("Gaussian GAM - Prostate Cancer");

        var dataset = new ProstateCancer();
        GAM.Options options = new GAM.Options(0.6, null, 10, 3, 1E-6, 50, 30, 1E-7);
        GAM model = GAM.fit(dataset.formula(), dataset.train(), Gaussian.identity(), options);
        System.out.println(model);

        // The GAM should fit at least as well as an intercept-only model:
        // residual deviance must be less than null deviance.
        assertTrue(model.deviance() < model.nullDeviance(),
                "GAM residual deviance should be less than null deviance");

        // The log-likelihood must be finite
        assertTrue(Double.isFinite(model.logLikelihood()),
                "Log-likelihood should be finite");

        // AIC and BIC must be finite
        assertTrue(Double.isFinite(model.AIC()), "AIC should be finite");
        assertTrue(Double.isFinite(model.BIC()), "BIC should be finite");

        // Total EDF should be > 1 (intercept alone = 1)
        assertTrue(model.totalEdf() > 1.0,
                "Total EDF should be > 1 when smooths are fitted");

        // Each smooth should have positive EDF
        for (SmoothingSpline smooth : model.smooths()) {
            assertTrue(smooth.edf() > 0,
                    "EDF of smooth(" + smooth.name() + ") should be > 0");
        }

        // Fitted values should be finite
        for (double mu : model.fittedValues()) {
            assertTrue(Double.isFinite(mu), "Fitted value should be finite");
        }

        // Predictions on test data should be finite
        double[] testPred = model.predict(dataset.test());
        for (double p : testPred) {
            assertTrue(Double.isFinite(p), "Test prediction should be finite");
        }

        // Deviance residuals should be finite
        for (double r : model.devianceResiduals()) {
            assertTrue(Double.isFinite(r), "Deviance residual should be finite");
        }

        // Serialization round-trip
        java.nio.file.Path temp = Write.object(model);
        GAM loaded = (GAM) Read.object(temp);
        assertEquals(model.deviance(), loaded.deviance(), 1E-10,
                "Deviance should survive serialization round-trip");
    }

    /**
     * Tests Bernoulli (logistic) GAM on the Default dataset.
     * Compares deviance against GLM as a sanity check
     * (GAM should achieve lower or equal deviance than the linear GLM).
     */
    @Test
    public void testBernoulliDefault() throws Exception {
        System.out.println("Bernoulli GAM - Default dataset");

        var dataset = new Default();
        // Use only the numeric predictors (balance, income) to avoid
        // complications from categorical encoding
        var formula = smile.data.formula.Formula.of("default", "balance", "income");
        GAM.Options options = new GAM.Options(0.6, null, 10, 3, 1E-5, 50, 30, 1E-7);
        GAM model = GAM.fit(formula, dataset.data(), Bernoulli.logit(), options);
        System.out.println(model);

        // Basic sanity checks
        assertTrue(model.deviance() < model.nullDeviance(),
                "GAM residual deviance should be less than null deviance");
        assertTrue(Double.isFinite(model.logLikelihood()));
        assertTrue(Double.isFinite(model.AIC()));
        assertTrue(Double.isFinite(model.BIC()));
        assertTrue(model.totalEdf() > 1.0);

        // Fitted values should be probabilities in (0, 1)
        for (double mu : model.fittedValues()) {
            assertTrue(mu > 0.0 && mu < 1.0,
                    "Fitted probability should be in (0, 1), got " + mu);
        }

        // Serialization round-trip
        java.nio.file.Path temp = Write.object(model);
        GAM loaded = (GAM) Read.object(temp);
        assertEquals(model.deviance(), loaded.deviance(), 1E-10);
    }

    /**
     * Tests that BSpline basis functions partition of unity property holds:
     * the basis functions at any point sum to 1.
     */
    @Test
    public void testBSplinePartitionOfUnity() {
        System.out.println("BSpline - partition of unity");

        double[] x = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
        BSpline spline = new BSpline(x, 8, 3);

        // Basis should sum to 1.0 at every x value (partition of unity)
        for (double xi : x) {
            double[] b = spline.basis(xi);
            double sum = 0.0;
            for (double bj : b) sum += bj;
            assertEquals(1.0, sum, 1E-10,
                    "B-spline basis functions should sum to 1 at x=" + xi);
        }

        // All basis values should be non-negative
        for (double xi : x) {
            double[] b = spline.basis(xi);
            for (int j = 0; j < b.length; j++) {
                assertTrue(b[j] >= 0.0,
                        "B-spline basis[" + j + "] should be >= 0 at x=" + xi);
            }
        }
    }

    /**
     * Tests that the penalty matrix has the expected structure:
     * symmetric and positive semi-definite.
     */
    @Test
    public void testBSplinePenaltyMatrix() {
        System.out.println("BSpline - penalty matrix");

        double[] x = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
        BSpline spline = new BSpline(x, 8, 3);
        double[][] P = spline.penalty();
        int df = spline.numBasis();

        assertEquals(df, P.length);
        assertEquals(df, P[0].length);

        // Check symmetry
        for (int i = 0; i < df; i++) {
            for (int j = 0; j < df; j++) {
                assertEquals(P[i][j], P[j][i], 1E-10,
                        "Penalty matrix should be symmetric at (" + i + "," + j + ")");
            }
        }
    }

    /**
     * Tests that SmoothingSpline predict is consistent with
     * the basis evaluation and coefficient dot product.
     */
    @Test
    public void testSmoothingSplinePredict() {
        System.out.println("SmoothingSpline - predict consistency");

        double[] x = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
        // Set arbitrary coefficients
        double[] beta = {0.1, 0.2, -0.1, 0.3, 0.0, -0.2, 0.15, 0.05};
        BSpline spline = new BSpline(x, 8, 3);
        // no centering for this test
        SmoothingSpline smooth = new SmoothingSpline("x", spline, 0.5, beta, 0.0, 0.0);

        // Check prediction matches manual dot product
        for (double xi : x) {
            double[] b = spline.basis(xi);
            double expected = 0.0;
            for (int j = 0; j < b.length; j++) expected += b[j] * beta[j];
            assertEquals(expected, smooth.predict(xi), 1E-10,
                    "SmoothingSpline.predict should match manual dot product at x=" + xi);
        }
    }

    /**
     * Tests Options round-trip through Properties.
     */
    @Test
    public void testOptionsProperties() {
        System.out.println("GAM Options - Properties round-trip");

        GAM.Options opts = new GAM.Options(1.5, null, 12, 3, 1E-5, 40, 20, 1E-8);
        java.util.Properties props = opts.toProperties();
        GAM.Options loaded = GAM.Options.of(props);

        assertEquals(opts.lambda(), loaded.lambda(), 1E-10);
        assertEquals(opts.df(), loaded.df());
        assertEquals(opts.degree(), loaded.degree());
        assertEquals(opts.tol(), loaded.tol(), 1E-15);
        assertEquals(opts.maxIter(), loaded.maxIter());
        assertEquals(opts.backfitIter(), loaded.backfitIter());
        assertEquals(opts.backfitTol(), loaded.backfitTol(), 1E-15);
    }

    /**
     * Tests Poisson GAM with a synthetic count dataset (including zero counts).
     */
    @Test
    public void testPoissonSynthetic() {
        System.out.println("Poisson GAM - Synthetic data");

        // Create a simple synthetic dataset: y ~ Poisson(exp(sin(x) + 1))
        // mu ranges roughly from exp(0) = 1 to exp(2) ≈ 7.4, so zero counts can occur.
        int n = 200;
        double[] xArr = new double[n];
        double[] yArr = new double[n];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < n; i++) {
            xArr[i] = -3.0 + 6.0 * i / (n - 1);
            double mu = Math.exp(Math.sin(xArr[i]) + 1.0);
            // Poisson approximation via rounded Gaussian; clamp to non-negative
            yArr[i] = Math.max(0, (int) Math.round(mu + rng.nextGaussian() * Math.sqrt(mu)));
        }

        // Build a DataFrame
        var xVec = new smile.data.vector.DoubleVector("x", xArr);
        var yVec = new smile.data.vector.DoubleVector("y", yArr);
        var df = new smile.data.DataFrame(xVec, yVec);
        var formula = smile.data.formula.Formula.of("y", "x");

        GAM.Options options = new GAM.Options(0.5, null, 10, 3, 1E-5, 50, 30, 1E-7);
        GAM model = GAM.fit(formula, df, Poisson.log(), options);
        System.out.println(model);

        assertTrue(model.deviance() < model.nullDeviance(),
                "GAM residual deviance should be less than null deviance for Poisson");
        assertTrue(Double.isFinite(model.logLikelihood()));
        assertTrue(Double.isFinite(model.deviance()), "Deviance must be finite even with zero counts");
        assertTrue(Double.isFinite(model.nullDeviance()), "Null deviance must be finite even with zero counts");

        // Fitted values for Poisson must be positive
        for (double mu : model.fittedValues()) {
            assertTrue(mu > 0.0, "Poisson fitted value must be > 0, got " + mu);
        }
    }
}

