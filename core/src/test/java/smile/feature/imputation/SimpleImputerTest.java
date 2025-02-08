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
    }

    @AfterEach
    public void tearDown() {
    }

    static void impute(Function<double[][], double[][]> imputer, double[][] data, double rate, double expected) {
        MathEx.setSeed(19650218); // to get repeatable results.

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
        assertEquals(expected, error, 1E-2);
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

        impute(SimpleImputer::impute, data, 0.01, 39.11);
        impute(SimpleImputer::impute, data, 0.05, 48.86);
        impute(SimpleImputer::impute, data, 0.10, 45.24);
        impute(SimpleImputer::impute, data, 0.15, 44.59);
        impute(SimpleImputer::impute, data, 0.20, 41.93);
        impute(SimpleImputer::impute, data, 0.25, 44.77);
    }

    @Test
    public void testSimpleImputer() throws Exception {
        System.out.println("SimpleImputer");
        var control = new SyntheticControl();
        double[][] data = control.x();
        DataFrame df = DataFrame.of(data);
        SimpleImputer simpleImputer = SimpleImputer.fit(df);
        Function<double[][], double[][]> imputer = x -> simpleImputer.apply(DataFrame.of(x)).toArray();

        impute(imputer, data, 0.01, 38.88);
        impute(imputer, data, 0.05, 48.80);
        impute(imputer, data, 0.10, 45.04);
    }

    @Test
    public void testJson() throws Exception {
        System.out.println("SimpleImputer on JSON");
        DataFrame df = Read.json(Paths.getTestData("json/access.json"));
        System.out.println(df);
        SimpleImputer simpleImputer = SimpleImputer.fit(df);
        System.out.println(simpleImputer);
        System.out.println(simpleImputer.apply(df));
/*
        impute(imputer, data, 0.01, 38.88);
        impute(imputer, data, 0.05, 48.80);
        impute(imputer, data, 0.10, 45.04);*/
    }
}