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
public class BetaDistributionTest {

    public BetaDistributionTest() {
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
     * Test of constructor, of class BetaDistribution.
     */
    @Test
    public void testBetaDistribution() {
        System.out.println("BetaDistribution");
        MathEx.setSeed(19650218); // to get repeatable results.
        BetaDistribution instance = new BetaDistribution(3, 2.1);
        double[] data = instance.rand(1000);
        BetaDistribution est = BetaDistribution.fit(data);
        assertEquals(3.31, est.getAlpha(), 1E-2);
        assertEquals(2.31, est.getBeta(), 1E-2);
    }

    /**
     * Test of length method, of class Beta.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(2, instance.length());
    }

    /**
     * Test of mean method, of class Beta.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(0.2857143, instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class Beta.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(0.0255102, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class Beta.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(0.1597191, instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class Beta.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(-0.4845307, instance.entropy(), 1E-7);
    }

    /**
     * Test of p method, of class Beta.
     */
    @Test
    public void testP() {
        System.out.println("p");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(0, instance.p(-0.1), 1E-4);
        assertEquals(0, instance.p(0.0), 1E-4);
        assertEquals(1.9683, instance.p(0.1), 1E-4);
        assertEquals(2.4576, instance.p(0.2), 1E-4);
        assertEquals(2.1609, instance.p(0.3), 1E-4);
        assertEquals(1.5552, instance.p(0.4), 1E-4);
        assertEquals(0.9375, instance.p(0.5), 1E-4);
        assertEquals(0.0, instance.p(1.0), 1E-4);
        assertEquals(0.0, instance.p(1.5), 1E-4);
    }

    /**
     * Test of logP method, of class Beta.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertTrue(Double.isInfinite(instance.logp(-0.1)));
        assertTrue(Double.isInfinite(instance.logp(0.0)));
        assertEquals(0.6771702, instance.logp(0.1), 1E-7);
        assertEquals(0.8991853, instance.logp(0.2), 1E-7);
        assertEquals(0.7705248, instance.logp(0.3), 1E-7);
        assertEquals(0.4416042, instance.logp(0.4), 1E-7);
        assertEquals(-0.06453852, instance.logp(0.5), 1E-7);
        assertTrue(Double.isInfinite(instance.logp(1.0)));
        assertTrue(Double.isInfinite(instance.logp(1.5)));
    }

    /**
     * Test of cdf method, of class Beta.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(0, instance.cdf(-0.1), 1E-5);
        assertEquals(0, instance.cdf(0.0), 1E-5);
        assertEquals(0.114265, instance.cdf(0.1), 1E-5);
        assertEquals(0.34464, instance.cdf(0.2), 1E-5);
        assertEquals(0.579825, instance.cdf(0.3), 1E-5);
        assertEquals(0.76672, instance.cdf(0.4), 1E-5);
        assertEquals(0.890625, instance.cdf(0.5), 1E-5);
        assertEquals(1.0, instance.cdf(1.0), 1E-5);
        assertEquals(1.0, instance.cdf(1.5), 1E-5);
    }

    /**
     * Test of quantile method, of class Beta.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(0.008255493, instance.quantile(0.001), 1E-5);
        assertEquals(0.09259526, instance.quantile(0.1), 1E-5);
        assertEquals(0.1398807, instance.quantile(0.2), 1E-5);
        assertEquals(0.1818035, instance.quantile(0.3), 1E-5);
        assertEquals(0.2225835, instance.quantile(0.4), 1E-5);
        assertEquals(0.26445, instance.quantile(0.5), 1E-5);
        assertEquals(0.5103163, instance.quantile(0.9), 1E-5);
        assertEquals(0.7056863, instance.quantile(0.99), 1E-5);
    }
}