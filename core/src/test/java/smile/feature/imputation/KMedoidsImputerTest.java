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
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.math.distance.Distance;
import smile.datasets.SyntheticControl;
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static smile.feature.imputation.SimpleImputerTest.impute;

/**
 *
 * @author Haifeng Li
 */
public class KMedoidsImputerTest {

    public KMedoidsImputerTest() {
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
        System.out.println("KMedoidsImputer");
        var control = new SyntheticControl();
        double[][] data = control.x();
        DataFrame df = DataFrame.of(data);
        Distance<Tuple> distance = (x, y) -> {
            double[] xd = x.toArray();
            double[] yd = y.toArray();
            return MathEx.distanceWithMissingValues(xd, yd);
        };
        KMedoidsImputer kmedoidsImputer = KMedoidsImputer.fit(df, distance, 20);
        Function<double[][], double[][]> imputer = x -> kmedoidsImputer.apply(DataFrame.of(x)).toArray();

        assertEquals(18.92, impute(imputer, data, 0.01), 1E-2);
        assertEquals(17.19, impute(imputer, data, 0.05), 1E-2);
        assertEquals(26.76, impute(imputer, data, 0.10), 1E-2);
    }

    @Test
    public void testGivenCompleteTupleWhenApplyingKMedoidsImputerThenSameTupleIsReturned() throws Exception {
        // Given
        var control = new SyntheticControl();
        double[][] data = control.x();
        DataFrame df = DataFrame.of(data);
        Distance<Tuple> distance = (x, y) -> MathEx.distanceWithMissingValues(x.toArray(), y.toArray());
        KMedoidsImputer imputer = KMedoidsImputer.fit(df, distance, 10);

        Tuple complete = df.get(0);
        assertFalse(SimpleImputer.hasMissing(complete));

        // When — the fast-path (hasMissing == false) should return the same object
        Tuple result = imputer.apply(complete);

        // Then — identity: the exact same reference is returned
        assertSame(complete, result, "KMedoidsImputer should return the original tuple when there are no missing values");
    }

    @Test
    public void testGivenTupleWithMissingWhenApplyingKMedoidsImputerThenMissingIsFilled() throws Exception {
        // Given
        var control = new SyntheticControl();
        double[][] data = control.x();
        DataFrame df = DataFrame.of(data);
        Distance<Tuple> distance = (x, y) -> MathEx.distanceWithMissingValues(x.toArray(), y.toArray());
        KMedoidsImputer imputer = KMedoidsImputer.fit(df, distance, 10);

        // Make a copy of the first row with column 0 forced to NaN
        Tuple original = df.get(0);
        Object[] values = new Object[original.length()];
        for (int i = 0; i < values.length; i++) values[i] = original.get(i);
        values[0] = Double.NaN;
        Tuple withMissing = Tuple.of(df.schema(), values);

        // When
        Tuple result = imputer.apply(withMissing);

        // Then — the missing column should now be filled with a finite value
        assertTrue(Double.isFinite(result.getDouble(0)), "missing value should be filled");
        // Existing values at non-missing positions should be preserved
        for (int i = 1; i < original.length(); i++) {
            assertEquals(original.getDouble(i), result.getDouble(i), 1e-9,
                    "non-missing column " + i + " should be unchanged");
        }
    }
}