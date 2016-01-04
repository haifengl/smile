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
import smile.math.Math;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class BernoulliDistributionTest {

    public BernoulliDistributionTest() {
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
     * Test of constructor, of class BernoulliDistribution.
     */
    @Test
    public void testBernoulliDistribution() {
        System.out.println("BernoulliDistribution");
        BernoulliDistribution instance = new BernoulliDistribution(0.4);
        int[] data = new int[1000];
        for (int i = 0; i < data.length; i++)
            data[i] = (int) instance.rand();
        BernoulliDistribution est = new BernoulliDistribution(data);
        assertEquals(0.4, est.getProb(), 5E-2);
    }

    /**
     * Test of npara method, of class BernoulliDistribution.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(1, instance.npara());
    }

    /**
     * Test of mean method, of class BernoulliDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(0.3, instance.mean(), 1E-7);
    }

    /**
     * Test of var method, of class BernoulliDistribution.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(0.21, instance.var(), 1E-7);
    }

    /**
     * Test of sd method, of class BernoulliDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(Math.sqrt(0.21), instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class BernoulliDistribution.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(-0.3*Math.log2(0.3) - 0.7*Math.log2(0.7), instance.entropy(), 1E-7);
    }

    /**
     * Test of p method, of class BernoulliDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(0.7, instance.p(0), 1E-7);
        assertEquals(0.3, instance.p(1), 1E-7);
        assertEquals(0.0, instance.p(2), 1E-7);
    }

    /**
     * Test of logP method, of class BernoulliDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(Math.log(0.7), instance.logp(0), 1E-7);
        assertEquals(Math.log(0.3), instance.logp(1), 1E-7);
    }

    /**
     * Test of cdf method, of class BernoulliDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(0.7, instance.cdf(0), 1E-7);
        assertEquals(1.0, instance.cdf(1), 1E-7);
    }

    /**
     * Test of quantile method, of class BernoulliDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        BernoulliDistribution instance = new BernoulliDistribution(0.3);
        instance.rand();
        assertEquals(0, instance.quantile(0), 1E-7);
        assertEquals(0, instance.quantile(0.7), 1E-7);
        assertEquals(1, instance.quantile(1), 1E-7);
    }
}