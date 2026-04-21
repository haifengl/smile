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
import smile.data.transform.Transform;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("Invalid lower=0.800000 >= upper=0.200000",
                assertThrows(IllegalArgumentException.class, () -> WinsorScaler.fit(data, 0.8, 0.2, "x")).getMessage());
    }

    @Test
    public void testGivenEqualLowerAndUpperWhenFittingWinsorScalerThenExceptionIsThrown() {
        // Given
        StructType schema = new StructType(new StructField("x", DataTypes.DoubleType));
        DataFrame data = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{1.0}),
                Tuple.of(schema, new Object[]{2.0})
        ));

        // When / Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WinsorScaler.fit(data, 0.5, 0.5, "x"));
        assertTrue(ex.getMessage().contains(">="), "message should mention >=");
    }

    @Test
    public void testGivenWinsorScalerWithColumnSubsetWhenApplyingThenOnlySubsetIsTransformed() {
        // Given
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("y", DataTypes.DoubleType)
        );
        DataFrame train = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{0.0, 100.0}),
                Tuple.of(schema, new Object[]{10.0, 200.0})
        ));
        DataFrame test = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{5.0, 150.0})
        ));

        // When – only transform "x", leave "y" unchanged
        InvertibleColumnTransform transform = WinsorScaler.fit(train, "x");
        DataFrame result = transform.apply(test);

        // Then
        assertEquals(150.0, result.getDouble(0, 1), TOLERANCE);
    }

    @Test
    public void testGivenOutOfRangeTestValueWhenApplyingScalerThenOutputIsClamped() {
        // Given
        DataFrame train = DataFrame.of(new double[][]{{0.0}, {10.0}}, "x");
        DataFrame below = DataFrame.of(new double[][]{{-5.0}}, "x");
        DataFrame above = DataFrame.of(new double[][]{{20.0}}, "x");

        // When
        InvertibleColumnTransform scaler = Scaler.fit(train);

        // Then
        assertEquals(0.0, scaler.apply(below).getDouble(0, 0), TOLERANCE);
        assertEquals(1.0, scaler.apply(above).getDouble(0, 0), TOLERANCE);
    }

    @Test
    public void testGivenAllNegativeColumnWhenApplyingMaxAbsScalerThenScaleIsCorrect() {
        // Given
        DataFrame train = DataFrame.of(new double[][]{{-3.0}, {-1.0}, {-2.0}}, "x");

        // When
        InvertibleColumnTransform scaler = MaxAbsScaler.fit(train);
        DataFrame result = scaler.apply(train);

        // Then – max(|-3|,|-1|,|-2|) = 3.0; values / 3.0
        assertEquals(-1.0, result.getDouble(0, 0), TOLERANCE);
        assertEquals(-1.0 / 3.0, result.getDouble(1, 0), TOLERANCE);
        assertEquals(-2.0 / 3.0, result.getDouble(2, 0), TOLERANCE);
        // roundtrip
        assertEquals(-3.0, scaler.invert(result).getDouble(0, 0), 1E-10);
    }

    @Test
    public void testGivenTwoTransformsWhenPipelinedThenBothAreApplied() {
        // Given – a column with known mean and range
        DataFrame train = DataFrame.of(new double[][]{{0.0}, {2.0}, {4.0}, {6.0}, {8.0}}, "x");

        // When
        Transform pipeline = Transform.pipeline(
                Standardizer.fit(train),
                MaxAbsScaler.fit(Standardizer.fit(train).apply(train))
        );
        DataFrame result = pipeline.apply(train);

        // Then – all values should be in [-1, 1] and the max absolute value is 1.0
        double maxAbs = 0.0;
        for (int i = 0; i < result.nrow(); i++) {
            double v = result.getDouble(i, 0);
            assertTrue(v >= -1.0 - TOLERANCE && v <= 1.0 + TOLERANCE,
                    "value out of [-1,1]: " + v);
            maxAbs = Math.max(maxAbs, Math.abs(v));
        }
        assertEquals(1.0, maxAbs, 1E-10);
    }

    @Test
    public void testGivenStandardizerWhenAppliedThenMeanIsZeroAndStdIsOne() {
        // Given
        DataFrame train = DataFrame.of(new double[][]{{2.0}, {4.0}, {4.0}, {4.0}, {5.0}, {5.0}, {7.0}, {9.0}}, "x");

        // When
        InvertibleColumnTransform scaler = Standardizer.fit(train);
        DataFrame result = scaler.apply(train);

        // Then
        double[] values = result.column("x").toDoubleArray();
        double mean = java.util.Arrays.stream(values).average().orElseThrow();
        double variance = java.util.Arrays.stream(values).map(v -> (v - mean) * (v - mean)).average().orElseThrow();
        assertEquals(0.0, mean, 1E-10);
        assertEquals(1.0, variance * values.length / (values.length - 1), 1E-10);
    }

    @Test
    public void testGivenRobustStandardizerWhenAppliedThenOutlierHasLessInfluenceThanStandardizer() {
        // Given – data with a clear outlier
        DataFrame train = DataFrame.of(new double[][]{{1.0}, {2.0}, {3.0}, {4.0}, {5.0}, {100.0}}, "x");

        // When
        double[] standardized = Standardizer.fit(train).apply(train).column("x").toDoubleArray();
        double[] robust       = RobustStandardizer.fit(train).apply(train).column("x").toDoubleArray();

        // Then – the bulk of data (first 5 rows) should be less spread by robust
        double stdRange = standardized[4] - standardized[0];
        double robRange = robust[4] - robust[0];
        assertTrue(robRange > stdRange,
                "Robust scaler should spread the bulk data more than standard scaler");
    }
}

