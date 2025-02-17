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
import org.junit.jupiter.api.*;
import smile.data.DataFrame;
import smile.datasets.SyntheticControl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(12.22, impute(imputer, data, 0.05), 1E-2);
        assertEquals(11.63, impute(imputer, data, 0.10), 1E-2);
    }
}