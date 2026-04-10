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
package smile.data.transform;

import java.util.BitSet;
import java.util.Map;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.DoubleVector;
import smile.data.vector.NullableDoubleVector;
import smile.util.function.Function;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering bug-fixes and coverage gaps in smile.data.transform.
 * Bug fixes verified:
 *  1. Transform.fit()      - ArrayIndexOutOfBoundsException on empty trainers array.
 *  2. Transform.pipeline() - ArrayIndexOutOfBoundsException on empty transforms array.
 *  3. Transform.apply(DataFrame) - NoSuchElementException on empty DataFrame
 *     (used result.getFirst() instead of data.schema()).
 *  4. InvertibleColumnTransform.invert(DataFrame) - Nullable columns were silently
 *     converted to non-nullable DoubleVector (null mask lost).
 *  5. InvertibleColumnTransform.invert(DataFrame) - Nested parallelism hazard:
 *     sequential outer forEach + parallel inner stream.
 */
public class TransformRegressionTest {

    // ------------------------------------------------------------------
    // Shared test fixtures
    // ------------------------------------------------------------------

    /** Non-nullable 5-row frame with columns x and y. */
    DataFrame df;

    /** Nullable 4-row frame: x has a null at index 1, y has a null at index 3. */
    DataFrame dfNullable;

    public TransformRegressionTest() {
        df = new DataFrame(
                new DoubleVector("x", new double[]{1.0, 2.0, 3.0, 4.0, 5.0}),
                new DoubleVector("y", new double[]{10.0, 20.0, 30.0, 40.0, 50.0})
        );

        // Build nullable columns: bit set = position is null
        double[] xVals = {1.0, Double.NaN, 3.0, 4.0};
        BitSet xNull = new BitSet(4);
        xNull.set(1);  // x[1] is null

        double[] yVals = {10.0, 20.0, 30.0, Double.NaN};
        BitSet yNull = new BitSet(4);
        yNull.set(3);  // y[3] is null

        dfNullable = new DataFrame(
                new NullableDoubleVector("x", xVals, xNull),
                new NullableDoubleVector("y", yVals, yNull)
        );
    }

    // ==================================================================
    // BUG FIX 1 – Transform.fit() with empty trainers throws AIOOBE
    // ==================================================================

    @Test
    public void testFitEmptyTrainersThrows() {
        System.out.println("BUG-FIX: Transform.fit() with no trainers must throw");
        assertThrows(IllegalArgumentException.class,
                () -> Transform.fit(df));
    }

    @Test
    public void testFitNullTrainersThrows() {
        System.out.println("BUG-FIX: Transform.fit() with null trainers must throw");
        assertThrows(IllegalArgumentException.class,
                () -> Transform.fit(df, (java.util.function.Function<DataFrame, Transform>[]) null));
    }

    // ==================================================================
    // BUG FIX 2 – Transform.pipeline() with empty transforms throws AIOOBE
    // ==================================================================

    @Test
    public void testPipelineEmptyThrows() {
        System.out.println("BUG-FIX: Transform.pipeline() with no transforms must throw");
        assertThrows(IllegalArgumentException.class, Transform::pipeline);
    }

    @Test
    public void testPipelineNullThrows() {
        System.out.println("BUG-FIX: Transform.pipeline() with null must throw");
        assertThrows(IllegalArgumentException.class,
                () -> Transform.pipeline((Transform[]) null));
    }

    // ==================================================================
    // BUG FIX 3 – Transform.apply(DataFrame) on an empty DataFrame
    //             used result.getFirst() → NoSuchElementException
    // ==================================================================

    @Test
    public void testApplyEmptyDataFrame() {
        System.out.println("BUG-FIX: Transform.apply(empty DataFrame) must not throw");
        // Build an empty frame with the same schema as df
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("y", DataTypes.DoubleType)
        );
        // Create a zero-row frame
        DataFrame empty = new DataFrame(
                new DoubleVector("x", new double[0]),
                new DoubleVector("y", new double[0])
        );

        Map<String, Function> transforms = Map.of("x", v -> v * 2.0);
        ColumnTransform ct = new ColumnTransform("scale2", transforms);

