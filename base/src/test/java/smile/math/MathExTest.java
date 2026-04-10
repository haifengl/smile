/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.math;

import smile.tensor.Matrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class MathExTest {

    public MathExTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testIsZero() {
        System.out.println("isZero");
        assertTrue(MathEx.isZero(0.0));
        assertTrue(MathEx.isZero(Double.MIN_VALUE));
        assertTrue(MathEx.isZero(Double.MIN_NORMAL));
        assertFalse(MathEx.isZero(MathEx.EPSILON));
    }

    @Test
    public void testIsPower2() {
        System.out.println("isPower2");
        assertFalse(MathEx.isPower2(-1));
        assertFalse(MathEx.isPower2(0));
        assertTrue(MathEx.isPower2(1));
        assertTrue(MathEx.isPower2(2));
        assertFalse(MathEx.isPower2(3));
        assertTrue(MathEx.isPower2(4));
        assertTrue(MathEx.isPower2(8));
        assertTrue(MathEx.isPower2(16));
        assertTrue(MathEx.isPower2(32));
        assertTrue(MathEx.isPower2(64));
        assertTrue(MathEx.isPower2(128));
        assertTrue(MathEx.isPower2(256));
        assertTrue(MathEx.isPower2(512));
        assertTrue(MathEx.isPower2(1024));
        assertTrue(MathEx.isPower2(65536));
        assertTrue(MathEx.isPower2(131072));
    }

    @Test
    public void testLog2() {
        System.out.println("log2");
        assertEquals(0, MathEx.log2(1), 1E-6);
        assertEquals(1, MathEx.log2(2), 1E-6);
        assertEquals(1.584963, MathEx.log2(3), 1E-6);
        assertEquals(2, MathEx.log2(4), 1E-6);
    }

    @Test
    public void testPow2() {
        System.out.println("pow2");
        assertEquals(0, MathEx.pow2(0), 1E-10);
        assertEquals(1, MathEx.pow2(1), 1E-10);
        assertEquals(4, MathEx.pow2(2), 1E-10);
        assertEquals(9, MathEx.pow2(3), 1E-10);
    }

    @Test
    public void testFactorial() {
        System.out.println("factorial");
        assertEquals(1.0, MathEx.factorial(0), 1E-7);
        assertEquals(1.0, MathEx.factorial(1), 1E-7);
        assertEquals(2.0, MathEx.factorial(2), 1E-7);
        assertEquals(6.0, MathEx.factorial(3), 1E-7);
        assertEquals(24.0, MathEx.factorial(4), 1E-7);
    }

    @Test
    public void testLogFactorial() {
        System.out.println("logFactorial");
        assertEquals(0.0, MathEx.lfactorial(0), 1E-7);
        assertEquals(0.0, MathEx.lfactorial(1), 1E-7);
        assertEquals(Math.log(2.0), MathEx.lfactorial(2), 1E-7);
        assertEquals(Math.log(6.0), MathEx.lfactorial(3), 1E-7);
        assertEquals(Math.log(24.0), MathEx.lfactorial(4), 1E-7);
    }

    @Test
    public void testChoose() {
        System.out.println("choose");
        assertEquals(1.0, MathEx.choose(10, 0), 1E-7);
        assertEquals(10.0, MathEx.choose(10, 1), 1E-7);
        assertEquals(45.0, MathEx.choose(10, 2), 1E-7);
        assertEquals(120.0, MathEx.choose(10, 3), 1E-7);
        assertEquals(210.0, MathEx.choose(10, 4), 1E-7);
    }

    @Test
    public void testLogChoose() {
        System.out.println("logChoose");
        assertEquals(0.0, MathEx.lchoose(10, 0), 1E-6);
        assertEquals(2.302585, MathEx.lchoose(10, 1), 1E-6);
        assertEquals(3.806662, MathEx.lchoose(10, 2), 1E-6);
        assertEquals(4.787492, MathEx.lchoose(10, 3), 1E-6);
        assertEquals(5.347108, MathEx.lchoose(10, 4), 1E-6);
    }

    @Test
    public void testRandom() {
        System.out.println("random");
        double[] prob = {0.473646292, 0.206116725, 0.009308497, 0.227844687, 0.083083799};
        int[] sample = MathEx.random(prob, 300);
        double[][] hist = Histogram.of(sample, 5);
        double[] p = new double[5];
        for (int i = 0; i < 5; i++) {
            p[i] = hist[2][i] / 300.0;
        }
        assertTrue(MathEx.KullbackLeiblerDivergence(prob, p) < 0.05);
    }

    @Test
    public void testRandom2() {
        System.out.println("random");
        double[] prob = {0.473646292, 0.206116725, 0.009308497, 0.227844687, 0.083083799};
        int[] sample = new int[300];
        for (int i = 0; i < 300; i++) {
            sample[i] = MathEx.random(prob);
        }

        double[][] hist = Histogram.of(sample, 5);
        double[] p = new double[5];
        for (int i = 0; i < 5; i++) {
            p[i] = hist[2][i] / 300.0;
        }

        assertTrue(MathEx.KullbackLeiblerDivergence(prob, p) < 0.05);
    }

    @Test
    public void testMin_3args() {
        System.out.println("min");
        int a = -1;
        int b = 0;
        int c = 1;
        int expResult = -1;
        int result = MathEx.min(a, b, c);
        assertEquals(expResult, result);
    }

    @Test
    public void testMax_3args() {
        System.out.println("max");
        int a = -1;
        int b = 0;
        int c = 1;
        int expResult = 1;
        int result = MathEx.max(a, b, c);
        assertEquals(expResult, result);
    }

    @Test
    public void testMin_doubleArr() {
        System.out.println("min");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(-2.196822, MathEx.min(x), 1E-7);
    }

    @Test
    public void testMax_doubleArr() {
        System.out.println("max");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(1.0567679, MathEx.max(x), 1E-7);
    }

    @Test
    public void testMin_doubleArrArr() {
        System.out.println("min");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(-0.89044952, MathEx.min(A), 1E-7);
    }

    @Test
    public void testMax_doubleArrArr() {
        System.out.println("max");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        assertEquals(0.7220180, MathEx.max(A), 1E-7);
    }

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
        assertTrue(MathEx.equals(MathEx.transpose(A), B, 1E-7));
    }

    @Test
    public void testRowMin() {
        System.out.println("rowMin");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.07121225, -0.89044952, -0.6391588};

        double[] result = MathEx.rowMin(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testRowMax() {
        System.out.println("rowMax");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.7220180, 0.3700456, 0.6240573};

        double[] result = MathEx.rowMax(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testRowSums() {
        System.out.println("rowSums");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {1.4814300, -0.7852925, 0.4343743};

        double[] result = MathEx.rowSums(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testRowMeans() {
        System.out.println("rowMeans");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.4938100, -0.2617642, 0.1447914};

        double[] result = MathEx.rowMeans(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testColMin() {
        System.out.println("colMin");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.6391588, -0.89044952, 0.3700456};

        double[] result = MathEx.colMin(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testColMax() {
        System.out.println("colMax");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {0.7220180, 0.44947578, 0.6881997};

        double[] result = MathEx.colMax(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testColSums() {
        System.out.println("colSums");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.1820294, -0.3697615, 1.6823026};

        double[] result = MathEx.colSums(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testColMeans() {
        System.out.println("colMeans");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };
        double[] r = {-0.06067647, -0.12325383, 0.56076753};

        double[] result = MathEx.colMeans(A);
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testSum_doubleArr() {
        System.out.println("sum");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        assertEquals(45, MathEx.sum(data), 1E-6);
    }

    /**
     * Test of mean method, of class Math.
     */
    @Test
    public void testMean_doubleArr() {
        System.out.println("mean");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        assertEquals(5, MathEx.mean(data), 1E-6);
    }

    @Test
    public void testVar_doubleArr() {
        System.out.println("var");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        assertEquals(7.5, MathEx.var(data), 1E-6);
    }

    @Test
    public void testStdev() {
        System.out.println("stdev");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        assertEquals(2.73861, MathEx.stdev(data), 1E-5);
    }

    @Test
    public void testColStdevs() {
        System.out.println("colStdevs");
        double[][] data = {
            {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0},
            {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0},
            {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0},
        };

        data = MathEx.transpose(data);

        assertEquals(2.73861, MathEx.colStdevs(data)[0], 1E-5);
        assertEquals(2.73861, MathEx.colStdevs(data)[1], 1E-5);
        assertEquals(2.73861, MathEx.colStdevs(data)[2], 1E-5);
    }

    @Test
    public void testMad() {
        System.out.println("mad");
        double[] data = {1, 1, 2, 2, 4, 6, 9};
        assertEquals(1.0, MathEx.mad(data), 1E-5);
    }

    @Test
    public void testPdist() {
        double[][] data = {
                {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515},
                {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300},
                {-3.9749544, -1.6219752, 0.9094410, 0.1106760, -0.0071785}
        };

        Matrix d = MathEx.pdist(data);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(MathEx.distance(data[i], data[j]), d.get(i, j), 1E-10);
            }
        }
    }

    @Test
    public void testPdistSquaredHalf() {
        double[][] data = {
                {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515},
                {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300},
                {-3.9749544, -1.6219752, 0.9094410, 0.1106760, -0.0071785}
        };

        double[][] d = new double[3][];
        for (int i = 0; i < 3; i++) {
            d[i] = new double[i];
        }

        MathEx.pdist(data, d, MathEx::squaredDistance);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < i; j++) {
                assertEquals(MathEx.squaredDistance(data[i], data[j]), d[i][j], 1E-10);
            }
        }
    }

    @Test
    public void testDistance_doubleArr_doubleArr() {
        System.out.println("distance");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(2.422302, MathEx.distance(x, y), 1E-6);
    }

    @Test
    public void testSquaredDistance_doubleArr_doubleArr() {
        System.out.println("squaredDistance");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(5.867547, MathEx.squaredDistance(x, y), 1E-6);
    }

    @Test
    public void testDot_doubleArr_doubleArr() {
        System.out.println("dot");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(3.350726, MathEx.dot(x, y), 1E-6);
    }

    @Test
    public void testCov_doubleArr_doubleArr() {
        System.out.println("cov");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.5894983, MathEx.cov(x, y), 1E-7);
    }

    @Test
    public void testCor_doubleArr_doubleArr() {
        System.out.println("cor");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.4686847, MathEx.cor(x, y), 1E-7);
    }

    @Test
    public void testSpearman_doubleArr_doubleArr() {
        System.out.println("spearman");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.3, MathEx.spearman(x, y), 1E-7);
    }

    @Test
    public void testKendall_doubleArr_doubleArr() {
        System.out.println("kendall");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        assertEquals(0.2, MathEx.kendall(x, y), 1E-7);
    }

    @Test
    public void testNorm1_doubleArr() {
        System.out.println("norm1");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(4.638106, MathEx.norm1(x), 1E-6);
    }

    @Test
    public void testNorm2_doubleArr() {
        System.out.println("norm2");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(2.647086, MathEx.norm2(x), 1E-6);
    }

    @Test
    public void testNormInf_doubleArr() {
        System.out.println("normInf");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(2.196822, MathEx.normInf(x), 1E-6);
    }

    @Test
    public void testNorm_doubleArr() {
        System.out.println("norm");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        assertEquals(2.647086, MathEx.norm(x), 1E-6);
    }

    @Test
    public void testStandardize() {
        System.out.println("standardize");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        MathEx.standardize(data);
        assertEquals(0, MathEx.mean(data), 1E-7);
        assertEquals(1, MathEx.stdev(data), 1E-7);
    }

    @Test
    public void testUnitize() {
        System.out.println("unitize");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        MathEx.unitize(data);
        assertEquals(1, MathEx.norm(data), 1E-7);
    }

    @Test
    public void testUnitize1() {
        System.out.println("unitize1");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        MathEx.unitize1(data);
        assertEquals(1, MathEx.norm1(data), 1E-7);
    }

    @Test
    public void testUnitize2() {
        System.out.println("unitize2");
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
        MathEx.unitize2(data);
        assertEquals(1, MathEx.norm2(data), 1E-7);
    }

    @Test
    public void testClone() {
        System.out.println("clone");
        double[][] A = {
            {0.7220180, 0.07121225, 0.6881997},
            {-0.2648886, -0.89044952, 0.3700456},
            {-0.6391588, 0.44947578, 0.6240573}
        };

        double[][] B = MathEx.clone(A);

        assertTrue(MathEx.equals(A, B));
        assertNotSame(A, B);
        for (int i = 0; i < A.length; i++) {
            assertNotSame(A[i], B[i]);
        }
    }

    @Test
    public void testAdd_doubleArr_doubleArr() {
        System.out.println("add");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        double[] z = {-3.9749544, -1.6219752, 0.9094410, 0.1106760, -0.0071785};
        MathEx.add(x, y);
        assertTrue(MathEx.equals(x, z));
    }

    @Test
    public void testMinus_doubleArr_doubleArr() {
        System.out.println("sub");
        double[] x = {-2.1968219, -0.9559913, -0.0431738, 1.0567679, 0.3853515};
        double[] y = {-1.7781325, -0.6659839, 0.9526148, -0.9460919, -0.3925300};
        double[] z = {-0.4186894, -0.2900074, -0.9957886, 2.0028598, 0.7778815};
        MathEx.sub(x, y);
        assertTrue(MathEx.equals(x, z));
    }

    @Test
    public void testClamp() {
        System.out.println("clamp");
        assertEquals(5, MathEx.clamp(5, 0, 10));
        assertEquals(0, MathEx.clamp(-1, 0, 10));
        assertEquals(10, MathEx.clamp(15, 0, 10));
        assertEquals(0.5, MathEx.clamp(0.5, 0.0, 1.0), 1E-15);
        assertEquals(0.0, MathEx.clamp(-0.5, 0.0, 1.0), 1E-15);
        assertEquals(1.0, MathEx.clamp(1.5, 0.0, 1.0), 1E-15);
    }

    @Test
    public void testRandnScalar() {
        System.out.println("randn()");
        MathEx.setSeed(19650218);
        // Just verify it generates finite numbers
        for (int i = 0; i < 100; i++) {
            assertTrue(Double.isFinite(MathEx.randn()));
        }
    }

    @Test
    public void testRandnVector() {
        System.out.println("randn(n)");
        MathEx.setSeed(19650218);
        double[] x = MathEx.randn(1000);
        assertEquals(1000, x.length);
        // Mean should be close to 0, variance close to 1
        assertEquals(0.0, MathEx.mean(x), 0.1);
        assertEquals(1.0, MathEx.var(x), 0.1);
    }

    @Test
    public void testVarNumericallyStable() {
        System.out.println("var numerically stable (Welford)");
        // A dataset with large mean but small variance — classic catastrophic cancellation case
        double[] x = new double[1000];
        for (int i = 0; i < x.length; i++) {
            x[i] = 1e8 + (i - 500);  // mean = 1e8, exact var = sum((i-500)^2/999)
        }
        double variance = MathEx.var(x);
        // Exact variance = sum of (i - 500)^2 for i=0..999 divided by 999
        // = sum of k^2 for k=-500..499 / 999  (approx 500^2/3 = ~83416)
        assertEquals(83416.666, variance, 1.0);
    }

    @Test
    public void testSumIntNoOverflow() {
        System.out.println("sum(int[]) no integer overflow");
        int[] x = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE};
        long result = MathEx.sum(x);
        assertEquals((long) Integer.MAX_VALUE * 2, result);
    }

    @Test
    public void testAxpy() {
        System.out.println("axpy");
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {4.0, 5.0, 6.0};
        MathEx.axpy(2.0, x, y);
        assertEquals(6.0, y[0], 1E-15);
        assertEquals(9.0, y[1], 1E-15);
        assertEquals(12.0, y[2], 1E-15);
    }

    @Test
    public void testPow() {
        System.out.println("pow");
        double[] x = {1.0, 2.0, 3.0};
        double[] y = MathEx.pow(x, 2.0);
        assertEquals(1.0, y[0], 1E-15);
        assertEquals(4.0, y[1], 1E-15);
        assertEquals(9.0, y[2], 1E-15);
    }

    @Test
    public void testContains() {
        System.out.println("contains");
        // Square polygon (0,0)-(1,0)-(1,1)-(0,1)
        double[][] polygon = {{0,0},{1,0},{1,1},{0,1}};
        assertTrue(MathEx.contains(polygon, new double[]{0.5, 0.5}));
        assertFalse(MathEx.contains(polygon, new double[]{2.0, 0.5}));
    }

    @Test
    public void testOmit() {
        System.out.println("omit");
        int[] a = {1, 2, 3, 2, 4};
        int[] b = MathEx.omit(a, 2);
        assertEquals(3, b.length);
        assertEquals(1, b[0]);
        assertEquals(3, b[1]);
        assertEquals(4, b[2]);
    }

    @Test
    public void testOmitNaN() {
        System.out.println("omitNaN");
        double[] a = {1.0, Double.NaN, 3.0, Double.NaN, 5.0};
        double[] b = MathEx.omitNaN(a);
        assertEquals(3, b.length);
        assertEquals(1.0, b[0], 1E-15);
        assertEquals(3.0, b[1], 1E-15);
        assertEquals(5.0, b[2], 1E-15);
    }

    @Test
    public void testMode() {
        System.out.println("mode");
        int[] a = {1, 2, 2, 3, 3, 3, 4};
        assertEquals(3, MathEx.mode(a));
    }

    @Test
    public void testEntropy() {
        System.out.println("entropy");
        // Uniform distribution: H = log(n)
        double[] p = {0.25, 0.25, 0.25, 0.25};
        assertEquals(Math.log(4), MathEx.entropy(p), 1E-10);
        // Degenerate: H = 0
        double[] q = {1.0, 0.0, 0.0, 0.0};
        assertEquals(0.0, MathEx.entropy(q), 1E-10);
    }

    @Test
    public void testJensenShannonDivergence() {
        System.out.println("JensenShannonDivergence");
        double[] p = {0.5, 0.5};
        double[] q = {0.5, 0.5};
        // Same distributions: JSD = 0
        assertEquals(0.0, MathEx.JensenShannonDivergence(p, q), 1E-10);
    }

    @Test
    public void testSolveTridiagonal() {
        System.out.println("solve (tridiagonal)");
        // Simple 3x3: b=[4,4,4], a=[0,1,1], c=[1,1,0], r=[1,2,3]
        double[] a = {0, 1, 1};
        double[] b = {4, 4, 4};
        double[] c = {1, 1, 0};
        double[] r = {1, 2, 3};
        double[] x = MathEx.solve(a, b, c, r);
        // Verify: b*x[0] + c*x[1] = r[0]
        assertEquals(r[0], b[0]*x[0] + c[0]*x[1], 1E-10);
        assertEquals(r[1], a[1]*x[0] + b[1]*x[1] + c[1]*x[2], 1E-10);
        assertEquals(r[2], a[2]*x[1] + b[2]*x[2], 1E-10);
    }

    @Test
    public void testUnique() {
        System.out.println("unique");
        int[] a = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3};
        int[] u = MathEx.unique(a);
        assertEquals(7, u.length);
    }

    @Test
    public void testWhichMinMax() {
        System.out.println("whichMin/whichMax");
        double[] x = {3.0, 1.0, 4.0, 1.5, 5.9};
        assertEquals(1, MathEx.whichMin(x));
        assertEquals(4, MathEx.whichMax(x));
    }

    @Test
    public void testReverse() {
        System.out.println("reverse");
        double[] x = {1.0, 2.0, 3.0, 4.0, 5.0};
        MathEx.reverse(x);
        assertEquals(5.0, x[0], 1E-15);
        assertEquals(4.0, x[1], 1E-15);
        assertEquals(3.0, x[2], 1E-15);
        assertEquals(2.0, x[3], 1E-15);
        assertEquals(1.0, x[4], 1E-15);
    }

    @Test
    public void testRound() {
        System.out.println("round");
        assertEquals(3.14, MathEx.round(Math.PI, 2), 1E-10);
        assertEquals(3.142, MathEx.round(Math.PI, 3), 1E-10);
        assertEquals(300.0, MathEx.round(314.15, -2), 1E-10);
    }

    @Test
    public void testCosine() {
        System.out.println("cosine");
        double[] x = {1.0, 0.0, 0.0};
        double[] y = {0.0, 1.0, 0.0};
        assertEquals(0.0, MathEx.cosine(x, y), 1E-15);
        assertEquals(1.0, MathEx.cosine(x, x), 1E-15);
    }

    @Test
    public void testLog1pe() {
        System.out.println("log1pe");
        // log(1 + exp(0)) = log(2)
        assertEquals(Math.log(2), MathEx.log1pe(0.0), 1E-10);
        // Large positive: log1pe(x) ≈ x
        assertEquals(100.0, MathEx.log1pe(100.0), 1E-6);
        // Large negative: log1pe(x) ≈ exp(x)
        assertEquals(Math.exp(-50.0), MathEx.log1pe(-50.0), 1E-20);
        // Moderate values
        assertEquals(Math.log1p(Math.exp(5.0)), MathEx.log1pe(5.0), 1E-10);
    }

    @Test
    public void testLog() {
        System.out.println("log (without underflow)");
        // Normal case
        assertEquals(Math.log(1.0), MathEx.log(1.0), 1E-15);
        assertEquals(Math.log(Math.E), MathEx.log(Math.E), 1E-15);
        // Underflow protection
        assertEquals(-690.7755, MathEx.log(0.0), 1E-4);
        assertEquals(-690.7755, MathEx.log(1E-301), 1E-4);
    }

    @Test
    public void testSigmoid() {
        System.out.println("sigmoid");
        assertEquals(0.5, MathEx.sigmoid(0.0), 1E-15);
        assertEquals(1.0, MathEx.sigmoid(100.0), 1E-6);
        assertEquals(0.0, MathEx.sigmoid(-100.0), 1E-6);
        // sigmoid(-x) = 1 - sigmoid(x)
        for (double x : new double[]{-5.0, -1.0, 0.5, 2.0, 10.0}) {
            assertEquals(1.0, MathEx.sigmoid(x) + MathEx.sigmoid(-x), 1E-15);
        }
    }

    @Test
    public void testIsProbablePrime() {
        System.out.println("isProbablePrime");
        // Known primes
        assertTrue(MathEx.isProbablePrime(2, 10));
        assertTrue(MathEx.isProbablePrime(3, 10));
        assertTrue(MathEx.isProbablePrime(7, 10));
        assertTrue(MathEx.isProbablePrime(97, 10));
        assertTrue(MathEx.isProbablePrime(7919, 10));
        // Known composites
        assertFalse(MathEx.isProbablePrime(4, 10));
        assertFalse(MathEx.isProbablePrime(100, 10));
        assertFalse(MathEx.isProbablePrime(999, 10));
    }
}
