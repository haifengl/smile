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

import java.util.Arrays;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import smile.math.blas.Layout;
import smile.math.blas.UPLO;
import static smile.math.blas.Transpose.*;
import static org.junit.Assert.*;

/**
 * Test Matrix with OpenBLAS.
 *
 * @author Haifeng Li
 */
public class MatrixTest {

    double[][] A = {
            {0.9000, 0.4000, 0.0000f},
            {0.4000, 0.5000, 0.3000f},
            {0.0000, 0.3000, 0.8000f}
    };
    double[] b = {0.5, 0.5, 0.5f};
    double[][] C = {
            {0.97, 0.56, 0.12f},
            {0.56, 0.50, 0.39f},
            {0.12, 0.39, 0.73f}
    };

    Matrix matrix = Matrix.of(A);

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
    public void testNrows() {
        System.out.println("nrow");
        assertEquals(3, matrix.nrow());
    }

    @Test
    public void testNcols() {
        System.out.println("ncol");
        assertEquals(3, matrix.ncol());
    }

    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(0.9, matrix.get(0, 0), 1E-7);
        assertEquals(0.8, matrix.get(2, 2), 1E-7);
        assertEquals(0.5, matrix.get(1, 1), 1E-7);
        assertEquals(0.0, matrix.get(2, 0), 1E-7);
        assertEquals(0.0, matrix.get(0, 2), 1E-7);
        assertEquals(0.4, matrix.get(0, 1), 1E-7);
    }

    @Test
    public void testColMeans() {
        System.out.println("colMeans");
        double[][] A = {
                { 0.7220180,  0.07121225, 0.6881997f},
                {-0.2648886, -0.89044952, 0.3700456f},
                {-0.6391588,  0.44947578, 0.6240573f}
        };
        double[] r = {-0.06067647, -0.12325383, 0.56076753f};

        double[] result = Matrix.of(A).colMeans();
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testRowMeans() {
        System.out.println("rowMeans");
        double[][] A = {
                { 0.7220180,  0.07121225, 0.6881997f},
                {-0.2648886, -0.89044952, 0.3700456f},
                {-0.6391588,  0.44947578, 0.6240573f}
        };
        double[] r = {0.4938100, -0.2617642, 0.1447914f};

        double[] result = Matrix.of(A).rowMeans();
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7);
        }
    }

    @Test
    public void testTranspose() {
        Matrix t = matrix.transpose();
        assertEquals(Layout.COL_MAJOR, matrix.layout());
        assertEquals(Layout.ROW_MAJOR, t.layout());
        assertEquals(3, t.nrow());
        assertEquals(3, t.ncol());

        assertEquals(0.9, matrix.get(0, 0), 1E-7);
        assertEquals(0.8, matrix.get(2, 2), 1E-7);
        assertEquals(0.5, matrix.get(1, 1), 1E-7);
        assertEquals(0.0, matrix.get(0, 2), 1E-7);
        assertEquals(0.0, matrix.get(2, 0), 1E-7);
        assertEquals(0.4, matrix.get(1, 0), 1E-7);
    }

    @Test
    public void testSubmatrix() {
        Matrix sub = matrix.submatrix(0, 1, 2, 2);
        assertEquals(3, sub.nrow());
        assertEquals(2, sub.ncol());
        assertEquals(0.4, sub.get(0,0), 1E-7);
        assertEquals(0.8, sub.get(2,1), 1E-7);

        Matrix sub2 = sub.submatrix(0, 0, 1, 1);
        assertEquals(2, sub2.nrow());
        assertEquals(2, sub2.ncol());
        assertEquals(0.4, sub.get(0,0), 1E-7);
        assertEquals(0.3, sub.get(1,1), 1E-7);
    }

    @Test
    public void testMvOffset() {
        System.out.println("mv offfset ");
        double[] d = new double[matrix.ncol() + matrix.nrow()];
        System.arraycopy(b, 0, d, 0, b.length);
        matrix.mv(d, 0, b.length);
        assertEquals(0.65, d[3], 1E-7);
        assertEquals(0.60, d[4], 1E-7);
        assertEquals(0.55, d[5], 1E-7);
    }

    @Test
    public void testSubAx() {
        System.out.println("submatrix ax");
        Matrix sub = matrix.submatrix(1, 0, 2, 2);
        double[] d = sub.mv(b);
        assertEquals(0.60, d[0], 1E-7);
        assertEquals(0.55, d[1], 1E-7);
    }

    @Test
    public void testAx() {
        System.out.println("ax");
        double[] d = new double[matrix.nrow()];
        matrix.mv(b, d);
        assertEquals(0.65, d[0], 1E-7);
        assertEquals(0.60, d[1], 1E-7);
        assertEquals(0.55, d[2], 1E-7);
    }

    @Test
    public void testAxpy() {
        System.out.println("axpy");
        double[] d = new double[matrix.nrow()];
        Arrays.fill(d, 1.0f);
        matrix.mv(NO_TRANSPOSE, 1.0, b, 1.0, d);
        assertEquals(1.65, d[0], 1E-7);
        assertEquals(1.60, d[1], 1E-7);
        assertEquals(1.55, d[2], 1E-7);
    }

    @Test
    public void testAxpy2() {
        System.out.println("axpy b = 2");
        double[] d = new double[matrix.nrow()];
        Arrays.fill(d, 1.0f);
        matrix.mv(NO_TRANSPOSE, 1.0, b, 2.0, d);
        assertEquals(2.65, d[0], 1E-7);
        assertEquals(2.60, d[1], 1E-7);
        assertEquals(2.55, d[2], 1E-7);
    }

    @Test
    public void testAtx() {
        System.out.println("atx");
        double[] d = new double[matrix.nrow()];
        matrix.tv(b, d);
        assertEquals(0.65, d[0], 1E-7);
        assertEquals(0.60, d[1], 1E-7);
        assertEquals(0.55, d[2], 1E-7);
    }

    @Test
    public void testAtxpy() {
        System.out.println("atxpy");
        double[] d = new double[matrix.nrow()];
        Arrays.fill(d, 1.0f);
        matrix.mv(TRANSPOSE, 1.0, b, 1.0, d);
        assertEquals(1.65, d[0], 1E-7);
        assertEquals(1.60, d[1], 1E-7);
        assertEquals(1.55, d[2], 1E-7);
    }

    @Test
    public void testAtxpy2() {
        System.out.println("atxpy b = 2");
        double[] d = new double[matrix.nrow()];
        Arrays.fill(d, 1.0f);
        matrix.mv(TRANSPOSE, 1.0, b, 2.0, d);
        assertEquals(2.65, d[0], 1E-7);
        assertEquals(2.60, d[1], 1E-7);
        assertEquals(2.55, d[2], 1E-7);
    }

    @Test
    public void testAAT() {
        System.out.println("AAT");
        Matrix c = matrix.aat();
        assertEquals(c.nrow(), 3);
        assertEquals(c.ncol(), 3);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    @Test
    public void testAdd() {
        System.out.println("add");
        double[][] A = {
                { 0.7220180,  0.07121225, 0.6881997f},
                {-0.2648886, -0.89044952, 0.3700456f},
                {-0.6391588,  0.44947578, 0.6240573f}
        };
        double[][] B = {
                {0.6881997, -0.07121225,  0.7220180f},
                {0.3700456,  0.89044952, -0.2648886f},
                {0.6240573, -0.44947578, -0.6391588f}
        };
        double[][] C = {
                { 1.4102177, 0,  1.4102177f},
                { 0.1051570, 0,  0.1051570f},
                {-0.0151015, 0, -0.0151015f}
        };
        Matrix a = Matrix.of(A);
        Matrix b = Matrix.of(B);
        a.add(1.0, b);
        assertTrue(MathEx.equals(C, a.toArray(), 1E-7));
    }

    @Test
    public void testSub() {
        System.out.println("sub");
        double[][] A = {
                { 0.7220180,  0.07121225, 0.6881997f},
                {-0.2648886, -0.89044952, 0.3700456f},
                {-0.6391588,  0.44947578, 0.6240573f}
        };
        double[][] B = {
                {0.6881997, -0.07121225,  0.7220180f},
                {0.3700456,  0.89044952, -0.2648886f},
                {0.6240573, -0.44947578, -0.6391588f}
        };
        double[][] C = {
                { 0.0338183,  0.1424245, -0.0338183f},
                {-0.6349342, -1.7808990,  0.6349342f},
                {-1.2632161,  0.8989516,  1.2632161f}
        };
        Matrix a = Matrix.of(A);
        Matrix b = Matrix.of(B);
        a.sub(b);
        assertTrue(MathEx.equals(C, a.toArray(), 1E-7));
    }

    @Test
    public void testMm() {
        System.out.println("mm");
        double[][] A = {
                { 0.7220180,  0.07121225, 0.6881997f},
                {-0.2648886, -0.89044952, 0.3700456f},
                {-0.6391588,  0.44947578, 0.6240573f}
        };
        double[][] B = {
                {0.6881997, -0.07121225,  0.7220180f},
                {0.3700456,  0.89044952, -0.2648886f},
                {0.6240573, -0.44947578, -0.6391588f}
        };
        double[][] C = {
                { 0.9527204, -0.2973347,  0.06257778f},
                {-0.2808735, -0.9403636, -0.19190231f},
                { 0.1159052,  0.1652528, -0.97941688f}
        };
        double[][] D = {
                { 0.9887140,  0.1482942, -0.0212965f},
                { 0.1482942, -0.9889421, -0.0015881f},
                {-0.0212965, -0.0015881, -0.9997719f}
        };
        double[][] E = {
                {0.0000,  0.0000, 1.0000f},
                {0.0000, -1.0000, 0.0000f},
                {1.0000,  0.0000, 0.0000f}
        };

        Matrix a = Matrix.of(A);
        Matrix b = Matrix.of(B);
        double[][] F = b.mm(a).transpose().toArray();

        assertTrue(MathEx.equals(a.mm(b).toArray(),   C, 1E-7));
        assertTrue(MathEx.equals(a.mt(b).toArray(),  D, 1E-7));
        assertTrue(MathEx.equals(a.tm(b).toArray(),  E, 1E-7));
        assertTrue(MathEx.equals(a.tt(b).toArray(), F, 1E-7));
    }

    @Test
    public void testLU() {
        System.out.println("LU");
        double[][] A = {
                {0.9000, 0.4000, 0.7000f},
                {0.4000, 0.5000, 0.3000f},
                {0.7000, 0.3000, 0.8000f}
        };

        double[] b = {0.5, 0.5, 0.5f};
        double[] x = {-0.2027027, 0.8783784, 0.4729730f};

        Matrix a = Matrix.of(A);
        Matrix.LU lu = a.lu();
        double[] x2 = lu.solve(b);
        assertEquals(x.length, x2.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], x2[i], 1E-7);
        }

        double[][] B = {
                {0.5, 0.2f},
                {0.5, 0.8f},
                {0.5, 0.3f}
        };
        double[][] X = {
                {-0.2027027, -1.2837838f},
                { 0.8783784,  2.2297297f},
                { 0.4729730,  0.6621622f}
        };

        Matrix X2 = Matrix.of(B);
        lu.solve(X2);
        assertEquals(X.length, X2.nrow());
        assertEquals(X[0].length, X2.ncol());
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], X2.get(i, j), 1E-7);
            }
        }
    }

    @Test
    public void testQR() {
        System.out.println("QR");
        double[][] A = {
                {0.9000, 0.4000, 0.7000f},
                {0.4000, 0.5000, 0.3000f},
                {0.7000, 0.3000, 0.8000f}
        };

        double[] b = {0.5, 0.5, 0.5f};
        double[] x = {-0.2027027, 0.8783784, 0.4729730f};

        Matrix a = Matrix.of(A);
        Matrix.QR qr = a.qr();
        double[] x2 = qr.solve(b);
        assertEquals(x.length, x2.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], x2[i], 1E-7);
        }

        double[][] B = {
                {0.5, 0.2f},
                {0.5, 0.8f},
                {0.5, 0.3f}
        };
        double[][] X = {
                {-0.2027027, -1.2837838f},
                { 0.8783784,  2.2297297f},
                { 0.4729730,  0.6621622f}
        };

        Matrix X2 = Matrix.of(B);
        qr.solve(X2);
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], X2.get(i, j), 1E-7);
            }
        }
    }
    @Test
    public void testCholesky() {
        System.out.println("Cholesky");
        double[][] A = {
                {0.9000, 0.4000, 0.7000f},
                {0.4000, 0.5000, 0.3000f},
                {0.7000, 0.3000, 0.8000f}
        };
        double[][] L = {
                {0.9486833,  0.00000000, 0.0000000f},
                {0.4216370,  0.56764621, 0.0000000f},
                {0.7378648, -0.01957401, 0.5051459f}
        };

        Matrix a = Matrix.of(A);
        a.uplo(UPLO.LOWER);
        Matrix.Cholesky cholesky = a.cholesky();
        for (int i = 0; i < a.nrow(); i++) {
            for (int j = 0; j <= i; j++) {
                assertEquals(Math.abs(L[i][j]), Math.abs(cholesky.lu.get(i, j)), 1E-7);
            }
        }

        double[] b = {0.5, 0.5, 0.5f};
        double[] x = {-0.2027027, 0.8783784, 0.4729730f};

        double[] x2 = cholesky.solve(b);
        assertEquals(x.length, x2.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], x2[i], 1E-7);
        }

        double[][] B = {
                {0.5, 0.2f},
                {0.5, 0.8f},
                {0.5, 0.3f}
        };
        double[][] X = {
                {-0.2027027, -1.2837838f},
                { 0.8783784,  2.2297297f},
                { 0.4729730,  0.6621622f}
        };

        Matrix X2 = Matrix.of(B);
        cholesky.solve(X2);
        assertEquals(X.length, X2.nrow());
        assertEquals(X[0].length, X2.ncol());
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], X2.get(i, j), 1E-6);
            }
        }
    }

    @Test
    public void testSymmEVD() {
        System.out.println("EVD of symmetric matrix");
        double[][] A = {
                {0.9000, 0.4000, 0.7000f},
                {0.4000, 0.5000, 0.3000f},
                {0.7000, 0.3000, 0.8000f}
        };
        double[][] eigenVectors = {
                {0.6881997, -0.07121225, 0.7220180f},
                {0.3700456, 0.89044952, -0.2648886f},
                {0.6240573, -0.44947578, -0.6391588f}
        };
        double[] eigenValues = {1.7498382, 0.3165784, 0.1335834f};

        Matrix a = Matrix.of(A);
        a.uplo(UPLO.LOWER);
        Matrix.EVD eig = a.eigen().sort();
        assertArrayEquals(eigenValues, eig.wr, 1E-7);

        assertEquals(eigenVectors.length, eig.Vr.nrow());
        assertEquals(eigenVectors[0].length, eig.Vr.ncol());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(eig.Vr.get(i, j)), 1E-7);
            }
        }

        eig = a.eigen(false, false, true).sort();
        for (int i = 0; i < eigenValues.length; i++) {
            assertEquals(eigenValues[i], eig.wr[i], 1E-7);
        }
        assertNull(eig.wi);
        assertNull(eig.Vl);
        assertNull(eig.Vr);
    }

    @Test
    public void testEVD() {
        System.out.println("EVD ");
        double[][] A = {
                {0.9000, 0.4000, 0.7000f},
                {0.4000, 0.5000, 0.3000f},
                {0.8000, 0.3000, 0.8000f}
        };
        double[][] eigenVectors = {
                {0.6706167, 0.05567643, 0.6876103f},
                {0.3584968, -0.88610002, -0.1508644f},
                {0.6494254, 0.46013791, -0.7102337f}
        };
        double[] eigenValues = {1.79171122, 0.31908143, 0.08920735f};


        Matrix a = Matrix.of(A);
        Matrix.EVD eig = a.eigen().sort();
        assertArrayEquals(eigenValues, eig.wr, 1E-7);

        assertEquals(eigenVectors.length,    eig.Vr.nrow());
        assertEquals(eigenVectors[0].length, eig.Vr.ncol());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(eig.Vr.get(i, j)), 1E-7);
            }
        }

        eig = a.eigen(false, false, true).sort();
        for (int i = 0; i < eigenValues.length; i++) {
            assertEquals(eigenValues[i], eig.wr[i], 1E-7);
        }
        for (int i = 0; i < eigenValues.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-7);
        }

        assertNull(eig.Vl);
        assertNull(eig.Vr);
    }

    @Test
    public void testSVD1() {
        System.out.println("SVD symm");
        double[][] A = {
                {0.9000, 0.4000, 0.7000f},
                {0.4000, 0.5000, 0.3000f},
                {0.7000, 0.3000, 0.8000f}
        };

        double[] s = {1.7498382, 0.3165784, 0.1335834f};

        double[][] U = {
                {0.6881997, -0.07121225,  0.7220180f},
                {0.3700456,  0.89044952, -0.2648886f},
                {0.6240573, -0.44947578, -0.6391588f}
        };

        double[][] V = {
                {0.6881997, -0.07121225,  0.7220180f},
                {0.3700456,  0.89044952, -0.2648886f},
                {0.6240573, -0.44947578, -0.6391588f}
        };

        Matrix matrix = Matrix.of(A);
        matrix.uplo(UPLO.LOWER);
        Matrix.SVD svd = matrix.svd();
        assertArrayEquals(s, svd.s, 1E-7);

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-7);
            }
        }
    }

    @Test
    public void testSVD2() {
        System.out.println("SVD asymm");
        double[][] A = {
                { 1.19720880, -1.8391378,  0.3019585, -1.1165701, -1.7210814,  0.4918882, -0.04247433f},
                { 0.06605075,  1.0315583,  0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340f},
                {-1.02637715,  1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532,  0.17966051f},
                {-1.45817729, -0.8942353,  0.3459245,  1.5068363, -2.0180708, -0.3696350, -1.19575563f},
                {-0.07318103, -0.2783787,  1.2237598,  0.1995332,  0.2545336, -0.1392502, -1.88207227f},
                { 0.88248425, -0.9360321,  0.1393172,  0.1393281, -0.3277873, -0.5553013,  1.63805985f},
                { 0.12641406, -0.8710055, -0.2712301,  0.2296515,  1.1781535, -0.2158704, -0.27529472f}
        };

        double[] s = {3.8589375, 3.4396766, 2.6487176, 2.2317399, 1.5165054, 0.8109055, 0.2706515f};

        double[][] U = {
                {-0.3082776,  0.77676231,  0.01330514,  0.23231424, -0.47682758,  0.13927109,  0.02640713f},
                {-0.4013477, -0.09112050,  0.48754440,  0.47371793,  0.40636608,  0.24600706, -0.37796295f},
                { 0.0599719, -0.31406586,  0.45428229, -0.08071283, -0.38432597,  0.57320261,  0.45673993f},
                {-0.7694214, -0.12681435, -0.05536793, -0.62189972, -0.02075522, -0.01724911, -0.03681864f},
                {-0.3319069, -0.17984404, -0.54466777,  0.45335157,  0.19377726,  0.12333423,  0.55003852f},
                { 0.1259351,  0.49087824,  0.16349687, -0.32080176,  0.64828744,  0.20643772,  0.38812467f},
                { 0.1491884,  0.01768604, -0.47884363, -0.14108924,  0.03922507,  0.73034065, -0.43965505f}
        };

        double[][] V = {
                {-0.2122609, -0.54650056,  0.08071332, -0.43239135, -0.2925067,  0.1414550,  0.59769207f},
                {-0.1943605,  0.63132116, -0.54059857, -0.37089970, -0.1363031,  0.2892641,  0.17774114f},
                { 0.3031265, -0.06182488,  0.18579097, -0.38606409, -0.5364911,  0.2983466, -0.58642548f},
                { 0.1844063,  0.24425278,  0.25923756,  0.59043765, -0.4435443,  0.3959057,  0.37019098f},
                {-0.7164205,  0.30694911,  0.58264743, -0.07458095, -0.1142140, -0.1311972, -0.13124764f},
                {-0.1103067, -0.10633600,  0.18257905, -0.03638501,  0.5722925,  0.7784398, -0.09153611f},
                {-0.5156083, -0.36573746, -0.47613340,  0.41342817, -0.2659765,  0.1654796, -0.32346758f}
        };

        Matrix.SVD svd = Matrix.of(A).svd();
        assertArrayEquals(s, svd.s, 1E-7);

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-7);
            }
        }
    }

    @Test
    public void testSVD3() {
        System.out.println("SVD m = n+1");
        double[][] A = {
                { 1.19720880, -1.8391378,  0.3019585, -1.1165701, -1.7210814,  0.4918882f},
                { 0.06605075,  1.0315583,  0.8294362, -0.3646043, -1.6038017, -0.9188110f},
                {-1.02637715,  1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532f},
                {-1.45817729, -0.8942353,  0.3459245,  1.5068363, -2.0180708, -0.3696350f},
                {-0.07318103, -0.2783787,  1.2237598,  0.1995332,  0.2545336, -0.1392502f},
                { 0.88248425, -0.9360321,  0.1393172,  0.1393281, -0.3277873, -0.5553013f},
                { 0.12641406, -0.8710055, -0.2712301,  0.2296515,  1.1781535, -0.2158704f}
        };

        double[] s = {3.6447007, 3.1719019, 2.4155022, 1.6952749, 1.0349052, 0.6735233f};

        double[][] U = {
                {-0.66231606,  0.51980064, -0.26908096, -0.33255132,  0.1998343961,  0.25344461f},
                {-0.30950323, -0.38356363, -0.57342388,  0.43584295, -0.2842953084,  0.06773874f},
                { 0.17209598, -0.40152786, -0.25549740, -0.47194228, -0.1795895194,  0.60960160f},
                {-0.58855512, -0.52801793,  0.59486615, -0.13721651, -0.0004042427, -0.01414006f},
                {-0.06838272,  0.03221968,  0.14785619,  0.64819245,  0.3955572924,  0.53374206f},
                {-0.23683786,  0.25613876,  0.07459517,  0.19208798, -0.7235935956, -0.10586201f},
                { 0.16959559,  0.27570548,  0.39014092,  0.02900709, -0.4085787191,  0.51310416f}
        };

        double[][] V = {
                {-0.08624942,  0.642381656, -0.35639657,  0.2600624, -0.303192728, -0.5415995f},
                { 0.46728106, -0.567452824, -0.56054543,  0.1717478,  0.067268188, -0.3337846f},
                {-0.26399674, -0.005897261, -0.02438536,  0.8302504,  0.448103782,  0.1989057f},
                {-0.03389306, -0.296652409,  0.68563317,  0.2309273, -0.145824242, -0.6051146f},
                { 0.83642784,  0.352498963,  0.29305340,  0.2264531,  0.006202435,  0.1973149f},
                { 0.06127719,  0.230326187,  0.04693098, -0.3300697,  0.825499232, -0.3880689f}
        };

        Matrix.SVD svd = Matrix.of(A).svd();
        assertArrayEquals(s, svd.s, 1E-7);

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-7);
            }
        }
    }

    @Test
    public void testSVD4() {
        System.out.println("SVD m = n+2");
        double[][] A = {
                { 1.19720880, -1.8391378,  0.3019585, -1.1165701, -1.7210814f},
                { 0.06605075,  1.0315583,  0.8294362, -0.3646043, -1.6038017f},
                {-1.02637715,  1.0747931, -0.8089055, -0.4726863, -0.2064826f},
                {-1.45817729, -0.8942353,  0.3459245,  1.5068363, -2.0180708f},
                {-0.07318103, -0.2783787,  1.2237598,  0.1995332,  0.2545336f},
                { 0.88248425, -0.9360321,  0.1393172,  0.1393281, -0.3277873f},
                { 0.12641406, -0.8710055, -0.2712301,  0.2296515,  1.1781535f}
        };

        double[] s = {3.6392869, 3.0965326, 2.4131673, 1.6285557, 0.7495616f};

        double[][] U = {
                {-0.68672751, -0.47077690, -0.27062524,  0.30518577,  0.35585700f},
                {-0.28422169,  0.33969351, -0.56700359, -0.38788214, -0.15789372f},
                { 0.18880503,  0.39049353, -0.26448028,  0.50872376,  0.42411327f},
                {-0.56957699,  0.56761727,  0.58111879,  0.11662686, -0.01347444f},
                {-0.06682433, -0.04559753,  0.15586923, -0.68802278,  0.60990585f},
                {-0.23677832, -0.29935481,  0.09428368, -0.03224480, -0.50781217f},
                { 0.16440378, -0.31082218,  0.40550635,  0.09794049,  0.19627380f}
        };

        double[][] V = {
                {-0.10646320, -0.668422750, -0.33744231, -0.1953744, -0.6243702f},
                { 0.48885825,  0.546411345, -0.57018018, -0.2348795, -0.2866678f},
                {-0.26164973,  0.002221196, -0.01788181, -0.9049532,  0.3350739f},
                {-0.02353895,  0.306904408,  0.68247803, -0.2353931, -0.6197333f},
                { 0.82502638, -0.400562630,  0.30810911, -0.1797507,  0.1778750f}
        };

        Matrix.SVD svd = Matrix.of(A).svd();
        assertArrayEquals(s, svd.s, 1E-7);

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-7);
            }
        }
    }

    @Test
    public void testSVD5() {
        System.out.println("SVD m = n+3");
        double[][] A = {
                { 1.19720880, -1.8391378,  0.3019585, -1.1165701f},
                { 0.06605075,  1.0315583,  0.8294362, -0.3646043f},
                {-1.02637715,  1.0747931, -0.8089055, -0.4726863f},
                {-1.45817729, -0.8942353,  0.3459245,  1.5068363f},
                {-0.07318103, -0.2783787,  1.2237598,  0.1995332f},
                { 0.88248425, -0.9360321,  0.1393172,  0.1393281f},
                { 0.12641406, -0.8710055, -0.2712301,  0.2296515f}
        };

        double[] s = {3.2188437, 2.5504483, 1.7163918, 0.9212875f};

        double[][] U = {
                {-0.7390710,  0.15540183,  0.093738524, -0.60555964f},
                { 0.1716777,  0.26405327, -0.616548935, -0.11171885f},
                { 0.4583068,  0.19615535,  0.365025826, -0.56118537f},
                { 0.1185448, -0.88710768, -0.004538332, -0.24629659f},
                {-0.1055393, -0.19831478, -0.634814754, -0.26986239f},
                {-0.3836089, -0.06331799,  0.006896881,  0.41026537f},
                {-0.2047156, -0.19326474,  0.273456965,  0.06389058f}
        };

        double[][] V = {
                {-0.5820171,  0.4822386, -0.12201399,  0.6432842f},
                { 0.7734720,  0.4993237, -0.27962029,  0.2724507f},
                {-0.1670058, -0.1563235, -0.94966302, -0.2140379f},
                { 0.1873664, -0.7026270, -0.07117046,  0.6827473f}
        };

        Matrix.SVD svd = Matrix.of(A).svd();
        assertArrayEquals(s, svd.s, 1E-7);

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-7);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-7);
            }
        }
    }

    @Test
    public void testSVD6() {
        System.out.println("SVD m = n-1");
        double[][] A = {
                { 1.19720880, -1.8391378,  0.3019585, -1.1165701, -1.7210814,  0.4918882, -0.04247433f},
                { 0.06605075,  1.0315583,  0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340f},
                {-1.02637715,  1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532,  0.17966051f},
                {-1.45817729, -0.8942353,  0.3459245,  1.5068363, -2.0180708, -0.3696350, -1.19575563f},
                {-0.07318103, -0.2783787,  1.2237598,  0.1995332,  0.2545336, -0.1392502, -1.88207227f},
                { 0.88248425, -0.9360321,  0.1393172,  0.1393281, -0.3277873, -0.5553013,  1.63805985f}
        };

        double[] s = {3.8244094, 3.4392541, 2.3784254, 2.1694244, 1.5150752, 0.4743856f};

        double[][] U = {
                { 0.31443091,  0.77409564, -0.06404561,  0.2362505,  0.48411517,  0.08732402f},
                { 0.37429954, -0.08997642,  0.33948894,  0.7403030, -0.37663472, -0.21598420f},
                {-0.08460683, -0.30944648,  0.49768196,  0.1798789,  0.40657776,  0.67211259f},
                { 0.78096534, -0.13597601,  0.27845058, -0.5407806,  0.01748391, -0.03632677f},
                { 0.35762337, -0.18789909, -0.68652942,  0.1670810, -0.20242003,  0.54459792f},
                {-0.12678093,  0.49307962,  0.29000123, -0.2083774, -0.64590538,  0.44281315f}
        };

        double[][] V = {
                {-0.2062642,  0.54825338, -0.27956720,  0.3408979, -0.2925761, -0.412469519f},
                {-0.2516350, -0.62127194,  0.28319612,  0.5322251, -0.1297528, -0.410270389f},
                { 0.3043552,  0.05848409, -0.35475338,  0.2434904, -0.5456797,  0.040325347f},
                { 0.2047160, -0.24974630,  0.01491918, -0.6588368, -0.4616580, -0.465507184f},
                {-0.6713331, -0.30795185, -0.57548345, -0.1976943, -0.1242132,  0.261610893f},
                {-0.1122210,  0.10728081, -0.28476779, -0.1527923,  0.5474147, -0.612188492f},
                {-0.5443460,  0.37590198,  0.55072289, -0.2115256, -0.2675392, -0.003003781f}
        };

        Matrix.SVD svd = Matrix.of(A).svd();
        assertArrayEquals(s, svd.s, 1E-6);

        assertEquals(U.length, svd.U.nrow());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }
    }

    @Test
    public void testSVD7() {
        System.out.println("SVD m = n-2");
        double[][] A = {
                { 1.19720880, -1.8391378,  0.3019585, -1.1165701, -1.7210814,  0.4918882, -0.04247433f},
                { 0.06605075,  1.0315583,  0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340f},
                {-1.02637715,  1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532,  0.17966051f},
                {-1.45817729, -0.8942353,  0.3459245,  1.5068363, -2.0180708, -0.3696350, -1.19575563f},
                {-0.07318103, -0.2783787,  1.2237598,  0.1995332,  0.2545336, -0.1392502, -1.88207227f}
        };

        double[] s = {3.8105658, 3.0883849, 2.2956507, 2.0984771, 0.9019027f};

        double[][] U = {
                { 0.4022505, -0.8371341,  0.218900330, -0.01150020,  0.29891712f},
                { 0.3628648,  0.1788073,  0.520476180,  0.66921454, -0.34294833f},
                {-0.1204081,  0.3526074,  0.512685919, -0.03159520,  0.77286790f},
                { 0.7654028,  0.3523577, -0.005786511, -0.53518467, -0.05955197f},
                { 0.3258590,  0.1369180, -0.646766462,  0.51439164,  0.43836489f}
        };

        double[][] V = {
                {-0.1340510, -0.6074832, -0.07579249,  0.38390363, -0.4471462f},
                {-0.3332981,  0.5665840,  0.37922383,  0.48268873, -0.1570301f},
                { 0.3105522, -0.0324612, -0.30945577,  0.48678825, -0.3365301f},
                { 0.1820803,  0.4083420, -0.35471238, -0.43842294, -0.6389961f},
                {-0.7114696,  0.1311251, -0.64046888,  0.07815179,  0.1194533f},
                {-0.1112159, -0.2728406, -0.19551704, -0.23056606,  0.1841538f},
                {-0.4720051, -0.2247534,  0.42477493, -0.36219292, -0.4534882f}
        };

        Matrix.SVD svd = Matrix.of(A).svd();
        assertArrayEquals(s, svd.s, 1E-6);

        assertEquals(U.length, svd.U.nrow());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }
    }

    @Test
    public void testSVD8() {
        System.out.println("SVD m = n-3");
        double[][] A = {
                { 1.19720880, -1.8391378,  0.3019585, -1.1165701, -1.7210814,  0.4918882, -0.04247433f},
                { 0.06605075,  1.0315583,  0.8294362, -0.3646043, -1.6038017, -0.9188110, -0.63760340f},
                {-1.02637715,  1.0747931, -0.8089055, -0.4726863, -0.2064826, -0.3325532,  0.17966051f},
                {-1.45817729, -0.8942353,  0.3459245,  1.5068363, -2.0180708, -0.3696350, -1.19575563f}
        };

        double[] s = {3.668957, 3.068763, 2.179053, 1.293110f};

        double[][] U = {
                {-0.4918880,  0.7841689, -0.1533124,  0.34586230f},
                {-0.3688033, -0.2221466, -0.8172311, -0.38310353f},
                { 0.1037476, -0.3784190, -0.3438745,  0.85310363f},
                {-0.7818356, -0.4387814,  0.4363243,  0.07632262f}
        };

        double[][] V = {
                { 0.11456074,  0.63620515, -0.23901163, -0.4625536f},
                { 0.36382542, -0.54930940, -0.60614838, -0.1412273f},
                {-0.22044591,  0.06740501, -0.13539726, -0.6782114f},
                {-0.14811938, -0.41609019,  0.59161667, -0.4135324f},
                { 0.81615679, -0.00968160,  0.35107473, -0.2405136f},
                { 0.09577622,  0.28606564,  0.28844835,  0.1625626f},
                { 0.32967585,  0.18412070, -0.02567023,  0.2254902f}
        };

        Matrix.SVD svd = Matrix.of(A).svd();
        assertArrayEquals(s, svd.s, 1E-6);

        assertEquals(U.length, svd.U.nrow());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6);
            }
        }
    }

    @Test
    public void testSVD9() {
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

        double[] s = {3.34088, 2.5417, 2.35394, 1.64453, 1.50483, 1.30638, 0.845903, 0.560134, 0.363677f};

        double[][] Vt = {
                { 0.197393,   0.60599,   0.462918,   0.542114,   0.279469,  0.00381521,  0.0146315, 0.0241368,   0.0819574f},
                { 0.0559135, -0.165593,  0.127312,   0.231755,  -0.106775,  -0.192848,  -0.437875, -0.615122,   -0.529937f},
                {-0.11027,    0.497326, -0.207606,  -0.569921,   0.50545,   -0.0981842, -0.192956, -0.252904,   -0.0792731f},
                {-0.949785,  -0.0286489, 0.0416092,  0.267714,   0.150035,   0.0150815,  0.0155072, 0.010199,   -0.0245549f},
                {-0.0456786,  0.206327, -0.378336,   0.205605,  -0.327194,  -0.394841,  -0.349485, -0.149798,    0.601993f},
                {-0.0765936, -0.256475,  0.7244,    -0.368861,   0.034813,  -0.300161,  -0.212201,  9.74342e-05, 0.362219f},
                {-0.177318,   0.432984,  0.23689,   -0.2648,    -0.672304,   0.34084,    0.152195, -0.249146,   -0.0380342f},
                {-0.0143933,  0.0493053, 0.0088255, -0.0194669, -0.0583496,  0.454477,  -0.761527,  0.449643,   -0.0696375f},
                {-0.0636923,  0.242783,  0.0240769, -0.0842069, -0.262376,  -0.619847,  0.0179752,  0.51989,    -0.453507f}
        };

        double[][] Ut = {
                { 0.221351,   0.197645,   0.24047,   0.403599,    0.644481,  0.265037,   0.265037,   0.300828,  0.205918,  0.0127462, 0.0361358,    0.0317563f},
                { 0.11318,    0.0720878, -0.043152, -0.0570703,   0.167301, -0.10716,   -0.10716,    0.14127,  -0.273647, -0.490162, -0.622785,    -0.450509f},
                {-0.288958,  -0.13504,    0.164429,  0.337804,   -0.361148,  0.425998,   0.425998,  -0.330308,  0.177597, -0.23112,  -0.223086,    -0.141115f},
                {-0.414751,  -0.55224,   -0.594962,  0.0991137,   0.333462,  0.0738122,  0.0738122,  0.188092, -0.0323519, 0.024802,  0.000700072, -0.00872947f},
                { 0.106275,  -0.281769,   0.106755, -0.331734,    0.158955, -0.0803194, -0.0803194, -0.114785,  0.53715,  -0.59417,   0.0682529,    0.300495f},
                {-0.340983,   0.495878,  -0.254955,  0.384832,   -0.206523, -0.169676,  -0.169676,   0.272155,  0.080944, -0.392125,  0.114909,     0.277343f},
                {-0.522658,   0.0704234,  0.30224,  -0.00287218,  0.165829, -0.282916,  -0.282916,  -0.0329941, 0.466898,  0.288317, -0.159575,    -0.339495f},
                {-0.0604501, -0.00994004, 0.062328, -0.000390504, 0.034272, -0.0161465, -0.0161465, -0.018998, -0.0362988, 0.254568, -0.681125,     0.6784180f},
                {-0.406678,  -0.10893,    0.492444,  0.0123293,   0.270696, -0.0538747, -0.0538747, -0.165339, -0.579426, -0.225424,  0.231961,     0.182535f}
        };

        Matrix.SVD svd = Matrix.of(A).svd();
        assertArrayEquals(s, svd.s, 1E-5);

        assertEquals(Ut[0].length, svd.U.nrow());
        assertEquals(Ut.length, svd.U.ncol());
        for (int i = 0; i < Ut.length; i++) {
            for (int j = 0; j < Ut[i].length; j++) {
                assertEquals(Math.abs(Ut[i][j]), Math.abs(svd.U.get(j, i)), 1E-6);
            }
        }

        assertEquals(Vt[0].length, svd.V.nrow());
        assertEquals(Vt.length, svd.V.ncol());
        for (int i = 0; i < Vt.length; i++) {
            for (int j = 0; j < Vt[i].length; j++) {
                assertEquals(Math.abs(Vt[i][j]), Math.abs(svd.V.get(j, i)), 1E-6);
            }
        }
    }

    @Test
    public void testPinv() {
        System.out.println("SVD pinv");
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

        Matrix a = Matrix.of(A);
        Matrix pinv = a.svd().pinv();

        Matrix x = pinv.mm(a).mm(pinv);
        assertTrue(x.equals(pinv, 1E-7));

        x = a.mm(pinv).mm(a);
        assertTrue(x.equals(a, 1E-7));
    }

    @Test
    public void testSVDSolve() {
        System.out.println("SVD solve");
        double[][] A = {
                {0.9000, 0.4000, 0.7000f},
                {0.4000, 0.5000, 0.3000f},
                {0.7000, 0.3000, 0.8000f}
        };
        double[] b = {0.5, 0.5, 0.5f};
        double[] x = {-0.2027027, 0.8783784, 0.4729730f};

        Matrix a = Matrix.of(A);
        Matrix.SVD svd = a.svd();
        double[] x2 = svd.solve(b);
        assertEquals(x.length, x2.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], x2[i], 1E-7);
        }
    }
}