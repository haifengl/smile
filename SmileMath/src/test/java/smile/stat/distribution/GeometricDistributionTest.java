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
public class GeometricDistributionTest {

    public GeometricDistributionTest() {
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
     * Test of constructor, of class GeometricDistribution.
     */
    @Test
    public void testGeometricDistribution() {
        System.out.println("GeometricDistribution");
        GeometricDistribution instance = new GeometricDistribution(0.4);
        int[] data = new int[1000];
        for (int i = 0; i < data.length; i++)
            data[i] = (int) instance.rand();
        GeometricDistribution est = new GeometricDistribution(data);
        assertEquals(0.4, est.getProb(), 5E-2);
    }

    /**
     * Test of npara method, of class GeometricDistribution.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(1, instance.npara());
    }

    /**
     * Test of mean method, of class GeometricDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(2.333333, instance.mean(), 1E-6);
    }

    /**
     * Test of var method, of class GeometricDistribution.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(0.7/0.09, instance.var(), 1E-7);
    }

    /**
     * Test of sd method, of class GeometricDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(2.788867, instance.sd(), 1E-6);
    }

    /**
     * Test of p method, of class GeometricDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(0.3, instance.p(0), 1E-6);
        assertEquals(0.21, instance.p(1), 1E-6);
        assertEquals(0.147, instance.p(2), 1E-6);
        assertEquals(0.1029, instance.p(3), 1E-6);
        assertEquals(0.07203, instance.p(4), 1E-6);
        assertEquals(0.008474257, instance.p(10), 1E-6);
        assertEquals(0.0002393768, instance.p(20), 1E-6);
    }

    /**
     * Test of logP method, of class GeometricDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(Math.log(0.3), instance.logp(0), 1E-6);
        assertEquals(Math.log(0.21), instance.logp(1), 1E-6);
        assertEquals(Math.log(0.147), instance.logp(2), 1E-6);
        assertEquals(Math.log(0.1029), instance.logp(3), 1E-6);
        assertEquals(Math.log(0.07203), instance.logp(4), 1E-6);
        assertEquals(Math.log(0.008474257), instance.logp(10), 1E-6);
        assertEquals(Math.log(0.0002393768), instance.logp(20), 1E-6);
    }

    /**
     * Test of cdf method, of class GeometricDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(0.3, instance.cdf(0), 1E-6);
        assertEquals(0.51, instance.cdf(1), 1E-6);
        assertEquals(0.657, instance.cdf(2), 1E-6);
        assertEquals(0.7599, instance.cdf(3), 1E-6);
        assertEquals(0.83193, instance.cdf(4), 1E-6);
        assertEquals(0.9802267, instance.cdf(10), 1E-6);
        assertEquals(0.9994415, instance.cdf(20), 1E-6);
    }

    /**
     * Test of quantile method, of class GeometricDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        GeometricDistribution instance = new GeometricDistribution(0.3);
        instance.rand();
        assertEquals(0, instance.quantile(0.01), 1E-6);
        assertEquals(0, instance.quantile(0.1), 1E-6);
        assertEquals(0, instance.quantile(0.2), 1E-6);
        assertEquals(0, instance.quantile(0.3), 1E-6);
        assertEquals(1, instance.quantile(0.4), 1E-6);
        assertEquals(2, instance.quantile(0.6), 1E-6);
        assertEquals(4, instance.quantile(0.8), 1E-6);
        assertEquals(6, instance.quantile(0.9), 1E-6);
        assertEquals(12, instance.quantile(0.99), 1E-6);
    }
}