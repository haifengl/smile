/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.regression;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.CPU;
import smile.data.DataFrame;
import smile.data.Longley;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.math.MathEx;
import smile.validation.*;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class LASSOTest {
    public LASSOTest() {
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

        DataFrame df = DataFrame.of(A).merge(DoubleVector.of("y", y));
        RegressionValidation<LinearModel> result = RegressionValidation.of(Formula.lhs("y"), df, df,
                (formula, data) -> LASSO.fit(formula, data, 0.1, 0.001, 500));

        System.out.println(result.model);
        System.out.println(result);
        
        assertEquals(5.0259443688265355, result.model.intercept(), 1E-7);
        double[] w = {0.9659945126777854, -3.7147706312985876E-4, 0.9553629503697613, 9.416740009376934E-4};
        for (int i = 0; i < w.length; i++) {
            assertEquals(w[i], result.model.coefficients()[i], 1E-5);
        }
    }

    @Test
    public void testLongley() throws Exception {
        System.out.println("longley");

        LinearModel model = LASSO.fit(Longley.formula, Longley.data, 0.1);
        System.out.println(model);

        RegressionMetrics metrics = LOOCV.regression(Longley.formula, Longley.data,
                (f, x) -> LASSO.fit(f, x, 0.1));

        System.out.println(metrics);
        assertEquals(1.4146, metrics.rmse, 1E-4);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.

        LinearModel model = LASSO.fit(CPU.formula, CPU.data, 0.1);
        System.out.println(model);

        RegressionValidations<LinearModel> result  = CrossValidation.regression(10, CPU.formula, CPU.data,
                (f, x) -> LASSO.fit(f, x, 0.1));

        System.out.println(result);
        assertEquals(51.0009, result.avg.rmse, 1E-4);
    }
}