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

import smile.data.CPU;
import smile.data.DataFrame;
import smile.data.Longley;
import smile.data.formula.Formula;
import smile.io.Arff;
import smile.math.kernel.GaussianKernel;
import smile.math.kernel.LinearKernel;
import smile.math.kernel.PolynomialKernel;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import smile.util.Paths;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
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

        SVR<double[]> svr = new SVR<>(new LinearKernel(), 0.5, 10.0, 1E-3);
        KernelMachine<double[]> model = svr.fit(Longley.x, Longley.y);
        System.out.println(model);

        double rmse = Validation.test(model, Longley.x, Longley.y);
        System.out.println("RMSE = " + rmse);
        assertEquals(0.9233178794283378, rmse, 1E-4);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        // to get repeatable results.
        MathEx.setSeed(19650218);

        double[][] x = MathEx.clone(CPU.x);
        MathEx.standardize(x);

        SVR<double[]> svr = new SVR<>(new LinearKernel(), 5.0, 1.0, 1E-3);
        KernelMachine<double[]> model = svr.fit(x, CPU.y);
        System.out.println(model);

        double rmse = CrossValidation.regression(10, x, CPU.y, (xi, yi) -> svr.fit(xi, yi));
        System.out.println("10-CV RMSE = " + rmse);
        assertEquals(162.84821957220652, rmse, 1E-4);
    }
}