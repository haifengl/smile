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
import org.junit.jupiter.api.Test;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CRFLabelerTest {
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

    private static Function<Integer, Tuple> features() {
        StructType schema = new StructType(new StructField("x", DataTypes.IntType));
        return value -> Tuple.of(schema, new int[] {value});
    }
}

