/*******************************************************************************
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
 ******************************************************************************/

package smile.math;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.stat.distribution.GaussianDistribution;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class LevenbergMarquardtTest {

    public LevenbergMarquardtTest() {
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
    public void test() {
        System.out.println("LevenbergMarquardt");
        DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {

            @Override
            public double f(double[] x) {
                return 1 / (1 + x[0] * Math.pow(x[2], x[1]));
            }

            @Override
            public double g(double[] x, double[] g) {
                double pow = Math.pow(x[2], x[1]);
                double de = 1 + x[0] * pow;
                g[0] = -pow / (de * de);
                g[1] = -(x[0] * x[1] * Math.pow(x[2], x[1]-1)) / (de * de);
                return 1 / de;
            }
        };

        double[] x = new double[100];
        double[] y = new double[100];
        GaussianDistribution d = new GaussianDistribution(0.0, 0.03);
        for (int i = 0; i < x.length; i++) {
            x[i] = (i+1) * 0.05;
            y[i] = 1.0 / (1 + 1.2 * Math.pow(x[i], 1.8)) + d.rand();
        }

        double[] p = {0.5, 1.1};
        double ss = LevenbergMarquardt.fit(func, x, y, p);
        assertEquals(3.2760, ss, 1E-4);
        assertEquals(1.1962, p[0], 1E-4);
        assertEquals(1.7955, p[1], 1E-4);
    }
}

