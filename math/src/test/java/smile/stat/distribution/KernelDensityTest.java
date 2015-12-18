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
public class KernelDensityTest {

    double[] x = {
        -1.55794654,  0.30033828,  0.28937459, -0.28727551, -0.61498845,  0.14952967,
         1.51912638,  0.23609780,  0.16453335,  0.64263266, -0.13563630,  0.39385392,
        -1.00369308, -0.19202007,  0.27667572, -1.51659152, -0.03901663,  0.38888456,
        -1.15581528,  0.66037692,  0.51299960,  0.38924826, -1.48458408,  0.06363872,
         0.69407661,  0.69765414, -0.49251761,  2.08045018,  0.90395702, -1.17954013,
        -0.94640217, -0.11156842, -1.35019326,  1.51925877,  0.22340115,  2.63770254,
         0.32744665,  1.28664700, -0.19881111,  2.04193339, -1.01105904,  0.19517024,
        -3.66880217,  1.28355883, -1.02960013, -0.45840780,  1.55232434,  0.12640701,
        -0.31654612,  0.51517122,  2.90239624,  4.29210637,  3.35887337,  3.89542230,
         4.15146739,  3.16608003,  3.69720758,  3.60120187,  3.42600323,  3.99102268,
         3.32007377,  3.20715257,  6.02027780,  1.01850842,  3.76725145,  3.18578139,
         1.56201504,  4.08197314,  2.84196863,  4.13243510,  4.72991327,  4.11461702,
         3.38684715,  2.70418409,  3.40647059,  3.29402538,  3.08532048,  3.48893171,
         5.05833004,  3.44992726,  4.76429010,  1.70002674,  2.40614476,  2.95027628,
         4.46030960,  2.60520029,  4.51555913,  5.30909752,  3.80157472,  2.58921481,
         3.83018423,  2.33632882,  3.70880986,  4.53759004,  4.67695148,  3.95250189,
         2.81431009,  0.89490397,  4.46476035,  2.61995346,  6.46189066,  5.24997327,
         5.97333084,  6.83321072,  8.29570598,  6.66027754,  8.36501175,  6.72549291,
         7.24849982,  7.20677453,  8.63224826,  6.34095948,  7.70794565,  8.59772271,
         7.57569812,  6.94658925,  7.48082735,  6.01037527,  8.42542654,  8.26103364,
         5.38948668,  7.60189243,  6.40585265,  7.62080829,  5.79106794,  9.23032371,
         6.16093875,  6.18723127,  6.70248604,  7.75852071,  6.57107926,  7.14126227,
         6.61010448,  5.98299217,  7.01536707,  6.07319659,  9.45587403,  4.67227804,
         7.52217929,  8.63082502,  6.06689350,  8.18995118,  7.78955153,  8.33424996,
         6.84911307,  6.91882814,  8.09092025,  5.52966548,  6.96083522,  6.27545961
    };

    public KernelDensityTest() {
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
     * Test of bandwidth method, of class KernelDensity.
     */
    @Test
    public void testBandwidth() {
        System.out.println("bandwidth");
        KernelDensity instance = new KernelDensity(x);
        double expResult = 1.1933;
        double result = instance.bandwidth();
        assertEquals(expResult, result, 1E-4);
    }

    /**
     * Test of mean method, of class KernelDensity.
     */
    @Test
    public void testMean() {
        System.out.println("mean");
        KernelDensity instance = new KernelDensity(x);
        double expResult = 3.55417;
        double result = instance.mean();
        assertEquals(expResult, result, 1E-5);
    }

    /**
     * Test of var method, of class KernelDensity.
     */
    @Test
    public void testVar() {
        System.out.println("var");
        KernelDensity instance = new KernelDensity(x);
        double expResult = 9.404966;
        double result = instance.var();
        assertEquals(expResult, result, 1E-6);
    }

    /**
     * Test of sd method, of class KernelDensity.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        KernelDensity instance = new KernelDensity(x);
        double expResult = 3.066752;
        double result = instance.sd();
        assertEquals(expResult, result, 1E-6);
    }

    /**
     * Test of p method, of class KernelDensity.
     */
    @Test
    public void testP() {
        System.out.println("p");
        KernelDensity instance = new KernelDensity(x);
        double expResult = 0.10122;
        double result = instance.p(3.5);
        assertEquals(expResult, result, 1E-5);
    }

    /**
     * Test of logp method, of class KernelDensity.
     */
    @Test
    public void testLogp() {
        System.out.println("logp");
        KernelDensity instance = new KernelDensity(x);
        double expResult = -2.29044906;
        double result = instance.logp(3.5);
        assertEquals(expResult, result, 1E-8);
    }
}