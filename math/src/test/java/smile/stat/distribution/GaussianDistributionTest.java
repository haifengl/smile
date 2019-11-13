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

package smile.stat.distribution;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class GaussianDistributionTest {

    public GaussianDistributionTest() {
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
     * Test of constructor, of class GaussianDistribution.
     */
    @Test
    public void testGaussianDistribution() {
        System.out.println("GaussianDistribution");
        MathEx.setSeed(19650218); // to get repeatable results.
        GaussianDistribution instance = new GaussianDistribution(3, 2.1);
        double[] data = instance.rand(1000);
        GaussianDistribution est = GaussianDistribution.fit(data);
        assertEquals(3.05, est.mu, 1E-2);
        assertEquals(2.13, est.sigma, 1E-2);
    }

    /**
     * Test of length method, of class Exponential.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        GaussianDistribution instance = new GaussianDistribution(3.0, 2.0);
        instance.rand();
        assertEquals(2, instance.length());
    }

    /**
     * Test of mean method, of class Exponential.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        GaussianDistribution instance = new GaussianDistribution(0.0, 1.0);
        instance.rand();
        assertEquals(0.0, instance.mean(), 1E-7);
        instance = new GaussianDistribution(1.0, 2.0);
        instance.rand();
        assertEquals(1.0, instance.mean(), 1E-7);
        instance = new GaussianDistribution(2.0, 0.5);
        instance.rand();
        assertEquals(2.0, instance.mean(), 1E-7);
        instance = new GaussianDistribution(3.0, 3.8);
        instance.rand();
        assertEquals(3.0, instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class Exponential.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        GaussianDistribution instance = new GaussianDistribution(0.0, 1.0);
        instance.rand();
        assertEquals(1.0, instance.variance(), 1E-7);
        instance = new GaussianDistribution(1.0, 2.0);
        instance.rand();
        assertEquals(4.0, instance.variance(), 1E-7);
        instance = new GaussianDistribution(2.0, 0.5);
        instance.rand();
        assertEquals(0.25, instance.variance(), 1E-7);
        instance = new GaussianDistribution(3.0, 3.8);
        instance.rand();
        assertEquals(14.44, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class Exponential.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        GaussianDistribution instance = new GaussianDistribution(0.0, 1.0);
        instance.rand();
        assertEquals(1.0, instance.sd(), 1E-7);
        instance = new GaussianDistribution(1.0, 2.0);
        instance.rand();
        assertEquals(2.0, instance.sd(), 1E-7);
        instance = new GaussianDistribution(2.0, 0.5);
        instance.rand();
        assertEquals(0.5, instance.sd(), 1E-7);
        instance = new GaussianDistribution(3.0, 3.8);
        instance.rand();
        assertEquals(3.8, instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class Exponential.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        GaussianDistribution instance = new GaussianDistribution(0.0, 1.0);
        instance.rand();
        assertEquals(1.418939, instance.entropy(), 1E-6);
        instance = new GaussianDistribution(1.0, 2.0);
        instance.rand();
        assertEquals(2.112086, instance.entropy(), 1E-6);
        instance = new GaussianDistribution(2.0, 0.5);
        instance.rand();
        assertEquals(0.7257914, instance.entropy(), 1E-6);
        instance = new GaussianDistribution(3.0, 3.8);
        instance.rand();
        assertEquals(2.753940, instance.entropy(), 1E-6);
    }

    /**
     * Test of p method, of class Gaussian.
     */
    @Test
    public void testP() {
        System.out.println("p");
        GaussianDistribution instance = new GaussianDistribution(4.0, 3.0);
        instance.rand();
        assertEquals(2.482015e-06, instance.p(-10), 1E-10);
        assertEquals(0.01799699, instance.p(-2), 1E-7);
        assertEquals(0.1064827, instance.p(2), 1E-7);
        assertEquals(0.1257944, instance.p(3), 1E-7);
        assertEquals(0.1329808, instance.p(4), 1E-7);
        assertEquals(0.1257944, instance.p(5), 1E-7);
        assertEquals(0.1064827, instance.p(6), 1E-7);
        assertEquals(0.0806569, instance.p(7), 1E-7);
        assertEquals(0.01799699, instance.p(10), 1E-7);
        assertEquals(8.85434e-08, instance.p(20), 1E-12);
    }

    /**
     * Test of logP method, of class Exponential.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        GaussianDistribution instance = new GaussianDistribution(4.0, 3.0);
        instance.rand();
        assertEquals(-12.90644, instance.logp(-10), 1E-5);
        assertEquals(-4.017551, instance.logp(-2),  1E-6);
        assertEquals(-2.517551, instance.logp(1.0), 1E-6);
        assertEquals(-2.239773, instance.logp(2.0), 1E-6);
        assertEquals(-2.073106, instance.logp(3.0), 1E-6);
        assertEquals(-2.017551, instance.logp(4.0), 1E-6);
        assertEquals(-4.017551, instance.logp(10),  1E-6);
        assertEquals(-16.23977, instance.logp(20),  1E-5);
    }

    /**
     * Test of cdf method, of class Gaussian.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        GaussianDistribution instance = new GaussianDistribution(4.0, 3.0);
        instance.rand();
        assertEquals(1.530627e-06, instance.cdf(-10), 1E-12);
        assertEquals(0.02275013, instance.cdf(-2), 1E-7);
        assertEquals(0.2524925, instance.cdf(2), 1E-7);
        assertEquals(0.3694413, instance.cdf(3), 1E-7);
        assertEquals(0.5000000, instance.cdf(4), 1E-7);
        assertEquals(0.6305587, instance.cdf(5), 1E-7);
        assertEquals(0.7475075, instance.cdf(6), 1E-7);
        assertEquals(0.8413447, instance.cdf(7), 1E-7);
        assertEquals(0.9772499, instance.cdf(10), 1E-7);
        assertEquals(0.9998771, instance.cdf(15), 1E-7);
        assertEquals(1.0000000, instance.cdf(20), 1E-7);
    }

    /**
     * Test of cdf method, of class Gaussian.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        GaussianDistribution instance = new GaussianDistribution(4.0, 3.0);
        instance.rand();
        assertEquals(1.475136, instance.quantile(0.2), 1E-6);
        assertEquals(2.426798, instance.quantile(0.3), 1E-6);
        assertEquals(3.239959, instance.quantile(0.4), 1E-6);
        assertEquals(4.000000, instance.quantile(0.5), 1E-6);
        assertEquals(4.760041, instance.quantile(0.6), 1E-6);
        assertEquals(5.573202, instance.quantile(0.7), 1E-6);
    }
}