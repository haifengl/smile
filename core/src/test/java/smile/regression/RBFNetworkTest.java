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
import smile.base.rbf.RBF;
import smile.data.*;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;
import smile.validation.RMSE;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class RBFNetworkTest {

    public RBFNetworkTest() {
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

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(Longley.x);
        MathEx.standardize(x);
        double[] prediction = LOOCV.regression(x, Longley.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 10, 5.0)));
        double rmse = RMSE.of(Longley.y, prediction);
        System.out.println("RMSE = " + rmse);
        assertEquals(4.922188709128203, rmse, 1E-4);

        RBFNetwork<double[]> model = RBFNetwork.fit(Longley.x, Longley.y, RBF.fit(Longley.x, 10, 5.0));
        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(CPU.x);
        MathEx.standardize(x);
        double[] prediction = CrossValidation.regression(10, x, CPU.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        double rmse = RMSE.of(CPU.y, prediction);
        System.out.println("RMSE = " + rmse);
        assertEquals(24.967181232853843, rmse, 1E-4);
    }

    @Test
    public void test2DPlanes() {
        System.out.println("2dplanes");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[] prediction = CrossValidation.regression(10, Planes.x, Planes.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        double rmse = RMSE.of(Planes.y, prediction);
        System.out.println("RMSE = " + rmse);
        assertEquals(1.7161562336830596, rmse, 1E-4);
    }

    @Test
    public void testAilerons() {
        System.out.println("ailerons");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(Ailerons.x);
        MathEx.standardize(x);
        double[] prediction = CrossValidation.regression(10, x, Ailerons.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        double rmse = RMSE.of(Ailerons.y, prediction);
        System.out.println("RMSE = " + rmse);
        assertEquals(2.440061343792136E-4, rmse, 1E-4);
    }

    @Test
    public void testBank32nh() {
        System.out.println("bank32nh");

        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = MathEx.clone(Bank32nh.x);
        MathEx.standardize(x);
        double[] prediction = CrossValidation.regression(10, x, Bank32nh.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        double rmse = RMSE.of(Bank32nh.y, prediction);
        System.out.println("RMSE = " + rmse);
        assertEquals(0.08711993434501915, rmse, 1E-4);
    }
}