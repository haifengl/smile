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
 * Unit tests for Error metric.
 */
public class ErrorTest {

    @Test
    public void givenTwoMismatches_whenErrorComputed_thenReturns2() {
        int[] truth      = {1, 0, 1, 0, 1};
        int[] prediction = {1, 0, 0, 1, 1};
        // positions 2 and 3 differ
        assertEquals(2, Error.of(truth, prediction));
    }

    @Test
    public void givenPerfectPrediction_whenErrorComputed_thenReturns0() {
        int[] truth = {1, 0, 1, 0, 1};
        assertEquals(0, Error.of(truth, truth));
    }

    @Test
    public void givenAllWrong_whenErrorComputed_thenReturnsLength() {
        int[] truth      = {1, 0, 1, 0, 1};
        int[] prediction = {0, 1, 0, 1, 0};
        assertEquals(truth.length, Error.of(truth, prediction));
    }

    @Test
    public void givenSingleElement_whenMatch_thenReturns0() {
        assertEquals(0, Error.of(new int[]{3}, new int[]{3}));
    }

    @Test
    public void givenSingleElement_whenMismatch_thenReturns1() {
        assertEquals(1, Error.of(new int[]{3}, new int[]{5}));
    }

    @Test
    public void givenMulticlassLabels_whenComputingError_thenCountsCorrectly() {
        int[] truth      = {0, 1, 2, 0, 1, 2};
        int[] prediction = {0, 1, 2, 1, 1, 0};
        // positions 3 (0 vs 1) and 5 (2 vs 0) differ
        assertEquals(2, Error.of(truth, prediction));
    }

    @Test
    public void givenErrorInstance_whenScoreCalled_thenReturnsDoubleCount() {
        int[] truth      = {1, 0, 1};
        int[] prediction = {0, 0, 1};
        // 1 mismatch at position 0
        Error instance = new Error();
        assertEquals(1.0, instance.score(truth, prediction), 1E-10);
    }

    @Test
    public void givenMismatchedLengths_whenErrorComputed_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            Error.of(new int[]{1, 0}, new int[]{1}));
    }

    @Test
    public void givenErrorInstance_whenToStringCalled_thenReturnsErrorLabel() {
        assertEquals("Error", new Error().toString());
    }

    @Test
    public void givenErrorAndAccuracy_whenComputedOnSameData_thenSumToN() {
        int[] truth      = {1, 0, 1, 0, 1, 0, 1, 0, 1, 0};
        int[] prediction = {1, 0, 1, 0, 1, 0, 0, 1, 1, 0};
        int n = truth.length;
        int errors = Error.of(truth, prediction);
        double acc = Accuracy.of(truth, prediction);
        // errors / n + accuracy == 1
        assertEquals(1.0, (double) errors / n + acc, 1E-10);
    }
}
