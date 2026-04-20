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
package smile.model.cart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Loss}.
 */
public class LossTest {

    // -----------------------------------------------------------------------
    // Least-squares
    // -----------------------------------------------------------------------

    @Test
    public void givenSymmetricY_whenLsIntercept_thenReturnsMeanAndZeroMeanResiduals() {
        // Given: y = [1, 2, 3, 4]  → mean = 2.5
        Loss ls = Loss.ls();
        double[] y = {1.0, 2.0, 3.0, 4.0};

        // When
        double b = ls.intercept(y);

        // Then
        assertEquals(2.5, b, 1E-12);
        // residuals = y - mean = [-1.5, -0.5, 0.5, 1.5]
        double[] res = ls.residual();
        assertEquals(-1.5, res[0], 1E-12);
        assertEquals(-0.5, res[1], 1E-12);
        assertEquals( 0.5, res[2], 1E-12);
        assertEquals( 1.5, res[3], 1E-12);
    }

    @Test
    public void givenResiduals_whenLsValue_thenReturnsMse() {
        // Given: residuals [-1.5, -0.5, 0.5, 1.5] from intercept above
        Loss ls = Loss.ls();
        ls.intercept(new double[]{1.0, 2.0, 3.0, 4.0});

        // When: MSE = (2.25 + 0.25 + 0.25 + 2.25) / 4 = 5.0 / 4 = 1.25
        double v = ls.value();

        // Then
        assertEquals(1.25, v, 1E-12);
    }

    @Test
    public void givenAllSamples_whenLsOutput_thenReturnsWeightedMeanResidual() {
        // Given: all residuals after centring sum to zero
        Loss ls = Loss.ls();
        ls.intercept(new double[]{1.0, 2.0, 3.0, 4.0});

        // When: output of entire node = weighted mean of residuals = 0
        double out = ls.output(new int[]{0, 1, 2, 3}, new int[]{1, 1, 1, 1});

        // Then
        assertEquals(0.0, out, 1E-12);
    }

    @Test
    public void givenSubsetWithUniformWeights_whenLsOutput_thenReturnsMeanOfSubsetResiduals() {
        // Given: y = [0, 4]  → mean = 2, residuals = [-2, 2]
        // Subset = {0}: output = -2
        Loss ls = Loss.ls();
        ls.intercept(new double[]{0.0, 4.0});

        // When
        double out = ls.output(new int[]{0}, new int[]{1, 1});

        // Then
        assertEquals(-2.0, out, 1E-12);
    }

    @Test
    public void givenLossLs_whenToString_thenReturnsExpectedLabel() {
        assertEquals("LeastSquares", Loss.ls().toString());
    }

    // -----------------------------------------------------------------------
    // LAD (least absolute deviation)
    // -----------------------------------------------------------------------

    @Test
    public void givenSymmetricY_whenLadIntercept_thenReturnsMedian() {
        // Given: y = [1, 3, 5]  → median = 3
        Loss lad = Loss.lad();
        double b = lad.intercept(new double[]{1.0, 3.0, 5.0});

        // Then
        assertEquals(3.0, b, 1E-12);
        // residuals = [-2, 0, 2]
        double[] res = lad.residual();
        assertEquals(-2.0, res[0], 1E-12);
        assertEquals( 0.0, res[1], 1E-12);
        assertEquals( 2.0, res[2], 1E-12);
    }

    @Test
    public void givenResiduals_whenLadValue_thenReturnsMae() {
        // Given: residuals [-2, 0, 2]
        Loss lad = Loss.lad();
        lad.intercept(new double[]{1.0, 3.0, 5.0});

        // MAE = (2 + 0 + 2) / 3 = 4/3
        assertEquals(4.0 / 3.0, lad.value(), 1E-12);
    }

    @Test
    public void givenEntireNode_whenLadOutput_thenReturnsMedianResidual() {
        // residuals = [-2, 0, 2]  → median = 0
        Loss lad = Loss.lad();
        lad.intercept(new double[]{1.0, 3.0, 5.0});

        double out = lad.output(new int[]{0, 1, 2}, new int[]{1, 1, 1});
        assertEquals(0.0, out, 1E-12);
    }

    @Test
    public void givenResiduals_whenLadResponse_thenReturnsSigns() {
        // residuals = [-2, 0, 2]  → response = signum = [-1, 0, 1]
        Loss lad = Loss.lad();
        lad.intercept(new double[]{1.0, 3.0, 5.0});
        double[] response = lad.response();

        assertEquals(-1.0, response[0], 1E-12);
        assertEquals( 0.0, response[1], 1E-12);
        assertEquals( 1.0, response[2], 1E-12);
    }

