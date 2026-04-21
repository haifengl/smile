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
package smile.feature.imputation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.datasets.Longley;
import smile.datasets.SyntheticControl;
import smile.datasets.USArrests;
import smile.io.Read;
import smile.math.MathEx;
import smile.io.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class SimpleImputerTest {

    public SimpleImputerTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    static double impute(Function<double[][], double[][]> imputer, double[][] data, double rate) {
        int n = 0;
        double[][] missing = new double[data.length][data[0].length];
        for (int i = 0; i < missing.length; i++) {
            for (int j = 0; j < missing[i].length; j++) {
                if (MathEx.random() < rate) {
                    n++;
                    missing[i][j] = Double.NaN;
                } else {
                    missing[i][j] = data[i][j];
                }
            }
        }

        double[][] imputed = imputer.apply(missing);

        double error = 0.0;
        for (int i = 0; i < imputed.length; i++) {
            for (int j = 0; j < imputed[i].length; j++) {
                error += Math.abs(data[i][j] - imputed[i][j]) / data[i][j];
            }
        }

        error = 100 * error / n;
        System.out.format("The error of %d%% missing values = %.2f%n", (int) (100 * rate),  error);
        return error;
    }

    @Test
    public void testUSArrests() throws Exception {
        var usa = new USArrests();
        System.out.println(usa.data());
        SimpleImputer imputer = SimpleImputer.fit(usa.data());
        System.out.println(imputer);
    }

    @Test
    public void testLongley() throws Exception {
        var longley = new Longley();
        System.out.println(longley.data());
        SimpleImputer imputer = SimpleImputer.fit(longley.data());
        System.out.println(imputer);
    }

    @Test
    public void testAverage() throws Exception {
        System.out.println("Column Average Imputation");
        var control = new SyntheticControl();
        double[][] data = control.x();

        assertEquals(39.11, impute(SimpleImputer::impute, data, 0.01), 1E-2);
        assertEquals(49.73, impute(SimpleImputer::impute, data, 0.05), 1E-2);
        assertEquals(58.06, impute(SimpleImputer::impute, data, 0.10), 1E-2);
        assertEquals(8.83, impute(SimpleImputer::impute, data, 0.15), 1E-2);
        assertEquals(55.95, impute(SimpleImputer::impute, data, 0.20), 1E-2);
        assertEquals(48.67, impute(SimpleImputer::impute, data, 0.25), 1E-2);
    }

    @Test
    public void test() throws Exception {
        System.out.println("SimpleImputer");
        var control = new SyntheticControl();
        double[][] data = control.x();
        DataFrame df = DataFrame.of(data);
        SimpleImputer simpleImputer = SimpleImputer.fit(df);
        Function<double[][], double[][]> imputer = x -> simpleImputer.apply(DataFrame.of(x)).toArray();

        assertEquals(38.88, impute(imputer, data, 0.01), 1E-2);
        assertEquals(49.63, impute(imputer, data, 0.05), 1E-2);
        assertEquals(58.04, impute(imputer, data, 0.10), 1E-2);
    }

    @Test
    public void testJson() throws Exception {
        System.out.println("SimpleImputer on JSON");
        DataFrame df = Read.json(Paths.getTestData("imputation/access.json"));
        System.out.println(df);
        SimpleImputer imputer = SimpleImputer.fit(df);
        System.out.println(imputer);
        System.out.println(imputer.apply(df));
    }

    @Test
    public void testCsv() throws Exception {
        System.out.println("SimpleImputer on CSV");
        DataFrame df = Read.csv(Paths.getTestData("imputation/ratio.csv").toString(), "header=true");
        System.out.println(df);
        SimpleImputer imputer = SimpleImputer.fit(df);
        System.out.println(imputer);
        System.out.println(imputer.apply(df));
    }

    // ---- utility method tests ----

    @Test
    public void testGivenNullValueWhenCheckingIsMissingThenTrue() {
        assertTrue(SimpleImputer.isMissing(null));
    }

    @Test
    public void testGivenNaNValueWhenCheckingIsMissingThenTrue() {
        assertTrue(SimpleImputer.isMissing(Double.NaN));
        assertTrue(SimpleImputer.isMissing(Float.NaN));
    }

    @Test
    public void testGivenFiniteValueWhenCheckingIsMissingThenFalse() {
        assertFalse(SimpleImputer.isMissing(0.0));
        assertFalse(SimpleImputer.isMissing(42));
        assertFalse(SimpleImputer.isMissing("hello"));
    }

    @Test
    public void testGivenTupleWithMissingWhenCheckingHasMissingThenTrue() {
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType)
        );
        Tuple t = Tuple.of(schema, new Object[]{1.0, Double.NaN});
        assertTrue(SimpleImputer.hasMissing(t));
    }

    @Test
    public void testGivenCompleteTupleWhenCheckingHasMissingThenFalse() {
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType)
        );
        Tuple t = Tuple.of(schema, new Object[]{1.0, 2.0});
        assertFalse(SimpleImputer.hasMissing(t));
    }

    // ---- apply(Tuple) test ----

    @Test
    public void testGivenTupleWithMissingWhenApplyingImputerThenMissingIsReplaced() {
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType)
        );
        SimpleImputer imputer = new SimpleImputer(Map.of("a", 99.0, "b", 7.0));
        Tuple in = Tuple.of(schema, new Object[]{Double.NaN, 2.0});
        Tuple out = imputer.apply(in);

        assertEquals(99.0, out.getDouble(0), 1e-9);
        assertEquals(2.0,  out.getDouble(1), 1e-9);
    }

    @Test
    public void testGivenCompleteTupleWhenApplyingImputerThenValuesAreUnchanged() {
        StructType schema = new StructType(
                new StructField("a", DataTypes.DoubleType),
                new StructField("b", DataTypes.DoubleType)
        );
        SimpleImputer imputer = new SimpleImputer(Map.of("a", 99.0, "b", 7.0));
        Tuple in = Tuple.of(schema, new Object[]{3.0, 4.0});
        Tuple out = imputer.apply(in);

        assertEquals(3.0, out.getDouble(0), 1e-9);
        assertEquals(4.0, out.getDouble(1), 1e-9);
    }

    // ---- subset of columns ----

    @Test
    public void testGivenColumnSubsetWhenFittingImputerThenOnlyThoseColumnsAreImputed() {
        StructType schema = new StructType(
                new StructField("x", DataTypes.DoubleType),
                new StructField("y", DataTypes.DoubleType)
        );
        DataFrame df = DataFrame.of(schema, List.of(
                Tuple.of(schema, new Object[]{1.0, 10.0}),
                Tuple.of(schema, new Object[]{2.0, 20.0}),
                Tuple.of(schema, new Object[]{3.0, 30.0})
        ));

        // Only impute column "x"; "y" should not get an imputation entry.
        SimpleImputer imputer = SimpleImputer.fit(df, "x");
        String repr = imputer.toString();
        assertTrue(repr.contains("x ->"), "x should be in the imputer");
        assertFalse(repr.contains("y ->"), "y should NOT be in the imputer");
    }

    // ---- invalid arguments ----

    @Test
    public void testGivenEmptyDataFrameWhenFittingImputerThenIllegalArgumentException() {
        StructType schema = new StructType(new StructField("a", DataTypes.DoubleType));
        DataFrame empty = DataFrame.of(schema, List.of());
        assertThrows(IllegalArgumentException.class, () -> SimpleImputer.fit(empty));
    }

    @Test
    public void testGivenNegativeLowerWhenFittingThenIllegalArgumentException() {
        DataFrame df = DataFrame.of(new double[][]{{1.0, 2.0}, {3.0, 4.0}});
        assertThrows(IllegalArgumentException.class, () -> SimpleImputer.fit(df, -0.1, 0.9));
    }

    @Test
    public void testGivenUpperGreaterThanOneWhenFittingThenIllegalArgumentException() {
        DataFrame df = DataFrame.of(new double[][]{{1.0, 2.0}, {3.0, 4.0}});
        assertThrows(IllegalArgumentException.class, () -> SimpleImputer.fit(df, 0.0, 1.1));
    }

    @Test
    public void testGivenLowerGreaterThanUpperWhenFittingThenIllegalArgumentException() {
        DataFrame df = DataFrame.of(new double[][]{{1.0, 2.0}, {3.0, 4.0}});
        assertThrows(IllegalArgumentException.class, () -> SimpleImputer.fit(df, 0.8, 0.2));
    }

    // ---- impute(double[][]) raw array API ----

    @Test
    public void testGivenAllMissingRowWhenImputingRawArrayThenIllegalArgumentException() {
        double[][] data = {
                {1.0, 2.0},
                {Double.NaN, Double.NaN},   // entirely missing row
                {3.0, 4.0}
        };
        assertThrows(IllegalArgumentException.class, () -> SimpleImputer.impute(data));
    }

    @Test
    public void testGivenAllMissingColumnWhenImputingRawArrayThenIllegalArgumentException() {
        double[][] data = {
                {1.0, Double.NaN},
                {2.0, Double.NaN},
                {3.0, Double.NaN}
        };
        assertThrows(IllegalArgumentException.class, () -> SimpleImputer.impute(data));
    }

    @Test
    public void testGivenSingleMissingValueWhenImputingRawArrayThenFilledWithColumnMean() {
        double[][] data = {
                {1.0, 2.0},
                {Double.NaN, 4.0},
                {3.0, 6.0}
        };
        double[][] result = SimpleImputer.impute(data);
        // column-0 mean of {1.0, 3.0} = 2.0
        assertEquals(2.0, result[1][0], 1e-9);
        // non-missing values are preserved unchanged
        assertEquals(1.0, result[0][0], 1e-9);
        assertEquals(3.0, result[2][0], 1e-9);
    }

    // ---- trimmed-mean path (lower != upper) ----

    @Test
    public void testGivenTrimmedMeanParamsWhenFittingThenImputationValueFallsInTrimmedRange() {
        // Build a dataset with outliers.  Median (0.5) = 10, trimmed mean [0.1,0.9] ≈ 10.
        double[] col = {1.0, 8.0, 9.0, 10.0, 11.0, 12.0, 100.0};
        double[][] raw = new double[col.length][1];
        for (int i = 0; i < col.length; i++) raw[i][0] = col[i];
        DataFrame df = DataFrame.of(raw);

        SimpleImputer imputer = SimpleImputer.fit(df, 0.1, 0.9);

        // Apply to a tuple with a missing value and check the fill value is reasonable.
        StructType schema = df.schema();
        Tuple missing = Tuple.of(schema, new Object[]{Double.NaN});
        Tuple filled = imputer.apply(missing);
        double fill = filled.getDouble(0);
        assertTrue(Double.isFinite(fill), "fill value must be finite");
        assertTrue(fill >= 1.0 && fill <= 100.0, "fill value should be within data range");
    }
}