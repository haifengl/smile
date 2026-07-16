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

import smile.stat.distribution.GaussianDistribution;
import smile.util.function.DifferentiableMultivariateFunction;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class LevenbergMarquardtTest {
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
            g[1] = -(x[0] * x[1] * Math.log(x[2]) * pow) / (de * de);
            return 1 / de;
        }
    };

    public LevenbergMarquardtTest() {
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

    @Test
    public void test() {
        System.out.println("LevenbergMarquardt");

        MathEx.setSeed(19650218); // to get repeatable results.
        double[] x = new double[100];
        double[] y = new double[100];
        GaussianDistribution d = new GaussianDistribution(0.0, 1);
        for (int i = 0; i < x.length; i++) {
            x[i] = (i+1) * 0.05;
            y[i] = 1.0 / (1 + 1.2 * Math.pow(x[i], 1.8)) + d.rand() * 0.03;
        }

        double[] p = {0.5, 0.0};
        LevenbergMarquardt lma = LevenbergMarquardt.fit(func, x, y, p);
        assertEquals(0.0863, lma.sse(), 1E-4);
        assertEquals(1.2260, lma.parameters()[0], 1E-4);
        assertEquals(1.8024, lma.parameters()[1], 1E-4);
    }

    /**
     * Test the double[][] overload with a 2D input.
     * Uses a linear model: f(a, b; x0, x1) = a*x0 + b*x1.
     */
    @Test
    public void testMatrixInput() {
        System.out.println("LevenbergMarquardt double[][] input");

        // f(p; x) = p[0]*x[0] + p[1]*x[1], true p = {2.0, 3.0}
        DifferentiableMultivariateFunction linear = new DifferentiableMultivariateFunction() {
            @Override
            public double f(double[] x) {
                return x[0] * x[2] + x[1] * x[3];
            }

            @Override
            public double g(double[] x, double[] g) {
                g[0] = x[2];
                g[1] = x[3];
                return x[0] * x[2] + x[1] * x[3];
            }
        };

        int n = 20;
        double[][] X = new double[n][2];
        double[] y = new double[n];
        MathEx.setSeed(19650218);
        for (int i = 0; i < n; i++) {
            X[i][0] = i + 1.0;
            X[i][1] = (n - i) + 0.0;
            y[i] = 2.0 * X[i][0] + 3.0 * X[i][1];
        }

        double[] p = {1.0, 1.0};
        LevenbergMarquardt lma = LevenbergMarquardt.fit(linear, X, y, p);
        assertEquals(2.0, lma.parameters()[0], 1E-4);
        assertEquals(3.0, lma.parameters()[1], 1E-4);
        assertEquals(0.0, lma.sse(), 1E-6);
    }

    @Test
    public void testInvalidArgs() {
        System.out.println("LevenbergMarquardt invalid args");
        double[] x = {1.0};
        double[] y = {1.0};
        double[] p = {1.0};
        assertThrows(IllegalArgumentException.class, () ->
            LevenbergMarquardt.fit(func, x, y, p, -0.001, 10));
        assertThrows(IllegalArgumentException.class, () ->
            LevenbergMarquardt.fit(func, x, y, p, 0.001, 0));
    }

    @Test
    public void testResiduals() {
        System.out.println("LevenbergMarquardt residuals");
        MathEx.setSeed(19650218);
        double[] x = new double[100];
        double[] y = new double[100];
        for (int i = 0; i < x.length; i++) {
            x[i] = (i+1) * 0.05;
            y[i] = 1.0 / (1 + 1.2 * Math.pow(x[i], 1.8));
        }

        double[] p = {0.5, 0.0};
        LevenbergMarquardt lma = LevenbergMarquardt.fit(func, x, y, p);

        // fitted values and residuals should match sse
        double sse = 0;
        for (int i = 0; i < x.length; i++) {
            double r = y[i] - lma.fittedValues()[i];
            assertEquals(lma.residuals()[i], r, 1E-10);
            sse += r * r;
        }
        assertEquals(lma.sse(), sse, 1E-8);
    }
}

