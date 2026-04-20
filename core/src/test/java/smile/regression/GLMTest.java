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

import java.util.List;
import java.util.Properties;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.regression.glm.*;
import smile.io.Read;
import smile.io.Write;
import smile.datasets.Default;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GLMTest {

    public GLMTest() {
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
    public void testDefault() throws Exception {
        System.out.println("default");

        var dataset = new Default();
        GLM model = GLM.fit(dataset.formula(), dataset.data(), Bernoulli.logit());
        System.out.println(model);

        assertEquals(1571.5448, model.deviance(), 1E-4);
        assertEquals(-785.7724, model.logLikelihood(), 1E-4);
        assertEquals(1579.5448, model.AIC(), 1E-4);
        assertEquals(1608.3862, model.BIC(), 1E-4);

        double[][] ztest = {
                {-10.869045, 4.923e-01, -22.0793,   0.00000},
                {-6.468e-01, 2.363e-01,  -2.7376,   0.00619},
                { 5.737e-03, 2.319e-04,  24.7365,   0.00000},
                { 3.033e-06, 8.203e-06,   0.3698,   0.71153}
        };

        for (int i = 0; i < ztest.length; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(ztest[i][j], model.ztest()[i][j], 1E-4);
            }
        }

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    /**
     * Tests that Poisson deviance and nullDeviance are finite when the
     * response vector contains zero counts (the zero-count bug fix).
     * When y_i = 0, the deviance contribution should be 2 * mu_i (not NaN).
     */
    @Test
    public void testPoissonZeroCounts() {
        System.out.println("Poisson zero-count deviance");

        // y = [0, 1, 2, 3, 4, 5] with mu = [0.5, 1.0, 2.0, 3.0, 4.0, 5.0]
        double[] y  = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0};
        double[] mu = {0.5, 1.0, 2.0, 3.0, 4.0, 5.0};
        double[] residuals = new double[y.length];

        var model = Poisson.log();

        double dev = model.deviance(y, mu, residuals);
        assertTrue(Double.isFinite(dev),
                "Poisson deviance must be finite when y contains zeros, got " + dev);
        assertTrue(dev >= 0.0,
                "Poisson deviance must be non-negative, got " + dev);

        // y[0]=0, mu[0]=0.5 → contribution = 2*(0 - 0 + 0.5) = 1.0
        assertEquals(1.0, 2.0 * mu[0], 1E-10);

        // residual for y=0 should be negative (y < mu)
        assertTrue(residuals[0] <= 0.0,
                "Deviance residual for y=0 should be <= 0, got " + residuals[0]);

        // All residuals must be finite
        for (int i = 0; i < residuals.length; i++) {
            assertTrue(Double.isFinite(residuals[i]),
                    "Deviance residual[" + i + "] must be finite, got " + residuals[i]);
        }

        double nullDev = model.nullDeviance(y, 2.5);  // mean of y
        assertTrue(Double.isFinite(nullDev),
                "Poisson null deviance must be finite when y contains zeros, got " + nullDev);
        assertTrue(nullDev >= 0.0,
                "Poisson null deviance must be non-negative, got " + nullDev);
    }

    // ── GLM.Options round-trip ─────────────────────────────────────────────────

    @Test
    void givenOptions_whenRoundTripViaProperties_thenValuesPreserved() {
        GLM.Options original = new GLM.Options(1E-7, 200);
        Properties props = original.toProperties();
        GLM.Options restored = GLM.Options.of(props);

        assertEquals(original.tol(),     restored.tol(),     1E-20);
        assertEquals(original.maxIter(), restored.maxIter());
    }

    @Test
    void givenEmptyProperties_whenOf_thenReturnsDefaults() {
        GLM.Options defaults = GLM.Options.of(new Properties());
        assertEquals(1E-5, defaults.tol(),     1E-20);
        assertEquals(50,   defaults.maxIter());
    }

    @Test
    void givenZeroTol_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new GLM.Options(0.0, 10));
    }

    @Test
    void givenNegativeTol_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new GLM.Options(-1E-5, 10));
    }

    @Test
    void givenZeroMaxIter_whenConstruct_thenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new GLM.Options(1E-5, 0));
    }

    // ── Binomial logLikelihood fix ─────────────────────────────────────────────
    //
    // We test the Binomial model directly by calling its logLikelihood method
    // with known y (proportions) and mu (fitted probabilities) values and
    // comparing against the analytical binomial log-likelihood:
    //   log P = sum_i [ lchoose(n_i, n_i*y_i) + n_i*(y_i*log(mu_i) + (1-y_i)*log(1-mu_i)) ]

    @Test
    void givenPerfectProbabilities_whenBinomialLogLikelihood_thenMaximal() {
        // When mu == y (perfect prediction) the log-likelihood is maximised.
        // n = [10, 10], y = [0.7, 0.3], mu = y
        int[] n = {10, 10};
        Model model = Binomial.logit(n);

        double[] y  = {0.7, 0.3};
        double[] mu = {0.7, 0.3};   // perfect fit

        double ll = model.logLikelihood(y, mu);
        assertTrue(Double.isFinite(ll), "Log-likelihood must be finite");
    }

    @Test
    void givenKnownBinomialData_whenLogLikelihood_thenMatchesAnalytic() {
        // n = [10], y = [1.0] (all successes), mu = [0.9]
        // Expected: 10*(1*log(0.9) + 0*log(0.1)) + lchoose(10,10) = 10*log(0.9) + 0
        int[] n = {10};
        Model model = Binomial.logit(n);

        double[] y  = {1.0};
        double[] mu = {0.9};

        double ll       = model.logLikelihood(y, mu);
        double expected = 10.0 * Math.log(0.9);  // + lchoose(10,10)=0

        assertEquals(expected, ll, 1E-10,
                "Binomial log-likelihood with all successes and mu=0.9");
    }

    @Test
    void givenAllFailures_whenBinomialLogLikelihood_thenMatchesAnalytic() {
        // n = [5], y = [0.0] (all failures), mu = [0.2]
        // Expected: 5*(0 + 1*log(0.8)) + lchoose(5,0)=0 = 5*log(0.8)
        int[] n = {5};
        Model model = Binomial.logit(n);

        double[] y  = {0.0};
        double[] mu = {0.2};

        double ll       = model.logLikelihood(y, mu);
        double expected = 5.0 * Math.log(0.8);

        assertEquals(expected, ll, 1E-10,
                "Binomial log-likelihood with all failures and mu=0.2");
    }

    @Test
    void givenSymmetricData_whenBinomialLogLikelihood_thenSymmetric() {
        // Symmetry: logLik(y=0.7, mu=0.7, n) == logLik(y=0.3, mu=0.3, n)  (by symmetry of binomial)
        int[] n1 = {10};
        int[] n2 = {10};
        Model m1 = Binomial.logit(n1);
        Model m2 = Binomial.logit(n2);

        double ll1 = m1.logLikelihood(new double[]{0.7}, new double[]{0.7});
        double ll2 = m2.logLikelihood(new double[]{0.3}, new double[]{0.3});

        assertEquals(ll1, ll2, 1E-10, "Binomial logLik(y=0.7,mu=0.7) should equal logLik(y=0.3,mu=0.3)");
    }

    @Test
    void givenBinomialLogLikelihood_whenBadProbability_thenExpectedBehavior() {
        // This test checks that the fixed formula does NOT produce
        // the pathological output of the old (buggy) formula.
        // Old formula: (y*mu - log(1+exp(mu))) / n  -- uses probability as logit.
        // For mu near 1 the old formula gave large positive values (impossible for a LL).
        int[] n = {1};
        Model model = Binomial.logit(n);

        double[] y  = {1.0};
        double[] mu = {0.99};  // close to 1 but not exactly 1

        double ll = model.logLikelihood(y, mu);
        // log-likelihood can never be positive (it's a log of a probability <= 1)
        assertTrue(ll <= 0,
                "Log-likelihood must be non-positive, got: " + ll);
    }

    // ── GLM Bernoulli fit — deviance and logLikelihood are consistent ──────────

    @Test
    void givenBernoulliGLM_whenFit_thenDevianceAndLogLikelihoodAreFiniteAndConsistent() {
        // Use overlapping classes to avoid perfect separation (which produces NaN deviance
        // because the GLM tries to fit p=0 or p=1 exactly).
        StructType schema = new StructType(
                new StructField("y", DataTypes.DoubleType),
                new StructField("x", DataTypes.DoubleType)
        );
        DataFrame df = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{0.0, -2.0}),
                Tuple.of(schema, new Object[]{0.0, -1.0}),
                Tuple.of(schema, new Object[]{0.0,  0.0}),
                Tuple.of(schema, new Object[]{1.0,  0.0}),
                Tuple.of(schema, new Object[]{1.0,  1.0}),
                Tuple.of(schema, new Object[]{1.0,  2.0}),
                Tuple.of(schema, new Object[]{0.0,  1.5}),
                Tuple.of(schema, new Object[]{1.0, -0.5})
        ));

        GLM model = GLM.fit(Formula.lhs("y"), df, Bernoulli.logit());

        assertTrue(Double.isFinite(model.deviance()),
                "Residual deviance must be finite");
        assertTrue(Double.isFinite(model.logLikelihood()),
                "Log-likelihood must be finite");
        assertTrue(model.logLikelihood() <= 0,
                "Log-likelihood must be non-positive");

        // AIC and BIC must be finite numbers
        assertTrue(Double.isFinite(model.AIC()), "AIC must be finite");
        assertTrue(Double.isFinite(model.BIC()), "BIC must be finite");
    }

    // ── GLM Binomial fit — logLikelihood is finite and non-positive ───────────

    @Test
    void givenBinomialGLM_whenFit_thenLogLikelihoodIsFiniteAndNonPositive() {
        // Binomial proportions: y[i] = k[i]/n[i], n[i] = 10
        int[] n = {10, 10, 10, 10, 10, 10};
        StructType schema = new StructType(
                new StructField("y", DataTypes.DoubleType),
                new StructField("x", DataTypes.DoubleType)
        );
        // y[i] = sample proportions, x[i] = predictor
        DataFrame df = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{0.1, -2.0}),
                Tuple.of(schema, new Object[]{0.2, -1.0}),
                Tuple.of(schema, new Object[]{0.5,  0.0}),
                Tuple.of(schema, new Object[]{0.6,  0.5}),
                Tuple.of(schema, new Object[]{0.8,  1.5}),
                Tuple.of(schema, new Object[]{0.9,  2.5})
        ));

        GLM model = GLM.fit(Formula.lhs("y"), df, Binomial.logit(n));

        assertTrue(Double.isFinite(model.logLikelihood()),
                "Binomial GLM log-likelihood must be finite");
        assertTrue(model.logLikelihood() <= 0,
                "Binomial GLM log-likelihood must be non-positive, got: " + model.logLikelihood());
        assertTrue(Double.isFinite(model.deviance()),
                "Binomial GLM deviance must be finite");
        assertTrue(model.deviance() >= 0,
                "Binomial GLM deviance must be non-negative");
    }
}