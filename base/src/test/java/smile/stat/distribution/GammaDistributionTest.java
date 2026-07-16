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
package smile.stat.distribution;

import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class GammaDistributionTest {

    public GammaDistributionTest() {
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
     * Test of constructor, of class GammaDistribution.
     */
    @Test
    public void testGammaDistribution() {
        System.out.println("GammaDistribution");
        MathEx.setSeed(19650218); // to get repeatable results.
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        double[] data = instance.rand(1000);
        GammaDistribution est = GammaDistribution.fit(data);
        // Marsaglia-Tsang algorithm produces different samples;
        // verify estimates are close to the true parameters (k=3, theta=2.1)
        assertEquals(3.0, est.k,     0.3);
        assertEquals(2.1, est.theta, 0.3);
    }

    /**
     * Test non-integer shape parameter using Marsaglia-Tsang algorithm.
     */
    @Test
    public void testNonIntegerShape() {
        System.out.println("GammaDistribution non-integer shape");
        MathEx.setSeed(19650218);
        // k=0.5 => chi-square(1) scaled by theta
        GammaDistribution dist = new GammaDistribution(0.5, 2.0);
        double[] data = dist.rand(10000);
        // All values must be positive
        for (double v : data) {
            assertTrue(v > 0, "Negative sample: " + v);
        }
        double mean = 0;
        for (double v : data) mean += v;
        mean /= data.length;
        // Expected mean = k * theta = 0.5 * 2 = 1.0
        assertEquals(1.0, mean, 0.05);
    }

    /**
     * Test shape parameter between 0 and 1.
     */
    @Test
    public void testShapeLessThanOne() {
        System.out.println("GammaDistribution shape < 1");
        MathEx.setSeed(19650218);
        GammaDistribution dist = new GammaDistribution(0.3, 1.0);
        double[] data = dist.rand(10000);
        double mean = 0;
        for (double v : data) mean += v;
        mean /= data.length;
        // Expected mean = 0.3
        assertEquals(0.3, mean, 0.05);
    }

    /**
     * Test of length method, of class Gamma.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(2, instance.length());
    }

    /**
     * Test of mean method, of class Gamma.
     */
    @Test
    public void testMean() {
        System.out.println("var");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(6.3, instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class Gamma.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(13.23, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class Gamma.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(3.637307, instance.sd(), 1E-6);
    }

    /**
     * Test of entropy method, of class Gamma.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(2.589516, instance.entropy(), 1E-6);
    }

    /**
     * Test of p method, of class Gamma.
     */
    @Test
    public void testP() {
        System.out.println("p");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(0.0, instance.p(-0.1), 1E-7);
        assertEquals(0.0, instance.p(0.0), 1E-7);
        assertEquals(0.0005147916, instance.p(0.1), 1E-7);
        assertEquals(0.03353553, instance.p(1.0), 1E-7);
        assertEquals(0.08332174, instance.p(2.0), 1E-7);
        assertEquals(0.1164485, instance.p(3.0), 1E-7);
        assertEquals(0.1285892, instance.p(4.0), 1E-7);
        assertEquals(0.04615759, instance.p(10), 1E-7);
        assertEquals(0.001578462, instance.p(20), 1E-7);
        assertEquals(3.036321e-05, instance.p(30), 1E-7);
    }

    /**
     * Test of logP method, of class Gamma.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertTrue(Double.isInfinite(instance.logp(-0.1)));
        assertTrue(Double.isInfinite(instance.logp(0.0)));
        assertEquals(-3.39515,  instance.logp(1.0), 1E-5);
        assertEquals(-2.485046, instance.logp(2.0), 1E-6);
        assertEquals(-2.150306, instance.logp(3.0), 1E-6);
        assertEquals(-2.051132, instance.logp(4.0), 1E-6);
        assertEquals(-3.075694, instance.logp(10.0), 1E-6);
        assertEquals(-6.451304, instance.logp(20.0), 1E-6);
        assertEquals(-10.40228, instance.logp(30.0), 1E-5);
    }

    /**
     * Test of cdf method, of class Gamma.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(0.0, instance.cdf(-0.1), 1E-7);
        assertEquals(0.0, instance.cdf(0.0), 1E-7);
        assertEquals(0.01264681, instance.cdf(1.0), 1E-7);
        assertEquals(0.07175418, instance.cdf(2.0), 1E-7);
        assertEquals(0.1734485, instance.cdf(3.0), 1E-7);
        assertEquals(0.2975654, instance.cdf(4.0), 1E-7);
        assertEquals(0.8538087, instance.cdf(10.0), 1E-7);
        assertEquals(0.995916, instance.cdf(20.0), 1E-7);
        assertEquals(0.9999267, instance.cdf(30.0), 1E-7);
    }

    /**
     * Test of quantile method, of class Gamma.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(0.4001201, instance.quantile(0.001), 1E-7);
        assertEquals(0.9156948, instance.quantile(0.01), 1E-7);
        assertEquals(2.314337, instance.quantile(0.1), 1E-6);
        assertEquals(3.223593, instance.quantile(0.2), 1E-6);
        assertEquals(5.615527, instance.quantile(0.5), 1E-6);
        assertEquals(11.17687, instance.quantile(0.9), 1E-5);
        assertEquals(17.65249, instance.quantile(0.99), 1E-5);
        assertEquals(23.58063, instance.quantile(0.999), 1E-5);
    }

    /**
     * Test logp(0) edge cases: depends on k vs 1.
     */
    @Test
    public void testLogpAtZero() {
        System.out.println("GammaDistribution logp at x=0");
        // k > 1: p(0) = 0, logp(0) = -Inf
        assertEquals(Double.NEGATIVE_INFINITY, new GammaDistribution(3, 1).logp(0.0));
        // k = 1: p(0) = 1/theta (Exponential), logp(0) = -log(theta)
        double theta = 2.0;
        assertEquals(-Math.log(theta), new GammaDistribution(1, theta).logp(0.0), 1E-10);
        // k < 1: p(0) = +Inf
        assertEquals(Double.POSITIVE_INFINITY, new GammaDistribution(0.5, 1).logp(0.0));
    }

    /**
     * Test that p(x) == exp(logp(x)) for consistency.
     */
    @Test
    public void testLogpConsistency() {
        System.out.println("GammaDistribution logp consistency");
        GammaDistribution d = new GammaDistribution(3, 2.1);
        for (double x : new double[]{0.5, 1.0, 2.0, 5.0, 10.0}) {
            assertEquals(d.p(x), Math.exp(d.logp(x)), d.p(x) * 1E-10);
        }
    }
}

