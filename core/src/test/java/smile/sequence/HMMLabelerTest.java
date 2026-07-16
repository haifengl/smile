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
package smile.sequence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.math.MathEx;
import smile.stat.distribution.EmpiricalDistribution;
import smile.tensor.DenseMatrix;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for HMMLabeler — covers generic-type fit, p/logp wrappers,
 * predict happy path, and toString delegation.
 */
public class HMMLabelerTest {

    // 2-state, 2-symbol HMM: "fair" vs "biased" coin
    private static final double[] PI = {0.5, 0.5};
    private static final double[][] A = {{0.8, 0.2}, {0.2, 0.8}};
    private static final double[][] B = {{0.6, 0.4}, {0.4, 0.6}};

    private HMMLabeler<String> labeler;

    // Map "H" -> 0, "T" -> 1
    private static int symbolOrdinal(String s) {
        return "H".equals(s) ? 0 : 1;
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218);

        EmpiricalDistribution initial = new EmpiricalDistribution(PI);
        EmpiricalDistribution[] transition = new EmpiricalDistribution[A.length];
        for (int i = 0; i < transition.length; i++) {
            transition[i] = new EmpiricalDistribution(A[i]);
        }
        EmpiricalDistribution[] emission = new EmpiricalDistribution[B.length];
        for (int i = 0; i < emission.length; i++) {
            emission[i] = new EmpiricalDistribution(B[i]);
        }

        // Generate training data as String sequences
        int numSeqs = 200;
        String[][] observations = new String[numSeqs][];
        int[][] labels = new int[numSeqs][];
        String[] symbols = {"H", "T"};

        for (int i = 0; i < numSeqs; i++) {
            int len = 20;
            observations[i] = new String[len];
            labels[i] = new int[len];
            int state = (int) initial.rand();
            observations[i][0] = symbols[(int) emission[state].rand()];
            labels[i][0] = state;
            for (int j = 1; j < len; j++) {
                state = (int) transition[state].rand();
                observations[i][j] = symbols[(int) emission[state].rand()];
                labels[i][j] = state;
            }
        }

