/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.matrix;

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
public class ARPACKTest {

    public ARPACKTest() {
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
    public void testSparse500() {
        System.out.println("500 x 500 sparse matrix");
        double[][] A = new double[500][500];
        A[0][0] = A[1][1] = A[2][2] = A[3][3] = 2.0;
        for (int i = 4; i < 500; i++) {
            A[i][i] = (500 - i) / 500.0;
        }
        
        SparseMatrix a = new SparseMatrix(A, 1E-7);
        Matrix.EVD eig = ARPACK.syev(a, ARPACK.SymmOption.LA, 6);
        assertEquals(2.0, eig.wr[0], 1E-4);
        assertEquals(2.0, eig.wr[1], 1E-4);
        assertEquals(2.0, eig.wr[2], 1E-4);
        assertEquals(2.0, eig.wr[3], 1E-4);
        assertEquals(0.992, eig.wr[4], 1E-4);
        assertEquals(0.990, eig.wr[5], 1E-4);

        // non-symmetric
        eig = ARPACK.eigen(a, ARPACK.AsymmOption.LM, 6);
        assertEquals(2.0, eig.wr[0], 1E-4);
        assertEquals(2.0, eig.wr[1], 1E-4);
        assertEquals(2.0, eig.wr[2], 1E-4);
        assertEquals(2.0, eig.wr[3], 1E-4);
        assertEquals(0.992, eig.wr[4], 1E-4);
        assertEquals(0.990, eig.wr[5], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }
    }

    @Test
    public void testSVD() {
        System.out.println("SVD sparse matrix");
        double[][] A = {
                {1, 0, 0, 1, 0, 0, 0, 0, 0},
                {1, 0, 1, 0, 0, 0, 0, 0, 0},
                {1, 1, 0, 0, 0, 0, 0, 0, 0},
                {0, 1, 1, 0, 1, 0, 0, 0, 0},
                {0, 1, 1, 2, 0, 0, 0, 0, 0},
                {0, 1, 0, 0, 1, 0, 0, 0, 0},
                {0, 1, 0, 0, 1, 0, 0, 0, 0},
                {0, 0, 1, 1, 0, 0, 0, 0, 0},
                {0, 1, 0, 0, 0, 0, 0, 0, 1},
                {0, 0, 0, 0, 0, 1, 1, 1, 0},
                {0, 0, 0, 0, 0, 0, 1, 1, 1},
                {0, 0, 0, 0, 0, 0, 0, 1, 1}
        };

        double[] s = {3.34088, 2.5417, 2.35394, 1.64453, 1.50483, 1.30638, 0.845903, 0.560134, 0.363677};

        double[][] Vt = {
                { 0.197393,   0.60599,   0.462918,   0.542114,   0.279469,  0.00381521,  0.0146315, 0.0241368,   0.0819574},
                { 0.0559135, -0.165593,  0.127312,   0.231755,  -0.106775,  -0.192848,  -0.437875, -0.615122,   -0.529937},
                {-0.11027,    0.497326, -0.207606,  -0.569921,   0.50545,   -0.0981842, -0.192956, -0.252904,   -0.0792731},
                {-0.949785,  -0.0286489, 0.0416092,  0.267714,   0.150035,   0.0150815,  0.0155072, 0.010199,   -0.0245549},
                {-0.0456786,  0.206327, -0.378336,   0.205605,  -0.327194,  -0.394841,  -0.349485, -0.149798,    0.601993},
                {-0.0765936, -0.256475,  0.7244,    -0.368861,   0.034813,  -0.300161,  -0.212201,  9.74342e-05, 0.362219},
                {-0.177318,   0.432984,  0.23689,   -0.2648,    -0.672304,   0.34084,    0.152195, -0.249146,   -0.0380342},
                {-0.0143933,  0.0493053, 0.0088255, -0.0194669, -0.0583496,  0.454477,  -0.761527,  0.449643,   -0.0696375},
                {-0.0636923,  0.242783,  0.0240769, -0.0842069, -0.262376,  -0.619847,  0.0179752,  0.51989,    -0.453507}
        };

        double[][] Ut = {
                { 0.221351,   0.197645,   0.24047,   0.403599,    0.644481,  0.265037,   0.265037,   0.300828,  0.205918,  0.0127462, 0.0361358,    0.0317563},
                { 0.11318,    0.0720878, -0.043152, -0.0570703,   0.167301, -0.10716,   -0.10716,    0.14127,  -0.273647, -0.490162, -0.622785,    -0.450509},
                {-0.288958,  -0.13504,    0.164429,  0.337804,   -0.361148,  0.425998,   0.425998,  -0.330308,  0.177597, -0.23112,  -0.223086,    -0.141115},
                {-0.414751,  -0.55224,   -0.594962,  0.0991137,   0.333462,  0.0738122,  0.0738122,  0.188092, -0.0323519, 0.024802,  0.000700072, -0.00872947},
                { 0.106275,  -0.281769,   0.106755, -0.331734,    0.158955, -0.0803194, -0.0803194, -0.114785,  0.53715,  -0.59417,   0.0682529,    0.300495},
                {-0.340983,   0.495878,  -0.254955,  0.384832,   -0.206523, -0.169676,  -0.169676,   0.272155,  0.080944, -0.392125,  0.114909,     0.277343},
                {-0.522658,   0.0704234,  0.30224,  -0.00287218,  0.165829, -0.282916,  -0.282916,  -0.0329941, 0.466898,  0.288317, -0.159575,    -0.339495},
                {-0.0604501, -0.00994004, 0.062328, -0.000390504, 0.034272, -0.0161465, -0.0161465, -0.018998, -0.0362988, 0.254568, -0.681125,     0.6784180},
                {-0.406678,  -0.10893,    0.492444,  0.0123293,   0.270696, -0.0538747, -0.0538747, -0.165339, -0.579426, -0.225424,  0.231961,     0.182535}
        };

        int m = A.length;
        int n = A[0].length;
        int k = 3;

        // m > n
        SparseMatrix a = new SparseMatrix(A, 1E-8);
        Matrix.SVD svd = ARPACK.svd(a, k);
        for (int i = 0; i < k; i++) {
            assertEquals(s[i], svd.s[i], 1E-5);
        }

        assertEquals(m, svd.U.nrow());
        assertEquals(k, svd.U.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < m; i++) {
                assertEquals(Math.abs(Ut[j][i]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(n, svd.V.nrow());
        assertEquals(k, svd.V.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < n; i++) {
                assertEquals(Math.abs(Vt[j][i]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }

        // m < n
        svd = ARPACK.svd(a.transpose(), k);
        for (int i = 0; i < k; i++) {
            assertEquals(s[i], svd.s[i], 1E-5);
        }

        assertEquals(n, svd.U.nrow());
        assertEquals(k, svd.U.ncol());
        System.out.println(svd.U);
        System.out.println(svd.V);
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < n; i++) {
                assertEquals(Math.abs(Vt[j][i]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(m, svd.V.nrow());
        assertEquals(k, svd.V.ncol());
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < m; i++) {
                assertEquals(Math.abs(Ut[j][i]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }
    }
}