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
public class TDistributionTest {

    public TDistributionTest() {
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
     * Test of npara method, of class TDistribution.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        TDistribution instance = new TDistribution(20);
        instance.rand();
        assertEquals(1, instance.npara());
    }

    /**
     * Test of mean method, of class TDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        TDistribution instance = new TDistribution(20);
        instance.rand();
        assertEquals(0.0, instance.mean(), 1E-7);
    }

    /**
     * Test of var method, of class TDistribution.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        TDistribution instance = new TDistribution(20);
        instance.rand();
        assertEquals(10/9.0, instance.var(), 1E-7);
    }

    /**
     * Test of sd method, of class TDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        TDistribution instance = new TDistribution(20);
        instance.rand();
        assertEquals(Math.sqrt(10/9.0), instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class TDistribution.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        TDistribution instance = new TDistribution(20);
        assertEquals(1.46954202, instance.entropy(), 1E-7);
    }

    /**
     * Test of p method, of class TDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        TDistribution instance = new TDistribution(20);
        instance.rand();
        assertEquals(2.660085e-09, instance.p(-10.0), 1E-16);
        assertEquals(0.05808722, instance.p(-2.0), 1E-7);
        assertEquals(0.2360456, instance.p(-1.0), 1E-7);
        assertEquals(0.3939886, instance.p(0.0), 1E-7);
        assertEquals(0.2360456, instance.p(1.0), 1E-7);
        assertEquals(0.05808722, instance.p(2.0), 1E-7);
        assertEquals(2.660085e-09, instance.p(10.0), 1E-16);
    }

    /**
     * Test of logP method, of class TDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        TDistribution instance = new TDistribution(20);
        instance.rand();
        assertEquals(Math.log(2.660085e-09), instance.logp(-10.0), 1E-5);
        assertEquals(Math.log(0.05808722), instance.logp(-2.0), 1E-5);
        assertEquals(Math.log(0.2360456), instance.logp(-1.0), 1E-5);
        assertEquals(Math.log(0.3939886), instance.logp(0.0), 1E-5);
        assertEquals(Math.log(0.2360456), instance.logp(1.0), 1E-5);
        assertEquals(Math.log(0.05808722), instance.logp(2.0), 1E-5);
        assertEquals(Math.log(2.660085e-09), instance.logp(10.0), 1E-5);
    }

    /**
     * Test of cdf method, of class TDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        TDistribution instance = new TDistribution(20);
        instance.rand();
        assertEquals(1.581891e-09, instance.cdf(-10.0), 1E-15);
        assertEquals(0.02963277, instance.cdf(-2.0), 1E-7);
        assertEquals(0.1646283, instance.cdf(-1.0), 1E-7);
        assertEquals(0.5, instance.cdf(0.0), 1E-7);
        assertEquals(0.8353717, instance.cdf(1.0), 1E-7);
        assertEquals(0.9703672, instance.cdf(2.0), 1E-7);
        assertEquals(1.0, instance.cdf(10.0), 1E-7);
    }

    /**
     * Test of quantile method, of class TDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        TDistribution instance = new TDistribution(20);
        instance.rand();
        assertEquals(-3.551808, instance.quantile(0.001), 1E-6);
        assertEquals(-2.527977, instance.quantile(0.01), 1E-6);
        assertEquals(-1.325341, instance.quantile(0.1), 1E-6);
        assertEquals(-0.8599644, instance.quantile(0.2), 1E-6);
        assertEquals(0.0,      instance.quantile(0.5), 1E-6);
        assertEquals(1.325341, instance.quantile(0.9), 1E-6);
        assertEquals(2.527977, instance.quantile(0.99), 1E-6);
        assertEquals(3.551808, instance.quantile(0.999), 1E-6);
    }
}