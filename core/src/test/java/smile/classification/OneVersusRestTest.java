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
package smile.classification;

import org.junit.jupiter.api.Test;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OneVersusRest: fit, predict, and isSoft consistency.
 */
class OneVersusRestTest {

    /**
     * Three well-separated 2-D clusters.
     * Class 0: near (0,0), class 1: near (10,0), class 2: near (5,9).
     */
    private static final double[][] DATA = {
            {0.0, 0.0}, {0.5, 0.0}, {0.0, 0.5},          // class 0
            {10.0, 0.0}, {10.5, 0.0}, {10.0, 0.5},        // class 1
            {5.0, 9.0}, {5.5, 9.0}, {5.0, 9.5}            // class 2
    };
    private static final int[] LABELS = {0, 0, 0, 1, 1, 1, 2, 2, 2};

    // ── isSoft consistency (bug fix: was always true) ──────────────────────────

    @Test
    void givenClassifierWithoutScore_whenFit_thenIsSoftIsFalse() {
        // KNN does not override score(), so Platt scaling cannot be fitted.
        // Before the fix, isSoft() returned true unconditionally even when
        // predict() would throw UnsupportedOperationException.
        OneVersusRest<double[]> model = OneVersusRest.fit(DATA, LABELS,
                (x, y) -> KNN.fit(x, y, 3));

        assertFalse(model.isSoft(),
                "isSoft() must be false when Platt scaling could not be fitted");
    }

    @Test
    void givenClassifierWithoutScore_whenPredictWithPosteriori_thenThrows() {
        OneVersusRest<double[]> model = OneVersusRest.fit(DATA, LABELS,
                (x, y) -> KNN.fit(x, y, 3));

        // Hard predict (via score) also unavailable for OVR without Platt
        assertThrows(UnsupportedOperationException.class,
                () -> model.predict(new double[]{0.0, 0.0}),
                "predict(x) must throw when Platt is unavailable");

        assertThrows(UnsupportedOperationException.class,
                () -> model.predict(new double[]{0.0, 0.0}, new double[3]),
                "predict(x, posteriori) must throw when Platt is unavailable");
    }

    @Test
    void givenClassifierWithScore_whenFit_thenIsSoftIsTrue() {
        MathEx.setSeed(42);
        // LogisticRegression.Binomial supports score() so Platt fitting succeeds.
        OneVersusRest<double[]> model = OneVersusRest.fit(DATA, LABELS,
                (x, y) -> LogisticRegression.fit(x, y));

        assertTrue(model.isSoft(),
                "isSoft() must be true when Platt scaling was fitted successfully");
    }

    // ── basic fit and predict ─────────────────────────────────────────────────

    @Test
    void givenOnlyTwoClasses_whenFit_thenThrowsIllegalArgumentException() {
        double[][] x = {{0.0}, {1.0}, {2.0}, {3.0}};
        int[] y = {0, 0, 1, 1};
        assertThrows(IllegalArgumentException.class,
                () -> OneVersusRest.fit(x, y, (a, b) -> KNN.fit(a, b, 1)));
    }

    @Test
    void givenMismatchedXY_whenFit_thenThrowsIllegalArgumentException() {
        double[][] x = {{0.0}, {1.0}};
        int[] y = {0, 1, 2};
        assertThrows(IllegalArgumentException.class,
                () -> OneVersusRest.fit(x, y, (a, b) -> KNN.fit(a, b, 1)));
    }

    @Test
    void givenModelWithPlatt_whenPredict_thenReturnsOriginalLabel() {
        MathEx.setSeed(42);
        OneVersusRest<double[]> model = OneVersusRest.fit(DATA, LABELS,
                (x, y) -> LogisticRegression.fit(x, y));

        assertEquals(3, model.numClasses());

        // Well-separated clusters — predictions should be correct for the training centroids.
        int p0 = model.predict(new double[]{0.0, 0.0});
        int p1 = model.predict(new double[]{10.0, 0.0});
        int p2 = model.predict(new double[]{5.0, 9.0});

        assertTrue(p0 == 0 || p0 == 1 || p0 == 2, "Must return one of {0,1,2}");
        assertEquals(0, p0, "Centroid of class 0 should predict class 0");
        assertEquals(1, p1, "Centroid of class 1 should predict class 1");
        assertEquals(2, p2, "Centroid of class 2 should predict class 2");
    }

    @Test
    void givenModelWithPlatt_whenPredictWithPosteriori_thenProbabilitiesSumToOne() {
        MathEx.setSeed(42);
        OneVersusRest<double[]> model = OneVersusRest.fit(DATA, LABELS,
                (x, y) -> LogisticRegression.fit(x, y));

        double[] posteriori = new double[3];
        model.predict(new double[]{0.0, 0.0}, posteriori);

        double sum = 0;
        for (double p : posteriori) {
            assertTrue(p >= 0.0, "Probability must be non-negative");
            sum += p;
        }
        assertEquals(1.0, sum, 1E-9, "Posteriori must sum to 1");
    }
}
