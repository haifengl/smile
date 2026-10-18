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
package smile.feature.importance;

import java.util.Properties;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.classification.DecisionTree;
import smile.classification.FLD;
import smile.classification.LDA;
import smile.classification.LogisticRegression;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.datasets.CPU;
import smile.datasets.Iris;
import smile.datasets.ProstateCancer;
import smile.datasets.WeatherNominal;
import smile.math.MathEx;
import smile.model.cart.SplitRule;
import smile.regression.LinearModel;
import smile.regression.OLS;
import smile.regression.RBFNetwork;
import smile.regression.RidgeRegression;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for {@link KernelSHAP}.
 */
public class KernelSHAPTest {
    private static final double TOLERANCE = 1E-5;

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);
    }

    @Test
    public void testGivenLinearFunctionWhenComputingKernelShapThenMatchesAnalyticalShapleyValues() {
        // Given: f(x) = 3.5 + 2.0 * x0 - 1.5 * x1 + 4.0 * x2 - 0.5 * x3
        double intercept = 3.5;
        double[] beta = {2.0, -1.5, 4.0, -0.5};
        double[][] background = {
                {1.0, 2.0, 0.5, 4.0},
                {3.0, 0.0, 1.5, 2.0},
                {2.0, 4.0, 2.5, 0.0},
                {4.0, 2.0, 3.5, 2.0}
        };
        double[] bgMean = MathEx.colMeans(background);
        ToDoubleFunction<double[]> f = x -> {
            double sum = intercept;
            for (int j = 0; j < beta.length; j++) {
                sum += beta[j] * x[j];
            }
            return sum;
        };

        KernelSHAP<double[]> explainer = KernelSHAP.of(background, f);
        double[] x = {5.0, -1.0, 3.0, 1.0};

        // When
        double[] phi = explainer.shap(x);
        double[] base = explainer.baseValues();

        // Then: analytical Shapley value for linear model is phi_j = beta_j * (x_j - E[x_j])
        double expectedBase = f.applyAsDouble(bgMean);
        assertEquals(1, base.length);
        assertEquals(expectedBase, base[0], TOLERANCE);
        assertEquals(beta.length, phi.length);
        for (int j = 0; j < beta.length; j++) {
            double expectedPhi = beta[j] * (x[j] - bgMean[j]);
            assertEquals(expectedPhi, phi[j], TOLERANCE);
        }
        assertEquals(f.applyAsDouble(x) - base[0], MathEx.sum(phi), TOLERANCE);
    }

    @Test
    public void testGivenWeightedBackgroundWhenComputingKernelShapThenMatchesWeightedAnalyticalShapleyValues() {
        // Given
        double[] beta = {1.5, -2.5, 3.0};
        double[][] background = {
                {0.0, 1.0, 2.0},
                {2.0, 3.0, 4.0},
                {4.0, 5.0, 6.0}
        };
        double[] weights = {1.0, 2.0, 5.0};
        double weightSum = MathEx.sum(weights);
        double[] weightedMean = new double[3];
        for (int i = 0; i < background.length; i++) {
            for (int j = 0; j < 3; j++) {
                weightedMean[j] += (weights[i] / weightSum) * background[i][j];
            }
        }
        ToDoubleFunction<double[]> f = x -> beta[0] * x[0] + beta[1] * x[1] + beta[2] * x[2];

        KernelSHAP<double[]> explainer = KernelSHAP.of(background, weights, f, new KernelSHAP.Options());
        double[] x = {3.0, -1.0, 0.5};

        // When
        double[] phi = explainer.shap(x);

        // Then
        for (int j = 0; j < 3; j++) {
            assertEquals(beta[j] * (x[j] - weightedMean[j]), phi[j], TOLERANCE);
        }
        assertEquals(f.applyAsDouble(x) - explainer.baseValues()[0], MathEx.sum(phi), TOLERANCE);
    }

    @Test
    public void testGivenNonLinearRegressionModelsWhenComputingKernelShapThenEfficiencyAxiomHolds() throws Exception {
        // Given
        var prostate = new ProstateCancer();
        DataFrame train = prostate.train();
        double[][] xTrain = prostate.formula().x(train).toArray();
        double[] yTrain = prostate.formula().y(train).toDoubleArray();

        RBFNetwork<double[]> rbf = RBFNetwork.fit(xTrain, yTrain, new Properties());
        double[][] bg = KernelSHAP.sample(xTrain, 30);
        KernelSHAP<double[]> explainer = KernelSHAP.of(bg, rbf);

        // When
        double[] x = xTrain[0];
        double[] phi = explainer.shap(x);
        double base = explainer.baseValues()[0];
        double pred = rbf.predict(x);

        // Then: sum(phi) == f(x) - E[f(z)]
        assertEquals(x.length, phi.length);
        assertEquals(pred - base, MathEx.sum(phi), 1E-6);
    }

    @Test
    public void testGivenMultiClassSoftAndHardClassifiersWhenComputingKernelShapThenEfficiencyAxiomHoldsPerClass() throws Exception {
        // Given: Iris 3-class classification
        var iris = new Iris();
        double[][] x = iris.formula().x(iris.data()).toArray();
        int[] y = iris.formula().y(iris.data()).toIntArray();
        double[][] bg = KernelSHAP.sample(x, 30);

        LogisticRegression logit = LogisticRegression.fit(x, y);
        LDA lda = LDA.fit(x, y);
        FLD fld = FLD.fit(x, y); // hard classifier (isSoft() == false)

        // When / Then: Soft LogisticRegression
        KernelSHAP<double[]> logitShap = KernelSHAP.of(bg, logit);
        double[] phiLogit = logitShap.shap(x[0]);
        double[] baseLogit = logitShap.baseValues();
        double[] probLogit = new double[3];
        logit.predict(x[0], probLogit);

        int m = x[0].length;
        int k = 3;
        assertEquals(m * k, phiLogit.length);
        for (int c = 0; c < k; c++) {
            double classSum = 0.0;
            for (int j = 0; j < m; j++) {
                classSum += phiLogit[j * k + c];
            }
            assertEquals(probLogit[c] - baseLogit[c], classSum, 1E-6);
        }

        // When / Then: Soft LDA
        KernelSHAP<double[]> ldaShap = KernelSHAP.of(bg, lda);
        double[] phiLda = ldaShap.shap(x[10]);
        double[] baseLda = ldaShap.baseValues();
        double[] probLda = new double[3];
        lda.predict(x[10], probLda);
        for (int c = 0; c < k; c++) {
            double classSum = 0.0;
            for (int j = 0; j < m; j++) {
                classSum += phiLda[j * k + c];
            }
            assertEquals(probLda[c] - baseLda[c], classSum, 1E-6);
        }

        // When / Then: Hard FLD
        assertFalse(fld.isSoft());
        KernelSHAP<double[]> fldShap = KernelSHAP.of(bg, fld);
        double[] phiFld = fldShap.shap(x[0]);
        double[] baseFld = fldShap.baseValues();
        int predClass = fld.predict(x[0]);
        for (int c = 0; c < k; c++) {
            double classSum = 0.0;
            for (int j = 0; j < m; j++) {
                classSum += phiFld[j * k + c];
            }
            double indicator = (fld.classes()[c] == predClass) ? 1.0 : 0.0;
            assertEquals(indicator - baseFld[c], classSum, 1E-6);
        }
    }

    @Test
    public void testGivenLinearModelWhenComparingDirectShapAndKernelShapThenResultsMatch() throws Exception {
        // Given
        var prostate = new ProstateCancer();
        LinearModel ols = OLS.fit(prostate.formula(), prostate.train());
        LinearModel ridge = RidgeRegression.fit(prostate.formula(), prostate.train(), 0.5);
        KernelSHAP<Tuple> kernelOls = KernelSHAP.of(prostate.train(), ols);
        KernelSHAP<Tuple> kernelRidge = KernelSHAP.of(prostate.train(), ridge);

        Tuple row = prostate.test().get(0);

        // When
        double[] directOlsPhi = ols.shap(row);
        double[] kernelOlsPhi = kernelOls.shap(row);
        double[] directRidgePhi = ridge.shap(row);
        double[] kernelRidgePhi = kernelRidge.shap(row);

        // Then: LinearModel.shap(row) and KernelSHAP.of(train, model).shap(row) must agree
        assertArrayEquals(directOlsPhi, kernelOlsPhi, 1E-4);
        assertArrayEquals(directRidgePhi, kernelRidgePhi, 1E-4);

        // And aggregated DataFrame shap is non-negative and finite
        double[] importance = ols.shap(prostate.test());
        assertEquals(ols.schema().length(), importance.length);
        for (double v : importance) {
            assertTrue(v >= 0.0 && Double.isFinite(v));
        }
    }

    @Test
    public void testGivenRandomForestWhenComparingKernelShapAndTreeShapThenTopFeaturesAgree() throws Exception {
        // Given
        var iris = new Iris();
        var options = new RandomForest.Options(20, 2, SplitRule.GINI, 10, 50, 5, 1.0, null, null, null);
        RandomForest rf = RandomForest.fit(iris.formula(), iris.data(), options);
        KernelSHAP<Tuple> kernelShap = KernelSHAP.of(iris.data(), rf);

        // When: explain a setosa instance (row 0)
        Tuple row = iris.data().get(0);
        double[] treePhi = rf.shap(row);
        double[] kernPhi = kernelShap.shap(row);

        // Then: both identify petal length (feature 2) and petal width (feature 3)
        // as having much larger attribution magnitude than sepal width (feature 1) for class 0
        int k = 3;
        double treePetal = Math.abs(treePhi[2 * k]) + Math.abs(treePhi[3 * k]);
        double treeSepal = Math.abs(treePhi[0]) + Math.abs(treePhi[k]);
        double kernPetal = Math.abs(kernPhi[2 * k]) + Math.abs(kernPhi[3 * k]);
        double kernSepal = Math.abs(kernPhi[0]) + Math.abs(kernPhi[k]);

        assertTrue(treePetal > treeSepal);
        assertTrue(kernPetal > kernSepal);
        // Same sign for dominant petal features on class 0
        assertEquals(Math.signum(treePhi[2 * k]), Math.signum(kernPhi[2 * k]));
        assertEquals(Math.signum(treePhi[3 * k]), Math.signum(kernPhi[3 * k]));
    }

    @Test
    public void testGivenCategoricalDataFrameWhenComputingKernelShapThenHandlesNominalColumnsWithoutError() throws Exception {
        // Given: WeatherNominal has nominal/categorical columns
        var weather = new WeatherNominal();
        DecisionTree tree = DecisionTree.fit(weather.formula(), weather.data(), new DecisionTree.Options(SplitRule.GINI, 10, 20, 1));
        KernelSHAP<Tuple> explainer = KernelSHAP.of(weather.data(), tree);

        // When
        Tuple row = weather.data().get(0);
        double[] phi = explainer.shap(row);
        double[] agg = explainer.shap(weather.data());

        // Then
        int m = tree.schema().length();
        int k = tree.numClasses();
        assertEquals(m * k, phi.length);
        assertEquals(m * k, agg.length);

        double[] prob = new double[k];
        tree.predict(row, prob);
        double[] base = explainer.baseValues();
        for (int c = 0; c < k; c++) {
            double sum = 0.0;
            for (int j = 0; j < m; j++) {
                sum += phi[j * k + c];
            }
            assertEquals(prob[c] - base[c], sum, 1E-6);
        }
    }

    @Test
    public void testGivenHighDimensionalInputWhenComputingKernelShapThenPairedSamplingPathRecoversLinearWeights() {
        // Given: M = 16 features (2^16 - 2 = 65534 > maxSamples = 600, triggering paired shell + MC sampling)
        int M = 16;
        double[] beta = new double[M];
        for (int j = 0; j < M; j++) {
            beta[j] = (j % 2 == 0 ? 1.0 : -1.0) * (j + 1) * 0.25;
        }
        int N = 20;
        double[][] bg = new double[N][M];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                bg[i][j] = MathEx.random(-2.0, 2.0);
            }
        }
        double[] bgMean = MathEx.colMeans(bg);
        ToDoubleFunction<double[]> f = x -> {
            double s = 1.25;
            for (int j = 0; j < M; j++) s += beta[j] * x[j];
            return s;
        };

        KernelSHAP<double[]> explainer = KernelSHAP.of(bg, f, new KernelSHAP.Options(800, 1E-8));
        double[] x = new double[M];
        for (int j = 0; j < M; j++) {
            x[j] = 1.5;
        }

        // When
        double[] phi = explainer.shap(x);

        // Then: efficiency holds to machine precision and linear attributions are closely approximated
        assertEquals(f.applyAsDouble(x) - explainer.baseValues()[0], MathEx.sum(phi), 1E-6);
        for (int j = 0; j < M; j++) {
            double exact = beta[j] * (x[j] - bgMean[j]);
            assertEquals(exact, phi[j], 1E-2);
        }
    }

    @Test
    public void testGivenEdgeCasesWhenComputingKernelShapThenHandlesSingleFeatureConstantAndCollinearFeatures() {
        // 1. M = 0 features
        double[][] emptyBg = {new double[0], new double[0]};
        KernelSHAP<double[]> zeroFeatureShap = KernelSHAP.of(emptyBg, (ToDoubleFunction<double[]>) x -> 42.0);
        assertEquals(0, zeroFeatureShap.shap(new double[0]).length);

        // 2. M = 1 feature
        double[][] singleBg = {{1.0}, {3.0}};
        KernelSHAP<double[]> singleFeatureShap = KernelSHAP.of(singleBg, (ToDoubleFunction<double[]>) x -> 5.0 * x[0] + 1.0);
        double[] singlePhi = singleFeatureShap.shap(new double[]{4.0});
        assertEquals(1, singlePhi.length);
        assertEquals(5.0 * (4.0 - 2.0), singlePhi[0], 1E-9);

        // 3. Constant model f(x) = 7.0 -> all SHAP values are 0
        double[][] bg = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}};
        KernelSHAP<double[]> constShap = KernelSHAP.of(bg, (ToDoubleFunction<double[]>) x -> 7.0);
        double[] constPhi = constShap.shap(new double[]{10.0, 20.0, 30.0});
        assertArrayEquals(new double[]{0.0, 0.0, 0.0}, constPhi, 1E-9);

        // 4. Symmetric/identical features (feature 0 and feature 2 have identical values and weights)
        Function<double[], double[]> symFunc = x -> new double[]{3.0 * x[0] + 2.0 * x[1] + 3.0 * x[2]};
        double[][] symBg = {{0.0, 0.0, 0.0}, {2.0, 2.0, 2.0}};
        KernelSHAP<double[]> symShap = KernelSHAP.of(symBg, symFunc);
        double[] symPhi = symShap.shap(new double[]{4.0, 1.0, 4.0});
        // Feature 0 (first) and Feature 2 (eliminated last) must receive identical attribution
        assertEquals(symPhi[0], symPhi[2], 1E-7);
        assertEquals(9.0, symPhi[0], 1E-5);
        assertEquals(0.0, symPhi[1], 1E-5);

        // 5. Empty stream aggregation
        assertEquals(0, symShap.shap(Stream.empty()).length);
    }

    @Test
    public void testGivenOptionsAndSampleHelpersWhenConfiguredThenRoundTripsAndValidates() throws Exception {
        // Given / When
        KernelSHAP.Options opts = new KernelSHAP.Options(512, 1E-6);
        Properties props = opts.toProperties();
        KernelSHAP.Options parsed = KernelSHAP.Options.of(props);

        // Then
        assertEquals(512, parsed.numSamples());
        assertEquals(1E-6, parsed.ridge(), 1E-12);
        assertEquals(new KernelSHAP.Options(), KernelSHAP.Options.of(null));

        assertThrows(IllegalArgumentException.class, () -> new KernelSHAP.Options(-1, 1E-8));
        assertThrows(IllegalArgumentException.class, () -> new KernelSHAP.Options(100, -1.0));
        assertThrows(IllegalArgumentException.class, () -> new KernelSHAP.Options(100, Double.NaN));

        var cpu = new CPU();
        DataFrame sampledDf = KernelSHAP.sample(cpu.data(), 16);
        assertEquals(16, sampledDf.size());
        assertSame(sampledDf, KernelSHAP.sample(sampledDf, 32));
        assertThrows(IllegalArgumentException.class, () -> KernelSHAP.sample(cpu.data(), 0));
    }
}
