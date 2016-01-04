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
public class LogisticDistributionTest {

    public LogisticDistributionTest() {
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
     * Test of npara method, of class LogisticDistribution.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(2, instance.npara());
    }

    /**
     * Test of mean method, of class LogisticDistribution.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(2.0, instance.mean(), 1E-7);
    }

    /**
     * Test of var method, of class LogisticDistribution.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(Math.PI*Math.PI/3, instance.var(), 1E-7);
    }

    /**
     * Test of sd method, of class LogisticDistribution.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(Math.PI/Math.sqrt(3), instance.sd(), 1E-7);
    }

    /**
     * Test of entropy method, of class LogisticDistribution.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(2.0, instance.mean(), 1E-7);
    }

    /**
     * Test of p method, of class LogisticDistribution.
     */
    @Test
    public void testP() {
        System.out.println("p");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(0.1050736, instance.p(0.001), 1E-7);
        assertEquals(0.1057951, instance.p(0.01), 1E-7);
        assertEquals(0.1131803, instance.p(0.1), 1E-7);
        assertEquals(0.1217293, instance.p(0.2), 1E-7);
        assertEquals(0.1491465, instance.p(0.5), 1E-7);
        assertEquals(0.1966119, instance.p(1.0), 1E-7);
        assertEquals(0.25,      instance.p(2.0), 1E-7);
        assertEquals(0.04517666, instance.p(5.0), 1E-7);
        assertEquals(0.0003352377, instance.p(10.0), 1E-7);
    }

    /**
     * Test of logP method, of class LogisticDistribution.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(Math.log(0.1050736), instance.logp(0.001), 1E-5);
        assertEquals(Math.log(0.1057951), instance.logp(0.01), 1E-5);
        assertEquals(Math.log(0.1131803), instance.logp(0.1), 1E-5);
        assertEquals(Math.log(0.1217293), instance.logp(0.2), 1E-5);
        assertEquals(Math.log(0.1491465), instance.logp(0.5), 1E-5);
        assertEquals(Math.log(0.1966119), instance.logp(1.0), 1E-5);
        assertEquals(Math.log(0.25),      instance.logp(2.0), 1E-5);
        assertEquals(Math.log(0.04517666), instance.logp(5.0), 1E-5);
        assertEquals(Math.log(0.0003352377), instance.logp(10.0), 1E-5);
    }

    /**
     * Test of cdf method, of class LogisticDistribution.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(0.1193080, instance.cdf(0.001), 1E-7);
        assertEquals(0.1202569, instance.cdf(0.01), 1E-7);
        assertEquals(0.1301085, instance.cdf(0.1), 1E-7);
        assertEquals(0.1418511, instance.cdf(0.2), 1E-7);
        assertEquals(0.1824255, instance.cdf(0.5), 1E-7);
        assertEquals(0.2689414, instance.cdf(1.0), 1E-7);
        assertEquals(0.5 ,      instance.cdf(2.0), 1E-7);
        assertEquals(0.9525741, instance.cdf(5.0), 1E-7);
        assertEquals(0.9996646, instance.cdf(10.0), 1E-7);
    }

    /**
     * Test of quantile method, of class LogisticDistribution.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        LogisticDistribution instance = new LogisticDistribution(2.0, 1.0);
        instance.rand();
        assertEquals(-4.906755,  instance.quantile(0.001), 1E-6);
        assertEquals(-2.59512,   instance.quantile(0.01), 1E-5);
        assertEquals(-0.1972246, instance.quantile(0.1), 1E-7);
        assertEquals(0.6137056,  instance.quantile(0.2), 1E-6);
        assertEquals(2.0,        instance.quantile(0.5), 1E-7);
        assertEquals(4.197225,   instance.quantile(0.9), 1E-6);
        assertEquals(6.59512,    instance.quantile(0.99), 1E-5);
        assertEquals(8.906755,   instance.quantile(0.999), 1E-6);
    }

}