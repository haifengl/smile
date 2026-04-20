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
package smile.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModelSelectionTest {

    @Test
    public void givenLogLikelihoodAndK_whenComputingAIC_thenReturnsTwoKMinusTwoLogL() {
        // AIC = 2k - 2*logL
        double logL = -10.0;
        int k = 3;
        double expected = 2 * k - 2 * logL; // = 6 + 20 = 26
        assertEquals(expected, ModelSelection.AIC(logL, k), 1E-12);
    }

    @Test
    public void givenZeroLogLikelihood_whenComputingAIC_thenReturnsDoubleK() {
        // AIC = 2k - 2*0 = 2k
        assertEquals(6.0, ModelSelection.AIC(0.0, 3), 1E-12);
    }

    @Test
    public void givenPositiveLogLikelihood_whenComputingAIC_thenReturnsCorrectValue() {
        // AIC = 2*5 - 2*100 = 10 - 200 = -190
        assertEquals(-190.0, ModelSelection.AIC(100.0, 5), 1E-12);
    }

    @Test
    public void givenLogLikelihoodKAndN_whenComputingBIC_thenReturnsKLogNMinusTwoLogL() {
        // BIC = k*log(n) - 2*logL
        double logL = -10.0;
        int k = 3;
        int n = 100;
        double expected = k * Math.log(n) - 2 * logL; // = 3*ln(100) + 20
        assertEquals(expected, ModelSelection.BIC(logL, k, n), 1E-10);
    }

    @Test
    public void givenPerfectFitModel_whenComputingBIC_thenReturnsKLogN() {
        // BIC = k*log(n) - 2*0 = k*log(n)
        int k = 2;
        int n = 50;
        assertEquals(k * Math.log(n), ModelSelection.BIC(0.0, k, n), 1E-12);
    }

    @Test
    public void givenSameModels_whenAICAndBICCompared_thenBICPenalizesMoreWithLargeN() {
        // For large n, BIC penalty k*log(n) > 2k (AIC penalty) when log(n) > 2, i.e. n > e^2 ≈ 7.4
        double logL = -50.0;
        int k = 4;
        int n = 1000;
        double aic = ModelSelection.AIC(logL, k);
        double bic = ModelSelection.BIC(logL, k, n);
        // BIC has larger penalty so BIC > AIC for large n
        assertTrue(bic > aic);
    }

    @Test
    public void givenTwoModels_whenAICRanked_thenLowerAICPreferred() {
        // Model A: k=2, logL=-5  → AIC = 4 + 10 = 14
        // Model B: k=5, logL=-3  → AIC = 10 + 6 = 16
        double aicA = ModelSelection.AIC(-5.0, 2);
        double aicB = ModelSelection.AIC(-3.0, 5);
        assertTrue(aicA < aicB);
    }

    @Test
    public void givenTwoModels_whenBICRanked_thenLowerBICPreferred() {
        // Model A: k=2, logL=-5, n=100  → BIC = 2*ln(100) - 2*(-5) = 9.21 + 10 = 19.21
        // Model B: k=5, logL=-3, n=100  → BIC = 5*ln(100) - 2*(-3) = 23.03 + 6 = 29.03
        double bicA = ModelSelection.BIC(-5.0, 2, 100);
        double bicB = ModelSelection.BIC(-3.0, 5, 100);
        assertTrue(bicA < bicB);
    }
}
