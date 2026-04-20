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
 * Unit tests for MutualInformation.
 *
 * MI(X;Y) = H(X) when X == Y (perfect clustering).
 * MI(X;Y) = 0 when X and Y are statistically independent.
 */
public class MutualInformationTest {

    /**
     * Perfectly identical two-class balanced clustering.
     * n=6, 3 of class 0 and 3 of class 1.
     * MI = H(truth) = -2*(0.5*ln(0.5)) = ln(2) in nats.
     *
     * Contingency (diagonal): [[3,0],[0,3]]
     * MI = (3/6)*ln((3/6)/((3/6)*(3/6))) * 2 = ln(2) ≈ 0.693147
     */
    @Test
    public void givenPerfect2ClassClustering_whenMIComputed_thenEqualsEntropy() {
        int[] x = {0, 0, 0, 1, 1, 1};
        assertEquals(Math.log(2), MutualInformation.of(x, x), 1E-10);
    }

    /**
     * Perfect three-class balanced clustering.
     * MI = H(truth) = ln(3) ≈ 1.0986.
     */
    @Test
    public void givenPerfect3ClassClustering_whenMIComputed_thenEqualsEntropy() {
        int[] x = {0, 0, 1, 1, 2, 2};
        assertEquals(Math.log(3), MutualInformation.of(x, x), 1E-10);
    }

    /**
     * Statistically independent balanced clusterings.
     * truth=[0,0,1,1], cluster=[0,1,0,1]
     * Contingency: all cells = 1, uniform distribution.
     * p(i,j) = 1/4 = p1(i)*p2(j) for all (i,j) → MI = 0.
     */
    @Test
    public void givenIndependentClusterings_whenMIComputed_thenReturns0() {
        int[] truth   = {0, 0, 1, 1};
        int[] cluster = {0, 1, 0, 1};
        assertEquals(0.0, MutualInformation.of(truth, cluster), 1E-10);
    }

    /**
     * Perfect clustering with relabeled classes (permutation-invariant).
     * truth=[0,0,1,1], cluster=[1,1,0,0] — same partition, different labels.
     * MI should equal H(truth) = ln(2).
     */
    @Test
    public void givenRelabeledPerfectClustering_whenMIComputed_thenEqualsEntropy() {
        int[] truth   = {0, 0, 1, 1};
        int[] cluster = {1, 1, 0, 0};
        assertEquals(Math.log(2), MutualInformation.of(truth, cluster), 1E-10);
    }

    /**
     * MI is symmetric: MI(X;Y) = MI(Y;X).
     */
    @Test
    public void givenTwoClusterings_whenMIIsComputed_thenItIsSymmetric() {
        int[] x = {0, 0, 1, 1, 2, 2};
        int[] y = {0, 1, 1, 2, 2, 0};
        assertEquals(MutualInformation.of(x, y), MutualInformation.of(y, x), 1E-10);
    }

    /**
     * MI is non-negative for any two clusterings.
     */
    @Test
    public void givenAnyTwoClusterings_whenMIIsComputed_thenIsNonNegative() {
        int[] x = {0, 0, 1, 2, 1, 2, 0};
        int[] y = {1, 0, 0, 2, 1, 0, 2};
        assertTrue(MutualInformation.of(x, y) >= 0.0);
    }

    @Test
    public void givenMIInstance_whenScoreCalled_thenMatchesStaticOf() {
        int[] x = {0, 0, 0, 1, 1, 1};
        MutualInformation instance = new MutualInformation();
        assertEquals(MutualInformation.of(x, x), instance.score(x, x), 1E-10);
    }

    @Test
    public void givenMIInstance_whenToStringCalled_thenReturnsMutualInformation() {
        assertEquals("MutualInformation", new MutualInformation().toString());
    }

    /**
     * On the large clustering dataset used in AdjustedRandIndex tests,
     * MI should be a positive finite value.
     */
    @Test
    public void givenLargeClusteringDataset_whenMIComputed_thenIsPositiveAndFinite() {
        int[] clusters = {2,3,3,1,1,3,3,1,3,1,1,3,3,3,3,3,2,3,3,1,1,1,1,1,1,4,1,3,3,3,3,3,1,4,4,4,3,1,1,3,1,4,3,3,3,3,1,1,3,1,1,3,3,3,3,4,3,1,3,1,3,1,1,1,1,1,3,3,2,3,3,1,1,3,3,3,3,3,3,1,1,3,2,3,2,2,4,1,3,1,3,1,1,3,4,4,4,1,2,3,1,1,3,1,1,1,4,3,3,2,3,3,1,3,3,1,1,1,3,4,4,2,3,3,3,3,1,1,1,3,3,3,2,3,3,3,2,3,3,1,3,1,3,3,1,1,3,3,3,1,1,1,1,3,3,4,3,2,3,1,1,3,1,2,3,1,1,3,3,1,1,1,1,1,3,1,3,1,3,1,3,1,1,3,1,1,1,3,2,1,2,1,1,1,1,1,3,1,1,3,3,1,3,3,3};
        int[] alt      = {3,2,2,0,0,2,2,0,2,0,0,2,2,2,2,2,3,2,2,0,0,0,0,0,0,3,0,2,2,2,2,2,0,3,3,3,2,0,0,2,0,3,2,2,2,2,0,0,2,0,0,2,2,2,2,3,2,0,2,0,2,0,0,0,0,0,2,2,3,2,2,0,0,2,2,2,2,2,2,0,0,2,3,2,0,3,3,0,2,0,2,0,0,2,3,3,3,0,3,2,0,0,2,0,0,0,3,2,2,3,2,2,0,2,2,0,0,0,2,3,3,3,2,2,2,2,0,0,0,2,2,2,3,2,2,2,2,2,2,0,2,0,2,2,0,0,2,1,2,0,0,0,0,2,2,3,2,1,2,0,0,2,0,3,2,0,0,2,2,0,0,0,0,0,2,0,2,0,2,0,0,0,0,2,0,0,0,2,3,0,0,0,0,0,0,0,2,0,0,2,2,0,2,2,2};
        double mi = MutualInformation.of(clusters, alt);
        assertTrue(Double.isFinite(mi) && mi > 0.0,
                "MI should be positive and finite for a near-perfect clustering");
    }
}
