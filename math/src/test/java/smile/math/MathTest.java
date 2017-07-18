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
package smile.math;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.matrix.*;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class MathTest {

    public MathTest() {
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

    @Test
    public void testIsZero() {
        System.out.println("isZero");
        assertEquals(true, Math.isZero(0.0));
        assertEquals(true, Math.isZero(Double.MIN_VALUE));
        assertEquals(true, Math.isZero(Double.MIN_NORMAL));
        assertEquals(false, Math.isZero(Math.EPSILON));
    }

    /**
     * Test of isPower2 method, of class Math.
     */
    @Test
    public void testIsPower2() {
        System.out.println("isPower2");
        assertEquals(false, Math.isPower2(-1));
        assertEquals(false, Math.isPower2(0));
        assertEquals(true, Math.isPower2(1));
        assertEquals(true, Math.isPower2(2));
        assertEquals(false, Math.isPower2(3));
        assertEquals(true, Math.isPower2(4));
        assertEquals(true, Math.isPower2(8));
        assertEquals(true, Math.isPower2(16));
        assertEquals(true, Math.isPower2(32));
        assertEquals(true, Math.isPower2(64));
        assertEquals(true, Math.isPower2(128));
        assertEquals(true, Math.isPower2(256));
        assertEquals(true, Math.isPower2(512));
        assertEquals(true, Math.isPower2(1024));
        assertEquals(true, Math.isPower2(65536));
        assertEquals(true, Math.isPower2(131072));
    }

    /**
     * Test of log2 method, of class Math.
     */
    @Test
    public void testLog2() {
        System.out.println("log2");
        assertEquals(0, Math.log2(1), 1E-6);
        assertEquals(1, Math.log2(2), 1E-6);
        assertEquals(1.584963, Math.log2(3), 1E-6);
        assertEquals(2, Math.log2(4), 1E-6);
    }

    /**
     * Test of sqr method, of class Math.
     */
    @Test
    public void testSqr() {
        System.out.println("sqr");
        assertEquals(0, Math.sqr(0), 1E-10);
        assertEquals(1, Math.sqr(1), 1E-10);
        assertEquals(4, Math.sqr(2), 1E-10);
        assertEquals(9, Math.sqr(3), 1E-10);
    }

    /**
     * Test of factorial method, of class Math.
     */
    @Test
    public void testFactorial() {
        System.out.println("factorial");
        assertEquals(1.0, Math.factorial(0), 1E-7);
        assertEquals(1.0, Math.factorial(1), 1E-7);
        assertEquals(2.0, Math.factorial(2), 1E-7);
        assertEquals(6.0, Math.factorial(3), 1E-7);
        assertEquals(24.0, Math.factorial(4), 1E-7);
    }

    /**
     * Test of logFactorial method, of class Math.
     */
    @Test
    public void testLogFactorial() {
        System.out.println("logFactorial");
        assertEquals(0.0, Math.logFactorial(0), 1E-7);
        assertEquals(0.0, Math.logFactorial(1), 1E-7);
        assertEquals(Math.log(2.0), Math.logFactorial(2), 1E-7);
        assertEquals(Math.log(6.0), Math.logFactorial(3), 1E-7);
        assertEquals(Math.log(24.0), Math.logFactorial(4), 1E-7);
    }

    /**
     * Test of choose method, of class Math.
     */
    @Test
    public void testChoose() {
        System.out.println("choose");
        assertEquals(1.0, Math.choose(10, 0), 1E-7);
        assertEquals(10.0, Math.choose(10, 1), 1E-7);
        assertEquals(45.0, Math.choose(10, 2), 1E-7);
        assertEquals(120.0, Math.choose(10, 3), 1E-7);
        assertEquals(210.0, Math.choose(10, 4), 1E-7);
    }

    /**
     * Test of logChoose method, of class Math.
     */
    @Test
    public void testLogChoose() {
        System.out.println("logChoose");
        assertEquals(0.0, Math.logChoose(10, 0), 1E-6);
        assertEquals(2.302585, Math.logChoose(10, 1), 1E-6);
        assertEquals(3.806662, Math.logChoose(10, 2), 1E-6);
        assertEquals(4.787492, Math.logChoose(10, 3), 1E-6);
        assertEquals(5.347108, Math.logChoose(10, 4), 1E-6);
    }

    /**
     * Test of random method, of class Math.
     */
    @Test
    public void testRandom() {
        System.out.println("random");
        double[] prob = {0.473646292, 0.206116725, 0.009308497, 0.227844687, 0.083083799};
        int[] sample = Math.random(prob, 300);
        double[][] hist = Histogram.histogram(sample, 5);
        double[] p = new double[5];
        for (int i = 0; i < 5; i++) {
            p[i] = hist[2][i] / 300.0;
        }
        assertTrue(Math.KullbackLeiblerDivergence(prob, p) < 0.05);
    }

    /**
     * Test of random method, of class Math.
     */
    @Test
    public void testRandom2() {
        System.out.println("random");
        double[] prob = {0.473646292, 0.206116725, 0.009308497, 0.227844687, 0.083083799};
        int[] sample = new int[300];
        for (int i = 0; i < 300; i++) {
            sample[i] = Math.random(prob);
        }

        double[][] hist = Histogram.histogram(sample, 5);
        double[] p = new double[5];
        for (int i = 0; i < 5; i++) {
            p[i] = hist[2][i] / 300.0;
        }

        assertTrue(Math.KullbackLeiblerDivergence(prob, p) < 0.05);
    }

    /**
     * Test of min method, of class Math.
     */
    @Test
    public void testMin_3args() {
        System.out.println("min");
        int a = -1;
        int b = 0;
        int c = 1;
        int expResult = -1;
        int result = Math.min(a, b, c);
        assertEquals(expResult, result);
    }

    /**
     * Test of max method, of class Math.
     */
    @Test
    public void testMax_3args() {
        System.out.println("max");
        int a = -1;
        int b = 0;
        int c = 1;
        int expResult = 1;
        int result = Math.max(a, b, c);
        assertEquals(expResult, result);
    }

    /**
     * Test of min method, of class Math.
     */
    @Test
    public void testMin_doubleArr() {
        System.out.println("min");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(-2.196822, Math.min(x), 1E-7);
    }

    /**
     * Test of max method, of class Math.
     */
    @Test
    public void testMax_doubleArr() {
        System.out.println("max");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(1.0567679, Math.max(x), 1E-7);
    }

    /**
     * Test of min method, of class Math.
     */
    @Test
    public void testMin_doubleArrArr() {
        System.out.println("min");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(-0.89044952, Math.min(A), 1E-7);
    }

    /**
     * Test of max method, of class Math.
     */
    @Test
    public void testMax_doubleArrArr() {
        System.out.println("max");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(0.7220180, Math.max(A), 1E-7);
    }

    /**
     * Test of transpose method, of class Math.
     */
    @Test
    public void testTranspose() {
        System.out.println("transpose");
        double[][] A = {
                {0.7220180, 0.07121225, 0.6881997},
                {-0.2648886, -0.89044952, 0.3700456},
                {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = {
                {0.72201800, -0.2648886, -0.6391588},
                {0.07121225, -0.8904495, 0.4494758},
                {0.68819970, 0.3700456, 0.6240573}
        };
        assertTrue(Math.equals(Math.transpose(A), B, 1E-7));
    }

    /**
     * Test of rowMin method, of class Math.
     */
    @Test
    public void testRowMin() {
        System.out.println("rowMin");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.07121225, -0.89044952, -0.6391588};

        double[] result = Math.rowMin(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of rowMax method, of class Math.
     */
    @Test
    public void testRowMax() {
        System.out.println("rowMax");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.7220180, 0.3700456, 0.6240573};

        double[] result = Math.rowMax(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of rowSum method, of class Math.
     */
    @Test
    public void testRowSums() {
        System.out.println("rowSums");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {1.4814300, -0.7852925, 0.4343743};

        double[] result = Math.rowSums(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of rowMean method, of class Math.
     */
    @Test
    public void testRowMeans() {
        System.out.println("rowMeans");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.4938100, -0.2617642, 0.1447914};

        double[] result = Math.rowMeans(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of colMin method, of class Math.
     */
    @Test
    public void testColMin() {
        System.out.println("colMin");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.6391588, -0.89044952, 0.3700456};

        double[] result = Math.colMin(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of colMax method, of class Math.
     */
    @Test
    public void testColMax() {
        System.out.println("colMax");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.7220180, 0.44947578, 0.6881997};

        double[] result = Math.colMax(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of colSum method, of class Math.
     */
    @Test
    public void testColSums() {
        System.out.println("colSums");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.1820294, -0.3697615, 1.6823026};

        double[] result = Math.colSums(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of colMean method, of class Math.
     */
    @Test
    public void testColMeans() {
        System.out.println("colMeans");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.06067647, -0.12325383, 0.56076753};

        double[] result = Math.colMeans(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of sum method, of class Math.
     */
    @Test
    public void testSum_doubleArr() {
        System.out.println("sum");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        assertEquals(45, Math.sum(data), 1E-6);
    }

    /**
     * Test of mean method, of class Math.
     */
    @Test
    public void testMean_doubleArr() {
        System.out.println("mean");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        assertEquals(5, Math.mean(data), 1E-6);
    }

    /**
     * Test of var method, of class Math.
     */
    @Test
    public void testVar_doubleArr() {
        System.out.println("var");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        assertEquals(7.5, Math.var(data), 1E-6);
    }

    /**
     * Test of sd method, of class Math.
     */
    @Test
    public void testSd() {
        System.out.println("sd");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        assertEquals(2.73861, Math.sd(data), 1E-5);
    }

    /**
     * Test of colSd method, of class Math.
     */
    @Test
    public void testColSd() {
        System.out.println("colSd");
        double[][] data = {
            {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0},
            {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0},
            {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0},
        };

        data = Math.transpose(data);

        assertEquals(2.73861, Math.colSds(data)[0], 1E-5);
        assertEquals(2.73861, Math.colSds(data)[1], 1E-5);
        assertEquals(2.73861, Math.colSds(data)[2], 1E-5);
    }

    /**
     * Test of mad method, of class Math.
     */
    @Test
    public void testMad() {
        System.out.println("mad");
        double[] data = {1, 1, 2, 2, 4, 6, 9};
        assertEquals(1.0, Math.mad(data), 1E-5);
    }

    /**
     * Test of distance method, of class Math.
     */
    @Test
    public void testDistance_doubleArr_doubleArr() {
        System.out.println("distance");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(2.422302, Math.distance(x, y), 1E-6);
    }

    /**
     * Test of squaredDistance method, of class Math.
     */
    @Test
    public void testSquaredDistance_doubleArr_doubleArr() {
        System.out.println("squaredDistance");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(5.867547, Math.squaredDistance(x, y), 1E-6);
    }

    /**
     * Test of dot method, of class Math.
     */
    @Test
    public void testDot_doubleArr_doubleArr() {
        System.out.println("dot");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(3.350726, Math.dot(x, y), 1E-6);
    }

    /**
     * Test of cov method, of class Math.
     */
    @Test
    public void testCov_doubleArr_doubleArr() {
        System.out.println("cov");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.5894983, Math.cov(x, y), 1E-7);
    }

    /**
     * Test of cor method, of class Math.
     */
    @Test
    public void testCor_doubleArr_doubleArr() {
        System.out.println("cor");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.4686847, Math.cor(x, y), 1E-7);
    }

    /**
     * Test of spearman method, of class Math.
     */
    @Test
    public void testSpearman_doubleArr_doubleArr() {
        System.out.println("spearman");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.3, Math.spearman(x, y), 1E-7);
    }

    /**
     * Test of kendall method, of class Math.
     */
    @Test
    public void testKendall_doubleArr_doubleArr() {
        System.out.println("kendall");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.2, Math.kendall(x, y), 1E-7);
    }

    /**
     * Test of norm1 method, of class Math.
     */
    @Test
    public void testNorm1_doubleArr() {
        System.out.println("norm1");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(4.638106, Math.norm1(x), 1E-6);
    }

    /**
     * Test of norm2 method, of class Math.
     */
    @Test
    public void testNorm2_doubleArr() {
        System.out.println("norm2");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(2.647086, Math.norm2(x), 1E-6);
    }

    /**
     * Test of normInf method, of class Math.
     */
    @Test
    public void testNormInf_doubleArr() {
        System.out.println("normInf");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(2.196822, Math.normInf(x), 1E-6);
    }

    /**
     * Test of norm method, of class Math.
     */
    @Test
    public void testNorm_doubleArr() {
        System.out.println("norm");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(2.647086, Math.norm(x), 1E-6);
    }

    /**
     * Test of standardize method, of class StatUtils.
     */
    @Test
    public void testStandardize() {
        System.out.println("standardize");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        Math.standardize(data);
        assertEquals(0, Math.mean(data), 1E-7);
        assertEquals(1, Math.sd(data), 1E-7);
    }

    /**
     * Test of unitize method, of class Math.
     */
    @Test
    public void testUnitize() {
        System.out.println("unitize");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        Math.unitize(data);
        assertEquals(1, Math.norm(data), 1E-7);
    }

    /**
     * Test of unitize1 method, of class Math.
     */
    @Test
    public void testUnitize1() {
        System.out.println("unitize1");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        Math.unitize1(data);
        assertEquals(1, Math.norm1(data), 1E-7);
    }

    /**
     * Test of unitize2 method, of class Math.
     */
    @Test
    public void testUnitize2() {
        System.out.println("unitize2");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        Math.unitize2(data);
        assertEquals(1, Math.norm2(data), 1E-7);
    }

    /**
     * Test of GoodTuring method, of class Math.
     */
    @Test
    public void testGoodTuring() {
        System.out.println("GoodTuring");
        int[] r = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12};
        int[] Nr = {120, 40, 24, 13, 15, 5, 11, 2, 2, 1, 3};
        double p0 = 0.2047782;
        double[] p = {
            0.0009267, 0.0024393, 0.0040945, 0.0058063, 0.0075464,
            0.0093026, 0.0110689, 0.0128418, 0.0146194, 0.0164005, 0.0199696};

        double[] result = new double[r.length];
        assertEquals(p0, Math.GoodTuring(r, Nr, result), 1E-7);
        for (int i = 0; i < r.length; i++) {
            assertEquals(p[i], result[i], 1E-7);
        }
    }

    /**
     * Test of clone method, of class Math.
     */
    @Test
    public void testClone() {
        System.out.println("clone");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };

        double[][] B = Math.clone(A);

        assertTrue(Math.equals(A, B));
        assertTrue(A != B);
        for (int i = 0; i < A.length; i++) {
            assertTrue(A[i] != B[i]);
        }
    }

    /**
     * Test of plusEquals method, of class Math.
     */
    @Test
    public void testAdd_doubleArr_doubleArr() {
        System.out.println("add");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        double[] z = {-3.9749544, -1.6219752, 0.9094410, 0.1106760, -0.0071785};
        Math.plus(x, y);
        assertTrue(Math.equals(x, z));
    }

    /**
     * Test of minusEquals method, of class Math.
     */
    @Test
    public void testMinus_doubleArr_doubleArr() {
        System.out.println("minus");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        double[] z = {-0.4186894, -0.2900074, -0.9957886, 2.0028598, 0.7778815};
        Math.minus(x, y);
        assertTrue(Math.equals(x, z));
    }

    /**
     * Test of root method, of class Math.
     */
    @Test
    public void testRoot_4args() {
        System.out.println("root");
        Function func = new Function() {

            @Override
            public double f(double x) {
                return x * x * x + x * x - 5 * x + 3;
            }
        };
        double result = Math.root(func, -4, -2, 1E-7);
        assertEquals(-3, result, 1E-7);
    }

    /**
     * Test of root method, of class Math.
     */
    @Test
    public void testRoot_5args() {
        System.out.println("root");
        Function func = new DifferentiableFunction() {

            @Override
            public double f(double x) {
                return x * x * x + x * x - 5 * x + 3;
            }

            @Override
            public double df(double x) {
                return 3 * x * x + 2 * x - 5;
            }
        };
        double result = Math.root(func, -4, -2, 1E-7);
        assertEquals(-3, result, 1E-7);
    }

    /**
     * Test of min method, of class Math.
     */
    @Test
    public void testMin_5args() {
        System.out.println("L-BFGS");
        DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {

            @Override
            public double f(double[] x) {
                double f = 0.0;
                for (int j = 1; j <= x.length; j += 2) {
                    double t1 = 1.e0 - x[j - 1];
                    double t2 = 1.e1 * (x[j] - x[j - 1] * x[j - 1]);
                    f = f + t1 * t1 + t2 * t2;
                }
                return f;
            }

            @Override
            public double f(double[] x, double[] g) {
                double f = 0.0;
                for (int j = 1; j <= x.length; j += 2) {
                    double t1 = 1.e0 - x[j - 1];
                    double t2 = 1.e1 * (x[j] - x[j - 1] * x[j - 1]);
                    g[j + 1 - 1] = 2.e1 * t2;
                    g[j - 1] = -2.e0 * (x[j - 1] * g[j + 1 - 1] + t1);
                    f = f + t1 * t1 + t2 * t2;
                }
                return f;
            }
        };

        double[] x = new double[100];
        for (int j = 1; j <= x.length; j += 2) {
            x[j - 1] = -1.2e0;
            x[j + 1 - 1] = 1.e0;
        }

        double result = Math.min(func, 5, x, 0.0001);
        assertEquals(3.2760183604E-14, result, 1E-15);
    }

    /**
     * Test of min method, of class Math.
     */
    @Test
    public void testMin_4args() {
        System.out.println("BFGS");
        DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {

            @Override
            public double f(double[] x) {
                double f = 0.0;
                for (int j = 1; j <= x.length; j += 2) {
                    double t1 = 1.e0 - x[j - 1];
                    double t2 = 1.e1 * (x[j] - x[j - 1] * x[j - 1]);
                    f = f + t1 * t1 + t2 * t2;
                }
                return f;
            }

            @Override
            public double f(double[] x, double[] g) {
                double f = 0.0;
                for (int j = 1; j <= x.length; j += 2) {
                    double t1 = 1.e0 - x[j - 1];
                    double t2 = 1.e1 * (x[j] - x[j - 1] * x[j - 1]);
                    g[j + 1 - 1] = 2.e1 * t2;
                    g[j - 1] = -2.e0 * (x[j - 1] * g[j + 1 - 1] + t1);
                    f = f + t1 * t1 + t2 * t2;
                }
                return f;
            }
        };

        double[] x = new double[100];
        for (int j = 1; j <= x.length; j += 2) {
            x[j - 1] = -1.2e0;
            x[j + 1 - 1] = 1.e0;
        }

        double result = Math.min(func, x, 0.0001);
        assertEquals(2.95793E-10, result, 1E-15);
    }
}
