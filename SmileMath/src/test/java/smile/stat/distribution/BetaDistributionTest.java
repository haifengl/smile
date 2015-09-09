/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        BetaDistribution instance = new BetaDistribution(3, 2.1);
        double[] data = new double[1000];
        for (int i = 0; i < data.length; i++)
            data[i] = instance.rand();
        BetaDistribution est = new BetaDistribution(data);
        assertEquals(3, est.getAlpha(), 5E-1);
        assertEquals(2.1, est.getBeta(), 5E-1);
    }

    /**
     * Test of npara method, of class Beta.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(2, instance.npara());
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
     * Test of var method, of class Beta.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        BetaDistribution instance = new BetaDistribution(2, 5);
        instance.rand();
        assertEquals(0.0255102, instance.var(), 1E-7);
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