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
public class NegativeBinomialDistributionTest {

    public NegativeBinomialDistributionTest() {
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
     * Test of npara method, of class NegativeBinomialDistribution.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        NegativeBinomialDistribution instance = new NegativeBinomialDistribution(3, 0.3);
        instance.rand();
        assertEquals(2, instance.npara());
    }

    /**
     * Test of mean method, of class NegativeBinomialDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        NegativeBinomialDistribution instance = new NegativeBinomialDistribution(3, 0.3);
        instance.rand();
        assertEquals(7, instance.mean(), 1E-7);
    }

    /**
     * Test of var method, of class NegativeBinomialDistribution.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        NegativeBinomialDistribution instance = new NegativeBinomialDistribution(3, 0.3);
        instance.rand();
        assertEquals(7/0.3, instance.var(), 1E-7);
    }

    /**
     * Test of sd method, of class NegativeBinomialDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        NegativeBinomialDistribution instance = new NegativeBinomialDistribution(3, 0.3);
        instance.rand();
        assertEquals(Math.sqrt(7/0.3), instance.sd(), 1E-7);
    }

    /**
     * Test of p method, of class NegativeBinomialDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        NegativeBinomialDistribution instance = new NegativeBinomialDistribution(3, 0.3);
        instance.rand();
        assertEquals(0.027, instance.p(0), 1E-7);
        assertEquals(0.0567, instance.p(1), 1E-7);
        assertEquals(0.07938, instance.p(2), 1E-7);
        assertEquals(0.09261, instance.p(3), 1E-7);
        assertEquals(0.05033709, instance.p(10), 1E-7);
    }

    /**
     * Test of logP method, of class NegativeBinomialDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        NegativeBinomialDistribution instance = new NegativeBinomialDistribution(3, 0.3);
        instance.rand();
        assertEquals(Math.log(0.027), instance.logp(0), 1E-7);
        assertEquals(Math.log(0.0567), instance.logp(1), 1E-7);
        assertEquals(Math.log(0.07938), instance.logp(2), 1E-7);
        assertEquals(Math.log(0.09261), instance.logp(3), 1E-7);
        assertEquals(Math.log(0.05033709), instance.logp(10), 1E-7);
    }

    /**
     * Test of cdf method, of class NegativeBinomialDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        NegativeBinomialDistribution instance = new NegativeBinomialDistribution(3, 0.3);
        instance.rand();
        assertEquals(0.027, instance.cdf(0), 1E-7);
        assertEquals(0.0837, instance.cdf(1), 1E-7);
        assertEquals(0.16308, instance.cdf(2), 1E-7);
        assertEquals(0.25569, instance.cdf(3), 1E-7);
        assertEquals(0.7975217, instance.cdf(10), 1E-7);
    }

    /**
     * Test of quantile method, of class NegativeBinomialDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        NegativeBinomialDistribution instance = new NegativeBinomialDistribution(3, 0.3);
        instance.rand();
        assertEquals(0, instance.quantile(0), 1E-7);
        assertEquals(0, instance.quantile(0.001), 1E-7);
        assertEquals(0, instance.quantile(0.01), 1E-7);
        assertEquals(2, instance.quantile(0.1), 1E-7);
        assertEquals(3, instance.quantile(0.2), 1E-7);
        assertEquals(13, instance.quantile(0.9), 1E-7);
        assertEquals(22, instance.quantile(0.99), 1E-7);
        assertEquals(30, instance.quantile(0.999), 1E-7);
    }
}