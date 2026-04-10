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

import java.util.Map;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.vector.DoubleVector;
import smile.util.function.Function;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for smile.data.transform package.
 *
 * @author Haifeng Li
 */
public class TransformTest {

    DataFrame df;

    public TransformTest() {
        df = new DataFrame(
                new DoubleVector("x", new double[]{1.0, 2.0, 3.0, 4.0, 5.0}),
                new DoubleVector("y", new double[]{10.0, 20.0, 30.0, 40.0, 50.0})
        );
    }

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    @Test
    public void testColumnTransformApplyTuple() {
        System.out.println("ColumnTransform apply tuple");
        // Double each value of column "x"
        Map<String, Function> transforms = Map.of("x", v -> v * 2.0);
        ColumnTransform ct = new ColumnTransform("double-x", transforms);

        Tuple row = df.get(0);   // x=1.0, y=10.0
        Tuple out = ct.apply(row);
        assertEquals(2.0,  out.getDouble(0), 1e-10); // x doubled
        assertEquals(10.0, out.getDouble(1), 1e-10); // y unchanged
    }

    @Test
    public void testColumnTransformApplyDataFrame() {
        System.out.println("ColumnTransform apply DataFrame");
        Map<String, Function> transforms = Map.of("x", v -> v + 100.0);
        ColumnTransform ct = new ColumnTransform("shift-x", transforms);

        DataFrame out = ct.apply(df);
        assertEquals(5, out.nrow());
        assertEquals(101.0, out.getDouble(0, 0), 1e-10);
        assertEquals(102.0, out.getDouble(1, 0), 1e-10);
        assertEquals(10.0,  out.getDouble(0, 1), 1e-10); // y unchanged
    }

    @Test
    public void testColumnTransformMultipleColumns() {
        System.out.println("ColumnTransform multiple columns");
        Map<String, Function> transforms = Map.of(
                "x", v -> v * 2.0,
                "y", v -> v / 10.0
        );
        ColumnTransform ct = new ColumnTransform("multi", transforms);
        DataFrame out = ct.apply(df);

        assertEquals(2.0, out.getDouble(0, 0), 1e-10);  // x: 1 * 2
        assertEquals(1.0, out.getDouble(0, 1), 1e-10);  // y: 10 / 10
        assertEquals(10.0, out.getDouble(4, 0), 1e-10); // x: 5 * 2
        assertEquals(5.0,  out.getDouble(4, 1), 1e-10); // y: 50 / 10
    }

    @Test
    public void testInvertibleColumnTransform() {
        System.out.println("InvertibleColumnTransform apply and invert");
        Map<String, Function> transforms = Map.of("x", v -> v * 3.0);
        Map<String, Function> inverses   = Map.of("x", v -> v / 3.0);
        InvertibleColumnTransform ict = new InvertibleColumnTransform("triple-x", transforms, inverses);

        DataFrame scaled = ict.apply(df);
        assertEquals(3.0, scaled.getDouble(0, 0), 1e-10);
        assertEquals(15.0, scaled.getDouble(4, 0), 1e-10);

        DataFrame restored = ict.invert(scaled);
        assertEquals(1.0, restored.getDouble(0, 0), 1e-10);
        assertEquals(5.0, restored.getDouble(4, 0), 1e-10);
    }

    @Test
    public void testInvertibleColumnTransformTuple() {
        System.out.println("InvertibleColumnTransform tuple invert");
        Map<String, Function> transforms = Map.of("x", v -> v + 10.0);
        Map<String, Function> inverses   = Map.of("x", v -> v - 10.0);
        InvertibleColumnTransform ict = new InvertibleColumnTransform("shift", transforms, inverses);

        Tuple row    = df.get(2); // x=3.0, y=30.0
        Tuple scaled = ict.apply(row);
        assertEquals(13.0, scaled.getDouble(0), 1e-10);

        Tuple back = ict.invert(scaled);
        assertEquals(3.0, back.getDouble(0), 1e-10);
        assertEquals(30.0, back.getDouble(1), 1e-10);
    }

    @Test
    public void testTransformPipeline() {
        System.out.println("Transform.pipeline");
        // Pipeline: first multiply by 2, then subtract 1
        Map<String, Function> t1 = Map.of("x", v -> v * 2.0);
        Map<String, Function> t2 = Map.of("x", v -> v - 1.0);
        ColumnTransform c1 = new ColumnTransform("step1", t1);
        ColumnTransform c2 = new ColumnTransform("step2", t2);
        Transform pipeline = Transform.pipeline(c1, c2);

        Tuple row = df.get(0); // x=1.0
        Tuple out = pipeline.apply(row);
        // 1 * 2 = 2, then 2 - 1 = 1
        assertEquals(1.0, out.getDouble(0), 1e-10);
    }

    @Test
    public void testTransformFit() {
        System.out.println("Transform.fit");
        // Two trainers: each creates a ColumnTransform that doubles the value
        Transform fitted = Transform.fit(df,
                data -> new ColumnTransform("t1", Map.of("x", v -> v * 2.0)),
                data -> new ColumnTransform("t2", Map.of("x", v -> v + 1.0))
        );

        Tuple row = df.get(0); // x=1.0
        Tuple out = fitted.apply(row);
        // Step 1: 1 * 2 = 2; Step 2 applied to transformed data: 2 + 1 = 3
        assertEquals(3.0, out.getDouble(0), 1e-10);
    }

    @Test
    public void testTransformAndThenCompose() {
        System.out.println("Transform andThen/compose");
        Map<String, Function> t1 = Map.of("x", v -> v * 10.0);
        Map<String, Function> t2 = Map.of("x", v -> v + 5.0);
        ColumnTransform c1 = new ColumnTransform("mul10", t1);
        ColumnTransform c2 = new ColumnTransform("add5",  t2);

        Transform andThen  = c1.andThen(c2);    // x → x*10 → x*10+5
        Transform composed = c2.compose(c1);    // same: x → c1 → c2

        Tuple row = df.get(0); // x=1.0
        assertEquals(15.0, andThen.apply(row).getDouble(0), 1e-10);
        assertEquals(15.0, composed.apply(row).getDouble(0), 1e-10);
    }
}

