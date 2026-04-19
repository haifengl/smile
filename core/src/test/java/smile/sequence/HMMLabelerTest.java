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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HMMLabelerTest {
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

