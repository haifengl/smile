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
import smile.math.matrix.BandMatrix;
import smile.math.matrix.EigenValueDecomposition;
import smile.math.matrix.LUDecomposition;
import smile.math.matrix.Matrix;
import static org.junit.Assert.*;
import smile.math.matrix.SingularValueDecomposition;
import smile.math.matrix.SparseMatrix;

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
    public void testRowSum() {
        System.out.println("rowSum");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {1.4814300, -0.7852925, 0.4343743};

        double[] result = Math.rowSum(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of rowMean method, of class Math.
     */
    @Test
    public void testRowMean() {
        System.out.println("rowMean");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.4938100, -0.2617642, 0.1447914};

        double[] result = Math.rowMean(A);
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
    public void testColSum() {
        System.out.println("colSum");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.1820294, -0.3697615, 1.6823026};

        double[] result = Math.colSum(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    /**
     * Test of colMean method, of class Math.
     */
    @Test
    public void testColMean() {
        System.out.println("colMean");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.06067647, -0.12325383, 0.56076753};

        double[] result = Math.colMean(A);
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

        assertEquals(2.73861, Math.colSd(data)[0], 1E-5);
        assertEquals(2.73861, Math.colSd(data)[1], 1E-5);
        assertEquals(2.73861, Math.colSd(data)[2], 1E-5);
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
     * Test of norm1 method, of class Math.
     */
    @Test
    public void testNorm1_doubleArrArr() {
        System.out.println("norm1");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(1.6823, Math.norm1(A), 1E-4);
    }

    /**
     * Test of norm2 method, of class Math.
     */
    @Test
    public void testNorm2_doubleArrArr() {
        System.out.println("norm2");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(1, Math.norm2(A), 1E-4);
    }

    /**
     * Test of norm method, of class Math.
     */
    @Test
    public void testNorm_doubleArrArr() {
        System.out.println("norm");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(1, Math.norm(A), 1E-4);
    }

    /**
     * Test of normInf method, of class Math.
     */
    @Test
    public void testNormInf_doubleArrArr() {
        System.out.println("normInf");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(1.7127, Math.normInf(A), 1E-4);
    }

    /**
     * Test of normFro method, of class Math.
     */
    @Test
    public void testNormFro() {
        System.out.println("normFro");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(1.7321, Math.normFro(A), 1E-4);
    }

    /**
     * Test of normalize method, of class StatUtils.
     */
    @Test
    public void testNormalize() {
        System.out.println("normalize");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        Math.normalize(data);
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
     * Test of plusEquals method, of class Math.
     */
    @Test
    public void testAdd_doubleArrArr_doubleArrArr() {
        System.out.println("add");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };
        double[][] C = {
            {1.4102177, 0, 1.4102177},
            {0.1051570, 0, 0.1051570},
            {-0.0151015, 0, -0.0151015}
        };
        Math.plus(A, B);
        assertTrue(Math.equals(A, C));
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
     * Test of minusEquals method, of class Math.
     */
    @Test
    public void testMinusEquals_doubleArrArr_doubleArrArr() {
        System.out.println("minus");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };
        double[][] C = {
            {0.0338183, 0.1424245, -0.0338183},
            {-0.6349342, -1.7808990, 0.6349342},
            {-1.2632161, 0.8989516, 1.2632161}
        };
        Math.minus(A, B);
        assertTrue(Math.equals(A, C, 1E-7));
    }

    /**
     * Test of times method, of class Math.
     */
    @Test
    public void testTimes_doubleArrArr_doubleArrArr() {
        System.out.println("times");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };
        double[][] C = {
            {0.9527204, -0.2973347, 0.06257778},
            {-0.2808735, -0.9403636, -0.19190231},
            {0.1159052, 0.1652528, -0.97941688}
        };
        assertTrue(Math.equals(Math.abmm(A, B), C, 1E-7));
    }

    /**
     * Test of times method, of class Math.
     */
    @Test
    public void testTimes_3args_1() {
        System.out.println("ax");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] B = {0.6881997, 0.3700456, 0.6240573};
        double[] C = {0.9527204, -0.2808735, 0.1159052};
        double[] D = {0, 0, 0};
        Math.ax(A, B, D);
        assertTrue(Math.equals(D, C, 1E-7));
    }

    /**
     * Test of times method, of class Math.
     */
    @Test
    public void testTimes_3args_2() {
        System.out.println("atx");
        double[][] A = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };
        double[] B = {0.7220180, 0.07121225, 0.6881997};
        double[] C = {0.9527204, -0.2973347, 0.06257778};
        double[] D = {0, 0, 0};
        Math.atx(A, B, D);
        assertTrue(Math.equals(D, C, 1E-7));
    }

    /**
     * Test of trace method, of class Math.
     */
    @Test
    public void testTrace() {
        System.out.println("trace");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(0.4556258, Math.trace(A), 1E-7);
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
     * Test of inverse method, of class Math.
     */
    @Test
    public void testInverse() {
        System.out.println("inverse");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[][] B = Math.inverse(A);
        assertTrue(Math.equals(B, Math.transpose(A), 1E-7));
    }

    /**
     * Test of det method, of class Math.
     */
    @Test
    public void testDet() {
        System.out.println("det");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(-1, Math.det(A), 1E-7);
    }

    /**
     * Test of rank method, of class Math.
     */
    @Test
    public void testRank() {
        System.out.println("rank");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(3, Math.rank(A), 1E-7);
    }

    /**
     * Test of eigen method, of class Math.
     */
    @Test
    public void testEigen_doubleArrArr_doubleArr() {
        System.out.println("eigen");
        double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
        };
        double[][] eigenVectors = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };
        double[] eigenValues = {1.7498382, 0.3165784, 0.1335834};

        double[] v = new double[3];
        for (int i = 0; i < v.length; i++) {
            v[i] = 1.0;
        }

        double eigenvalue = Math.eigen(A, v);
        assertEquals(eigenValues[0], eigenvalue, 1E-4);

        double ratio = Math.abs(eigenVectors[0][0] / v[0]);
        for (int i = 1; i < 3; i++) {
            assertEquals(ratio, Math.abs(eigenVectors[i][0] / v[i]), 1E-4);
        }

        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++) {
                A[i][j] = -A[i][j];
            }
        }

        for (int i = 0; i < v.length; i++) {
            v[i] = 1 + Math.random();
        }

        eigenvalue = EigenValueDecomposition.eigen(new Matrix(A), v, 1E-6);
        assertEquals(-eigenValues[0], eigenvalue, 1E-3);

        ratio = Math.abs(eigenVectors[0][0] / v[0]);
        for (int i = 1; i < 3; i++) {
            assertEquals(ratio, Math.abs(eigenVectors[i][0] / v[i]), 1E-3);
        }
    }

    /**
     * Test of eigen method, of class Math.
     */
    @Test
    public void testEigen_doubleArrArr() {
        System.out.println("eigen");
        double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
        };
        double[][] eigenVectors = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };
        double[] eigenValues = {1.7498382, 0.3165784, 0.1335834};
        EigenValueDecomposition result = Math.eigen(A, true);
        assertTrue(Math.equals(eigenValues, result.getEigenValues(), 1E-7));

        assertEquals(eigenVectors.length, result.getEigenVectors().length);
        assertEquals(eigenVectors[0].length, result.getEigenVectors()[0].length);
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(result.getEigenVectors()[i][j]), 1E-7);
            }
        }
    }

    /**
     * Test of eigen method, of class Math.
     */
    @Test
    public void testEigen_doubleArrArr_int() {
        System.out.println("eigen");
        double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
        };
        double[][] eigenVectors = {
            {0.6881997, -0.07121225, 0.7220180},
            {0.3700456, 0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
        };
        double[] eigenValues = {1.7498382, 0.3165784, 0.1335834};
        EigenValueDecomposition result = Math.eigen(A, 3);
        assertTrue(Math.equals(eigenValues, result.getEigenValues(), 1E-7));

        assertEquals(eigenVectors.length, result.getEigenVectors().length);
        assertEquals(eigenVectors[0].length, result.getEigenVectors()[0].length);
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(result.getEigenVectors()[i][j]), 1E-7);
            }
        }
    }

    /**
     * Test of svd method, of class Math.
     */
    @Test
    public void testSvd() {
        System.out.println("svd");
        double[][] A = {
            {1.19720880, -1.8391378, 0.3019585, -1.1165701, -1.7210814, 0.4918882, -0.04247433},
            {0.06605075, 1.0315583, 0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340},
            {-1.02637715, 1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532, 0.17966051},
            {-1.45817729, -0.8942353, 0.3459245, 1.5068363, -2.0180708, -0.3696350, -1.19575563},
            {-0.07318103, -0.2783787, 1.2237598, 0.1995332, 0.2545336, -0.1392502, -1.88207227},
            {0.88248425, -0.9360321, 0.1393172, 0.1393281, -0.3277873, -0.5553013, 1.63805985},
            {0.12641406, -0.8710055, -0.2712301, 0.2296515, 1.1781535, -0.2158704, -0.27529472}
        };

        double[] s = {3.8589375, 3.4396766, 2.6487176, 2.2317399, 1.5165054, 0.8109055, 0.2706515};

        double[][] U = {
            {-0.3082776, 0.77676231, 0.01330514, 0.23231424, -0.47682758, 0.13927109, 0.02640713},
            {-0.4013477, -0.09112050, 0.48754440, 0.47371793, 0.40636608, 0.24600706, -0.37796295},
            {0.0599719, -0.31406586, 0.45428229, -0.08071283, -0.38432597, 0.57320261, 0.45673993},
            {-0.7694214, -0.12681435, -0.05536793, -0.62189972, -0.02075522, -0.01724911, -0.03681864},
            {-0.3319069, -0.17984404, -0.54466777, 0.45335157, 0.19377726, 0.12333423, 0.55003852},
            {0.1259351, 0.49087824, 0.16349687, -0.32080176, 0.64828744, 0.20643772, 0.38812467},
            {0.1491884, 0.01768604, -0.47884363, -0.14108924, 0.03922507, 0.73034065, -0.43965505}
        };

        double[][] V = {
            {-0.2122609, -0.54650056, 0.08071332, -0.43239135, -0.2925067, 0.1414550, 0.59769207},
            {-0.1943605, 0.63132116, -0.54059857, -0.37089970, -0.1363031, 0.2892641, 0.17774114},
            {0.3031265, -0.06182488, 0.18579097, -0.38606409, -0.5364911, 0.2983466, -0.58642548},
            {0.1844063, 0.24425278, 0.25923756, 0.59043765, -0.4435443, 0.3959057, 0.37019098},
            {-0.7164205, 0.30694911, 0.58264743, -0.07458095, -0.1142140, -0.1311972, -0.13124764},
            {-0.1103067, -0.10633600, 0.18257905, -0.03638501, 0.5722925, 0.7784398, -0.09153611},
            {-0.5156083, -0.36573746, -0.47613340, 0.41342817, -0.2659765, 0.1654796, -0.32346758}
        };

        SingularValueDecomposition result = Math.svd(A);
        assertTrue(Math.equals(s, result.getSingularValues(), 1E-7));

        assertEquals(U.length, result.getU().length);
        assertEquals(U[0].length, result.getU()[0].length);
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(result.getU()[i][j]), 1E-7);
            }
        }

        assertEquals(V.length, result.getV().length);
        assertEquals(V[0].length, result.getV()[0].length);
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(result.getV()[i][j]), 1E-7);
            }
        }
    }

    /**
     * Test of solve method, of class Math.
     */
    @Test
    public void testSolve_doubleArrArr_doubleArr() {
        System.out.println("solve");
        double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
        };
        double[] B = {0.5, 0.5, 0.5};
        double[] X = {-0.2027027, 0.8783784, 0.4729730};
        double[] x = Math.solve(A, B);
        assertEquals(X.length, x.length);
        for (int i = 0; i < X.length; i++) {
            assertEquals(X[i], x[i], 1E-7);
        }
    }

    /**
     * Test of solve method, of class Math.
     */
    @Test
    public void testSolve_doubleArrArr_doubleArrArr() {
        System.out.println("solve");
        double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
        };
        double[][] B2 = {
            {0.5, 0.2},
            {0.5, 0.8},
            {0.5, 0.3}
        };
        double[][] X2 = {
            {-0.2027027, -1.2837838},
            {0.8783784, 2.2297297},
            {0.4729730, 0.6621622}
        };
        double[][] x = Math.solve(A, B2);
        assertEquals(X2.length, x.length);
        assertEquals(X2[0].length, x[0].length);
        for (int i = 0; i < X2.length; i++) {
            for (int j = 0; j < X2[i].length; j++) {
                assertEquals(X2[i][j], x[i][j], 1E-7);
            }
        }
    }

    /**
     * Test of solve method, of class Math.
     */
    @Test
    public void testTridiagonalSolve() {
        System.out.println("tridiagonal solve");
        double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
        };
        double[] r = {0.5, 0.5, 0.5};

        LUDecomposition lu = new LUDecomposition(A);
        double[] x = new double[r.length];
        lu.solve(r, x);

        double[] a = {0.0, 0.4, 0.3};
        double[] b = {0.9, 0.5, 0.8};
        double[] c = {0.4, 0.3, 0.0};
        double[] result = Math.solve(a, b, c, r);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-7);
        }
    }

    /**
     * Test of solve method, of class Math.
     */
    @Test
    public void testSolve() {
        System.out.println("solve");
        double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
        };
        double[] b = {0.5, 0.5, 0.5};

        LUDecomposition lu = new LUDecomposition(A);
        double[] x = new double[b.length];
        lu.solve(b, x);

        double[] result = new double[3];
        Math.solve(new Matrix(A), b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-7);
        }

        BandMatrix band = new BandMatrix(3, 1, 1);
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++) {
                if (A[i][j] != 0.0) {
                    band.set(i, j, A[i][j]);
                }
            }
        }

        result = new double[3];
        Math.solve(band, b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-7);
        }

        int[] rowIndex = {0, 1, 0, 1, 2, 1, 2};
        int[] colIndex = {0, 2, 5, 7};
        double[] val = {0.9, 0.4, 0.4, 0.5, 0.3, 0.3, 0.8};
        SparseMatrix sparse = new SparseMatrix(3, 3, val, rowIndex, colIndex);

        result = new double[3];
        Math.solve(sparse, b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-7);
        }
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
