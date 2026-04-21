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
import smile.data.transform.ColumnTransform;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.IntVector;
import smile.datasets.BreastCancer;
import smile.datasets.Default;
import smile.datasets.Iris;
import smile.datasets.Weather;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class InformationValueTest {

    public InformationValueTest() {
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
    public void testDefault() throws Exception {
        System.out.println("Default");

        var dataset = new Default();
        InformationValue[] iv = InformationValue.fit(dataset.data(), "default");
        System.out.println(InformationValue.toString(iv));

        assertEquals(3, iv.length);
        assertEquals(0.0364, iv[0].iv(), 1E-4);
        assertEquals(4.2638, iv[1].iv(), 1E-4);
        assertEquals(0.0664, iv[2].iv(), 1E-4);

        ColumnTransform transform = InformationValue.toTransform(iv);
        System.out.println(transform.apply(dataset.data()));
    }

    @Test
    public void testBreastCancer() throws Exception {
        System.out.println("BreastCancer");
        var cancer = new BreastCancer();
        InformationValue[] iv = InformationValue.fit(cancer.data(), "diagnosis");
        System.out.println(InformationValue.toString(iv));

        assertEquals(30, iv.length);
        assertEquals(0.2425, iv[ 9].iv(), 1E-4);
        assertEquals(0.1002, iv[11].iv(), 1E-4);
        assertEquals(0.0817, iv[14].iv(), 1E-4);
    }

    @Test
    public void testWeather() throws Exception {
        System.out.println("Weather");
        var weather = new Weather();
        InformationValue[] iv = InformationValue.fit(weather.data(), "play");
        System.out.println(InformationValue.toString(iv));

        assertEquals(4, iv.length);
        assertEquals(0.9012, iv[0].iv(), 1E-4);
        assertEquals(0.6291, iv[1].iv(), 1E-4);
        assertEquals(0.6291, iv[2].iv(), 1E-4);
        assertEquals(0.2930, iv[3].iv(), 1E-4);

        ColumnTransform transform = InformationValue.toTransform(iv);
        System.out.println(transform.apply(weather.data()));
    }

    @Test
    public void testGivenNBinsLessThanTwoWhenFittingThenIllegalArgumentException() throws Exception {
        var dataset = new Default();
        assertThrows(IllegalArgumentException.class,
                () -> InformationValue.fit(dataset.data(), "default", 1));
    }

    @Test
    public void testGivenMoreThanTwoClassesWhenFittingThenUnsupportedOperationException() throws Exception {
        var iris = new Iris();
        // Iris has 3 classes
        assertThrows(UnsupportedOperationException.class,
                () -> InformationValue.fit(iris.data(), "class"));
    }

    @Test
    public void testGivenMultipleFeaturesWhenSortingThenAscendingByIV() throws Exception {
        var dataset = new Default();
        InformationValue[] iv = InformationValue.fit(dataset.data(), "default");
        Arrays.sort(iv);
        for (int i = 1; i < iv.length; i++) {
            assertTrue(iv[i - 1].iv() <= iv[i].iv(),
                    "sorted IV values should be non-decreasing");
        }
    }

    @Test
    public void testGivenTwoIVsWhenComparingThenLowerIVIsLess() {
        InformationValue low  = new InformationValue("low",  0.05, new double[]{0.0}, null);
        InformationValue high = new InformationValue("high", 0.5,  new double[]{0.0}, null);
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
        assertEquals(0, low.compareTo(new InformationValue("copy", 0.05, new double[]{0.0}, null)));
    }

    @Test
    public void testGivenAllIVRangesWhenCallingToStringThenPredictivePowerIsCorrect() throws Exception {
        var dataset = new Default();
        InformationValue[] iv = InformationValue.fit(dataset.data(), "default");
        String table = InformationValue.toString(iv);
        // The Default dataset has 'balance' with IV ≈ 4.26 → "Suspicious"
        assertTrue(table.contains("Suspicious"), "balance IV > 0.5 should be labeled Suspicious");
        assertTrue(table.contains("Weak") || table.contains("Not useful") || table.contains("Medium"),
                "Other features should have meaningful predictive power labels");
    }

    @Test
    public void testGivenExactBreakPointWhenApplyingTransformThenValueGoesToNextBin() {
        // Build a simple InformationValue with 2 bins: woe = [1.0, -1.0], breaks = [5.0]
        // Value 5.0 equals breaks[0] → should go to bin 1 (woe=-1.0), not bin 0 (woe=1.0)
        double[] woe    = {1.0, -1.0};
        double[] breaks = {5.0};
        InformationValue iv = new InformationValue("feature", 0.3, woe, breaks);
        ColumnTransform transform = InformationValue.toTransform(new InformationValue[]{iv});

        // x < 5.0: bin 0
        StructType schema = new StructType(new StructField("feature", DataTypes.DoubleType));
        Tuple below = Tuple.of(schema, new Object[]{3.0});
        Tuple exact = Tuple.of(schema, new Object[]{5.0});
        Tuple above = Tuple.of(schema, new Object[]{7.0});

        DataFrame df_below = DataFrame.of(schema, List.of(below));
        DataFrame df_exact = DataFrame.of(schema, List.of(exact));
        DataFrame df_above = DataFrame.of(schema, List.of(above));

        assertEquals(1.0, transform.apply(df_below).getDouble(0, 0), 1e-9,
                "x < break should map to bin 0");
        assertEquals(-1.0, transform.apply(df_exact).getDouble(0, 0), 1e-9,
                "x == break should map to bin 1 (the bin that starts at the break)");
        assertEquals(-1.0, transform.apply(df_above).getDouble(0, 0), 1e-9,
                "x > break should map to bin 1");
    }

    @Test
    public void testGivenInvalidNominalValueWhenApplyingTransformThenIllegalArgumentException() throws Exception {
        var weather = new Weather();
        InformationValue[] iv = InformationValue.fit(weather.data(), "play");

        // Build an InformationValue for a nominal feature with 3 WoE bins (codes 0,1,2)
        // and then request code 99, which is out of range.
        double[] woeNominal = {0.1, 0.2, 0.3};
        InformationValue nominalIV = new InformationValue("outlook", 0.9, woeNominal, null);
        ColumnTransform transform = InformationValue.toTransform(new InformationValue[]{nominalIV});

        StructType schema = new StructType(new StructField("outlook", DataTypes.IntType));
        DataFrame dfBad = DataFrame.of(schema, List.of(Tuple.of(schema, new Object[]{99})));
        assertThrows(IllegalArgumentException.class, () -> transform.apply(dfBad));
    }
}
