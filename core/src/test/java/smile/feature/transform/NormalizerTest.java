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
package smile.feature.transform;

import java.util.List;
import org.junit.jupiter.api.Test;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@link Normalizer} edge cases.
 */
public class NormalizerTest {
    private static final double TOLERANCE = 1E-12;

    @Test
    public void testGivenZeroNormRowsWhenApplyingNormalizerThenValuesRemainFiniteAndUntouchedColumnsStayUnchanged() {
        // Given
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("y", DataTypes.DoubleType),
                new StructField("tag", DataTypes.DoubleType)
        );
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[] {0.0, 0.0, 7.0}),
                Tuple.of(schema, new Object[] {3.0, 4.0, 9.0})
        ));

        // When
        DataFrame l1 = new Normalizer(Normalizer.Norm.L1, "x", "y").apply(data);
        DataFrame l2 = new Normalizer(Normalizer.Norm.L2, "x", "y").apply(data);
        DataFrame lInf = new Normalizer(Normalizer.Norm.L_INF, "x", "y").apply(data);

        // Then
        assertEquals(0.0, l1.getDouble(0, 0), TOLERANCE);
        assertEquals(0.0, l1.getDouble(0, 1), TOLERANCE);
        assertEquals(7.0, l1.getDouble(0, 2), TOLERANCE);
        assertTrue(Double.isFinite(l1.getDouble(0, 0)));
        assertTrue(Double.isFinite(l1.getDouble(0, 1)));
        assertEquals(3.0 / 7.0, l1.getDouble(1, 0), TOLERANCE);
        assertEquals(4.0 / 7.0, l1.getDouble(1, 1), TOLERANCE);
        assertEquals(9.0, l1.getDouble(1, 2), TOLERANCE);

        assertEquals(0.0, l2.getDouble(0, 0), TOLERANCE);
        assertEquals(0.0, l2.getDouble(0, 1), TOLERANCE);
        assertEquals(7.0, l2.getDouble(0, 2), TOLERANCE);
        assertTrue(Double.isFinite(l2.getDouble(0, 0)));
        assertTrue(Double.isFinite(l2.getDouble(0, 1)));
        assertEquals(0.6, l2.getDouble(1, 0), TOLERANCE);
        assertEquals(0.8, l2.getDouble(1, 1), TOLERANCE);
        assertEquals(9.0, l2.getDouble(1, 2), TOLERANCE);

        assertEquals(0.0, lInf.getDouble(0, 0), TOLERANCE);
        assertEquals(0.0, lInf.getDouble(0, 1), TOLERANCE);
        assertEquals(7.0, lInf.getDouble(0, 2), TOLERANCE);
        assertTrue(Double.isFinite(lInf.getDouble(0, 0)));
        assertTrue(Double.isFinite(lInf.getDouble(0, 1)));
        assertEquals(0.75, lInf.getDouble(1, 0), TOLERANCE);
        assertEquals(1.0, lInf.getDouble(1, 1), TOLERANCE);
        assertEquals(9.0, lInf.getDouble(1, 2), TOLERANCE);
    }

    @Test
    public void testGivenNoColumnsWhenCreatingNormalizerThenConstructorRejectsInput() {
        // Given / When / Then
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new Normalizer(Normalizer.Norm.L2));
        assertEquals("Empty list of columns to transform", error.getMessage());
    }

    @Test
    public void testGivenNullNormWhenCreatingNormalizerThenNullPointerExceptionIsThrown() {
        // Given / When / Then
        assertThrows(NullPointerException.class, () -> new Normalizer(null, "x"));
    }

    @Test
    public void testGivenNegativeValuesWhenApplyingL1NormalizerThenCorrectFractionsAreReturned() {
        // Given: [-3, 1] — L1 norm = 4
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType)
        );
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{-3.0, 1.0})
        ));

        // When
        DataFrame result = new Normalizer(Normalizer.Norm.L1, "a", "b").apply(data);

        // Then
        assertEquals(-0.75, result.getDouble(0, 0), TOLERANCE);
        assertEquals( 0.25, result.getDouble(0, 1), TOLERANCE);
    }

    @Test
    public void testGivenMixedSchemaWhenApplyingNormalizerThenUntouchedColumnIsUnchanged() {
        // Given: schema has a numeric "x" and a numeric "tag"; only "x" is normalized
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("tag", DataTypes.DoubleType)
        );
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{3.0, 99.0})
        ));

        // When
        DataFrame result = new Normalizer(Normalizer.Norm.L2, "x").apply(data);

        // Then – "x" normalized to 1.0, "tag" unchanged
        assertEquals(1.0, result.getDouble(0, 0), TOLERANCE);
        assertEquals(99.0, result.getDouble(0, 1), TOLERANCE);
    }
}
