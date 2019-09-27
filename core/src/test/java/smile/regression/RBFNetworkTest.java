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
import smile.data.CPU;
import smile.data.Planes;
import smile.data.Bank32nh;
import smile.data.Ailerons;
import smile.math.MathEx;
import smile.validation.CrossValidation;
import smile.validation.LOOCV;

/**
 *
 * @author Haifeng Li
 */
public class RBFNetworkTest {
    double[][] longley = {
        {234.289,      235.6,        159.0,    107.608, 1947,   60.323},
        {259.426,      232.5,        145.6,    108.632, 1948,   61.122},
        {258.054,      368.2,        161.6,    109.773, 1949,   60.171},
        {284.599,      335.1,        165.0,    110.929, 1950,   61.187},
        {328.975,      209.9,        309.9,    112.075, 1951,   63.221},
        {346.999,      193.2,        359.4,    113.270, 1952,   63.639},
        {365.385,      187.0,        354.7,    115.094, 1953,   64.989},
        {363.112,      357.8,        335.0,    116.219, 1954,   63.761},
        {397.469,      290.4,        304.8,    117.388, 1955,   66.019},
        {419.180,      282.2,        285.7,    118.734, 1956,   67.857},
        {442.769,      293.6,        279.8,    120.445, 1957,   68.169},
        {444.546,      468.1,        263.7,    121.950, 1958,   66.513},
        {482.704,      381.3,        255.2,    123.366, 1959,   68.655},
        {502.601,      393.1,        251.4,    125.368, 1960,   69.564},
        {518.173,      480.6,        257.2,    127.852, 1961,   69.331},
        {554.894,      400.7,        282.7,    130.081, 1962,   70.551}
    };

    double[] y = {
        83.0,  88.5,  88.2,  89.5,  96.2,  98.1,  99.0, 100.0, 101.2,
        104.6, 108.4, 110.8, 112.6, 114.2, 115.7, 116.9
    };

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

    /**
     * Test of learn method, of class RKHSRegression.
     */
    @Test
    public void testLearn() {
        System.out.println("learn");

        MathEx.standardize(longley);
        double rss = LOOCV.test(longley, y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 10, 5.0)));
        System.out.println("MSE = " + rss);
    }

    /**
     * Test of learn method, of class RBFNetwork.
     */
    @Test
    public void testCPU() {
        System.out.println("CPU");
        double rss = CrossValidation.test(10, CPU.x, CPU.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        System.out.println("MSE = " + rss);
    }

    /**
     * Test of learn method, of class RBFNetwork.
     */
    @Test
    public void test2DPlanes() {
        System.out.println("2dplanes");
        double rss = CrossValidation.test(10, Planes.x, Planes.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        System.out.println("MSE = " + rss);
    }

    /**
     * Test of learn method, of class RBFNetwork.
     */
    @Test
    public void testAilerons() {
        System.out.println("ailerons");
        double rss = CrossValidation.test(10, Ailerons.x, Ailerons.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        System.out.println("MSE = " + rss);
    }

    /**
     * Test of learn method, of class RBFNetwork.
     */
    @Test
    public void testBank32nh() {
        System.out.println("bank32nh");
        double rss = CrossValidation.test(10, Bank32nh.x, Bank32nh.y, (xi, yi) -> RBFNetwork.fit(xi, yi, RBF.fit(xi, 20, 5.0)));
        System.out.println("MSE = " + rss);
    }
}