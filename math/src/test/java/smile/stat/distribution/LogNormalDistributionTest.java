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
public class LogNormalDistributionTest {

    public LogNormalDistributionTest() {
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
     * Test of constructor, of class LogNormalDistribution.
     */
    @Test
    public void testLogNormalDistribution() {
        System.out.println("LogNormalDistribution");
        MathEx.setSeed(19650218); // to get repeatable results.
        LogNormalDistribution instance = new LogNormalDistribution(3, 2.1);
        double[] data = instance.rand(1000);
        LogNormalDistribution est = LogNormalDistribution.fit(data);
        assertEquals(3.04, est.mu, 1E-2);
        assertEquals(2.12, est.sigma, 1E-2);
    }

    /**
     * Test of length method, of class LogNormalDistribution.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(2, instance.length());
    }

    /**
     * Test of mean method, of class LogNormalDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(4.481689, instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class LogNormalDistribution.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(34.51261, instance.variance(), 1E-5);
    }

    /**
     * Test of sd method, of class LogNormalDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(5.874744, instance.sd(), 1E-6);
    }

    /**
     * Test of entropy method, of class LogNormalDistribution.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(2.418939, instance.entropy(), 1E-6);
    }

    /**
     * Test of p method, of class LogNormalDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(6.006101e-06, instance.p(0.01), 1E-12);
        assertEquals(0.01707931, instance.p(0.1), 1E-7);
        assertEquals(0.2419707, instance.p(1.0), 1E-7);
        assertEquals(0.1902978, instance.p(2.0), 1E-7);
        assertEquals(0.06626564, instance.p(5.0), 1E-7);
        assertEquals(0.01707931, instance.p(10.0), 1E-7);
    }

    /**
     * Test of logP method, of class LogNormalDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(Math.log(6.006101e-06), instance.logp(0.01), 1E-5);
        assertEquals(Math.log(0.01707931), instance.logp(0.1), 1E-5);
        assertEquals(Math.log(0.2419707), instance.logp(1.0), 1E-5);
        assertEquals(Math.log(0.1902978), instance.logp(2.0), 1E-5);
        assertEquals(Math.log(0.06626564), instance.logp(5.0), 1E-5);
        assertEquals(Math.log(0.01707931), instance.logp(10.0), 1E-5);
    }

    /**
     * Test of cdf method, of class LogNormalDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(1.040252e-08, instance.cdf(0.01), 1E-12);
        assertEquals(0.0004789901, instance.cdf(0.1), 1E-7);
        assertEquals(0.1586553, instance.cdf(1.0), 1E-7);
        assertEquals(0.3794777, instance.cdf(2.0), 1E-7);
        assertEquals(0.7288829, instance.cdf(5.0), 1E-7);
        assertEquals(0.9036418, instance.cdf(10.0), 1E-7);
    }

    /**
     * Test of quantile method, of class LogNormalDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        LogNormalDistribution instance = new LogNormalDistribution(1.0, 1.0);
        instance.rand();
        assertEquals(0.2654449, instance.quantile(0.01), 1E-7);
        assertEquals(0.754612, instance.quantile(0.1), 1E-6);
        assertEquals(1.171610, instance.quantile(0.2), 1E-6);
        assertEquals(1.608978, instance.quantile(0.3), 1E-6);
        assertEquals(9.791861, instance.quantile(0.9), 1E-6);
        assertEquals(27.83649, instance.quantile(0.99), 1E-5);
    }

}