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
import org.junit.jupiter.api.*;
import smile.test.data.SyntheticControl;
import static smile.feature.imputation.SimpleImputerTest.impute;

/**
 *
 * @author Haifeng Li
 */
public class SVDImputerTest {

    public SVDImputerTest() {
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
        System.out.println("SVDImputer");
        double[][] data = SyntheticControl.x;
        int k = data[0].length / 5;

        Function<double[][], double[][]> imputer = x -> SVDImputer.impute(x, k, 10);
        impute(imputer, data, 0.01, 13.50);
        impute(imputer, data, 0.05, 15.84);
        impute(imputer, data, 0.10, 14.94);
        // Matrix will be rank deficient with higher missing rate.
    }
}