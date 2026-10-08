/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.validation.metric;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Unit tests for Matthews correlation coefficient.
 * @author Haifeng Li
 **/
public class MatthewsCorrelationTest {
    /** Builds truth/prediction arrays realizing a 2x2 confusion (tp, tn, fp, fn). */
    private static int[][] confusion(int tp, int tn, int fp, int fn) {
        int n = tp + tn + fp + fn;
        int[] truth = new int[n], prediction = new int[n];
        int i = 0;
        for (int k = 0; k < tp; k++) { truth[i] = 1; prediction[i] = 1; i++; }
        for (int k = 0; k < tn; k++) { truth[i] = 0; prediction[i] = 0; i++; }
        for (int k = 0; k < fp; k++) { truth[i] = 0; prediction[i] = 1; i++; }
        for (int k = 0; k < fn; k++) { truth[i] = 1; prediction[i] = 0; i++; }
        return new int[][]{truth, prediction};
    }

    /**
     * A 90%-accurate classifier on 110k samples: tp*tn = 2.5e9 overflows int.
     * The numerator must be computed in long, else the sign flips (-0.6016).
     * sklearn.metrics.matthews_corrcoef reports 9/11 = 0.8181818181818182.
     */
    @Test
    public void givenLargeSample_whenMCCComputed_thenNoIntOverflow() {
        int[][] c = confusion(50000, 50000, 5000, 5000);
        assertEquals(0.8181818181818182, MatthewsCorrelation.of(c[0], c[1]), 1E-9);
    }

    /**
     * A constant predictor zeroes a confusion marginal, so the denominator is 0.
     * MCC is 0 by convention (matches sklearn) rather than 0/0 = NaN.
     */
    @Test
    public void givenConstantPredictor_whenMCCComputed_thenReturnsZero() {
        int[] truth      = {1, 1, 0, 0, 1, 0};
        int[] prediction = {0, 0, 0, 0, 0, 0};
        assertEquals(0.0, MatthewsCorrelation.of(truth, prediction), 1E-10);
    }

    /** MCC is a correlation coefficient, so it must stay within [-1, 1]. */
    @Test
    public void givenVariousConfusions_whenMCCComputed_thenInUnitInterval() {
        int[][] cases = {
            {30, 60, 5, 5}, {90, 5, 3, 2}, {2, 2, 2, 2},
            {50000, 50000, 5000, 5000}, {1, 100, 100, 1}, {0, 4, 0, 2}
        };
        for (int[] tc : cases) {
            int[][] c = confusion(tc[0], tc[1], tc[2], tc[3]);
            double mcc = MatthewsCorrelation.of(c[0], c[1]);
            assertTrue(mcc >= -1.0 && mcc <= 1.0, "MCC out of [-1,1]: " + mcc);
        }
    }


    @Test
    void testPerfectPositiveCorrelation() {
        int[] truth = {1, 0, 1, 1, 0, 0, 1, 0};
        int[] prediction = {1, 0, 1, 1, 0, 0, 1, 0};
        assertEquals(1.0, MatthewsCorrelation.of(truth, prediction), 1E-10);
    }

    @Test
    void testPerfectNegativeCorrelation() {
        int[] truth = {1, 0, 1, 1, 0, 0, 1, 0};
        int[] prediction = {0, 1, 0, 0, 1, 1, 0, 1};
        assertEquals(-1.0, MatthewsCorrelation.of(truth, prediction), 1E-10);
    }

    @Test
    void testUncorrelated() {
        int[] truth = {0, 0, 1, 1, 0, 0, 1, 1};
        int[] prediction = {0, 1, 0, 1, 0, 1, 0, 1};
        assertEquals(0.0, MatthewsCorrelation.of(truth, prediction), 1E-10);
    }

    @Test
    void testKnownConfusion() {
        int[][] c = confusion(40, 30, 20, 10);
        assertEquals(1.0 / Math.sqrt(6), MatthewsCorrelation.of(c[0], c[1]), 1E-9);
    }

    @Test
    void testScoreAndToString() {
        int[] truth = {1, 0, 1, 1, 0};
        int[] prediction = {1, 0, 1, 0, 0};
        MatthewsCorrelation mcc = new MatthewsCorrelation();
        assertEquals(mcc.score(truth, prediction), MatthewsCorrelation.of(truth, prediction), 1E-10);
        assertEquals(MatthewsCorrelation.instance.score(truth, prediction), MatthewsCorrelation.of(truth, prediction), 1E-10);
        assertEquals("MatthewsCorrelation", mcc.toString());
    }

    @Test
    void testMismatchedLengths() {
        int[] truth = {1, 0, 1};
        int[] prediction = {1, 0};
        assertThrows(IllegalArgumentException.class, () -> MatthewsCorrelation.of(truth, prediction));
    }

    @Test
    void testNonBinaryClassification() {
        int[] truth = {0, 1, 2};
        int[] prediction = {0, 1, 2};
        assertThrows(IllegalArgumentException.class, () -> MatthewsCorrelation.of(truth, prediction));
    }
} // class MatthewsCorrelationTest
