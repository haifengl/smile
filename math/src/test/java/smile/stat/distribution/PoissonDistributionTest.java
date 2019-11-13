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
public class PoissonDistributionTest {

    public PoissonDistributionTest() {
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
     * Test of constructor, of class PoissonDistribution.
     */
    @Test
    public void testPoissonDistribution() {
        System.out.println("PoissonDistribution");
        MathEx.setSeed(19650218); // to get repeatable results.
        PoissonDistribution instance = new PoissonDistribution(5.5);
        int[] data = instance.randi(1000);
        PoissonDistribution est = PoissonDistribution.fit(data);
        assertEquals(5.52, est.lambda, 1E-2);
    }

    /**
     * Test of length method, of class Poisson.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertEquals(1, instance.length());
    }

    /**
     * Test of mean method, of class Poisson.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertEquals(3.5, instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class Poisson.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertEquals(3.5, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class Poisson.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertEquals(Math.sqrt(3.5), instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class Poisson.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertEquals(2.016878, instance.entropy(), 1E-6);
    }

    /**
     * Test of pdf method, of class Poisson.
     */
    @Test
    public void testPdf() {
        System.out.println("pdf");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertEquals(0.0, instance.p(-1), 1E-7);
        assertEquals(0.03019738, instance.p(0), 1E-7);
        assertEquals(0.1056908, instance.p(1), 1E-7);
        assertEquals(0.1849590, instance.p(2), 1E-7);
        assertEquals(0.2157855, instance.p(3), 1E-7);
        assertEquals(0.1888123, instance.p(4), 1E-7);
        assertEquals(0.00229555, instance.p(10), 1E-7);
        assertEquals(9.445079e-10, instance.p(20), 1E-10);
        assertEquals(8.256008e-106, instance.p(100), 1E-110);
    }

    /**
     * Test of logPdf method, of class Poisson.
     */
    @Test
    public void testLogPdf() {
        System.out.println("logPdf");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertTrue(Double.isInfinite(instance.logp(-1)));
        assertEquals(-3.5, instance.logp(0), 1E-7);
        assertEquals(-2.247237, instance.logp(1), 1E-6);
        assertEquals(-1.687621, instance.logp(2), 1E-6);
        assertEquals(-1.533471, instance.logp(3), 1E-6);
        assertEquals(-1.667002, instance.logp(4), 1E-6);
        assertEquals(-6.076783, instance.logp(10), 1E-6);
        assertEquals(-20.78036, instance.logp(20), 1E-5);
        assertEquals(-241.9631, instance.logp(100), 1E-4);
    }

    /**
     * Test of cdf method, of class Poisson.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertEquals(0, instance.cdf(-1), 1E-7);
        assertEquals(0.03019738, instance.cdf(0), 1E-7);
        assertEquals(0.1358882,  instance.cdf(1), 1E-7);
        assertEquals(0.3208472,  instance.cdf(2), 1E-7);
        assertEquals(0.5366327,  instance.cdf(3), 1E-7);
        assertEquals(0.725445,   instance.cdf(4), 1E-6);
        assertEquals(0.9989806,  instance.cdf(10), 1E-6);
        assertEquals(0.999999,   instance.cdf(15), 1E-6);
        assertEquals(1.000000,   instance.cdf(20), 1E-6);
    }

    /**
     * Test of quantile method, of class Poisson.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        PoissonDistribution instance = new PoissonDistribution(3.5);
        instance.rand();
        assertEquals(0, instance.quantile(0.01), 1E-7);
        assertEquals(1, instance.quantile(0.1), 1E-7);
        assertEquals(2, instance.quantile(0.2), 1E-7);
        assertEquals(2, instance.quantile(0.3), 1E-7);
        assertEquals(3, instance.quantile(0.4), 1E-6);
        assertEquals(3, instance.quantile(0.5), 1E-6);
        assertEquals(6, instance.quantile(0.9), 1E-6);
        assertEquals(8, instance.quantile(0.99), 1E-6);
    }
}