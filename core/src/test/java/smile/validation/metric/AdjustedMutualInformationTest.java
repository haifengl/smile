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
 * Unit tests for AdjustedMutualInformation (all four normalization variants).
 *
 * AMI = (I - E[MI]) / (norm(H) - E[MI]).
 * For identical clusterings, I = norm(H) = max(H1,H2), so AMI = 1.0.
 */
public class AdjustedMutualInformationTest {

    /** Full Iris dataset cluster labels (same as AdjustedRandIndexTest). */
    private static final int[] CLUSTERS = {2,3,3,1,1,3,3,1,3,1,1,3,3,3,3,3,2,3,3,1,1,1,1,1,1,4,1,3,3,3,3,3,1,4,4,4,3,1,1,3,1,4,3,3,3,3,1,1,3,1,1,3,3,3,3,4,3,1,3,1,3,1,1,1,1,1,3,3,2,3,3,1,1,3,3,3,3,3,3,1,1,3,2,3,2,2,4,1,3,1,3,1,1,3,4,4,4,1,2,3,1,1,3,1,1,1,4,3,3,2,3,3,1,3,3,1,1,1,3,4,4,2,3,3,3,3,1,1,1,3,3,3,2,3,3,3,2,3,3,1,3,1,3,3,1,1,3,3,3,1,1,1,1,3,3,4,3,2,3,1,1,3,1,2,3,1,1,3,3,1,1,1,1,1,3,1,3,1,3,1,3,1,1,3,1,1,1,3,2,1,2,1,1,1,1,1,3,1,1,3,3,1,3,3,3};
    private static final int[] ALT      = {3,2,2,0,0,2,2,0,2,0,0,2,2,2,2,2,3,2,2,0,0,0,0,0,0,3,0,2,2,2,2,2,0,3,3,3,2,0,0,2,0,3,2,2,2,2,0,0,2,0,0,2,2,2,2,3,2,0,2,0,2,0,0,0,0,0,2,2,3,2,2,0,0,2,2,2,2,2,2,0,0,2,3,2,0,3,3,0,2,0,2,0,0,2,3,3,3,0,3,2,0,0,2,0,0,0,3,2,2,3,2,2,0,2,2,0,0,0,2,3,3,3,2,2,2,2,0,0,0,2,2,2,3,2,2,2,2,2,2,0,2,0,2,2,0,0,2,1,2,0,0,0,0,2,2,3,2,1,2,0,0,2,0,3,2,0,0,2,2,0,0,0,0,0,2,0,2,0,2,0,0,0,0,2,0,0,0,2,3,0,0,0,0,0,0,0,2,0,0,2,2,0,2,2,2};

    /** Small perfect balanced 2-class clustering. */
    private static final int[] PERFECT = {0, 0, 0, 1, 1, 1};

    // ---- Perfect clustering: all variants should return 1.0 ----

    @Test
    public void givenPerfectClustering_whenMaxAMIComputed_thenReturns1() {
        assertEquals(1.0, AdjustedMutualInformation.max(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenPerfectClustering_whenMinAMIComputed_thenReturns1() {
        assertEquals(1.0, AdjustedMutualInformation.min(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenPerfectClustering_whenSumAMIComputed_thenReturns1() {
        assertEquals(1.0, AdjustedMutualInformation.sum(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenPerfectClustering_whenSqrtAMIComputed_thenReturns1() {
        assertEquals(1.0, AdjustedMutualInformation.sqrt(PERFECT, PERFECT), 1E-10);
    }

    // ---- score() dispatch: each instance should delegate to the correct static method ----

    @Test
    public void givenMAXInstance_whenScoreCalled_thenMatchesMaxStatic() {
        assertEquals(AdjustedMutualInformation.max(PERFECT, PERFECT),
                AdjustedMutualInformation.MAX.score(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenMINInstance_whenScoreCalled_thenMatchesMinStatic() {
        assertEquals(AdjustedMutualInformation.min(PERFECT, PERFECT),
                AdjustedMutualInformation.MIN.score(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenSUMInstance_whenScoreCalled_thenMatchesSumStatic() {
        assertEquals(AdjustedMutualInformation.sum(PERFECT, PERFECT),
                AdjustedMutualInformation.SUM.score(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenSQRTInstance_whenScoreCalled_thenMatchesSqrtStatic() {
        assertEquals(AdjustedMutualInformation.sqrt(PERFECT, PERFECT),
                AdjustedMutualInformation.SQRT.score(PERFECT, PERFECT), 1E-10);
    }

    // ---- Large dataset: all AMI variants should be > 0.8 for near-perfect clustering ----

    @Test
    public void givenNearPerfectClustering_whenMaxAMIComputed_thenIsAbove08() {
        assertTrue(AdjustedMutualInformation.max(CLUSTERS, ALT) > 0.8,
                "MAX AMI should be high for near-perfect clustering");
    }

    @Test
    public void givenNearPerfectClustering_whenMinAMIComputed_thenIsAbove08() {
        assertTrue(AdjustedMutualInformation.min(CLUSTERS, ALT) > 0.8);
    }

    @Test
    public void givenNearPerfectClustering_whenSumAMIComputed_thenIsAbove08() {
        assertTrue(AdjustedMutualInformation.sum(CLUSTERS, ALT) > 0.8);
    }

    @Test
    public void givenNearPerfectClustering_whenSqrtAMIComputed_thenIsAbove08() {
        assertTrue(AdjustedMutualInformation.sqrt(CLUSTERS, ALT) > 0.8);
    }

    // ---- Relabeled perfect clustering (permutation invariance) ----

    @Test
    public void givenRelabeledPerfectClustering_whenMaxAMIComputed_thenReturns1() {
        int[] truth   = {0, 0, 0, 1, 1, 1};
        int[] cluster = {1, 1, 1, 0, 0, 0};
        assertEquals(1.0, AdjustedMutualInformation.max(truth, cluster), 1E-10);
    }

    @Test
    public void givenAMIInstance_whenToStringCalled_thenIncludesMethodName() {
        assertTrue(AdjustedMutualInformation.MAX.toString().contains("MAX"));
    }
}
