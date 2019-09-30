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
import smile.base.RBF;
import smile.data.*;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;

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

    @Test
    public void testLongley() {
        System.out.println("longley");

        double[][] x = MathEx.clone(Longley.x);
        MathEx.standardize(x);
        double rmse = LOOCV.test(x, Longley.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 10, 5.0)));
        System.out.println("RMSE = " + rmse);
        assertEquals(3.690870261996264, rmse, 1E-4);
    }

    @Test
    public void testCPU() {
        System.out.println("CPU");
        double[][] x = MathEx.clone(CPU.x);
        MathEx.standardize(x);
        double rmse = CrossValidation.test(10, x, CPU.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        System.out.println("RMSE = " + rmse);
        assertEquals(14.168654451291467, rmse, 1E-4);
    }

    @Test
    public void test2DPlanes() {
        System.out.println("2dplanes");
        double rmse = CrossValidation.test(10, Planes.x, Planes.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        System.out.println("RMSE = " + rmse);
        assertEquals(1.6807682500184575, rmse, 1E-4);
    }

    @Test
    public void testAilerons() {
        System.out.println("ailerons");
        double[][] x = MathEx.clone(Ailerons.x);
        MathEx.standardize(x);
        double rmse = CrossValidation.test(10, x, Ailerons.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        System.out.println("RMSE = " + rmse);
        assertEquals(2.440061343792136E-4, rmse, 1E-4);
    }

    @Test
    public void testBank32nh() {
        System.out.println("bank32nh");
        double[][] x = MathEx.clone(Bank32nh.x);
        MathEx.standardize(x);
        double rmse = CrossValidation.test(10, x, Bank32nh.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        System.out.println("RMSE = " + rmse);
        assertEquals(0.08739766054766657, rmse, 1E-4);
    }
}