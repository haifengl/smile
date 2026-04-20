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

import org.junit.jupiter.api.Test;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BinarySparseLinearSVM verifying regression (continuous)
 * output — the critical bug fix that returned ±1 classification labels.
 */
class BinarySparseLinearSVMTest {

    /**
     * Build a larger binary-sparse training set.
     * Feature 0 is correlated with positive y, feature 1 with negative y.
     * p = 4 (vocabulary size).  Using more samples than features ensures the
     * SVR can learn non-trivial weights.
     */
    private static final int p = 4;

    // 20 samples: feature-0 on → large positive y; feature-1 on → large negative y
    private static final int[][] X = {
            {1, 0, 0, 0}, {1, 0, 1, 0}, {1, 0, 0, 1}, {1, 0, 1, 1},
            {1, 0, 0, 0}, {1, 0, 1, 0}, {1, 0, 0, 1}, {1, 0, 1, 1},
            {1, 1, 0, 0}, {1, 1, 1, 0},  // both f0 and f1 → intermediate
            {0, 1, 0, 0}, {0, 1, 1, 0}, {0, 1, 0, 1}, {0, 1, 1, 1},
            {0, 1, 0, 0}, {0, 1, 1, 0}, {0, 1, 0, 1}, {0, 1, 1, 1},
            {0, 0, 1, 0}, {0, 0, 0, 1},  // no f0 or f1 → near-zero
    };

    private static final double[] Y = {
             5.0,  4.8,  5.2,  4.9,
             5.1,  4.7,  5.3,  5.0,
             0.1,  0.2,
            -5.0, -4.8, -5.2, -4.9,
            -5.1, -4.7, -5.3, -5.0,
             0.0, -0.1,
    };

    private BinarySparseLinearSVM train() {
        MathEx.setSeed(12345);
        SVM.Options opts = new SVM.Options(0.5, 100.0, 1E-3);
        return SVM.fit(X, Y, p, opts);
    }

    // ── predict returns continuous value (bug fix) ─────────────────────────────

    @Test
    void givenTrainedModel_whenPredict_thenReturnsFiniteDouble() {
        // Before the fix, predict() returned +1.0 or -1.0 (classification labels).
        // After the fix, it returns model.f(x) — a continuous regression score.
        BinarySparseLinearSVM model = train();

        double yhat = model.predict(new int[]{1, 0, 0, 0});

        assertTrue(Double.isFinite(yhat), "Prediction must be a finite number");
    }

    @Test
    void givenTrainedModel_whenPredict_thenCanProduceValueOutside1() {
        // This directly tests the regression-vs-classification bug:
        // a regression model must produce values with magnitude > 1 when
        // the training targets span ±5.
        BinarySparseLinearSVM model = train();

        double pos = model.predict(new int[]{1, 0, 0, 0});  // target ≈ +5
        double neg = model.predict(new int[]{0, 1, 0, 0});  // target ≈ -5

        // With the buggy code |pos| == 1 and |neg| == 1.
        // With the fix the predictions should have larger magnitudes.
        assertTrue(Math.abs(pos) > 1.0 || Math.abs(neg) > 1.0,
                "At least one prediction should have magnitude > 1 (not restricted to ±1); "
                        + "pos=" + pos + ", neg=" + neg);
    }

    // ── weights and intercept accessors ───────────────────────────────────────

    @Test
    void givenTrainedModel_whenWeights_thenLengthEqualsP() {
        BinarySparseLinearSVM model = train();
        assertEquals(p, model.weights().length,
                "Weight vector length must equal the declared vocabulary size p");
    }

    @Test
    void givenTrainedModel_whenWeights_thenAllFinite() {
        BinarySparseLinearSVM model = train();
        for (double w : model.weights()) {
            assertTrue(Double.isFinite(w), "All weights must be finite");
        }
    }

    @Test
    void givenTrainedModel_whenIntercept_thenFinite() {
        BinarySparseLinearSVM model = train();
        assertTrue(Double.isFinite(model.intercept()), "Intercept must be finite");
    }

    // ── SVM.fit factory consistency ───────────────────────────────────────────

    @Test
    void givenFitViaFactory_whenPredict_thenConsistentWithDirectModel() {
        MathEx.setSeed(42);
        SVM.Options opts = new SVM.Options(0.5, 100.0, 1E-3);
        BinarySparseLinearSVM m1 = SVM.fit(X, Y, p, opts);
        BinarySparseLinearSVM m2 = SVM.fit(X, Y, p, opts);

        // Two identical fits on the same data must produce the same prediction.
        int[] query = {1, 0, 0, 0};
        assertEquals(m1.predict(query), m2.predict(query), 1E-10,
                "Deterministic fits should produce identical predictions");
    }
}
