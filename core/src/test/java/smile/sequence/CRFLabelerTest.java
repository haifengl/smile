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

import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CRFLabeler — covers happy-path predict and viterbi
 * for generic-type sequences, and ensures output dimensions are correct.
 *
 * @author Haifeng Li
 */
public class CRFLabelerTest {

    // Two-label ("B" = 0, "I" = 1) chunking-like task with a single integer feature.
    // Sequences alternate label 0 and 1 based on even/odd feature value.
    private static final Integer[][] TRAIN_OBS = {
            {0, 1, 0, 1},
            {1, 0, 1, 0},
            {0, 0, 1, 1},
            {1, 1, 0, 0}
    };

    private static final int[][] TRAIN_LABELS = {
            {0, 1, 0, 1},
            {1, 0, 1, 0},
            {0, 0, 1, 1},
            {1, 1, 0, 0}
    };

    // Use single-level trees (maxNodes=2 → 1 split per tree) to stay well within
    // the one-feature schema handled by CRF.extend at inference time.
    private static final CRF.Options FAST_OPTIONS = new CRF.Options(2, 2, 2, 1, 1.0);

    private CRFLabeler<Integer> labeler;

    private static Function<Integer, Tuple> features() {
        StructType schema = new StructType(new StructField("x", DataTypes.IntType));
        return value -> Tuple.of(schema, new int[]{value});
    }

    @BeforeEach
    public void setUp() {
        labeler = CRFLabeler.fit(TRAIN_OBS, TRAIN_LABELS, features(), FAST_OPTIONS);
    }

    @Test
    public void givenFittedLabeler_whenCreated_thenNotNull() {
        assertNotNull(labeler);
    }

    @Test
    public void givenSequence_whenPredict_thenOutputLengthMatchesInput() {
        Integer[] seq = {0, 1, 0, 1, 0};
        int[] result = labeler.predict(seq);
        assertEquals(seq.length, result.length);
    }

    @Test
    public void givenSequence_whenPredict_thenLabelsAreValidIndices() {
        Integer[] seq = {1, 0, 1, 0};
        int[] result = labeler.predict(seq);
        for (int label : result) {
            assertTrue(label == 0 || label == 1,
                    "Expected label in {0, 1}, got: " + label);
        }
    }

    @Test
    public void givenSequence_whenViterbi_thenOutputLengthMatchesInput() {
        Integer[] seq = {0, 1, 0};
        int[] result = labeler.viterbi(seq);
        assertEquals(seq.length, result.length);
    }

    @Test
    public void givenSequence_whenViterbi_thenLabelsAreValidIndices() {
        Integer[] seq = {1, 0, 1};
        int[] result = labeler.viterbi(seq);
        for (int label : result) {
            assertTrue(label == 0 || label == 1,
                    "Expected label in {0, 1}, got: " + label);
        }
    }

    @Test
    public void givenSingleElementSequence_whenPredict_thenReturnsSingleLabel() {
        Integer[] seq = {0};
        int[] result = labeler.predict(seq);
        assertEquals(1, result.length);
        assertTrue(result[0] == 0 || result[0] == 1);
    }

    @Test
    public void givenSingleElementSequence_whenViterbi_thenReturnsSingleLabel() {
        Integer[] seq = {1};
        int[] result = labeler.viterbi(seq);
        assertEquals(1, result.length);
        assertTrue(result[0] == 0 || result[0] == 1);
    }

    @Test
    public void givenEmptySequence_whenPredict_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> labeler.predict(new Integer[0]));
    }

    @Test
    public void givenEmptySequence_whenViterbi_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> labeler.viterbi(new Integer[0]));
    }

    @Test
    public void givenMismatchedSequenceCounts_whenFit_thenThrowIllegalArgumentException() {
        Integer[][] obs    = {{0, 1}};
        int[][]     labels = {{0, 1}, {0, 1}};
        assertThrows(IllegalArgumentException.class,
                () -> CRFLabeler.fit(obs, labels, features()));
    }

    @Test
    public void givenExplicitOptions_whenFitOverloadUsed_thenPredictWorks() {
        // Verify the two-argument fit overload (with explicit Options) works end-to-end.
        CRFLabeler<Integer> labeler2 = CRFLabeler.fit(TRAIN_OBS, TRAIN_LABELS, features(),
                new CRF.Options(3, 2, 2, 1, 0.5));
        Integer[] seq = {0, 1, 0};
        int[] result = labeler2.predict(seq);
        assertEquals(seq.length, result.length);
    }

    @Test
    public void givenMismatchedObservationAndLabelCounts_whenFittingLabeler_thenThrowIllegalArgumentException() {
        Integer[][] sequences = {{0, 1, 0}};
        int[][] labels = {{0, 0, 0}, {0, 0, 0}};

        assertThrows(IllegalArgumentException.class, () -> CRFLabeler.fit(sequences, labels, features()));
    }

    @Test
    public void givenEmptySequence_whenPredictingLabeler_thenThrowIllegalArgumentException() {
        CRFLabeler<Integer> labeler = CRFLabeler.fit(
                new Integer[][] {{0, 1}, {1, 0}},
                new int[][] {{0, 0}, {0, 0}},
                features(),
                new CRF.Options(1, 2, 2, 1, 1.0)
        );

        assertThrows(IllegalArgumentException.class, () -> labeler.predict(new Integer[0]));
    }

    @Test
    public void givenEmptySequence_whenDecodingByViterbi_thenThrowIllegalArgumentException() {
        CRFLabeler<Integer> labeler = CRFLabeler.fit(
                new Integer[][] {{0, 1}, {1, 0}},
                new int[][] {{0, 0}, {0, 0}},
                features(),
                new CRF.Options(1, 2, 2, 1, 1.0)
        );

        assertThrows(IllegalArgumentException.class, () -> labeler.viterbi(new Integer[0]));
    }
}

