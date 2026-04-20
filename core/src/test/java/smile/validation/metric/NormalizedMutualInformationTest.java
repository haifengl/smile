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
 * Unit tests for NormalizedMutualInformation (all five normalization variants).
 *
 * For identical clusterings, all NMI variants equal 1.0.
 * For statistically independent clusterings (MI=0), all variants equal 0.0.
 */
public class NormalizedMutualInformationTest {

    /** Perfect balanced 2-class clustering used across tests. */
    private static final int[] PERFECT = {0, 0, 0, 1, 1, 1};

    /**
     * Independent balanced clusterings: all-ones contingency table → MI=0
     * → all NMI denominators > 0, numerator = 0, so NMI = 0.
     */
    private static final int[] INDEP_X = {0, 0, 1, 1};
    private static final int[] INDEP_Y = {0, 1, 0, 1};

    // ---- Perfect clustering: all variants should return 1.0 ----

    @Test
    public void givenPerfectClustering_whenJointNMIComputed_thenReturns1() {
        assertEquals(1.0, NormalizedMutualInformation.joint(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenPerfectClustering_whenMaxNMIComputed_thenReturns1() {
        assertEquals(1.0, NormalizedMutualInformation.max(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenPerfectClustering_whenMinNMIComputed_thenReturns1() {
        assertEquals(1.0, NormalizedMutualInformation.min(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenPerfectClustering_whenSumNMIComputed_thenReturns1() {
        assertEquals(1.0, NormalizedMutualInformation.sum(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenPerfectClustering_whenSqrtNMIComputed_thenReturns1() {
        assertEquals(1.0, NormalizedMutualInformation.sqrt(PERFECT, PERFECT), 1E-10);
    }

    // ---- Independent clusterings: MI=0 → all variants should return 0.0 ----

    @Test
    public void givenIndependentClusterings_whenJointNMIComputed_thenReturns0() {
        assertEquals(0.0, NormalizedMutualInformation.joint(INDEP_X, INDEP_Y), 1E-10);
    }

    @Test
    public void givenIndependentClusterings_whenMaxNMIComputed_thenReturns0() {
        assertEquals(0.0, NormalizedMutualInformation.max(INDEP_X, INDEP_Y), 1E-10);
    }

    @Test
    public void givenIndependentClusterings_whenMinNMIComputed_thenReturns0() {
        assertEquals(0.0, NormalizedMutualInformation.min(INDEP_X, INDEP_Y), 1E-10);
    }

    @Test
    public void givenIndependentClusterings_whenSumNMIComputed_thenReturns0() {
        assertEquals(0.0, NormalizedMutualInformation.sum(INDEP_X, INDEP_Y), 1E-10);
    }

    @Test
    public void givenIndependentClusterings_whenSqrtNMIComputed_thenReturns0() {
        assertEquals(0.0, NormalizedMutualInformation.sqrt(INDEP_X, INDEP_Y), 1E-10);
    }

    // ---- score() dispatch: each instance should delegate to the correct static method ----

    @Test
    public void givenJOINTInstance_whenScoreCalled_thenMatchesJointStatic() {
        assertEquals(NormalizedMutualInformation.joint(PERFECT, PERFECT),
                NormalizedMutualInformation.JOINT.score(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenMAXInstance_whenScoreCalled_thenMatchesMaxStatic() {
        assertEquals(NormalizedMutualInformation.max(PERFECT, PERFECT),
                NormalizedMutualInformation.MAX.score(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenMINInstance_whenScoreCalled_thenMatchesMinStatic() {
        assertEquals(NormalizedMutualInformation.min(PERFECT, PERFECT),
                NormalizedMutualInformation.MIN.score(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenSUMInstance_whenScoreCalled_thenMatchesSumStatic() {
        assertEquals(NormalizedMutualInformation.sum(PERFECT, PERFECT),
                NormalizedMutualInformation.SUM.score(PERFECT, PERFECT), 1E-10);
    }

    @Test
    public void givenSQRTInstance_whenScoreCalled_thenMatchesSqrtStatic() {
        assertEquals(NormalizedMutualInformation.sqrt(PERFECT, PERFECT),
                NormalizedMutualInformation.SQRT.score(PERFECT, PERFECT), 1E-10);
    }

    // ---- NMI is in [0,1] for any real clustering ----

    @Test
    public void givenRealClustering_whenNMIComputed_thenAllVariantsAreInUnitInterval() {
        int[] clusters = {2,3,3,1,1,3,3,1,3,1,1,3,3,3,3,3,2,3,3,1,1,1,1,1,1,4,1,3,3,3};
        int[] alt      = {3,2,2,0,0,2,2,0,2,0,0,2,2,2,2,2,3,2,2,0,0,0,0,0,0,3,0,2,2,2};

        double joint = NormalizedMutualInformation.joint(clusters, alt);
        double max   = NormalizedMutualInformation.max(clusters, alt);
        double min   = NormalizedMutualInformation.min(clusters, alt);
        double sum   = NormalizedMutualInformation.sum(clusters, alt);
        double sqrt  = NormalizedMutualInformation.sqrt(clusters, alt);

        // Use a small epsilon above 1.0 to tolerate floating-point rounding errors.
        // All NMI variants are bounded by 1.0 mathematically, but finite-precision
        // arithmetic can produce values like 1.0000000000000002 for the MIN variant.
        double eps = 1E-10;
        assertTrue(joint >= 0.0 && joint <= 1.0 + eps, "JOINT NMI out of [0,1]: " + joint);
        assertTrue(max   >= 0.0 && max   <= 1.0 + eps, "MAX NMI out of [0,1]: " + max);
        assertTrue(min   >= 0.0 && min   <= 1.0 + eps, "MIN NMI out of [0,1]: " + min);
        assertTrue(sum   >= 0.0 && sum   <= 1.0 + eps, "SUM NMI out of [0,1]: " + sum);
        assertTrue(sqrt  >= 0.0 && sqrt  <= 1.0 + eps, "SQRT NMI out of [0,1]: " + sqrt);
    }

    @Test
    public void givenNMIInstance_whenToStringCalled_thenIncludesMethodName() {
        assertTrue(NormalizedMutualInformation.MAX.toString().contains("NMI") ||
                   NormalizedMutualInformation.MAX.toString().contains("MAX"));
    }
}
