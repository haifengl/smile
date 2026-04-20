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
package smile.validation.metric;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CrossEntropy (multiclass log loss).
 */
public class CrossEntropyTest {

    /**
     * All samples predicted at the same confidence 0.9 for the correct class.
     * CE = mean(-log(0.9)) = -log(0.9) for every sample.
     */
    @Test
    public void givenUniformHighConfidence_whenCrossEntropyComputed_thenMatchesNegLog() {
        int[] truth = {0, 1, 2, 0};
        double[][] prob = {
            {0.90, 0.05, 0.05},
            {0.05, 0.90, 0.05},
            {0.05, 0.05, 0.90},
            {0.90, 0.05, 0.05}
        };
        assertEquals(-Math.log(0.90), CrossEntropy.of(truth, prob), 1E-10);
    }

    /**
     * Perfect predictions (probability 1 on the correct class):
     * CE = mean(-log(1)) = 0.
     */
    @Test
    public void givenPerfectPredictions_whenCrossEntropyComputed_thenReturns0() {
        int[] truth = {0, 1, 2};
        double[][] prob = {
            {1.0, 0.0, 0.0},
            {0.0, 1.0, 0.0},
            {0.0, 0.0, 1.0}
        };
        assertEquals(0.0, CrossEntropy.of(truth, prob), 1E-10);
    }

    /**
     * Binary case: CrossEntropy should agree with LogLoss when there are only
     * two classes and we extract the positive class column.
     * truth=[0,1], prob=[[0.8,0.2],[0.3,0.7]]
     * CE = -(log(0.8)+log(0.7))/2
     */
    @Test
    public void givenBinaryProbabilities_whenCrossEntropyComputed_thenMatchesTwoClassFormula() {
        int[] truth = {0, 1};
        double[][] prob = {
            {0.8, 0.2},
            {0.3, 0.7}
        };
        double expected = -(Math.log(0.8) + Math.log(0.7)) / 2.0;
        assertEquals(expected, CrossEntropy.of(truth, prob), 1E-10);
    }

    @Test
    public void givenSingleSample_whenCrossEntropyComputed_thenReturnsNegLogOfCorrectClassProb() {
        int[] truth = {1};
        double[][] prob = {{0.2, 0.5, 0.3}};
        assertEquals(-Math.log(0.5), CrossEntropy.of(truth, prob), 1E-10);
    }

    @Test
    public void givenMismatchedLengths_whenCrossEntropyComputed_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            CrossEntropy.of(new int[]{0, 1}, new double[][]{{0.9, 0.1}}));
    }

    @Test
    public void givenCrossEntropyIsNonNegative_whenComputedOnTypicalData_thenIsPositive() {
        int[] truth = {0, 1, 2, 0, 1, 2};
        double[][] prob = {
            {0.7, 0.2, 0.1},
            {0.1, 0.6, 0.3},
            {0.2, 0.1, 0.7},
            {0.8, 0.1, 0.1},
            {0.1, 0.7, 0.2},
            {0.1, 0.2, 0.7}
        };
        assertTrue(CrossEntropy.of(truth, prob) > 0.0);
    }
}
