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
import static org.junit.Assert.*;

import smile.data.CPU;
import smile.data.Longley;
import smile.data.Prostate;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.RMSE;
import smile.validation.Validation;

/**
 *
 * @author Haifeng Li
 */
public class OLSTest {

    public OLSTest() {
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
    public void testLongley() throws Exception {
        System.out.println("longley");

        LinearModel model = OLS.fit(Longley.formula, Longley.data);
        System.out.println(model);

        assertEquals(12.8440, model.RSS(), 1E-4);
        assertEquals(1.1946, model.error(), 1E-4);
        assertEquals(9, model.df());
        assertEquals(0.9926, model.RSquared(), 1E-4);
        assertEquals(0.9877, model.adjustedRSquared(), 1E-4);
        assertEquals(202.5094, model.ftest(), 1E-4);
        assertEquals(4.42579E-9, model.pvalue(), 1E-14);

        double[][] ttest = {
                {   0.26353,    0.10815,   2.437,   0.0376},
                {   0.03648,    0.03024,   1.206,   0.2585},
                {   0.01116,    0.01545,   0.722,   0.4885},
                {  -1.73703,    0.67382,  -2.578,   0.0298},
                {  -1.41880,    2.94460,  -0.482,   0.6414},
                {   0.23129,    1.30394,   0.177,   0.8631},
                {2946.85636, 5647.97658,   0.522,   0.6144}
        };

        double[] residuals = {
                -0.6008156,  1.5502732,  0.1032287, -1.2306486, -0.3355139,  0.2693345,  0.8776759,
                0.1222429, -2.0086121, -0.4859826,  1.0663129,  1.2274906, -0.3835821,  0.2710215,
                0.1978569, -0.6402823
        };

        for (int i = 0; i < ttest.length; i++) {
            for (int j = 0; j < 4; j++) {
                assertEquals(ttest[i][j], model.ttest()[i][j], 1E-3);
            }
        }

        for (int i = 0; i < residuals.length; i++) {
            assertEquals(residuals[i], model.residuals()[i], 1E-4);
        }

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void testCPU() {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.

        LinearModel model = OLS.fit(CPU.formula, CPU.data);
        System.out.println(model);

        double[] prediction = CrossValidation.regression(10, CPU.formula, CPU.data, (f, x) -> OLS.fit(f, x));
        double rmse = RMSE.of(CPU.y, prediction);
        System.out.println("CPU 10-CV RMSE = " + rmse);
        assertEquals(55.272997, rmse, 1E-4);
    }

    /**
     * Test of learn method, of class LinearRegression.
     */
    @Test
    public void testProstate() {
        System.out.println("Prostate");

        LinearModel model = OLS.fit(Prostate.formula, Prostate.train);
        System.out.println(model);

        double[] prediction = Validation.test(model, Prostate.test);
        double rmse = RMSE.of(Prostate.testy, prediction);
        System.out.println("RMSE on test data = " + rmse);
        assertEquals(0.721993, rmse, 1E-4);
    }
}