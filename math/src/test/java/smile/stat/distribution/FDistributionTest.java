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
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class FDistributionTest {

    public FDistributionTest() {
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
     * Test of length method, of class FDistribution.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(2, instance.length());
    }

    /**
     * Test of mean method, of class FDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(10.0/9, instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class FDistribution.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(0.4320988, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class FDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(0.6573422, instance.sd(), 1E-7);
    }

    /**
     * Test of p method, of class FDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(2.90264e-06, instance.p(0.01), 1E-10);
        assertEquals(0.01504682, instance.p(0.1), 1E-7);
        assertEquals(0.1198157, instance.p(0.2), 1E-7);
        assertEquals(0.687882, instance.p(0.5), 1E-6);
        assertEquals(0.7143568, instance.p(1), 1E-7);
        assertEquals(6.652967e-06, instance.p(10), 1E-10);
    }

    /**
     * Test of logP method, of class FDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(-12.74989, instance.logp(0.01), 1E-5);
        assertEquals(-4.196589, instance.logp(0.1), 1E-6);
        assertEquals(-2.121800, instance.logp(0.2), 1E-6);
        assertEquals(-0.374138, instance.logp(0.5), 1E-6);
        assertEquals(-0.3363727, instance.logp(1), 1E-7);
        assertEquals(-11.92045, instance.logp(10), 1E-5);
    }

    /**
     * Test of cdf method, of class FDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(5.878315e-09, instance.cdf(0.01), 1E-15);
        assertEquals(0.0003410974, instance.cdf(0.1), 1E-10);
        assertEquals(0.006161513, instance.cdf(0.2), 1E-9);
        assertEquals(0.1298396, instance.cdf(0.5), 1E-6);
        assertEquals(0.5244995, instance.cdf(1), 1E-7);
        assertEquals(0.9999914, instance.cdf(10), 1E-7);
    }

    /**
     * Test of quantile method, of class FDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(0.2269944, instance.quantile(0.01), 1E-7);
        assertEquals(0.4543918, instance.quantile(0.1), 1E-7);
        assertEquals(0.5944412, instance.quantile(0.2), 1E-7);
        assertEquals(0.9662639, instance.quantile(0.5), 1E-7);
        assertEquals(3.368186, instance.quantile(0.99), 1E-6);
        assertEquals(7.180539, instance.quantile(0.9999), 1E-6);
    }

}