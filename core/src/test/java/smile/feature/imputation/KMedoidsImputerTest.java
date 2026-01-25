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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.imputation;

import java.util.function.Function;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.math.distance.Distance;
import smile.datasets.SyntheticControl;
import smile.math.MathEx;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() throws Exception {
        System.out.println("KMedoidsImputer");
        MathEx.setSeed(19650218); // to get repeatable results.
        var control = new SyntheticControl();
        double[][] data = control.x();
        DataFrame df = DataFrame.of(data);
        Distance<Tuple> distance = (x, y) -> {
            double[] xd = x.toArray();
            double[] yd = y.toArray();
            return MathEx.distanceWithMissingValues(xd, yd);
        };
        KMedoidsImputer kmedoidsImputer = KMedoidsImputer.fit(df, distance,20);
        Function<double[][], double[][]> imputer = x -> kmedoidsImputer.apply(DataFrame.of(x)).toArray();

        assertEquals(17.14, impute(imputer, data, 0.01), 1E-2);
        assertEquals(19.28, impute(imputer, data, 0.05), 1E-2);
        assertEquals(17.83, impute(imputer, data, 0.10), 1E-2);
    }
}