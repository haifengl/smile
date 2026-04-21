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
package smile.feature.selection;

import java.util.Arrays;
import java.util.List;
import smile.classification.LDA;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.datasets.Iris;
import smile.datasets.USPS;
import smile.validation.metric.Accuracy;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SumSquaresRatioTest {
    
    public SumSquaresRatioTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");
        var iris = new Iris();
        SumSquaresRatio[] ssr = SumSquaresRatio.fit(iris.data(), "class");
        assertEquals(4, ssr.length);
        assertEquals( 1.6226463, ssr[0].ratio(), 1E-6);
        assertEquals( 0.6444144, ssr[1].ratio(), 1E-6);
        assertEquals(16.0412833, ssr[2].ratio(), 1E-6);
        assertEquals(13.0520327, ssr[3].ratio(), 1E-6);
    }

    @Test
    public void testGivenOnlyOneClassWhenFittingThenExceptionIsThrown() {
        // A DataFrame where all rows belong to class 0 — either ClassLabels or
        // SumSquaresRatio itself will reject it with a RuntimeException.
        StructType schema = new StructType(
                new StructField("feat", DataTypes.DoubleType),
                new StructField("label", DataTypes.IntType)
        );
        DataFrame df = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{1.0, 0}),
                Tuple.of(schema, new Object[]{2.0, 0})
        ));
        assertThrows(RuntimeException.class,
                () -> SumSquaresRatio.fit(df, "label"));
    }

    @Test
    public void testGivenBinaryClassificationWhenFittingThenResultHasCorrectLength() {
        // k=2 is the minimum valid case for SSR
        StructType schema = new StructType(
                new StructField("feat", DataTypes.DoubleType),
                new StructField("label", DataTypes.IntType)
        );
        DataFrame df = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{1.0, 0}),
                Tuple.of(schema, new Object[]{3.0, 0}),
                Tuple.of(schema, new Object[]{5.0, 1}),
                Tuple.of(schema, new Object[]{7.0, 1})
        ));
        SumSquaresRatio[] ssr = SumSquaresRatio.fit(df, "label");
        assertEquals(1, ssr.length);
        assertTrue(ssr[0].ratio() > 0.0, "ratio should be positive");
        assertTrue(Double.isFinite(ssr[0].ratio()), "ratio should be finite");
    }

    @Test
    public void testGivenConstantFeatureWithinEachClassWhenFittingThenRatioIsMaxValue() {
        // Perfect separation: class 0 is always 1.0, class 1 is always 2.0
        // wss = 0, bss > 0 → ratio should be MAX_VALUE (not Infinity/NaN)
        StructType schema = new StructType(
                new StructField("feat", DataTypes.DoubleType),
                new StructField("label", DataTypes.IntType)
        );
        DataFrame df = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{1.0, 0}),
                Tuple.of(schema, new Object[]{1.0, 0}),
                Tuple.of(schema, new Object[]{2.0, 1}),
                Tuple.of(schema, new Object[]{2.0, 1})
        ));
        SumSquaresRatio[] ssr = SumSquaresRatio.fit(df, "label");
        assertEquals(1, ssr.length);
        assertEquals(Double.MAX_VALUE, ssr[0].ratio(), 0.0);
    }

    @Test
    public void testGivenConstantFeatureInAllClassesWhenFittingThenRatioIsZero() {
        // All values identical across all classes: bss = wss = 0 → ratio should be 0
        StructType schema = new StructType(
                new StructField("feat", DataTypes.DoubleType),
                new StructField("label", DataTypes.IntType)
        );
        DataFrame df = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{5.0, 0}),
                Tuple.of(schema, new Object[]{5.0, 0}),
                Tuple.of(schema, new Object[]{5.0, 1}),
                Tuple.of(schema, new Object[]{5.0, 1})
        ));
        SumSquaresRatio[] ssr = SumSquaresRatio.fit(df, "label");
        assertEquals(1, ssr.length);
        assertEquals(0.0, ssr[0].ratio(), 1e-12);
    }

    @Test
    public void testGivenMultipleFeaturesWhenSortingThenAscendingOrder() throws Exception {
        var iris = new Iris();
        SumSquaresRatio[] ssr = SumSquaresRatio.fit(iris.data(), "class");
        Arrays.sort(ssr);
        for (int i = 1; i < ssr.length; i++) {
            assertTrue(ssr[i - 1].ratio() <= ssr[i].ratio(),
                    "sorted ratios should be non-decreasing");
        }
    }

    @Test
    public void testGivenTwoRatiosWhenComparingThenLowerRatioIsLess() {
        SumSquaresRatio low  = new SumSquaresRatio("low",  0.5);
        SumSquaresRatio high = new SumSquaresRatio("high", 5.0);
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
        assertEquals(0, low.compareTo(new SumSquaresRatio("copy", 0.5)));
    }

    @Test
    @Tag("integration")
    public void tesUSPS() throws Exception {
        System.out.println("USPS");
        var usps = new USPS();
        SumSquaresRatio[] score = SumSquaresRatio.fit(usps.train(), "class");
        Arrays.sort(score);
        String[] columns = Arrays.stream(score).limit(121).map(SumSquaresRatio::feature).toArray(String[]::new);

        double[][] train = usps.formula().x(usps.train().drop(columns)).toArray();
        LDA lda = LDA.fit(train, usps.y());

        double[][] test = usps.formula().x(usps.test().drop(columns)).toArray();
        int[] prediction = lda.predict(test);

        double accuracy = new Accuracy().score(usps.testy(), prediction);
        System.out.format("SSR %.2f%%%n", 100 * accuracy);
        assertEquals(0.86, accuracy, 1E-2);
    }
}
