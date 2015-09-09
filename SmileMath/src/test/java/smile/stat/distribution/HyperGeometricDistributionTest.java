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
public class HyperGeometricDistributionTest {

    public HyperGeometricDistributionTest() {
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
     * Test of npara method, of class HyperGeometricDistribution.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        HyperGeometricDistribution instance = new HyperGeometricDistribution(100, 30, 70);
        instance.rand();
        assertEquals(3, instance.npara());
    }

    /**
     * Test of mean method, of class HyperGeometricDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        HyperGeometricDistribution instance = new HyperGeometricDistribution(100, 30, 70);
        instance.rand();
        assertEquals(21, instance.mean(), 1E-7);
        instance = new HyperGeometricDistribution(100, 30, 80);
        instance.rand();
        assertEquals(24, instance.mean(), 1E-7);
        instance = new HyperGeometricDistribution(100, 30, 60);
        instance.rand();
        assertEquals(18, instance.mean(), 1E-7);
    }

    /**
     * Test of var method, of class HyperGeometricDistribution.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        HyperGeometricDistribution instance = new HyperGeometricDistribution(100, 30, 70);
        instance.rand();
        assertEquals(4.454545, instance.var(), 1E-6);
        instance = new HyperGeometricDistribution(100, 30, 80);
        instance.rand();
        assertEquals(3.393939, instance.var(), 1E-6);
        instance = new HyperGeometricDistribution(100, 30, 60);
        instance.rand();
        assertEquals(5.090909, instance.var(), 1E-6);
    }

    /**
     * Test of sd method, of class HyperGeometricDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        HyperGeometricDistribution instance = new HyperGeometricDistribution(100, 30, 70);
        instance.rand();
        assertEquals(2.110579, instance.sd(), 1E-6);
        instance = new HyperGeometricDistribution(100, 30, 80);
        instance.rand();
        assertEquals(1.842265, instance.sd(), 1E-6);
        instance = new HyperGeometricDistribution(100, 30, 60);
        instance.rand();
        assertEquals(2.256304, instance.sd(), 1E-6);
    }

    /**
     * Test of p method, of class HyperGeometricDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        HyperGeometricDistribution instance = new HyperGeometricDistribution(100, 30, 70);
        instance.rand();
        assertEquals(0.0, instance.p(-1), 1E-6);
        assertEquals(3.404564e-26, instance.p(0), 1E-30);
        assertEquals(7.149584e-23, instance.p(1), 1E-27);
        assertEquals(3.576579e-20, instance.p(2), 1E-25);
        assertEquals(0.1655920, instance.p(20), 1E-7);
        assertEquals(0.1877461, instance.p(21), 1E-7);
        assertEquals(0.00041413, instance.p(28), 1E-8);
        assertEquals(4.136376e-05, instance.p(29), 1E-10);
        assertEquals(1.884349e-06, instance.p(30), 1E-12);
        assertEquals(0.0, instance.p(31), 1E-6);
    }

    /**
     * Test of logP method, of class HyperGeometricDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        HyperGeometricDistribution instance = new HyperGeometricDistribution(100, 30, 70);
        instance.rand();
        assertEquals(Math.log(3.404564e-26), instance.logp(0), 1E-5);
        assertEquals(Math.log(7.149584e-23), instance.logp(1), 1E-5);
        assertEquals(Math.log(3.576579e-20), instance.logp(2), 1E-5);
        assertEquals(Math.log(0.1655920), instance.logp(20), 1E-5);
        assertEquals(Math.log(0.1877461), instance.logp(21), 1E-5);
        assertEquals(Math.log(0.00041413), instance.logp(28), 1E-5);
        assertEquals(Math.log(4.136376e-05), instance.logp(29), 1E-5);
        assertEquals(Math.log(1.884349e-06), instance.logp(30), 1E-5);
    }

    /**
     * Test of cdf method, of class HyperGeometricDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        HyperGeometricDistribution instance = new HyperGeometricDistribution(100, 30, 70);
        instance.rand();
        assertEquals(3.404564e-26, instance.cdf(0), 1E-30);
        assertEquals(7.152988e-23, instance.cdf(1), 1E-27);
        assertEquals(3.583732e-20, instance.cdf(2), 1E-25);
        assertEquals(0.4013632, instance.cdf(20), 1E-7);
        assertEquals(0.5891093, instance.cdf(21), 1E-7);
        assertEquals(0.9999568, instance.cdf(28), 1E-7);
        assertEquals(0.9999981, instance.cdf(29), 1E-7);
        assertEquals(1.0, instance.cdf(30), 1E-7);
        assertEquals(1.0, instance.cdf(31), 1E-7);
    }

    /**
     * Test of quantile method, of class HyperGeometricDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        HyperGeometricDistribution instance = new HyperGeometricDistribution(100, 30, 70);
        instance.rand();
        assertEquals(0, instance.quantile(0), 1E-30);
        assertEquals(14, instance.quantile(0.001), 1E-27);
        assertEquals(16, instance.quantile(0.01), 1E-25);
        assertEquals(18, instance.quantile(0.1), 1E-25);
        assertEquals(19, instance.quantile(0.2), 1E-7);
        assertEquals(20, instance.quantile(0.3), 1E-7);
        assertEquals(24, instance.quantile(0.9), 1E-8);
        assertEquals(26, instance.quantile(0.99), 1E-10);
        assertEquals(27, instance.quantile(0.999), 1E-12);
        assertEquals(30, instance.quantile(1), 1E-6);
    }
}