    // -----------------------------------------------------------------------
    // Huber
    // -----------------------------------------------------------------------

    @Test
    public void givenHuberLoss_whenResidualNegativeAndAboveThreshold_thenUsesLinearTail() {
        // Regression test for the bug where 'r <= delta' was used instead of
        // 'Math.abs(r) <= delta', causing large negative residuals to be
        // incorrectly assigned the quadratic term.
        //
        // Setup: y = [0, 1, 2, 3, 10], p = 0.4
        //   intercept → b=2, residuals = [-2,-1,0,1,8]
        //   response()  → delta = 1.0  (3rd smallest of {2,1,0,1,8} i.e. index 2 = 1.0)
        //
        // Correct Huber value (|r|<=delta → quadratic; |r|>delta → linear):
        //   r=-2: linear = 1*(2-0.5) = 1.5
        //   r=-1: quadratic = 1
        //   r= 0: quadratic = 0
        //   r= 1: quadratic = 1
        //   r= 8: linear = 1*(8-0.5) = 7.5
        //   sum=11, mean=2.2
        //
        // Buggy value (r<=delta  →  quadratic even for r=-2 since -2<=1):
        //   r=-2: quadratic = 4  (WRONG)
        //   sum=13.5, mean=2.7
        Loss huber = Loss.huber(0.4);
        huber.intercept(new double[]{0.0, 1.0, 2.0, 3.0, 10.0});
        huber.response(); // sets delta = 1.0

        double v = huber.value();
        assertEquals(2.2, v, 1E-10);
    }

    @Test
    public void givenAllResidualsBelowDelta_whenHuberValue_thenPureQuadratic() {
        // When all residuals have |r| <= delta, Huber = MSE
        // y=[1,3,5] → b=3, residuals=[-2,0,2]
        // p=0.9: delta = select([2,0,2], 2) = 2nd largest = 2.0
        // value = (4+0+4)/3 = 8/3
        Loss huber = Loss.huber(0.9);
        huber.intercept(new double[]{1.0, 3.0, 5.0});
        huber.response(); // delta = 2.0 (all |residuals| <= 2.0)

        double v = huber.value();
        assertEquals(8.0 / 3.0, v, 1E-10);
    }

    @Test
    public void givenHuberLoss_whenInvalidP_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Loss.huber(0.0));
        assertThrows(IllegalArgumentException.class, () -> Loss.huber(1.0));
        assertThrows(IllegalArgumentException.class, () -> Loss.huber(-0.1));
    }

    @Test
    public void givenHuberLoss_whenToString_thenContainsPercentage() {
        String s = Loss.huber(0.7).toString();
        assertTrue(s.startsWith("Huber("));
        assertTrue(s.contains("70"));
    }

    // -----------------------------------------------------------------------
    // Quantile
    // -----------------------------------------------------------------------

    @Test
    public void givenQuantile_whenInvalidP_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Loss.quantile(0.0));
        assertThrows(IllegalArgumentException.class, () -> Loss.quantile(1.0));
    }

    @Test
    public void givenQuantileLoss_whenToString_thenContainsPercentage() {
        String s = Loss.quantile(0.3).toString();
        assertTrue(s.startsWith("Quantile("));
        assertTrue(s.contains("30"));
    }

    @Test
    public void givenQuantileLoss_whenResponseCalled_thenReturnsSignumResiduals() {
        // y=[1,3,5], p=0.5 → b=3, residuals=[-2,0,2] → response=signum=[-1,0,1]
        Loss q = Loss.quantile(0.5);
        q.intercept(new double[]{1.0, 3.0, 5.0});
        double[] response = q.response();

        assertEquals(-1.0, response[0], 1E-12);
        assertEquals( 0.0, response[1], 1E-12);
        assertEquals( 1.0, response[2], 1E-12);
    }

    // -----------------------------------------------------------------------
    // valueOf / toString round-trip
    // -----------------------------------------------------------------------

    @Test
    public void givenLossToString_whenValueOf_thenRoundTripsCorrectly() {
        assertEquals("LeastSquares",      Loss.valueOf("LeastSquares").toString());
        assertEquals("LeastAbsoluteDeviation", Loss.valueOf("LeastAbsoluteDeviation").toString());
        assertEquals(Loss.quantile(0.25).toString(), Loss.valueOf("Quantile(25.0%)").toString());
        assertEquals(Loss.huber(0.75).toString(),    Loss.valueOf("Huber(75.0%)").toString());
    }

    @Test
    public void givenUnknownLossName_whenValueOf_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Loss.valueOf("Unknown"));
    }
}