        labeler = HMMLabeler.fit(observations, labels, HMMLabelerTest::symbolOrdinal);
    }

    @Test
    public void givenStringObservations_whenFitLabeler_thenModelIsNonNull() {
        assertNotNull(labeler);
    }

    @Test
    public void givenObservationSequence_whenPredict_thenOutputLengthMatchesInput() {
        String[] o = {"H", "H", "T", "H", "T", "T"};
        int[] result = labeler.predict(o);
        assertEquals(o.length, result.length);
    }

    @Test
    public void givenObservationSequence_whenPredict_thenLabelsAreValidStateIndices() {
        String[] o = {"H", "T", "T", "H", "H", "T", "H"};
        int[] result = labeler.predict(o);
        for (int label : result) {
            assertTrue(label == 0 || label == 1,
                    "Expected label in {0, 1}, got: " + label);
        }
    }

    @Test
    public void givenObservationAndStateSequences_whenJointP_thenPositive() {
        String[] o = {"H", "H", "T", "T"};
        int[] s = {0, 0, 1, 1};
        double prob = labeler.p(o, s);
        assertTrue(prob > 0.0, "Joint probability should be positive");
        assertTrue(prob <= 1.0, "Joint probability should be at most 1");
    }

    @Test
    public void givenObservationAndStateSequences_whenLogJointP_thenConsistentWithP() {
        String[] o = {"H", "T", "H", "T"};
        int[] s = {0, 0, 1, 1};
        double prob    = labeler.p(o, s);
        double logProb = labeler.logp(o, s);
        assertEquals(Math.log(prob), logProb, 1E-9,
                "logp(o, s) should equal log(p(o, s))");
    }

    @Test
    public void givenObservationSequence_whenMarginalP_thenPositive() {
        String[] o = {"H", "T", "H", "T", "H"};
        double prob = labeler.p(o);
        assertTrue(prob > 0.0, "Marginal probability should be positive");
        assertTrue(prob <= 1.0, "Marginal probability should be at most 1");
    }

    @Test
    public void givenObservationSequence_whenMarginalLogP_thenConsistentWithP() {
        String[] o = {"H", "H", "T", "T", "H", "T"};
        double logProb = labeler.logp(o);
        assertTrue(Double.isFinite(logProb), "Log probability should be finite");
        assertEquals(Math.log(labeler.p(o)), logProb, 1E-6,
                "logp(o) should equal log(p(o))");
    }

    @Test
    public void givenMarginalAndJointProbabilities_whenCompared_thenMarginalIsGreater() {
        // Marginal probability is a sum over all state sequences, so P(o) >= P(o, s) for any specific s
        String[] o = {"H", "T", "T"};
        int[] s = {0, 1, 1};
        double marginal = labeler.p(o);
        double joint    = labeler.p(o, s);
        assertTrue(marginal >= joint,
                "Marginal P(o) should be >= joint P(o, s)");
    }

    @Test
    public void givenFittedLabeler_whenToString_thenDelegatesToModel() {
        String str = labeler.toString();
        assertNotNull(str, "toString() should not return null");
        assertFalse(str.isBlank(), "toString() should not return an empty string");
    }

    @Test
    public void givenSingleSymbolSequence_whenPredict_thenReturnsSingleLabel() {
        String[] o = {"H"};
        int[] result = labeler.predict(o);
        assertEquals(1, result.length);
        assertTrue(result[0] == 0 || result[0] == 1);
    }

    @Test
    public void givenMismatchedSequenceCounts_whenFit_thenThrowIllegalArgumentException() {
        String[][] obs    = {{"H", "T"}};
        int[][]    labels = {{0, 1}, {0, 1}};
        assertThrows(IllegalArgumentException.class,
                () -> HMMLabeler.fit(obs, labels, HMMLabelerTest::symbolOrdinal));
    }

    @Test
    public void givenEmptySequence_whenPredict_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> labeler.predict(new String[0]));
    }

    @Test
    public void givenKnownHmm_whenFitLabeler_thenConstructDirectlyAndPredict() {
        // Build the HMMLabeler directly via constructor (not only via fit)
        HMM model = new HMM(PI, DenseMatrix.of(A), DenseMatrix.of(B));
        HMMLabeler<Integer> intLabeler = new HMMLabeler<>(model, Integer::intValue);

        Integer[] o = {0, 0, 1, 1, 0, 1, 1, 0};
        int[] expected = {0, 0, 0, 0, 0, 0, 0, 0}; // from HMMTest.testPredict
        int[] result = intLabeler.predict(o);

        assertEquals(o.length, result.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], result[i]);
        }
    }

    @Test
    public void givenMismatchedObservationAndLabelCounts_whenFittingLabeler_thenThrowIllegalArgumentException() {
        Integer[][] observations = {{0, 1, 0}};
        int[][] labels = {{0, 1, 0}, {1, 0, 1}};

        assertThrows(IllegalArgumentException.class, () -> HMMLabeler.fit(observations, labels, Integer::intValue));
    }

    @Test
    public void givenEmptyObservationSequence_whenPredictingLabeler_thenThrowIllegalArgumentException() {
        HMMLabeler<Integer> labeler = HMMLabeler.fit(
                new Integer[][] {{0, 1, 0}, {1, 0, 1}},
                new int[][] {{0, 1, 0}, {1, 0, 1}},
                Integer::intValue
        );

        assertThrows(IllegalArgumentException.class, () -> labeler.predict(new Integer[0]));
    }

    @Test
    public void givenOutOfRangeSymbol_whenPredictingLabeler_thenThrowIllegalArgumentException() {
        HMMLabeler<Integer> labeler = HMMLabeler.fit(
                new Integer[][] {{0, 1, 0}, {1, 0, 1}},
                new int[][] {{0, 1, 0}, {1, 0, 1}},
                Integer::intValue
        );

        assertThrows(IllegalArgumentException.class, () -> labeler.predict(new Integer[] {0, 2, 1}));
    }

    @Test
    public void givenInvalidTrainingData_whenUpdatingLabeler_thenThrowIllegalArgumentException() {
        HMMLabeler<Integer> labeler = HMMLabeler.fit(
                new Integer[][] {{0, 1, 0}, {1, 0, 1}},
                new int[][] {{0, 1, 0}, {1, 0, 1}},
                Integer::intValue
        );

        assertThrows(IllegalArgumentException.class, () -> labeler.update(new Integer[0][], 1));
        assertThrows(IllegalArgumentException.class, () -> labeler.update(new Integer[][] {{0, 2, 1}}, 1));
    }
}

