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
package smile.model.svm;

import org.junit.jupiter.api.Test;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.LinearKernel;
import smile.math.kernel.MercerKernel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted robustness and correctness tests for SVM helpers in {@code smile.model.svm}.
 */
public class SVMRobustnessTest {

    // -------------------------------------------------------------------------
    // SVR constructor validation
    // -------------------------------------------------------------------------

    @Test
    public void testGivenInvalidSVRHyperparametersWhenConstructingThenThrowsMeaningfulException() {
        // eps <= 0
        assertThrows(IllegalArgumentException.class, () -> new SVR<>(new LinearKernel(), 0.0, 1.0, 1E-3));
        // C < 0
        assertThrows(IllegalArgumentException.class, () -> new SVR<>(new LinearKernel(), 0.1, -1.0, 1E-3));
        // tol <= 0
        assertThrows(IllegalArgumentException.class, () -> new SVR<>(new LinearKernel(), 0.1, 1.0, 0.0));
    }

    // -------------------------------------------------------------------------
    // SVR fit validation
    // -------------------------------------------------------------------------

    @Test
    public void testGivenEmptySVRTrainingDataWhenFittingThenThrowsMeaningfulException() {
        // Given
        SVR<double[]> svr = new SVR<>(new LinearKernel(), 0.1, 1.0, 1E-3);

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> svr.fit(new double[0][], new double[0]));

