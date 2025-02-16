/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
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

        impute(imputer, data, 0.01, 17.57);
        impute(imputer, data, 0.05, 20.09);
        impute(imputer, data, 0.10, 18.47);
    }
}