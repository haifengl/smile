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

package smile.stat.hypothesis;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.Math;

/**
 *
 * @author Haifeng Li
 */
public class TTestTest {

    public TTestTest() {
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
     * Test of test method, of class TTest.
     */
    @Test
    public void testTestOneSample() {
        System.out.println("test");
        double[] x = {0.48074284, -0.52975023, 1.28590721, 0.63456079, -0.41761197, 2.76072411,
            1.30321095, -1.16454533, 2.27210509, 1.46394553, -0.31713164, 1.26247543,
            2.65886430, 0.40773450, 1.18055440, -0.39611251, 2.13557687, 0.40878860,
            1.28461394, -0.02906355};
        
        TTest result = TTest.test(x, 1.0);
        assertEquals(19, result.df, 1E-10);
        assertEquals(-0.6641, result.t, 1E-4);
        assertEquals(0.5146, result.pvalue, 1E-4);

        result = TTest.test(x, 1.1);
        assertEquals(19, result.df, 1E-10);
        assertEquals(-1.0648, result.t, 1E-4);
        assertEquals(0.3003, result.pvalue, 1E-4);
    }

    /**
     * Test of test method, of class TTest.
     */
    @Test
    public void testTestTwoSample() {
        System.out.println("test");
        double[] x = {0.48074284, -0.52975023, 1.28590721, 0.63456079, -0.41761197, 2.76072411,
            1.30321095, -1.16454533, 2.27210509, 1.46394553, -0.31713164, 1.26247543,
            2.65886430, 0.40773450, 1.18055440, -0.39611251, 2.13557687, 0.40878860,
            1.28461394, -0.02906355};

        double[] y = {1.7495879, 1.9359727, 3.1294928, 0.0861894, 2.1643415, 0.1913219,
            -0.3947444, 1.6910837, 1.1548294, 0.2763955, 0.4794719, 3.1805501,
            1.5700497, 2.6860190, -0.4410879, 1.8900183, 1.3422381, -0.1701592};

        TTest result = TTest.test(x, y);
        assertEquals(35.167, result.df, 1E-3);
        assertEquals(-1.1219, result.t, 1E-4);
        assertEquals(0.2695, result.pvalue, 1E-4);

        double[] z = {0.6621329, 0.4688975, -0.1553013, 0.4564548, 2.2776146, 2.1543678,
            2.8555142, 1.5852899, 0.9091290, 1.6060025, 1.0111968, 1.2479493,
            0.9407034, 1.7167572, 0.5380608, 2.1290007, 1.8695506, 1.2139096};

        result = TTest.test(x, z);
        assertEquals(34.025, result.df, 1E-3);
        assertEquals(-1.518, result.t, 1E-3);
        assertEquals(0.1382, result.pvalue, 1E-4);
    }

    /**
     * Test of test method, of class TTest.
     */
    @Test
    public void testTestTwoSampleEqualVariance() {
        System.out.println("test");
        double[] x = {0.48074284, -0.52975023, 1.28590721, 0.63456079, -0.41761197, 2.76072411,
            1.30321095, -1.16454533, 2.27210509, 1.46394553, -0.31713164, 1.26247543,
            2.65886430, 0.40773450, 1.18055440, -0.39611251, 2.13557687, 0.40878860,
            1.28461394, -0.02906355};

        double[] y = {1.7495879, 1.9359727, 3.1294928, 0.0861894, 2.1643415, 0.1913219,
            -0.3947444, 1.6910837, 1.1548294, 0.2763955, 0.4794719, 3.1805501,
            1.5700497, 2.6860190, -0.4410879, 1.8900183, 1.3422381, -0.1701592};

        TTest result = TTest.test(x, y, true);
        assertEquals(36, result.df, 1E-10);
        assertEquals(-1.1247, result.t, 1E-4);
        assertEquals(0.2682, result.pvalue, 1E-4);

        double[] z = {0.6621329, 0.4688975, -0.1553013, 0.4564548, 2.2776146, 2.1543678,
            2.8555142, 1.5852899, 0.9091290, 1.6060025, 1.0111968, 1.2479493,
            0.9407034, 1.7167572, 0.5380608, 2.1290007, 1.8695506, 1.2139096};

        result = TTest.test(x, z, true);
        assertEquals(36, result.df, 1E-10);
        assertEquals(-1.4901, result.t, 1E-4);
        assertEquals(0.1449, result.pvalue, 1E-4);
    }

    /**
     * Test of pairedTest method, of class TTest.
     */
    @Test
    public void testPairedTest() {
        System.out.println("pairedTest");
        double[] y = {1.7495879, 1.9359727, 3.1294928, 0.0861894, 2.1643415, 0.1913219,
            -0.3947444, 1.6910837, 1.1548294, 0.2763955, 0.4794719, 3.1805501,
            1.5700497, 2.6860190, -0.4410879, 1.8900183, 1.3422381, -0.1701592};

        double[] z = {0.6621329, 0.4688975, -0.1553013, 0.4564548, 2.2776146, 2.1543678,
            2.8555142, 1.5852899, 0.9091290, 1.6060025, 1.0111968, 1.2479493,
            0.9407034, 1.7167572, 0.5380608, 2.1290007, 1.8695506, 1.2139096};

        TTest result = TTest.pairedTest(y, z);
        assertEquals(17, result.df, 1E-10);
        assertEquals(-0.1502, result.t, 1E-4);
        assertEquals(0.8824, result.pvalue, 1E-4);
    }

    /**
     * Test of test method, of class TTest.
     */
    @Test
    public void testTestCorr() {
        System.out.println("test");

        double[] x = {44.4, 45.9, 41.9, 53.3, 44.7, 44.1, 50.7, 45.2, 60.1};
        double[] y  = {2.6,  3.1,  2.5,  5.0,  3.6,  4.0,  5.2,  2.8,  3.8};

        TTest result = TTest.test(Math.cor(x,y), x.length-2);
        assertEquals(7, result.df, 1E-10);
        assertEquals(1.8411, result.t, 1E-4);
        assertEquals(0.1082, result.pvalue, 1E-4);
    }

}