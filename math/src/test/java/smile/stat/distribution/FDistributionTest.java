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
     * Test of npara method, of class FDistribution.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(2, instance.npara());
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
     * Test of var method, of class FDistribution.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        FDistribution instance = new FDistribution(10, 20);
        instance.rand();
        assertEquals(0.4320988, instance.var(), 1E-7);
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