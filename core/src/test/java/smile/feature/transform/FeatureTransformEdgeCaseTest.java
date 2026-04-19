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
import smile.data.transform.InvertibleColumnTransform;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Synthetic edge-case coverage for feature transforms.
 */
public class FeatureTransformEdgeCaseTest {
    private static final double TOLERANCE = 1E-12;

    @Test
    public void testGivenSubsetScalerWhenApplyingAndInvertingThenOnlyRequestedNumericColumnsChange() {
        // Given
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("y", DataTypes.DoubleType),
                new StructField("label", DataTypes.StringType)
        );
        DataFrame train = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[] {10.0, 100.0, "a"}),
                Tuple.of(schema, new Object[] {20.0, 200.0, "b"})
        ));
        DataFrame test = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[] {15.0, 150.0, "holdout"})
        ));

        // When
        InvertibleColumnTransform transform = Scaler.fit(train, "x");
        DataFrame scaled = transform.apply(test);
        DataFrame restored = transform.invert(scaled);

        // Then
        assertEquals(0.5, scaled.getDouble(0, 0), TOLERANCE);
        assertEquals(150.0, scaled.getDouble(0, 1), TOLERANCE);
        assertEquals("holdout", scaled.getString(0, 2));
        assertEquals(15.0, restored.getDouble(0, 0), TOLERANCE);
        assertEquals(150.0, restored.getDouble(0, 1), TOLERANCE);
        assertEquals("holdout", restored.getString(0, 2));
    }

    @Test
    public void testGivenConstantColumnsWhenApplyingTransformsThenFiniteFallbackScalingIsUsed() {
        // Given
        DataFrame constant = DataFrame.of(new double[][] {
                {5.0},
                {5.0},
                {5.0}
        }, "x");
        DataFrame zeros = DataFrame.of(new double[][] {
                {0.0},
                {0.0}
        }, "x");

        // When
        DataFrame scaled = Scaler.fit(constant).apply(constant);
        DataFrame winsor = WinsorScaler.fit(constant).apply(constant);
        DataFrame standardized = Standardizer.fit(constant).apply(constant);
        DataFrame robust = RobustStandardizer.fit(constant).apply(constant);
        DataFrame maxAbs = MaxAbsScaler.fit(zeros).apply(zeros);

        // Then
        assertEquals(0.0, scaled.getDouble(0, 0), TOLERANCE);
        assertEquals(0.0, winsor.getDouble(0, 0), TOLERANCE);
        assertEquals(0.0, standardized.getDouble(0, 0), TOLERANCE);
        assertEquals(0.0, robust.getDouble(0, 0), TOLERANCE);
        assertEquals(0.0, maxAbs.getDouble(0, 0), TOLERANCE);

        assertEquals(5.0, Scaler.fit(constant).invert(scaled).getDouble(0, 0), TOLERANCE);
        assertEquals(5.0, WinsorScaler.fit(constant).invert(winsor).getDouble(0, 0), TOLERANCE);
        assertEquals(5.0, Standardizer.fit(constant).invert(standardized).getDouble(0, 0), TOLERANCE);
        assertEquals(5.0, RobustStandardizer.fit(constant).invert(robust).getDouble(0, 0), TOLERANCE);
        assertEquals(0.0, MaxAbsScaler.fit(zeros).invert(maxAbs).getDouble(0, 0), TOLERANCE);
    }

    @Test
    public void testGivenInvalidTransformInputsWhenFittingThenHelpfulExceptionsAreThrown() {
        // Given
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("label", DataTypes.StringType)
        );
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[] {1.0, "a"}),
                Tuple.of(schema, new Object[] {2.0, "b"})
        ));
        DataFrame empty = DataFrame.of(schema, List.of());

        // When / Then
        assertEquals("Empty data frame",
                assertThrows(IllegalArgumentException.class, () -> Scaler.fit(empty)).getMessage());
        assertEquals("label is not numeric",
                assertThrows(IllegalArgumentException.class, () -> Standardizer.fit(data, "label")).getMessage());
        assertEquals("Invalid lower: -0.1",
                assertThrows(IllegalArgumentException.class, () -> WinsorScaler.fit(data, -0.1, 0.9, "x")).getMessage());
        assertEquals("Invalid upper: 1.1",
                assertThrows(IllegalArgumentException.class, () -> WinsorScaler.fit(data, 0.1, 1.1, "x")).getMessage());
        assertEquals("Invalid lower=0.800000 > upper=0.200000",
                assertThrows(IllegalArgumentException.class, () -> WinsorScaler.fit(data, 0.8, 0.2, "x")).getMessage());
    }
}

