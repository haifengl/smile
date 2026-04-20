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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlattScaling: fit and scale behavior.
 */
class PlattScalingTest {

    // ── scale (constructor) ────────────────────────────────────────────────────

    @Test
    void givenZeroScore_whenScale_thenProbabilityIsNearHalf() {
        // alpha=1, beta=0 → P(1|x=0) = 1/(1+exp(0)) = 0.5
        PlattScaling ps = new PlattScaling(1.0, 0.0);
        assertEquals(0.5, ps.scale(0.0), 1E-10);
    }

    @Test
    void givenLargePositiveScore_whenScale_thenProbabilityIsNearZero() {
        // For alpha > 0, large positive f → P = exp(-fApB)/(1+exp(-fApB)) → 0
        PlattScaling ps = new PlattScaling(1.0, 0.0);
        double p = ps.scale(100.0);
        assertTrue(p < 0.01, "P(1|f=100) should be near 0, got " + p);
    }

    @Test
    void givenLargeNegativeScore_whenScale_thenProbabilityIsNearOne() {
        // For alpha > 0, large negative f → fApB << 0 → P = 1/(1+exp(fApB)) → 1
        PlattScaling ps = new PlattScaling(1.0, 0.0);
        double p = ps.scale(-100.0);
        assertTrue(p > 0.99, "P(1|f=-100) should be near 1, got " + p);
    }

    @Test
    void givenAnyScore_whenScale_thenProbabilityInUnitInterval() {
        PlattScaling ps = new PlattScaling(0.5, -0.1);
        for (double f : new double[]{-100, -10, -1, 0, 1, 10, 100}) {
            double p = ps.scale(f);
            assertTrue(p >= 0.0 && p <= 1.0,
                    "Probability must be in [0,1] for f=" + f + ", got " + p);
        }
    }

    // ── fit(scores, y) ─────────────────────────────────────────────────────────

    @Test
    void givenPerfectlySeparableScores_whenFit_thenScaleIsMonotone() {
        // Positive class has large positive scores, negative class large negative.
        double[] scores = {-10.0, -8.0, -9.0, 10.0, 8.0, 9.0};
        int[]    y      = {  -1,   -1,   -1,   +1,  +1,  +1};

        PlattScaling ps = PlattScaling.fit(scores, y);

        // Positive scores should map to higher probability than negative scores.
        assertTrue(ps.scale(5.0) > ps.scale(-5.0),
                "Platt scaling should be monotone with the score");
    }

    @Test
    void givenSymmetricScores_whenFit_thenScaleIsSymmetricAroundHalf() {
        // Balanced, symmetric data: score 0 should yield ~0.5 after fitting.
        double[] scores = {-5.0, -4.0, -3.0, 3.0, 4.0, 5.0};
        int[]    y      = {  -1,   -1,   -1,  +1,  +1,  +1};

        PlattScaling ps = PlattScaling.fit(scores, y);
        double pAt0 = ps.scale(0.0);
        // Should be reasonably close to 0.5 for a symmetric problem.
        assertEquals(0.5, pAt0, 0.2,
                "Probability at score=0 should be close to 0.5 for symmetric data");
    }

    @Test
    void givenFitWithCustomMaxIters_whenFit_thenProducesValidModel() {
        double[] scores = {-1.0, 0.0, 1.0};
        int[]    y      = {  -1,  +1,  +1};
        assertDoesNotThrow(() -> PlattScaling.fit(scores, y, 5));
    }
}
