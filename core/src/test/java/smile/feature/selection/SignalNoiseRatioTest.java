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
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.IntVector;
import smile.datasets.BreastCancer;
import smile.datasets.Default;
import smile.datasets.Iris;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SignalNoiseRatioTest {
    
    public SignalNoiseRatioTest() {
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
        var x = iris.x();
        var y = iris.y();

        for (int i = 0; i < y.length; i++) {
            if (y[i] < 2) y[i] = 0;
            else y[i] = 1;
        }

        DataFrame data = iris.data().drop("class").add(new IntVector("y", y));
        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(data, "y");
        assertEquals(4, s2n.length);
        assertEquals(0.8743107, s2n[0].ratio(), 1E-7);
        assertEquals(0.1502717, s2n[1].ratio(), 1E-7);
        assertEquals(1.3446912, s2n[2].ratio(), 1E-7);
        assertEquals(1.4757334, s2n[3].ratio(), 1E-7);
    }

    @Test
    public void testDefault() throws Exception {
        System.out.println("Default");

        var dataset = new Default();
        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(dataset.data(), "default");
        assertEquals(2, s2n.length);
        assertEquals(1.1832, s2n[0].ratio(), 1E-4);
        assertEquals(0.0545, s2n[1].ratio(), 1E-4);
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("BreastCancer");
        var cancer = new BreastCancer();
        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(cancer.data(), "diagnosis");
        assertEquals(30, s2n.length);
        assertEquals(1.0666, s2n[0].ratio(), 1E-4);
        assertEquals(0.4746, s2n[1].ratio(), 1E-4);
        assertEquals(1.1078, s2n[2].ratio(), 1E-4);
    }

    @Test
    public void testGivenMoreThanTwoClassesWhenFittingThenUnsupportedOperationException() throws Exception {
        // Iris has 3 classes — must throw
        var iris = new Iris();
        assertThrows(UnsupportedOperationException.class,
                () -> SignalNoiseRatio.fit(iris.data(), "class"));
    }

    @Test
    public void testGivenConstantFeatureInBothClassesWhenFittingThenRatioIsZeroNotNaN() {
        // Both classes have the same constant value: mu1 == mu2, sd1 == sd2 == 0
        // S2N = 0.0/0.0 would produce NaN; after fix it should return 0.0
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
        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(df, "label");
        assertEquals(1, s2n.length);
        assertEquals(0.0, s2n[0].ratio(), 1E-12);
        assertFalse(Double.isNaN(s2n[0].ratio()), "ratio must not be NaN for a constant feature");
    }

    @Test
    public void testGivenPerfectlySeparatingConstantGroupsWhenFittingThenRatioIsInfinity() {
        // Class 0: all 1.0, class 1: all 2.0 — sd=0 in both, but means differ
        // S2N = (2.0-1.0) / 0.0 = +Infinity (by design — perfectly separating)
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
        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(df, "label");
        assertEquals(1, s2n.length);
        assertTrue(Double.isInfinite(s2n[0].ratio()), "ratio should be +Infinity for a perfect separator");
    }

    @Test
    public void testGivenMultipleFeaturesWhenSortingThenAscendingOrder() throws Exception {
        var dataset = new Default();
        SignalNoiseRatio[] s2n = SignalNoiseRatio.fit(dataset.data(), "default");
        // balance has lower ratio than income; after sorting ascending order holds
        Arrays.sort(s2n);
        for (int i = 1; i < s2n.length; i++) {
            assertTrue(s2n[i - 1].ratio() <= s2n[i].ratio(),
                    "sorted ratios should be non-decreasing");
        }
        assertEquals("SignalNoiseRatio", s2n[0].toString().split("\\(")[0]);
    }

    @Test
    public void testGivenTwoFeaturesWhenComparingThenLowerRatioIsLess() {
        SignalNoiseRatio low  = new SignalNoiseRatio("low",  0.1);
        SignalNoiseRatio high = new SignalNoiseRatio("high", 5.0);
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
        assertEquals(0, low.compareTo(new SignalNoiseRatio("copy", 0.1)));
    }
}
