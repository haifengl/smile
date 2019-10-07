/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.regression;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.CPU;
import smile.data.Longley;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RMSE;
import smile.validation.Validation;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class RidgeRegressionTest {
    public RidgeRegressionTest() {
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
    public void testLongley() {
        System.out.println("longley");
        LinearModel model = RidgeRegression.fit(Longley.formula, Longley.data, 0.1);
        System.out.println(model);

        assertEquals(-1.354007e+03, model.intercept(), 1E-3);
        assertEquals(5.457700e-02, model.coefficients()[0], 1E-7);
        assertEquals(1.198440e-02, model.coefficients()[1], 1E-7);
        assertEquals(1.261978e-02, model.coefficients()[2], 1E-7);
        assertEquals(-1.856041e-01, model.coefficients()[3], 1E-7);
        assertEquals(7.218054e-01, model.coefficients()[4], 1E-7);
        assertEquals(5.884884e-01, model.coefficients()[5], 1E-7);

        double[] prediction = LOOCV.regression(Longley.data, (x) -> RidgeRegression.fit(Longley.formula, x, 0.1));
        double rmse = RMSE.apply(Longley.y, prediction);

        System.out.println("LOOCV RMSE = " + rmse);
        assertEquals(1.7288188, rmse, 1E-7);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        // to get repeatable results.
        MathEx.setSeed(19650218);

        LinearModel model = RidgeRegression.fit(CPU.formula, CPU.data, 0.1);
        System.out.println(model);

        double[] prediction = CrossValidation.regression(10, CPU.data, (x) -> RidgeRegression.fit(CPU.formula, x, 0.1));
        double rmse = RMSE.apply(CPU.y, prediction);

        System.out.println("10-CV RMSE = " + rmse);
        assertEquals(55.268333864, rmse, 1E-7);
    }
}