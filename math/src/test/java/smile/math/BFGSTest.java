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

package smile.math;

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
     * Test L-BFGS.
     */
    @Test
    public void testLBFGS() {
        System.out.println("L-BFGS");
        DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {

            @Override
            public double f(double[] x) {
                double f = 0.0;
                for (int j = 1; j <= x.length; j += 2) {
                    double t1 = 1.e0 - x[j - 1];
                    double t2 = 1.e1 * (x[j] - x[j - 1] * x[j - 1]);
                    f = f + t1 * t1 + t2 * t2;
                }
                return f;
            }

            @Override
            public double g(double[] x, double[] g) {
                double f = 0.0;
                for (int j = 1; j <= x.length; j += 2) {
                    double t1 = 1.e0 - x[j - 1];
                    double t2 = 1.e1 * (x[j] - x[j - 1] * x[j - 1]);
                    g[j + 1 - 1] = 2.e1 * t2;
                    g[j - 1] = -2.e0 * (x[j - 1] * g[j + 1 - 1] + t1);
                    f = f + t1 * t1 + t2 * t2;
                }
                return f;
            }
        };

        double[] x = new double[100];
        for (int j = 1; j <= x.length; j += 2) {
            x[j - 1] = -1.2e0;
            x[j + 1 - 1] = 1.e0;
        }

        double result = MathEx.BFGS.minimize(func, 5, x);
        assertEquals(3.2760183604E-14, result, 1E-15);
    }

    /**
     * Test BFGS.
     */
    @Test
    public void testBFGS() {
        System.out.println("BFGS");
        DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {

            @Override
            public double f(double[] x) {
                double f = 0.0;
                for (int j = 1; j <= x.length; j += 2) {
                    double t1 = 1.e0 - x[j - 1];
                    double t2 = 1.e1 * (x[j] - x[j - 1] * x[j - 1]);
                    f = f + t1 * t1 + t2 * t2;
                }
                return f;
            }

            @Override
            public double g(double[] x, double[] g) {
                double f = 0.0;
                for (int j = 1; j <= x.length; j += 2) {
                    double t1 = 1.e0 - x[j - 1];
                    double t2 = 1.e1 * (x[j] - x[j - 1] * x[j - 1]);
                    g[j + 1 - 1] = 2.e1 * t2;
                    g[j - 1] = -2.e0 * (x[j - 1] * g[j + 1 - 1] + t1);
                    f = f + t1 * t1 + t2 * t2;
                }
                return f;
            }
        };

        double[] x = new double[100];
        for (int j = 1; j <= x.length; j += 2) {
            x[j - 1] = -1.2e0;
            x[j + 1 - 1] = 1.e0;
        }

        double result = MathEx.BFGS.minimize(func, x);
        assertEquals(2.2388137801857536E-12, result, 1E-15);
    }
}
