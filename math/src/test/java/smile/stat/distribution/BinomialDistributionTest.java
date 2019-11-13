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
public class BinomialDistributionTest {

    public BinomialDistributionTest() {
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
     * Test of npara method, of class BinomialDistribution.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals(2, instance.length());
    }

    /**
     * Test of mean method, of class BinomialDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals(30.0, instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class BinomialDistribution.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals(21.0, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class BinomialDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals(Math.sqrt(21.0), instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class BinomialDistribution.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals( 2.9412, instance.entropy(), 1E-4);
    }

    /**
     * Test of p method, of class BinomialDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals(3.234477e-16, instance.p(0), 1E-20);
        assertEquals(1.386204e-14, instance.p(1), 1E-18);
        assertEquals(1.170418e-06, instance.p(10), 1E-10);
        assertEquals(0.007575645, instance.p(20), 1E-7);
        assertEquals(0.08678386, instance.p(30), 1E-7);
        assertEquals(5.153775e-53, instance.p(100), 1E-58);
    }

    /**
     * Test of logP method, of class BinomialDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals(Math.log(3.234477e-16), instance.logp(0), 1E-5);
        assertEquals(Math.log(1.386204e-14), instance.logp(1), 1E-5);
        assertEquals(Math.log(1.170418e-06), instance.logp(10), 1E-5);
        assertEquals(Math.log(0.007575645), instance.logp(20), 1E-5);
        assertEquals(Math.log(0.08678386), instance.logp(30), 1E-5);
        assertEquals(Math.log(5.153775e-53), instance.logp(100), 1E-5);
    }

    /**
     * Test of cdf method, of class BinomialDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals(3.234477e-16, instance.cdf(0), 1E-20);
        assertEquals(1.418549e-14, instance.cdf(1), 1E-18);
        assertEquals(1.555566e-06, instance.cdf(10), 1E-10);
        assertEquals(0.01646285, instance.cdf(20), 1E-7);
        assertEquals(0.5491236, instance.cdf(30), 1E-7);
        assertEquals(1.0, instance.cdf(100), 1E-7);
    }

    /**
     * Test of quantile method, of class BinomialDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        BinomialDistribution instance = new BinomialDistribution(100, 0.3);
        instance.rand();
        assertEquals(0, instance.quantile(0), 1E-7);
        assertEquals(0, instance.quantile(0.00000000000000001), 1E-7);
        assertEquals(17, instance.quantile(0.001), 1E-7);
        assertEquals(20, instance.quantile(0.01), 1E-7);
        assertEquals(24, instance.quantile(0.1), 1E-7);
        assertEquals(26, instance.quantile(0.2), 1E-7);
        assertEquals(30, instance.quantile(0.5), 1E-7);
        assertEquals(36, instance.quantile(0.9), 1E-7);
        assertEquals(100, instance.quantile(1.0), 1E-7);
    }
}