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
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.LinearKernel;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.RMSE;
import smile.validation.Validation;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class SVRTest {

    public SVRTest() {
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

        Regression<double[]> model = SVR.fit(Longley.x, Longley.y, 0.5, 10.0, 1E-3);
        System.out.println(model);

        double[] prediction = Validation.test(model, Longley.x);
        double rmse = RMSE.apply(Longley.y, prediction);

        System.out.println("RMSE = " + rmse);
        assertEquals(0.9233178794283378, rmse, 1E-4);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(CPU.x);
        MathEx.standardize(x);

        GaussianKernel kernel = new GaussianKernel(36);
        KernelMachine<double[]> model = SVR.fit(x, CPU.y, kernel, 5.0, 1.0, 1E-3);
        System.out.println(model);

        double[] prediction = CrossValidation.regression(10, x, CPU.y, (xi, yi) -> SVR.fit(xi, yi,40.0, 10.0, 1E-3));
        double rmse = RMSE.apply(CPU.y, prediction);

        System.out.println("10-CV RMSE = " + rmse);
        assertEquals(54.63430240465948, rmse, 1E-4);
    }
}