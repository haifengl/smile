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
import smile.datasets.SyntheticControl;
import smile.math.MathEx;
import static org.junit.jupiter.api.Assertions.*;
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
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() throws Exception {
        System.out.println("SVDImputer");
        var control = new SyntheticControl();
        double[][] data = control.x();
        int k = data[0].length / 5;

        Function<double[][], double[][]> imputer = x -> SVDImputer.impute(x, k, 10);
        assertEquals(13.50, impute(imputer, data, 0.01), 1E-2);
        assertEquals(15.42, impute(imputer, data, 0.05), 1E-2);
        assertEquals(16.10, impute(imputer, data, 0.10), 1E-2);
        // Matrix will be rank deficient with higher missing rate.
    }

    @Test
    public void testGivenInvalidKWhenImputingThenIllegalArgumentException() {
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}};
        assertThrows(IllegalArgumentException.class, () -> SVDImputer.impute(data, 0, 5));
    }

    @Test
    public void testGivenKExceedingMinDimensionWhenImputingThenIllegalArgumentException() {
        // data is 3x2, so k must be <= min(3,2) = 2
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}};
        assertThrows(IllegalArgumentException.class, () -> SVDImputer.impute(data, 3, 5));
    }

    @Test
    public void testGivenInvalidMaxIterWhenImputingThenIllegalArgumentException() {
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}};
        assertThrows(IllegalArgumentException.class, () -> SVDImputer.impute(data, 1, 0));
    }

    @Test
    public void testGivenAllMissingRowWhenImputingThenIllegalArgumentException() {
        double[][] data = {
                {1.0, 2.0},
                {Double.NaN, Double.NaN},  // entire row missing
                {3.0, 4.0}
        };
        assertThrows(IllegalArgumentException.class, () -> SVDImputer.impute(data, 1, 5));
    }

    @Test
    public void testGivenAllMissingColumnWhenImputingThenIllegalArgumentException() {
        double[][] data = {
                {1.0, Double.NaN},
                {2.0, Double.NaN},
                {3.0, Double.NaN}
        };
        assertThrows(IllegalArgumentException.class, () -> SVDImputer.impute(data, 1, 5));
    }

    @Test
    public void testGivenCompleteDataWhenImputingThenOutputMatchesInput() {
        double[][] data = {
                {1.0, 2.0, 3.0},
                {4.0, 5.0, 6.0},
                {7.0, 8.0, 9.0}
        };
        double[][] result = SVDImputer.impute(data, 2, 5);
        // No missing values; imputed result should reproduce the original.
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                assertEquals(data[i][j], result[i][j], 1e-6,
                        "complete data should be preserved at [" + i + "][" + j + "]");
            }
        }
    }
}