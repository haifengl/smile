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

import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Arff;
import smile.math.kernel.PolynomialKernel;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import smile.util.Paths;
import smile.validation.CrossValidation;

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

    /**
     * Test of learn method, of class SVR.
     */
    @Test(expected = Test.None.class)
    public void testCPU() throws Exception {
        System.out.println("CPU");

        Arff arff = new Arff(Paths.getTestData("weka/cpu.arff"));
        DataFrame cpu = arff.read();
        Formula formula = Formula.lhs("class");

        double[][] x = formula.frame(cpu).toArray();
        double[] y = formula.response(cpu).toDoubleArray();
        MathEx.standardize(x);

        SVR<double[]> svr = new SVR<>(new PolynomialKernel(3, 1.0, 1.0), 0.1, 1.0, 1E-3);
        CrossValidation cv = new CrossValidation(x.length, 10);
        double rss = cv.test(x, y, (xi, yi) -> svr.fit(xi, yi));

        System.out.println("10-CV RMSE = " + rss);
    }
}