        // Must not throw NoSuchElementException
        DataFrame result = ct.apply(empty);
        assertEquals(0, result.nrow());
        assertEquals(2, result.ncol());
        // Schema must still match the input
        assertEquals("x", result.schema().field(0).name());
        assertEquals("y", result.schema().field(1).name());
    }

    // ==================================================================
    // BUG FIX 4 – InvertibleColumnTransform.invert(DataFrame) lost null mask
    // ==================================================================

    @Test
    public void testInvertNullableColumnPreservesNullMask() {
        System.out.println("BUG-FIX: invert(DataFrame) must preserve null mask on nullable columns");
        Map<String, Function> transforms = Map.of("x", v -> v * 10.0);
        Map<String, Function> inverses   = Map.of("x", v -> v / 10.0);
        InvertibleColumnTransform ict =
                new InvertibleColumnTransform("scale10", transforms, inverses);

        // Apply forward – should produce a nullable x column
        DataFrame scaled = ict.apply(dfNullable);
        assertTrue(scaled.column("x").isNullable(),
                "After apply(), scaled x column must be nullable");
        assertTrue(scaled.column("x").isNullAt(1),
                "Null at index 1 must survive apply()");

        // Invert – must also return nullable column with same null positions
        DataFrame restored = ict.invert(scaled);
        assertTrue(restored.column("x").isNullable(),
                "After invert(), restored x column must remain nullable");
        assertTrue(restored.column("x").isNullAt(1),
                "Null at index 1 must survive invert()");

        // Non-null values must be correctly inverted
        assertEquals(1.0, restored.getDouble(0, 0), 1e-10);
        assertEquals(3.0, restored.getDouble(2, 0), 1e-10);
        assertEquals(4.0, restored.getDouble(3, 0), 1e-10);
    }

    @Test
    public void testInvertNonNullableColumnStaysNonNullable() {
        System.out.println("invert(DataFrame) on non-nullable columns stays non-nullable");
        Map<String, Function> transforms = Map.of("x", v -> v + 100.0);
        Map<String, Function> inverses   = Map.of("x", v -> v - 100.0);
        InvertibleColumnTransform ict =
                new InvertibleColumnTransform("shift", transforms, inverses);

        DataFrame scaled   = ict.apply(df);
        DataFrame restored = ict.invert(scaled);

        assertFalse(restored.column("x").isNullable(),
                "Non-nullable column must remain non-nullable after invert()");
        assertEquals(1.0, restored.getDouble(0, 0), 1e-10);
        assertEquals(5.0, restored.getDouble(4, 0), 1e-10);
    }

    @Test
    public void testInvertUnchangedColumnsPreserveOriginalVector() {
        System.out.println("invert(DataFrame): columns not in inverse map are passed through unchanged");
        Map<String, Function> transforms = Map.of("x", v -> v * 2.0);
        Map<String, Function> inverses   = Map.of("x", v -> v / 2.0);
        InvertibleColumnTransform ict =
                new InvertibleColumnTransform("halve", transforms, inverses);

        DataFrame scaled   = ict.apply(df);
        DataFrame restored = ict.invert(scaled);

        // y was not in the inverse map — must be untouched
        assertEquals(10.0, restored.getDouble(0, 1), 1e-10);
        assertEquals(50.0, restored.getDouble(4, 1), 1e-10);
    }

    // ==================================================================
    // Invert round-trip correctness – both Tuple and DataFrame paths
    // ==================================================================

    @Test
    public void testInvertRoundTripDataFrame() {
        System.out.println("InvertibleColumnTransform round-trip: apply then invert");
        Map<String, Function> transforms = Map.of(
                "x", v -> (v - 3.0) / 1.5,   // standardise-like
                "y", v -> (v - 30.0) / 15.0
        );
        Map<String, Function> inverses = Map.of(
                "x", v -> v * 1.5 + 3.0,
                "y", v -> v * 15.0 + 30.0
        );
        InvertibleColumnTransform ict =
                new InvertibleColumnTransform("std", transforms, inverses);

        DataFrame scaled   = ict.apply(df);
        DataFrame restored = ict.invert(scaled);

        for (int i = 0; i < df.nrow(); i++) {
            assertEquals(df.getDouble(i, 0), restored.getDouble(i, 0), 1e-9,
                    "x round-trip failed at row " + i);
            assertEquals(df.getDouble(i, 1), restored.getDouble(i, 1), 1e-9,
                    "y round-trip failed at row " + i);
        }
    }

    @Test
    public void testInvertRoundTripTuple() {
        System.out.println("InvertibleColumnTransform round-trip: apply then invert on Tuple");
        Map<String, Function> transforms = Map.of("x", v -> v * v);  // square
        Map<String, Function> inverses   = Map.of("x", Math::sqrt);  // sqrt

        InvertibleColumnTransform ict =
                new InvertibleColumnTransform("square", transforms, inverses);

        Tuple row = df.get(2); // x=3.0, y=30.0
        Tuple scaled   = ict.apply(row);
        assertEquals(9.0, scaled.getDouble(0), 1e-10);

        Tuple restored = ict.invert(scaled);
        assertEquals(3.0, restored.getDouble(0), 1e-10);
        assertEquals(30.0, restored.getDouble(1), 1e-10); // y untouched
    }

    @Test
    public void testInvertTuplePassThroughUntransformedColumns() {
        System.out.println("InvertibleColumnTransform.invert(Tuple): untransformed columns pass through");
        Map<String, Function> transforms = Map.of("x", v -> v * 2.0);
        Map<String, Function> inverses   = Map.of("x", v -> v / 2.0);
        InvertibleColumnTransform ict =
                new InvertibleColumnTransform("double", transforms, inverses);

        Tuple row     = df.get(0); // x=1.0, y=10.0
        Tuple scaled  = ict.apply(row);
        Tuple restored= ict.invert(scaled);

        // x inverted
        assertEquals(1.0,  restored.getDouble(0), 1e-10);
        // y unchanged
        assertEquals(10.0, restored.getDouble(1), 1e-10);
    }

    // ==================================================================
    // Transform.pipeline – single and multi-step
    // ==================================================================

    @Test
    public void testPipelineSingleTransform() {
        System.out.println("Transform.pipeline with one transform is identity composition");
        Map<String, Function> t1 = Map.of("x", v -> v * 7.0);
        ColumnTransform c1 = new ColumnTransform("mul7", t1);
        Transform pipeline = Transform.pipeline(c1);

        Tuple out = pipeline.apply(df.get(0)); // x=1.0
        assertEquals(7.0, out.getDouble(0), 1e-10);
    }

    @Test
    public void testPipelineThreeSteps() {
        System.out.println("Transform.pipeline with three steps");
        // x → *2 → +3 → /5
        ColumnTransform c1 = new ColumnTransform("mul2", Map.of("x", v -> v * 2.0));
        ColumnTransform c2 = new ColumnTransform("add3", Map.of("x", v -> v + 3.0));
        ColumnTransform c3 = new ColumnTransform("div5", Map.of("x", v -> v / 5.0));
        Transform pipeline = Transform.pipeline(c1, c2, c3);

        Tuple out = pipeline.apply(df.get(0)); // x=1.0 → 2 → 5 → 1.0
        assertEquals(1.0, out.getDouble(0), 1e-10);
    }

    @Test
    public void testPipelineDataFrame() {
        System.out.println("Transform.pipeline on DataFrame");
        ColumnTransform c1 = new ColumnTransform("step1", Map.of("x", v -> v * 2.0));
        ColumnTransform c2 = new ColumnTransform("step2", Map.of("y", v -> v / 10.0));
        Transform pipeline = Transform.pipeline(c1, c2);

        DataFrame out = pipeline.apply(df);
        assertEquals(2.0, out.getDouble(0, 0), 1e-10);  // x: 1 * 2
        assertEquals(1.0, out.getDouble(0, 1), 1e-10);  // y: 10 / 10
        assertEquals(10.0, out.getDouble(4, 0), 1e-10); // x: 5 * 2
        assertEquals(5.0,  out.getDouble(4, 1), 1e-10); // y: 50 / 10
    }

    // ==================================================================
    // Transform.fit – sequential data-dependent training
    // ==================================================================

    @Test
    public void testFitSingleTrainer() {
        System.out.println("Transform.fit with one trainer");
        Transform fitted = Transform.fit(df,
                data -> new ColumnTransform("t", Map.of("x", v -> v * 2.0)));

        Tuple out = fitted.apply(df.get(0)); // x=1.0 → 2.0
        assertEquals(2.0, out.getDouble(0), 1e-10);
    }

    @Test
    public void testFitDataPropagatedBetweenTrainers() {
        System.out.println("Transform.fit: second trainer receives transformed data from first");
        // Trainer 1: x → x * 2  (data passed to trainer 2 has x ∈ {2,4,6,8,10})
        // Trainer 2: shift by constant derived from the already-transformed x column.
        // We verify the pipeline composes both transforms correctly.
        Transform fitted = Transform.fit(df,
                data -> new ColumnTransform("double", Map.of("x", v -> v * 2.0)),
                data -> {
                    // data here should have x ∈ {2,4,6,8,10} after step 1
                    double firstX = data.getDouble(0, 0); // should be 2.0
                    return new ColumnTransform("shift", Map.of("x", v -> v + firstX));
                });

        // x=1.0 → *2=2.0 → +2.0=4.0
        Tuple out = fitted.apply(df.get(0));
        assertEquals(4.0, out.getDouble(0), 1e-10);
    }

    // ==================================================================
    // andThen / compose symmetry
    // ==================================================================

    @Test
    public void testAndThenVsComposeSymmetry() {
        System.out.println("Transform: andThen(b) == b.compose(a)");
        ColumnTransform a = new ColumnTransform("a", Map.of("x", v -> v * 3.0));
        ColumnTransform b = new ColumnTransform("b", Map.of("x", v -> v - 1.0));

        Transform via_andThen  = a.andThen(b);
        Transform via_compose  = b.compose(a);

        Tuple row = df.get(1); // x=2.0
        double r1 = via_andThen.apply(row).getDouble(0);
        double r2 = via_compose.apply(row).getDouble(0);
        assertEquals(r1, r2, 1e-10);
        assertEquals(5.0, r1, 1e-10); // 2*3-1=5
    }

    @Test
    public void testAndThenDataFrame() {
        System.out.println("Transform.andThen on DataFrame");
        ColumnTransform c1 = new ColumnTransform("c1", Map.of("x", v -> v + 10.0));
        ColumnTransform c2 = new ColumnTransform("c2", Map.of("x", v -> v * 2.0));
        Transform chained = c1.andThen(c2);

        DataFrame out = chained.apply(df);
        // x: (1+10)*2 = 22
        assertEquals(22.0, out.getDouble(0, 0), 1e-10);
        // y unchanged
        assertEquals(10.0, out.getDouble(0, 1), 1e-10);
    }

    // ==================================================================
    // ColumnTransform – no-op columns and toString
    // ==================================================================

    @Test
    public void testColumnTransformNoTransformColumns() {
        System.out.println("ColumnTransform: columns not in map are passed through as-is");
        Map<String, Function> transforms = Map.of("x", v -> v * 100.0);
        ColumnTransform ct = new ColumnTransform("scale", transforms);

        DataFrame out = ct.apply(df);
        // y not in map — should be identical to original
        for (int i = 0; i < df.nrow(); i++) {
            assertEquals(df.getDouble(i, 1), out.getDouble(i, 1), 1e-10,
                    "y[" + i + "] must be unchanged");
        }
    }

    @Test
    public void testColumnTransformToString() {
        System.out.println("ColumnTransform.toString includes name");
        Function f = new Function() {
            @Override public double f(double x) { return x * 2; }
            @Override public String toString() { return "x*2"; }
        };
        ColumnTransform ct = new ColumnTransform("TestName", Map.of("x", f));
        assertTrue(ct.toString().contains("TestName"),
                "toString() must include the transform name");
        assertTrue(ct.toString().contains("x*2"),
                "toString() must include function description");
    }

    @Test
    public void testColumnTransformEmptyMapIsIdentity() {
        System.out.println("ColumnTransform with empty map acts as identity");
        ColumnTransform ct = new ColumnTransform("identity", Map.of());
        DataFrame out = ct.apply(df);

        for (int i = 0; i < df.nrow(); i++) {
            assertEquals(df.getDouble(i, 0), out.getDouble(i, 0), 1e-10);
            assertEquals(df.getDouble(i, 1), out.getDouble(i, 1), 1e-10);
        }
    }

    // ==================================================================
    // ColumnTransform – nullable column apply (DataFrame path)
    // ==================================================================

    @Test
    public void testColumnTransformNullableColumnApply() {
        System.out.println("ColumnTransform.apply(DataFrame): nullable columns stay nullable");
        Map<String, Function> transforms = Map.of("x", v -> v * 2.0);
        ColumnTransform ct = new ColumnTransform("double", transforms);

        DataFrame out = ct.apply(dfNullable);
        assertTrue(out.column("x").isNullable(),
                "Transformed nullable column must remain nullable");
        assertTrue(out.column("x").isNullAt(1),
                "Null at index 1 must survive apply()");
        assertEquals(2.0, out.getDouble(0, 0), 1e-10);
        assertEquals(6.0, out.getDouble(2, 0), 1e-10);
    }

    @Test
    public void testColumnTransformNullableUntouchedColumnPassesThrough() {
        System.out.println("ColumnTransform.apply(DataFrame): untouched nullable columns pass through");
        Map<String, Function> transforms = Map.of("x", v -> v * 2.0);
        ColumnTransform ct = new ColumnTransform("double", transforms);

        DataFrame out = ct.apply(dfNullable);
        // y was not transformed — should be the same vector object or same values
        assertTrue(out.column("y").isNullable(),
                "Untouched nullable y column must stay nullable");
        assertTrue(out.column("y").isNullAt(3),
                "Null at index 3 in y must be preserved");
        assertEquals(10.0, out.getDouble(0, 1), 1e-10);
        assertEquals(30.0, out.getDouble(2, 1), 1e-10);
    }

    // ==================================================================
    // InvertibleColumnTransform – nullable round-trip (apply + invert)
    // ==================================================================

    @Test
    public void testInvertibleNullableFullRoundTrip() {
        System.out.println("InvertibleColumnTransform: nullable full round-trip");
        Map<String, Function> transforms = Map.of(
                "x", v -> v - 2.0,
                "y", v -> v / 10.0
        );
        Map<String, Function> inverses = Map.of(
                "x", v -> v + 2.0,
                "y", v -> v * 10.0
        );
        InvertibleColumnTransform ict =
                new InvertibleColumnTransform("shift_scale", transforms, inverses);

        DataFrame scaled   = ict.apply(dfNullable);
        DataFrame restored = ict.invert(scaled);

        // x: null at 1 must survive both apply and invert
        assertTrue(restored.column("x").isNullable());
        assertTrue(restored.column("x").isNullAt(1));
        assertEquals(1.0 - 2.0 + 2.0, restored.getDouble(0, 0), 1e-10); // 1.0
        assertEquals(3.0 - 2.0 + 2.0, restored.getDouble(2, 0), 1e-10); // 3.0

        // y: null at 3 must survive
        assertTrue(restored.column("y").isNullable());
        assertTrue(restored.column("y").isNullAt(3));
        assertEquals(10.0, restored.getDouble(0, 1), 1e-10);
        assertEquals(30.0, restored.getDouble(2, 1), 1e-10);
    }

    // ==================================================================
    // InvertibleColumnTransform – Tuple invert with no matching column
    // ==================================================================

    @Test
    public void testInvertTupleNoMatchingColumnPassesThrough() {
        System.out.println("InvertibleColumnTransform.invert(Tuple): missing-key columns pass through");
        // inversemap has "z" which does not exist in df schema
        Map<String, Function> transforms = Map.of("x", v -> v * 2);
        Map<String, Function> inverses   = Map.of("z", v -> v / 2); // "z" not in schema
        InvertibleColumnTransform ict =
                new InvertibleColumnTransform("bad_key", transforms, inverses);

        Tuple row     = df.get(0);           // x=1.0, y=10.0
        Tuple scaled  = ict.apply(row);      // x=2.0, y=10.0
        Tuple restored= ict.invert(scaled);  // no "z" → pass all through

        // Since "z" is not in the schema, invert is a no-op
        assertEquals(2.0,  restored.getDouble(0), 1e-10); // x not inverted
        assertEquals(10.0, restored.getDouble(1), 1e-10); // y not inverted
    }
}

