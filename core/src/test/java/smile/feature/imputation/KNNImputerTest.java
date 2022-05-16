/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.feature.imputation;

import java.util.function.Function;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.DataFrame;
import smile.test.data.SyntheticControl;
import static smile.feature.imputation.SimpleImputerTest.impute;

/**
 *
 * @author Haifeng Li
 */
public class KNNImputerTest {

    public KNNImputerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test(expected = Test.None.class)
    public void test() throws Exception {
        System.out.println("KNNImputer");
        double[][] data = SyntheticControl.x;
        DataFrame df = DataFrame.of(data);
        KNNImputer knnImputer = new KNNImputer(df, 5);
        Function<double[][], double[][]> imputer = x -> knnImputer.apply(DataFrame.of(x)).toArray();

        impute(imputer, data, 0.01, 11.08);
        impute(imputer, data, 0.05, 12.22);
        impute(imputer, data, 0.10, 11.63);
    }
}