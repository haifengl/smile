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
 * Unit tests for LogLoss (binary cross-entropy).
 */
public class LogLossTest {

    /**
     * Canonical numeric example.
     * truth = [0, 0, 1, 1, 0]
     * prob  = [0.1, 0.4, 0.35, 0.8, 0.1]
     *
     * Per-sample losses (natural log):
     *   i=0 (truth=0): -ln(0.9) ≈ 0.10536
     *   i=1 (truth=0): -ln(0.6) ≈ 0.51083
     *   i=2 (truth=1): -ln(0.35) ≈ 1.04982
     *   i=3 (truth=1): -ln(0.8) ≈ 0.22314
     *   i=4 (truth=0): -ln(0.9) ≈ 0.10536
     * Mean = 1.99451 / 5 ≈ 0.39890
     */
    @Test
    public void givenCanonicalExample_whenLogLossComputed_thenMatchesExpected() {
        int[]    truth = {0, 0, 1, 1, 0};
        double[] prob  = {0.1, 0.4, 0.35, 0.8, 0.1};
        assertEquals(0.398902, LogLoss.of(truth, prob), 1E-5);
    }

    @Test
    public void givenPerfectPositivePrediction_whenLogLossComputed_thenReturns0() {
        // truth=1, prob=1.0 → -log(1) = 0
        assertEquals(0.0, LogLoss.of(new int[]{1}, new double[]{1.0}), 1E-10);
    }

    @Test
    public void givenPerfectNegativePrediction_whenLogLossComputed_thenReturns0() {
        // truth=0, prob=0.0 → -log(1-0) = 0
        assertEquals(0.0, LogLoss.of(new int[]{0}, new double[]{0.0}), 1E-10);
    }

    @Test
    public void givenHalfProbability_whenLogLossComputed_thenReturnsLog2() {
        // Both truth=0 and truth=1 at p=0.5 produce -log(0.5) = log(2)
        int[]    truth = {0, 1};
        double[] prob  = {0.5, 0.5};
        assertEquals(Math.log(2), LogLoss.of(truth, prob), 1E-10);
    }

    @Test
    public void givenAllPositives_whenLogLossComputed_thenReturnsNegativeMeanLogProb() {
        int[]    truth = {1, 1, 1};
        double[] prob  = {0.9, 0.8, 0.7};
        double expected = -(Math.log(0.9) + Math.log(0.8) + Math.log(0.7)) / 3.0;
        assertEquals(expected, LogLoss.of(truth, prob), 1E-10);
    }

    @Test
    public void givenAllNegatives_whenLogLossComputed_thenReturnsNegativeMeanLogComplement() {
        int[]    truth = {0, 0, 0};
        double[] prob  = {0.1, 0.2, 0.3};
        double expected = -(Math.log(0.9) + Math.log(0.8) + Math.log(0.7)) / 3.0;
        assertEquals(expected, LogLoss.of(truth, prob), 1E-10);
    }

    @Test
    public void givenLogLossInstance_whenScoreCalled_thenMatchesStaticOf() {
        int[]    truth = {0, 1, 0};
        double[] prob  = {0.2, 0.7, 0.3};
        LogLoss instance = new LogLoss();
        assertEquals(LogLoss.of(truth, prob), instance.score(truth, prob), 1E-10);
    }

    @Test
    public void givenNonBinaryLabel_whenLogLossComputed_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            LogLoss.of(new int[]{2}, new double[]{0.5}));
    }

    @Test
    public void givenMismatchedLengths_whenLogLossComputed_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            LogLoss.of(new int[]{1, 0}, new double[]{0.9}));
    }

    @Test
    public void givenLogLossInstance_whenToStringCalled_thenReturnsLogLoss() {
        assertEquals("LogLoss", new LogLoss().toString());
    }
}
