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
 * Unit tests for MAD (Mean Absolute Error).
 */
public class MADTest {

    @Test
    public void givenOneOffPrediction_whenMADComputed_thenReturnsMeanAbsoluteError() {
        // |5-4|=1 only, mean = 1/5 = 0.2
        double[] truth      = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] prediction = {1.0, 2.0, 3.0, 4.0, 4.0};
        assertEquals(0.2, MAD.of(truth, prediction), 1E-10);
    }

    @Test
    public void givenPerfectPrediction_whenMADComputed_thenReturns0() {
        double[] truth = {1.0, 2.0, 3.0, 4.0, 5.0};
        assertEquals(0.0, MAD.of(truth, truth), 1E-10);
    }

    @Test
    public void givenConstantOffset_whenMADComputed_thenReturnsOffset() {
        // every prediction off by 3.0
        double[] truth      = {1.0, 2.0, 3.0};
        double[] prediction = {4.0, 5.0, 6.0};
        assertEquals(3.0, MAD.of(truth, prediction), 1E-10);
    }

    @Test
    public void givenSymmetricError_whenMADComputed_thenSameForwardAndBackward() {
        double[] truth      = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] prediction = {1.0, 2.0, 3.0, 4.0, 4.0};
        assertEquals(MAD.of(truth, prediction), MAD.of(prediction, truth), 1E-10);
    }

    @Test
    public void givenSingleElement_whenMADComputed_thenReturnsSingleAbsoluteError() {
        assertEquals(2.5, MAD.of(new double[]{5.0}, new double[]{2.5}), 1E-10);
    }

    @Test
    public void givenMADInstance_whenScoreCalled_thenMatchesStaticOf() {
        double[] truth      = {1.0, 3.0, 5.0};
        double[] prediction = {2.0, 3.0, 4.0};
        MAD instance = new MAD();
        assertEquals(MAD.of(truth, prediction), instance.score(truth, prediction), 1E-10);
    }

    @Test
    public void givenMADInstance_whenToStringCalled_thenReturnsMAD() {
        assertEquals("MAD", new MAD().toString());
    }

    @Test
    public void givenMismatchedLengths_whenMADComputed_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            MAD.of(new double[]{1.0, 2.0}, new double[]{1.0}));
    }

    @Test
    public void givenNegativeAndPositiveErrors_whenMADComputed_thenAllErrorsAreAbsolute() {
        // errors: +1, -1, +2, -2 → mean absolute = 1.5
        double[] truth      = {0.0, 2.0, 0.0, 4.0};
        double[] prediction = {1.0, 1.0, 2.0, 2.0};
        assertEquals(1.5, MAD.of(truth, prediction), 1E-10);
    }
}
