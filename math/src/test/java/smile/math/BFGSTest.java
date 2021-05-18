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

package smile.math;

import java.util.Arrays;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class BFGSTest {

    DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {
        @Override
        public double f(double[] x) {
            double f = 0.0;
            for (int j = 1; j <= x.length; j += 2) {
                double t1 = 1.0 - x[j - 1];
                double t2 = 10.0 * (x[j] - x[j - 1] * x[j - 1]);
                f = f + t1 * t1 + t2 * t2;
            }
            return f;
        }

        @Override
        public double g(double[] x, double[] g) {
            double f = 0.0;
            for (int j = 1; j <= x.length; j += 2) {
                double t1 = 1.0 - x[j - 1];
                double t2 = 10.0 * (x[j] - x[j - 1] * x[j - 1]);
                g[j + 1 - 1] = 20.0 * t2;
                g[j - 1] = -2.0 * (x[j - 1] * g[j + 1 - 1] + t1);
                f = f + t1 * t1 + t2 * t2;
            }
            return f;
        }
    };

    public BFGSTest() {

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
     * Test BFGS.
     */
    @Test
    public void testBFGS() {
        System.out.println("BFGS");

        double[] x = new double[100];
        for (int j = 1; j <= x.length; j += 2) {
            x[j - 1] = -1.2;
            x[j] = 1.2;
        }

        double result = BFGS.minimize(func, x, 1E-5, 500);
        System.out.println(Arrays.toString(x));
        assertEquals(3.448974035627997E-10, result, 1E-15);
    }

    /**
     * Test L-BFGS.
     */
    @Test
    public void testLBFGS() {
        System.out.println("L-BFGS");

        double[] x = new double[100];
        for (int j = 1; j <= x.length; j += 2) {
            x[j - 1] = -1.2;
            x[j] = 1.2;
        }

        double result = BFGS.minimize(func, 5, x, 1E-5, 500);
        System.out.println(Arrays.toString(x));
        assertEquals(2.2877072513327043E-15, result, 1E-15);
    }

    /**
     * Test L-BFGS-B.
     */
    @Test
    public void testLBFGSB() {
        System.out.println("L-BFGS-B");

        double[] x = new double[100];
        double[] l = new double[100];
        double[] u = new double[100];
        for (int j = 1; j <= x.length; j += 2) {
            x[j - 1] = -1.2;
            x[j] = 1.2;

            l[j - 1] = 1.2;
            u[j - 1] = 2.5;
            l[j] = -2.5;
            u[j] = 0.8;
        }

        double result = BFGS.minimize(func, 5, x, l, u, 1E-8, 500);
        System.out.println(Arrays.toString(x));
        assertEquals(2050, result, 1E-7);
    }

    /**
     * Test L-BFGS-B.
     */
    @Test
    public void testLBFGSB2() {
        System.out.println("L-BFGS-B: (x_0 - 3)^2 + (x_1 - 4)^2 + 1");

        DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {
            @Override
            public double f(double[] x) {
                double x0 = x[0] - 3;
                double x1 = x[1] - 4;
                return x0 * x0 + x1 * x1 + 1;
            }

            @Override
            public double g(double[] x, double[] g) {
                double x0 = x[0] - 3;
                double x1 = x[1] - 4;
                g[0] = 2 * x0;
                g[1] = 2 * x1;
                return x0 * x0 + x1 * x1 + 1;
            }
        };

        double[] x = new double[2];
        double[] l = {3.5, 3.5};
        double[] u = {5.0, 5.0};

        double result = BFGS.minimize(func, 5, x, l, u, 1E-8, 500);
        assertEquals(3.5, x[0], 1E-15);
        assertEquals(4.0, x[1], 1E-15);
        assertEquals(1.25, result, 1E-15);
    }

    /**
     * Test L-BFGS-B.
     */
    @Test
    public void testLBFGSB3() {
        System.out.println("L-BFGS-B: x_0 ^ 4 + (x_0 - 3)^2 + (x_1 - 4)^2 + 1");

        DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {
            @Override
            public double f(double[] x) {
                double x0 = x[0] - 3;
                double x1 = x[1] - 4;
                return Math.pow(x[0], 4) + x0 * x0 + x1 * x1 + 1;
            }

            @Override
            public double g(double[] x, double[] g) {
                double x0 = x[0] - 3;
                double x1 = x[1] - 4;
                g[0] = 4 * Math.pow(x[0], 3) + 2 * x0;
                g[1] = 2 * x1;
                return Math.pow(x[0], 4) + x0 * x0 + x1 * x1 + 1;
            }
        };

        double[] x = new double[2];
        double[] l = {3.5, 3.5};
        double[] u = {5.0, 5.0};

        double result = BFGS.minimize(func, 5, x, l, u, 1E-8, 500);
        assertEquals(3.5, x[0], 1E-15);
        assertEquals(4.0, x[1], 1E-15);
        assertEquals(151.3125, result, 1E-15);
    }
}
