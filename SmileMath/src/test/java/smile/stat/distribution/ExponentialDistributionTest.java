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
public class ExponentialDistributionTest {

    public ExponentialDistributionTest() {
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
     * Test of constructor, of class ExponentialDistribution.
     */
    @Test
    public void testExponentialDistribution() {
        System.out.println("ExponentialDistribution");
        ExponentialDistribution instance = new ExponentialDistribution(3);
        double[] data = new double[1000];
        for (int i = 0; i < data.length; i++)
            data[i] = instance.rand();
        ExponentialDistribution est = new ExponentialDistribution(data);
        assertEquals(3, est.getLambda(), 2E-1);
    }

    /**
     * Test of npara method, of class Exponential.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        ExponentialDistribution instance = new ExponentialDistribution(1.0);
        instance.rand();
        assertEquals(1, instance.npara());
    }

    /**
     * Test of mean method, of class Exponential.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        ExponentialDistribution instance = new ExponentialDistribution(1.0);
        instance.rand();
        assertEquals(1.0, instance.mean(), 1E-7);
        instance = new ExponentialDistribution(2.0);
        instance.rand();
        assertEquals(0.5, instance.mean(), 1E-7);
        instance = new ExponentialDistribution(3.0);
        instance.rand();
        assertEquals(0.3333333, instance.mean(), 1E-7);
        instance = new ExponentialDistribution(4.0);
        instance.rand();
        assertEquals(0.25, instance.mean(), 1E-7);
    }

    /**
     * Test of var method, of class Exponential.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        ExponentialDistribution instance = new ExponentialDistribution(1.0);
        instance.rand();
        assertEquals(1.0, instance.var(), 1E-7);
        instance.rand();
        instance = new ExponentialDistribution(2.0);
        instance.rand();
        assertEquals(0.25, instance.var(), 1E-7);
        instance = new ExponentialDistribution(3.0);
        instance.rand();
        assertEquals(1.0/9, instance.var(), 1E-7);
        instance = new ExponentialDistribution(4.0);
        instance.rand();
        assertEquals(1.0/16, instance.var(), 1E-7);
    }

    /**
     * Test of sd method, of class Exponential.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        ExponentialDistribution instance = new ExponentialDistribution(1.0);
        instance.rand();
        assertEquals(1.0, instance.sd(), 1E-7);
        instance = new ExponentialDistribution(2.0);
        instance.rand();
        assertEquals(0.5, instance.sd(), 1E-7);
        instance = new ExponentialDistribution(3.0);
        instance.rand();
        assertEquals(0.3333333, instance.sd(), 1E-7);
        instance = new ExponentialDistribution(4.0);
        instance.rand();
        assertEquals(0.25, instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class Exponential.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        ExponentialDistribution instance = new ExponentialDistribution(1.0);
        instance.rand();
        assertEquals(1.0, instance.entropy(), 1E-7);
        instance = new ExponentialDistribution(2.0);
        instance.rand();
        assertEquals(1-Math.log(2), instance.entropy(), 1E-7);
        instance = new ExponentialDistribution(3.0);
        instance.rand();
        assertEquals(1-Math.log(3), instance.entropy(), 1E-7);
        instance = new ExponentialDistribution(4.0);
        instance.rand();
        assertEquals(1-Math.log(4), instance.entropy(), 1E-7);
    }

    /**
     * Test of p method, of class Exponential.
     */
    @Test
    public void testP() {
        System.out.println("p");
        ExponentialDistribution instance = new ExponentialDistribution(2.0);
        instance.rand();
        assertEquals(0, instance.p(-0.1), 1E-7);
        assertEquals(2.0, instance.p(0.0), 1E-7);
        assertEquals(0.2706706, instance.p(1.0), 1E-7);
        assertEquals(0.03663128, instance.p(2.0), 1E-7);
        assertEquals(0.004957504, instance.p(3.0), 1E-7);
        assertEquals(0.0006709253, instance.p(4.0), 1E-7);
    }

    /**
     * Test of logP method, of class Exponential.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        ExponentialDistribution instance = new ExponentialDistribution(2.0);
        instance.rand();
        assertTrue(Double.isInfinite(instance.logp(-0.1)));
        assertEquals(0.6931472, instance.logp(0.0), 1E-6);
        assertEquals(-1.306853, instance.logp(1.0), 1E-6);
        assertEquals(-3.306853, instance.logp(2.0), 1E-6);
        assertEquals(-5.306853, instance.logp(3.0), 1E-6);
        assertEquals(-7.306853, instance.logp(4.0), 1E-6);
    }

    /**
     * Test of cdf method, of class Exponential.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        ExponentialDistribution instance = new ExponentialDistribution(2.0);
        instance.rand();
        assertEquals(0, instance.cdf(-0.1), 1E-7);
        assertEquals(0, instance.cdf(0.0), 1E-7);
        assertEquals(0.8646647, instance.cdf(1.0), 1E-7);
        assertEquals(0.9816844, instance.cdf(2.0), 1E-7);
        assertEquals(0.9975212, instance.cdf(3.0), 1E-7);
        assertEquals(0.9996645, instance.cdf(4.0), 1E-7);
        assertEquals(0.9999999, instance.cdf(8.0), 1E-7);
        assertEquals(1.0, instance.cdf(10.0), 1E-7);
    }

    /**
     * Test of quantile method, of class Exponential.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        ExponentialDistribution instance = new ExponentialDistribution(2.0);
        instance.rand();
        assertEquals(0.05268026, instance.quantile(0.1), 1E-7);
        assertEquals(0.1783375, instance.quantile(0.3), 1E-7);
        assertEquals(0.3465736, instance.quantile(0.5), 1E-7);
        assertEquals(0.6019864, instance.quantile(0.7), 1E-7);
    }
}