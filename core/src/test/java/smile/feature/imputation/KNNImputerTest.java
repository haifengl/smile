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

import java.util.function.Function;
import org.junit.jupiter.api.*;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.datasets.SyntheticControl;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;
import static smile.feature.imputation.SimpleImputerTest.impute;

/**
 *
 * @author Haifeng Li
 */
public class KNNImputerTest {

    public KNNImputerTest() {
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

    @Test
    public void test() throws Exception {
        System.out.println("KNNImputer");
        var control = new SyntheticControl();
        double[][] data = control.x();
        DataFrame df = DataFrame.of(data);
        KNNImputer knnImputer = new KNNImputer(df, 5);
        Function<double[][], double[][]> imputer = x -> knnImputer.apply(DataFrame.of(x)).toArray();

        assertEquals(11.08, impute(imputer, data, 0.01), 1E-2);
        assertEquals(13.01, impute(imputer, data, 0.05), 1E-2);
        assertEquals(12.53, impute(imputer, data, 0.10), 1E-2);
    }

    @Test
    public void testGivenCompleteTupleWhenApplyingKNNImputerThenValuesAreUnchanged() throws Exception {
        // Given — training set has no missing values
        var control = new SyntheticControl();
        double[][] data = control.x();
        DataFrame df = DataFrame.of(data);
        KNNImputer imputer = new KNNImputer(df, 3);

        // Grab the first row as a complete tuple (no NaN)
        Tuple complete = df.get(0);
        assertFalse(SimpleImputer.hasMissing(complete));

        // When
        Tuple result = imputer.apply(complete);

        // Then — every value should be the same as the original
        for (int i = 0; i < complete.length(); i++) {
            assertEquals(complete.getDouble(i), result.getDouble(i), 1e-9,
                    "column " + i + " should be unchanged");
        }
    }

    @Test
    public void testGivenTupleWithMissingNumericWhenNeighborsAlsoMissingThenNullReturned() {
        // Given — training data where the column we want to impute is always NaN in neighbors
        StructType schema = new StructType(
                new StructField("key", DataTypes.DoubleType),
                new StructField("val", DataTypes.DoubleType)
        );
        // All training rows have NaN for "val"
        DataFrame train = DataFrame.of(schema, java.util.List.of(
                Tuple.of(schema, new Object[]{1.0, Double.NaN}),
                Tuple.of(schema, new Object[]{2.0, Double.NaN})
        ));
        KNNImputer imputer = new KNNImputer(train, 2);

        Tuple query = Tuple.of(schema, new Object[]{1.5, Double.NaN});
        Tuple result = imputer.apply(query);

        // The neighbor values for "val" are all NaN, so the imputer should return null
        assertNull(result.get(1), "should be null when no neighbor has a value for the missing column");
    }

    @Test
    public void testGivenMixedNeighborsWhenImputingThenNaNNeighborValuesAreExcludedFromMean() {
        // Given — two neighbors: one has NaN for the target column, one has 6.0
        StructType schema = new StructType(
                new StructField("key", DataTypes.DoubleType),
                new StructField("val", DataTypes.DoubleType)
        );
        DataFrame train = DataFrame.of(schema, java.util.List.of(
                Tuple.of(schema, new Object[]{1.0, Double.NaN}),  // NaN neighbor
                Tuple.of(schema, new Object[]{1.0, 6.0})           // valid neighbor
        ));
        KNNImputer imputer = new KNNImputer(train, 2);

        Tuple query = Tuple.of(schema, new Object[]{1.0, Double.NaN});
        Tuple result = imputer.apply(query);

        // Before the fix, the NaN neighbor would be included and the mean would be NaN.
        // After the fix, only the valid neighbor (6.0) contributes.
        double filled = result.getDouble(1);
        assertTrue(Double.isFinite(filled), "imputed value must be finite, not NaN");
        assertEquals(6.0, filled, 1e-9, "only the non-NaN neighbor should contribute");
    }
}