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
package smile.regression;

import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.datasets.CPU;
import smile.datasets.Longley;
import smile.validation.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class LASSOTest {
    public LASSOTest() {
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
    public void testToy() {
        double[][] A = {
            {1, 0, 0, 0.5},
            {0, 1, 0.2, 0.3},
            {1, 0.5, 0.2, 0.3},
            {0, 0.1, 0, 0.2},
            {0, 0.1, 1, 0.2}
        };
        
        double[] y = {6, 5.2, 6.2, 5, 6};

        DataFrame df = DataFrame.of(A).add(new DoubleVector("y", y));
        RegressionValidation<LinearModel> result = RegressionValidation.of(Formula.lhs("y"), df, df,
                (formula, data) -> LASSO.fit(formula, data, new LASSO.Options(0.1, 0.001, 500)));

        System.out.println(result.model());
        System.out.println(result);
        
        assertEquals(5.0259443688265355, result.model().intercept(), 1E-7);
        double[] w = {0.9659945126777854, -3.7147706312985876E-4, 0.9553629503697613, 9.416740009376934E-4};
        for (int i = 0; i < w.length; i++) {
            assertEquals(w[i], result.model().coefficients().get(i), 1E-5);
        }
    }

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");
        var longley = new Longley();
        LinearModel model = LASSO.fit(longley.formula(), longley.data(), new LASSO.Options(0.1));
        System.out.println(model);

        RegressionMetrics metrics = LOOCV.regression(longley.formula(), longley.data(),
                (f, x) -> LASSO.fit(f, x, new LASSO.Options(0.1)));

        System.out.println(metrics);
        assertEquals(1.4146, metrics.rmse(), 1E-4);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testCPU() throws Exception {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.
        var cpu = new CPU();
        LinearModel model = LASSO.fit(cpu.formula(), cpu.data(), new LASSO.Options(0.1));
        System.out.println(model);

        RegressionValidations<LinearModel> result  = CrossValidation.regression(10, cpu.formula(), cpu.data(),
                (f, x) -> LASSO.fit(f, x, new LASSO.Options(0.1)));

        System.out.println(result);
        assertEquals(51.0009, result.avg().rmse(), 1E-4);
    }
}
