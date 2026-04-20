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
 * Unit tests for AUC (Area Under the ROC Curve).
 *
 * AUC is computed via the Mann–Whitney U rank statistic:
 *   AUC = (sum_ranks_of_positives - pos*(pos+1)/2) / (pos * neg)
 */
public class AUCTest {

    /**
     * Perfect classifier: all positives scored higher than all negatives.
     * Sorted ascending: [0,0,1,1], ranks [1,2,3,4].
     * sum_pos = 3+4 = 7, AUC = (7 - 2*3/2) / (2*2) = 4/4 = 1.0
     */
    @Test
    public void givenPerfectClassifier_whenAUCComputed_thenReturns1() {
        int[]    truth = {0, 0, 1, 1};
        double[] prob  = {0.1, 0.2, 0.8, 0.9};
        assertEquals(1.0, AUC.of(truth, prob), 1E-10);
    }

    /**
     * Worst classifier: positives always ranked lower than negatives.
     * Sorted ascending: [1,1,0,0], ranks [1,2,3,4].
     * sum_pos = 1+2 = 3, AUC = (3 - 2*3/2) / (2*2) = 0/4 = 0.0
     */
    @Test
    public void givenWorstClassifier_whenAUCComputed_thenReturns0() {
        int[]    truth = {0, 0, 1, 1};
        double[] prob  = {0.8, 0.9, 0.1, 0.2};
        assertEquals(0.0, AUC.of(truth, prob), 1E-10);
    }

    /**
     * Partial classifier with 3 concordant pairs out of 4.
     * truth=[0,0,1,1], prob=[0.1,0.4,0.35,0.8].
     * Sorted: [0→0.1, 1→0.35, 0→0.4, 1→0.8], ranks=[1,2,3,4].
     * sum_pos = 2+4 = 6, AUC = (6 - 2*3/2) / (2*2) = 3/4 = 0.75
     */
    @Test
    public void givenPartialClassifier_whenAUCComputed_thenReturnsCorrectValue() {
        int[]    truth = {0, 0, 1, 1};
        double[] prob  = {0.1, 0.4, 0.35, 0.8};
        assertEquals(0.75, AUC.of(truth, prob), 1E-10);
    }

    /**
     * Ties: when multiple samples share the same score, ranks are averaged.
     * truth=[0,1,0,1], prob=[0.5,0.5,0.5,0.5].
     * All 4 elements tie → average rank = (1+2+3+4)/4 = 2.5 for everyone.
     * sum_pos = 2*2.5 = 5, AUC = (5 - 2*3/2) / (2*2) = (5-3)/4 = 0.5
     */
    @Test
    public void givenAllTiedScores_whenAUCComputed_thenReturnsHalf() {
        int[]    truth = {0, 1, 0, 1};
        double[] prob  = {0.5, 0.5, 0.5, 0.5};
        assertEquals(0.5, AUC.of(truth, prob), 1E-10);
    }

    @Test
    public void givenAUCInstance_whenScoreCalled_thenMatchesStaticOf() {
        int[]    truth = {0, 0, 1, 1};
        double[] prob  = {0.1, 0.2, 0.8, 0.9};
        AUC instance = new AUC();
        assertEquals(AUC.of(truth, prob), instance.score(truth, prob), 1E-10);
    }

    @Test
    public void givenNonBinaryLabel_whenAUCComputed_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            AUC.of(new int[]{0, 2, 1}, new double[]{0.1, 0.5, 0.9}));
    }

    @Test
    public void givenMismatchedLengths_whenAUCComputed_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            AUC.of(new int[]{0, 1}, new double[]{0.5}));
    }

    @Test
    public void givenAUCInstance_whenToStringCalled_thenReturnsAUC() {
        assertEquals("AUC", new AUC().toString());
    }

    @Test
    public void givenLargeDataset_whenAUCComputed_thenIsInUnitInterval() {
        int[] truth = new int[200];
        double[] prob = new double[200];
        for (int i = 0; i < 100; i++) {
            truth[i] = 0;
            prob[i]  = 0.3 + (i % 10) * 0.01;
        }
        for (int i = 100; i < 200; i++) {
            truth[i] = 1;
            prob[i]  = 0.6 + (i % 10) * 0.01;
        }
        double auc = AUC.of(truth, prob);
        assertTrue(auc >= 0.0 && auc <= 1.0, "AUC must be in [0,1]");
    }
}
