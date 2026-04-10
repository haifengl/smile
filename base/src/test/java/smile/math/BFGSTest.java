/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math;

import java.util.Arrays;
import org.junit.jupiter.api.*;
import smile.util.function.DifferentiableMultivariateFunction;
import static org.junit.jupiter.api.Assertions.*;

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

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
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

    /**
     * Tests that BFGS works correctly when the starting point has negative
     * coordinates (exercises the abs(xold[i]) fix in lineSearch).
     */
    @Test
    public void testBFGSNegativeStart() {
        System.out.println("BFGS negative starting point");
        // f(x) = (x0 + 5)^2 + (x1 + 7)^2, minimum at (-5, -7)
        DifferentiableMultivariateFunction func2 = new DifferentiableMultivariateFunction() {
            @Override
            public double f(double[] x) {
                return (x[0] + 5) * (x[0] + 5) + (x[1] + 7) * (x[1] + 7);
            }

            @Override
            public double g(double[] x, double[] g) {
                g[0] = 2 * (x[0] + 5);
                g[1] = 2 * (x[1] + 7);
                return f(x);
            }
        };

        double[] x = {-100.0, -200.0}; // very negative starting point
        double result = BFGS.minimize(func2, x, 1E-8, 200);
        assertEquals(-5.0, x[0], 1E-6);
        assertEquals(-7.0, x[1], 1E-6);
        assertEquals(0.0, result, 1E-10);
    }

    /**
     * Tests that L-BFGS-B history is correctly preserved across iterations
     * (exercises the y.clone()/s.clone() fix).
     */
    @Test
    public void testLBFGSBHistoryCloning() {
        System.out.println("L-BFGS-B: history cloning correctness");
        // Use a higher-dimensional problem that forces multiple history updates
        int n = 10;
        DifferentiableMultivariateFunction bowl = new DifferentiableMultivariateFunction() {
            @Override
            public double f(double[] x) {
                double sum = 0;
                for (int i = 0; i < x.length; i++) {
                    sum += (i + 1.0) * x[i] * x[i];
                }
                return sum;
            }

            @Override
            public double g(double[] x, double[] g) {
                double sum = 0;
                for (int i = 0; i < x.length; i++) {
                    g[i] = 2 * (i + 1.0) * x[i];
                    sum += (i + 1.0) * x[i] * x[i];
                }
                return sum;
            }
        };

        double[] x = new double[n];
        double[] l = new double[n];
        double[] u = new double[n];
        Arrays.fill(x, 1.0);
        Arrays.fill(l, -10.0);
        Arrays.fill(u, 10.0);

        // With the history-cloning fix, this should converge close to 0.
        // The test mainly ensures that the algorithm makes meaningful progress
        // (not stuck due to corrupted history from the pre-fix aliasing bug).
        double result = BFGS.minimize(bowl, 5, x, l, u, 1E-8, 500);
        assertTrue(result < 0.01, "Expected result < 0.01 but was: " + result);
        for (int i = 0; i < n; i++) {
            assertTrue(Math.abs(x[i]) < 0.1, "Expected |x[" + i + "]| < 0.1 but was: " + x[i]);
        }
    }

    /**
     * Tests invalid argument handling for all BFGS variants.
     */
    @Test
    public void testInvalidArgs() {
        System.out.println("BFGS invalid arguments");
        double[] x = {0.0};
        double[] l = {-1.0};
        double[] u = {1.0};

        // BFGS: invalid gtol
        assertThrows(IllegalArgumentException.class, () -> BFGS.minimize(func, x.clone(), -1E-5, 100));
        // BFGS: invalid maxIter
        assertThrows(IllegalArgumentException.class, () -> BFGS.minimize(func, x.clone(), 1E-5, 0));
        // L-BFGS: invalid m
        assertThrows(IllegalArgumentException.class, () -> BFGS.minimize(func, 0, x.clone(), 1E-5, 100));
        // L-BFGS-B: bound length mismatch
        assertThrows(IllegalArgumentException.class, () ->
            BFGS.minimize(func, 5, new double[2], new double[1], new double[2], 1E-5, 100));
        assertThrows(IllegalArgumentException.class, () ->
            BFGS.minimize(func, 5, new double[2], new double[2], new double[1], 1E-5, 100));
    }
}
