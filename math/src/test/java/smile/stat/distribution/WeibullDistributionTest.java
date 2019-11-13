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
import smile.math.special.Gamma;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class WeibullDistributionTest {

    public WeibullDistributionTest() {
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
     * Test of length method, of class WeibullDistribution.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(2, instance.length());
    }

    /**
     * Test of mean method, of class WeibullDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(Gamma.gamma(1+1/1.5), instance.mean(), 1E-7);
    }

    /**
     * Test of variance method, of class WeibullDistribution.
     */
    @Test
    public void testVariance() {
        System.out.println("variance");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(0.37569028, instance.variance(), 1E-7);
    }

    /**
     * Test of sd method, of class WeibullDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(0.61293579, instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class WeibullDistribution.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(0.78694011, instance.entropy(), 1E-7);
    }

    /**
     * Test of p method, of class WeibullDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(0.0, instance.p(0.0), 1E-7);
        assertEquals(0.4595763, instance.p(0.1), 1E-7);
        assertEquals(0.6134254, instance.p(0.2), 1E-7);
        assertEquals(0.7447834, instance.p(0.5), 1E-7);
        assertEquals(0.2926085, instance.p(1.5), 1E-7);
        assertEquals(0.0455367, instance.p(2.5), 1E-7);
        assertEquals(4.677527e-05, instance.p(5.0), 1E-10);
    }

    /**
     * Test of logP method, of class WeibullDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(Math.log(0.4595763), instance.logp(0.1), 1E-5);
        assertEquals(Math.log(0.6134254), instance.logp(0.2), 1E-5);
        assertEquals(Math.log(0.7447834), instance.logp(0.5), 1E-5);
        assertEquals(Math.log(0.2926085), instance.logp(1.5), 1E-5);
        assertEquals(Math.log(0.0455367), instance.logp(2.5), 1E-5);
        assertEquals(Math.log(4.677527e-05), instance.logp(5.0), 1E-5);
    }

    /**
     * Test of cdf method, of class WeibullDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(0.0, instance.cdf(0.0), 1E-7);
        assertEquals(0.03112801, instance.cdf(0.1), 1E-7);
        assertEquals(0.08555936, instance.cdf(0.2), 1E-7);
        assertEquals(0.2978115, instance.cdf(0.5), 1E-7);
        assertEquals(0.8407241, instance.cdf(1.5), 1E-7);
        assertEquals(0.9808, instance.cdf(2.5), 1E-7);
        assertEquals(0.999986, instance.cdf(5.0), 1E-6);
    }

    /**
     * Test of quantile method, of class WeibullDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        WeibullDistribution instance = new WeibullDistribution(1.5, 1.0);
        instance.rand();
        assertEquals(0.0, instance.p(0.0), 1E-7);
        assertEquals(0.2230755, instance.quantile(0.1), 1E-7);
        assertEquals(0.3678942, instance.quantile(0.2), 1E-7);
        assertEquals(0.7832198, instance.quantile(0.5), 1E-7);
        assertEquals(1.743722, instance.quantile(0.9), 1E-6);
        assertEquals(2.767985, instance.quantile(0.99), 1E-6);
        assertEquals(3.627087, instance.quantile(0.999), 1E-6);
    }

}