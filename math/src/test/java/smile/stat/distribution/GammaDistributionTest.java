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
public class GammaDistributionTest {

    public GammaDistributionTest() {
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
     * Test of constructor, of class GammaDistribution.
     */
    @Test
    public void testGammaDistribution() {
        System.out.println("GammaDistribution");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        double[] data = new double[1000];
        for (int i = 0; i < data.length; i++)
            data[i] = instance.rand();
        GammaDistribution est = new GammaDistribution(data);
        assertEquals(0.0, (est.getScale() - 2.1) / 2.1, 0.1);
        assertEquals(0.0, (est.getShape() - 3.0) / 3.0, 0.1);
    }

    /**
     * Test of npara method, of class Gamma.
     */
    @Test
    public void testNpara() {
        System.out.println("npara");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(2, instance.npara());
    }

    /**
     * Test of mean method, of class Gamma.
     */
    @Test
    public void testMean() {
        System.out.println("var");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(6.3, instance.mean(), 1E-7);
    }

    /**
     * Test of var method, of class Gamma.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(13.23, instance.var(), 1E-7);
    }

    /**
     * Test of sd method, of class Gamma.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(3.637307, instance.sd(), 1E-6);
    }

    /**
     * Test of entropy method, of class Gamma.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(2.589516, instance.entropy(), 1E-6);
    }

    /**
     * Test of p method, of class Gamma.
     */
    @Test
    public void testP() {
        System.out.println("p");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(0.0, instance.p(-0.1), 1E-7);
        assertEquals(0.0, instance.p(0.0), 1E-7);
        assertEquals(0.0005147916, instance.p(0.1), 1E-7);
        assertEquals(0.03353553, instance.p(1.0), 1E-7);
        assertEquals(0.08332174, instance.p(2.0), 1E-7);
        assertEquals(0.1164485, instance.p(3.0), 1E-7);
        assertEquals(0.1285892, instance.p(4.0), 1E-7);
        assertEquals(0.04615759, instance.p(10), 1E-7);
        assertEquals(0.001578462, instance.p(20), 1E-7);
        assertEquals(3.036321e-05, instance.p(30), 1E-7);
    }

    /**
     * Test of logP method, of class Gamma.
     */
    @Test
    public void testLogP() {
        System.out.println("logP");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertTrue(Double.isInfinite(instance.logp(-0.1)));
        assertTrue(Double.isInfinite(instance.logp(0.0)));
        assertEquals(-3.39515,  instance.logp(1.0), 1E-5);
        assertEquals(-2.485046, instance.logp(2.0), 1E-6);
        assertEquals(-2.150306, instance.logp(3.0), 1E-6);
        assertEquals(-2.051132, instance.logp(4.0), 1E-6);
        assertEquals(-3.075694, instance.logp(10.0), 1E-6);
        assertEquals(-6.451304, instance.logp(20.0), 1E-6);
        assertEquals(-10.40228, instance.logp(30.0), 1E-5);
    }

    /**
     * Test of cdf method, of class Gamma.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(0.0, instance.cdf(-0.1), 1E-7);
        assertEquals(0.0, instance.cdf(0.0), 1E-7);
        assertEquals(0.01264681, instance.cdf(1.0), 1E-7);
        assertEquals(0.07175418, instance.cdf(2.0), 1E-7);
        assertEquals(0.1734485, instance.cdf(3.0), 1E-7);
        assertEquals(0.2975654, instance.cdf(4.0), 1E-7);
        assertEquals(0.8538087, instance.cdf(10.0), 1E-7);
        assertEquals(0.995916, instance.cdf(20.0), 1E-7);
        assertEquals(0.9999267, instance.cdf(30.0), 1E-7);
    }

    /**
     * Test of quantile method, of class Gamma.
     */
    @Test
    public void testQuantile() {
        System.out.println("quantile");
        GammaDistribution instance = new GammaDistribution(3, 2.1);
        instance.rand();
        assertEquals(0.4001201, instance.quantile(0.001), 1E-7);
        assertEquals(0.9156948, instance.quantile(0.01), 1E-7);
        assertEquals(2.314337, instance.quantile(0.1), 1E-6);
        assertEquals(3.223593, instance.quantile(0.2), 1E-6);
        assertEquals(5.615527, instance.quantile(0.5), 1E-6);
        assertEquals(11.17687, instance.quantile(0.9), 1E-5);
        assertEquals(17.65249, instance.quantile(0.99), 1E-5);
        assertEquals(23.58063, instance.quantile(0.999), 1E-5);
    }
}