        // Then
        assertTrue(exception.getMessage().contains("Empty training data"));
    }

    @Test
    public void testGivenMismatchedSVRTrainingDataWhenFittingThenThrowsMeaningfulException() {
        // Given
        SVR<double[]> svr = new SVR<>(new LinearKernel(), 0.1, 1.0, 1E-3);

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> svr.fit(new double[][] {{1.0}}, new double[] {1.0, 2.0}));

        // Then
        assertTrue(exception.getMessage().contains("don't match"));
    }

    // -------------------------------------------------------------------------
    // SVR end-to-end: simple linear relationship y = 2x
    // -------------------------------------------------------------------------

    @Test
    public void testGivenLinearTrainingDataWhenFittingWithSVRThenPredictionsCloseToTruth() {
        // Given
        int n = 20;
        double[][] x = new double[n][1];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i][0] = i * 0.5;
            y[i] = 2.0 * x[i][0];
        }

        // When
        SVR<double[]> svr = new SVR<>(new LinearKernel(), 0.5, 10.0, 1E-3);
        smile.regression.KernelMachine<double[]> model = svr.fit(x, y);

        // Then — predictions should be close to 2*xi (within epsilon + some margin)
        for (int i = 0; i < n; i++) {
            double predicted = model.predict(x[i]);
            assertEquals(y[i], predicted, 2.0,
                    "Prediction at x=" + x[i][0] + " should be close to " + y[i]);
        }
    }

    // -------------------------------------------------------------------------
    // OCSVM constructor validation
    // -------------------------------------------------------------------------

    @Test
    public void testGivenInvalidOCSVMHyperparametersWhenConstructingThenThrowsMeaningfulException() {
        // nu <= 0
        assertThrows(IllegalArgumentException.class, () -> new OCSVM<>(new LinearKernel(), 0.0, 1E-3));
        // nu > 1
        assertThrows(IllegalArgumentException.class, () -> new OCSVM<>(new LinearKernel(), 1.1, 1E-3));
        // tol <= 0
        assertThrows(IllegalArgumentException.class, () -> new OCSVM<>(new LinearKernel(), 0.5, 0.0));
    }

    // -------------------------------------------------------------------------
    // OCSVM fit validation
    // -------------------------------------------------------------------------

    @Test
    public void testGivenEmptyOCSVMTrainingDataWhenFittingThenThrowsMeaningfulException() {
        // Given
        OCSVM<double[]> ocsvm = new OCSVM<>(new LinearKernel(), 0.5, 1E-3);

        // When
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ocsvm.fit(new double[0][]));

        // Then
        assertTrue(exception.getMessage().contains("Empty training data"));
    }

    /**
     * When nu * n rounds to 0 (e.g. n=1, nu=0.1 → round(0.1)=0), the fix
     * clamps vl to 1 so C stays finite and training completes without error.
     */
    @Test
    public void testGivenVerySmallNuWhenFittingOCSVMThenDoesNotProduceInfiniteC() {
        // Given: n=1, nu=0.1 → Math.round(0.1 * 1) = 0; before fix C = Infinity.
        double[][] x = {{1.0, 0.0}};
        OCSVM<double[]> ocsvm = new OCSVM<>(new LinearKernel(), 0.1, 1E-3);

        // When / Then: must not throw and the resulting model's intercept must be finite.
        KernelMachine<double[]> model = assertDoesNotThrow(() -> ocsvm.fit(x));
        assertTrue(Double.isFinite(model.intercept()),
                "Model intercept should be finite when vl is clamped to 1");
    }

    // -------------------------------------------------------------------------
    // OCSVM end-to-end: compact inlier cluster vs outlier
    // -------------------------------------------------------------------------

    @Test
    public void testGivenTightClusterWhenFittingOCSVMThenInliersScoreHigherThanOutlier() {
        // Given: 10 points tightly around origin
        int n = 10;
        double[][] x = new double[n][2];
        for (int i = 0; i < n; i++) {
            x[i][0] = 0.1 * Math.cos(2 * Math.PI * i / n);
            x[i][1] = 0.1 * Math.sin(2 * Math.PI * i / n);
        }
        double[] outlier = {100.0, 100.0};

        // When
        OCSVM<double[]> ocsvm = new OCSVM<>(new GaussianKernel(1.0), 0.5, 1E-3);
        KernelMachine<double[]> model = ocsvm.fit(x);

        // Then: score at origin should exceed score at far outlier
        double scoreInlier = model.score(new double[]{0.0, 0.0});
        double scoreOutlier = model.score(outlier);
        assertTrue(scoreInlier > scoreOutlier,
                "Inlier score (" + scoreInlier + ") should exceed outlier score (" + scoreOutlier + ")");
    }

    // -------------------------------------------------------------------------
    // LASVM constructor validation
    // -------------------------------------------------------------------------

    @Test
    public void testGivenInvalidLASVMHyperparametersWhenConstructingThenThrowsMeaningfulException() {
        // Cp < 0
        assertThrows(IllegalArgumentException.class,
                () -> new LASVM<>(new LinearKernel(), -0.1, 1.0, 1E-3));
        // Cn < 0
        assertThrows(IllegalArgumentException.class,
                () -> new LASVM<>(new LinearKernel(), 1.0, -0.1, 1E-3));
        // tol <= 0
        assertThrows(IllegalArgumentException.class,
                () -> new LASVM<>(new LinearKernel(), 1.0, 1.0, 0.0));
        // single-C constructor with negative C
        assertThrows(IllegalArgumentException.class,
                () -> new LASVM<>(new LinearKernel(), -1.0, 1E-3));
    }

    // -------------------------------------------------------------------------
    // LASVM fit validation
    // -------------------------------------------------------------------------

    @Test
    public void testGivenEmptyLASVMTrainingDataWhenFittingThenThrowsMeaningfulException() {
        // Given
        LASVM<double[]> lasvm = new LASVM<>(new LinearKernel(), 1.0, 1E-3);

        // When / Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> lasvm.fit(new double[0][], new int[0], 1));
        assertTrue(exception.getMessage().contains("Empty training data"));
    }

    @Test
    public void testGivenMismatchedLASVMTrainingDataWhenFittingThenThrowsMeaningfulException() {
        // Given
        LASVM<double[]> lasvm = new LASVM<>(new LinearKernel(), 1.0, 1E-3);
        double[][] x = {{1.0}, {2.0}};
        int[] y = {1};

        // When / Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> lasvm.fit(x, y, 1));
        assertTrue(exception.getMessage().contains("don't match"));
    }

    @Test
    public void testGivenZeroEpochsWhenFittingLASVMThenThrowsMeaningfulException() {
        // Given
        LASVM<double[]> lasvm = new LASVM<>(new LinearKernel(), 1.0, 1E-3);
        double[][] x = {{1.0}, {-1.0}};
        int[] y = {1, -1};

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> lasvm.fit(x, y, 0));
    }

    @Test
    public void testGivenNegativeEpochsWhenFittingLASVMThenThrowsMeaningfulException() {
        // Given
        LASVM<double[]> lasvm = new LASVM<>(new LinearKernel(), 1.0, 1E-3);
        double[][] x = {{1.0}, {-1.0}};
        int[] y = {1, -1};

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> lasvm.fit(x, y, -5));
    }

    // -------------------------------------------------------------------------
    // LASVM end-to-end: linearly separable 1-D problem
    // -------------------------------------------------------------------------

    @Test
    public void testGivenLinearlySeparableDataWhenFittingWithLASVMThenKernelMachineClassifiesCorrectly() {
        // Given: positive class at x > 0, negative at x < 0
        double[][] x = {
            {-3.0}, {-2.0}, {-1.5}, {-1.0}, {-0.5},
            {0.5},  {1.0},  {1.5},  {2.0},  {3.0}
        };
        int[] y = {-1, -1, -1, -1, -1, 1, 1, 1, 1, 1};

        // When
        LASVM<double[]> lasvm = new LASVM<>(new LinearKernel(), 1.0, 1E-3);
        KernelMachine<double[]> model = lasvm.fit(x, y, 1);

        // Then: all training points should have the correct sign
        int correct = 0;
        for (int i = 0; i < x.length; i++) {
            double score = model.score(x[i]);
            if ((score > 0 && y[i] == 1) || (score < 0 && y[i] == -1)) {
                correct++;
            }
        }
        assertTrue(correct >= 8,
                "At least 8/10 training points should be classified correctly, got " + correct);
    }

    // -------------------------------------------------------------------------
    // KernelMachine score correctness
    // -------------------------------------------------------------------------

    @Test
    public void testGivenKnownWeightsWhenScoringKernelMachineThenResultMatchesExpectedSum() {
        // Given: f(x) = b + sum_i w_i * k(x, v_i), linear kernel k(x,v)=dot(x,v)
        // v0={1,0}, v1={0,1}, w0=2, w1=-1, b=0.5
        // query x={3,4} → f = 0.5 + 2*(3*1+4*0) + (-1)*(3*0+4*1) = 0.5 + 6 - 4 = 2.5
        KernelMachine<double[]> km = new KernelMachine<>(
                new LinearKernel(),
                new double[][] {{1.0, 0.0}, {0.0, 1.0}},
                new double[] {2.0, -1.0},
                0.5
        );
        double[] query = {3.0, 4.0};

        // When
        double score = km.score(query);

        // Then
        assertEquals(2.5, score, 1E-12);
    }

    @Test
    public void testGivenKernelMachineWhenCallingToStringThenContainsVectorCount() {
        // Given
        KernelMachine<double[]> km = new KernelMachine<>(
                new LinearKernel(),
                new double[][] {{1.0}, {2.0}, {3.0}},
                new double[] {1.0, 1.0, 1.0}
        );

        // When
        String desc = km.toString();

        // Then
        assertTrue(desc.contains("3"), "toString should mention the 3 support vectors: " + desc);
    }

    // -------------------------------------------------------------------------
    // LinearKernelMachine invalid kernel type rejection
    // -------------------------------------------------------------------------

    @Test
    public void testGivenNonLinearKernelMachineWhenConvertingWithOfThenThrowsIllegalArgumentException() {
        // Given: GaussianKernel is not a LinearKernel
        KernelMachine<double[]> km = new KernelMachine<>(
                new GaussianKernel(1.0),
                new double[][] {{1.0, 0.0}},
                new double[] {1.0}
        );

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> LinearKernelMachine.of(km));
    }

    @Test
    public void testGivenNonBinarySparseKernelMachineWhenConvertingWithBinaryThenThrowsIllegalArgumentException() {
        // Given: an arbitrary MercerKernel<int[]> that is not BinarySparseLinearKernel
        MercerKernel<int[]> notBinary = new MercerKernel<>() {
            @Override public double k(int[] x, int[] y) { return 0; }
            @Override public double[] kg(int[] x, int[] y) { return new double[]{0}; }
            @Override public MercerKernel<int[]> of(double[] params) { return this; }
            @Override public double[] hyperparameters() { return new double[0]; }
            @Override public double[] lo() { return new double[0]; }
            @Override public double[] hi() { return new double[0]; }
        };
        KernelMachine<int[]> km = new KernelMachine<>(notBinary, new int[][] {{0}}, new double[] {1.0});

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> LinearKernelMachine.binary(1, km));
    }
}
