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
import smile.math.MathEx;
import smile.math.blas.Layout;
import smile.math.blas.UPLO;

import java.util.Arrays;

import static smile.math.blas.Transpose.*;
import static org.junit.Assert.*;

/**
 * Test FloatMatrix with OpenBLAS.
 *
 * @author Haifeng Li
 */
public class FloatMatrixTest {

    float[][] A = {
            {0.9000f, 0.4000f, 0.0000f},
            {0.4000f, 0.5000f, 0.3000f},
            {0.0000f, 0.3000f, 0.8000f}
    };
    float[] b = {0.5f, 0.5f, 0.5f};
    float[][] C = {
            {0.97f, 0.56f, 0.12f},
            {0.56f, 0.50f, 0.39f},
            {0.12f, 0.39f, 0.73f}
    };

    FloatMatrix matrix = new FloatMatrix(A);

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
        assertEquals(0.9f, matrix.get(0, 0), 1E-6f);
        assertEquals(0.8f, matrix.get(2, 2), 1E-6f);
        assertEquals(0.5f, matrix.get(1, 1), 1E-6f);
        assertEquals(0.0f, matrix.get(2, 0), 1E-6f);
        assertEquals(0.0f, matrix.get(0, 2), 1E-6f);
        assertEquals(0.4f, matrix.get(0, 1), 1E-6f);
    }

    @Test
    public void testColMeans() {
        System.out.println("colMeans");
        float[][] A = {
                { 0.7220180f,  0.07121225f, 0.6881997f},
                {-0.2648886f, -0.89044952f, 0.3700456f},
                {-0.6391588f,  0.44947578f, 0.6240573f}
        };
        float[] r = {-0.06067647f, -0.12325383f, 0.56076753f};

        float[] result = new FloatMatrix(A).colMeans();
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7f);
        }
    }

    @Test
    public void testRowMeans() {
        System.out.println("rowMeans");
        float[][] A = {
                { 0.7220180f,  0.07121225f, 0.6881997f},
                {-0.2648886f, -0.89044952f, 0.3700456f},
                {-0.6391588f,  0.44947578f, 0.6240573f}
        };
        float[] r = {0.4938100f, -0.2617642f, 0.1447914f};

        float[] result = new FloatMatrix(A).rowMeans();
        for (int i = 0; i < r.length; i++) {
            assertEquals(result[i], r[i], 1E-7f);
        }
    }

    @Test
    public void testTranspose() {
        FloatMatrix t = matrix.transpose();
        assertEquals(Layout.COL_MAJOR, matrix.layout());
        assertEquals(Layout.ROW_MAJOR, t.layout());
        assertFalse(t.isSubmatrix());
        assertEquals(3, t.nrow());
        assertEquals(3, t.ncol());

        assertEquals(0.9f, matrix.get(0, 0), 1E-6f);
        assertEquals(0.8f, matrix.get(2, 2), 1E-6f);
        assertEquals(0.5f, matrix.get(1, 1), 1E-6f);
        assertEquals(0.0f, matrix.get(0, 2), 1E-6f);
        assertEquals(0.0f, matrix.get(2, 0), 1E-6f);
        assertEquals(0.4f, matrix.get(1, 0), 1E-6f);
    }

    @Test
    public void testSubmatrix() {
        FloatMatrix sub = matrix.submatrix(0, 1, 2, 2);
        assertFalse(matrix.isSubmatrix());
        assertTrue(sub.isSubmatrix());
        assertEquals(3, sub.nrow());
        assertEquals(2, sub.ncol());
        assertEquals(0.4f, sub.get(0,0), 1E-6f);
        assertEquals(0.8f, sub.get(2,1), 1E-6f);

        FloatMatrix sub2 = sub.submatrix(0, 0, 1, 1);
        assertTrue(sub2.isSubmatrix());
        assertEquals(2, sub2.nrow());
        assertEquals(2, sub2.ncol());
        assertEquals(0.4f, sub.get(0,0), 1E-6f);
        assertEquals(0.3f, sub.get(1,1), 1E-6f);
    }

    @Test
    public void testMvOffset() {
        System.out.println("mv offfset ");
        float[] d = new float[matrix.ncol() + matrix.nrow()];
        System.arraycopy(b, 0, d, 0, b.length);
        matrix.mv(d, 0, b.length);
        assertEquals(0.65f, d[3], 1E-6f);
        assertEquals(0.60f, d[4], 1E-6f);
        assertEquals(0.55f, d[5], 1E-6f);
    }

    @Test
    public void testSubAx() {
        System.out.println("submatrix ax");
        FloatMatrix sub = matrix.submatrix(1, 0, 2, 2);
        float[] d = sub.mv(b);
        assertEquals(0.60f, d[0], 1E-6f);
        assertEquals(0.55f, d[1], 1E-6f);
    }

    @Test
    public void testAx() {
        System.out.println("ax");
        float[] d = new float[matrix.nrow()];
        matrix.mv(b, d);
        assertEquals(0.65f, d[0], 1E-6f);
        assertEquals(0.60f, d[1], 1E-6f);
        assertEquals(0.55f, d[2], 1E-6f);
    }

    @Test
    public void testAxpy() {
        System.out.println("axpy");
        float[] d = new float[matrix.nrow()];
        Arrays.fill(d, 1.0f);
        matrix.mv(NO_TRANSPOSE, 1.0f, b, 1.0f, d);
        assertEquals(1.65f, d[0], 1E-6f);
        assertEquals(1.60f, d[1], 1E-6f);
        assertEquals(1.55f, d[2], 1E-6f);
    }

    @Test
    public void testAxpy2() {
        System.out.println("axpy b = 2");
        float[] d = new float[matrix.nrow()];
        Arrays.fill(d, 1.0f);
        matrix.mv(NO_TRANSPOSE, 1.0f, b, 2.0f, d);
        assertEquals(2.65f, d[0], 1E-6f);
        assertEquals(2.60f, d[1], 1E-6f);
        assertEquals(2.55f, d[2], 1E-6f);
    }

    @Test
    public void testAtx() {
        System.out.println("atx");
        float[] d = new float[matrix.nrow()];
        matrix.tv(b, d);
        assertEquals(0.65f, d[0], 1E-6f);
        assertEquals(0.60f, d[1], 1E-6f);
        assertEquals(0.55f, d[2], 1E-6f);
    }

    @Test
    public void testAtxpy() {
        System.out.println("atxpy");
        float[] d = new float[matrix.nrow()];
        Arrays.fill(d, 1.0f);
        matrix.mv(TRANSPOSE, 1.0f, b, 1.0f, d);
        assertEquals(1.65f, d[0], 1E-6f);
        assertEquals(1.60f, d[1], 1E-6f);
        assertEquals(1.55f, d[2], 1E-6f);
    }

    @Test
    public void testAtxpy2() {
        System.out.println("atxpy b = 2");
        float[] d = new float[matrix.nrow()];
        Arrays.fill(d, 1.0f);
        matrix.mv(TRANSPOSE, 1.0f, b, 2.0f, d);
        assertEquals(2.65f, d[0], 1E-6f);
        assertEquals(2.60f, d[1], 1E-6f);
        assertEquals(2.55f, d[2], 1E-6f);
    }

    @Test
    public void testAAT() {
        System.out.println("AAT");
        FloatMatrix c = matrix.aat();
        assertEquals(c.nrow(), 3);
        assertEquals(c.ncol(), 3);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-6f);
            }
        }
    }

    @Test
    public void testAdd() {
        System.out.println("add");
        float[][] A = {
                { 0.7220180f,  0.07121225f, 0.6881997f},
                {-0.2648886f, -0.89044952f, 0.3700456f},
                {-0.6391588f,  0.44947578f, 0.6240573f}
        };
        float[][] B = {
                {0.6881997f, -0.07121225f,  0.7220180f},
                {0.3700456f,  0.89044952f, -0.2648886f},
                {0.6240573f, -0.44947578f, -0.6391588f}
        };
        float[][] C = {
                { 1.4102177f, 0f,  1.4102177f},
                { 0.1051570f, 0f,  0.1051570f},
                {-0.0151015f, 0f, -0.0151015f}
        };
        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix b = new FloatMatrix(B);
        a.add(1.0f, b);
        assertTrue(MathEx.equals(C, a.toArray(), 1E-6f));
    }

    @Test
    public void testSub() {
        System.out.println("sub");
        float[][] A = {
                { 0.7220180f,  0.07121225f, 0.6881997f},
                {-0.2648886f, -0.89044952f, 0.3700456f},
                {-0.6391588f,  0.44947578f, 0.6240573f}
        };
        float[][] B = {
                {0.6881997f, -0.07121225f,  0.7220180f},
                {0.3700456f,  0.89044952f, -0.2648886f},
                {0.6240573f, -0.44947578f, -0.6391588f}
        };
        float[][] C = {
                { 0.0338183f,  0.1424245f, -0.0338183f},
                {-0.6349342f, -1.7808990f,  0.6349342f},
                {-1.2632161f,  0.8989516f,  1.2632161f}
        };
        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix b = new FloatMatrix(B);
        a.sub(1.0f, b);
        assertTrue(MathEx.equals(C, a.toArray(), 1E-6f));
    }

    @Test
    public void testMm() {
        System.out.println("mm");
        float[][] A = {
                { 0.7220180f,  0.07121225f, 0.6881997f},
                {-0.2648886f, -0.89044952f, 0.3700456f},
                {-0.6391588f,  0.44947578f, 0.6240573f}
        };
        float[][] B = {
                {0.6881997f, -0.07121225f,  0.7220180f},
                {0.3700456f,  0.89044952f, -0.2648886f},
                {0.6240573f, -0.44947578f, -0.6391588f}
        };
        float[][] C = {
                { 0.9527204f, -0.2973347f,  0.06257778f},
                {-0.2808735f, -0.9403636f, -0.19190231f},
                { 0.1159052f,  0.1652528f, -0.97941688f}
        };
        float[][] D = {
                { 0.9887140f,  0.1482942f, -0.0212965f},
                { 0.1482942f, -0.9889421f, -0.0015881f},
                {-0.0212965f, -0.0015881f, -0.9997719f}
        };
        float[][] E = {
                {0.0000f,  0.0000f, 1.0000f},
                {0.0000f, -1.0000f, 0.0000f},
                {1.0000f,  0.0000f, 0.0000f}
        };

        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix b = new FloatMatrix(B);
        float[][] F = b.mm(a).transpose().toArray();

        assertTrue(MathEx.equals(a.mm(b).toArray(),   C, 1E-6f));
        assertTrue(MathEx.equals(a.mt(b).toArray(),  D, 1E-6f));
        assertTrue(MathEx.equals(a.tm(b).toArray(),  E, 1E-6f));
        assertTrue(MathEx.equals(a.tt(b).toArray(), F, 1E-6f));
    }

    @Test
    public void testLU() {
        System.out.println("LU");
        float[][] A = {
                {0.9000f, 0.4000f, 0.7000f},
                {0.4000f, 0.5000f, 0.3000f},
                {0.7000f, 0.3000f, 0.8000f}
        };

        float[] b = {0.5f, 0.5f, 0.5f};
        float[] x = {-0.2027027f, 0.8783784f, 0.4729730f};

        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix.LU lu = a.lu();
        float[] x2 = lu.solve(b);
        assertEquals(x.length, x2.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], x2[i], 1E-6f);
        }

        float[][] B = {
                {0.5f, 0.2f},
                {0.5f, 0.8f},
                {0.5f, 0.3f}
        };
        float[][] X = {
                {-0.2027027f, -1.2837838f},
                { 0.8783784f,  2.2297297f},
                { 0.4729730f,  0.6621622f}
        };

        FloatMatrix X2 = new FloatMatrix(B);
        lu.solve(X2);
        assertEquals(X.length, X2.nrow());
        assertEquals(X[0].length, X2.ncol());
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], X2.get(i, j), 1E-6f);
            }
        }
    }

    @Test
    public void testQR() {
        System.out.println("QR");
        float[][] A = {
                {0.9000f, 0.4000f, 0.7000f},
                {0.4000f, 0.5000f, 0.3000f},
                {0.7000f, 0.3000f, 0.8000f}
        };

        float[] b = {0.5f, 0.5f, 0.5f};
        float[] x = {-0.2027027f, 0.8783784f, 0.4729730f};

        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix.QR qr = a.qr();
        float[] x2 = qr.solve(b);
        assertEquals(x.length, x2.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], x2[i], 1E-6f);
        }

        float[][] B = {
                {0.5f, 0.2f},
                {0.5f, 0.8f},
                {0.5f, 0.3f}
        };
        float[][] X = {
                {-0.2027027f, -1.2837838f},
                { 0.8783784f,  2.2297297f},
                { 0.4729730f,  0.6621622f}
        };

        FloatMatrix X2 = new FloatMatrix(B);
        qr.solve(X2);
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], X2.get(i, j), 1E-6f);
            }
        }
    }
    @Test
    public void testCholesky() {
        System.out.println("Cholesky");
        float[][] A = {
                {0.9000f, 0.4000f, 0.7000f},
                {0.4000f, 0.5000f, 0.3000f},
                {0.7000f, 0.3000f, 0.8000f}
        };
        float[][] L = {
                {0.9486833f,  0.00000000f, 0.0000000f},
                {0.4216370f,  0.56764621f, 0.0000000f},
                {0.7378648f, -0.01957401f, 0.5051459f}
        };

        FloatMatrix a = new FloatMatrix(A);
        a.uplo(UPLO.LOWER);
        FloatMatrix.Cholesky cholesky = a.cholesky();
        for (int i = 0; i < a.nrow(); i++) {
            for (int j = 0; j <= i; j++) {
                assertEquals(Math.abs(L[i][j]), Math.abs(cholesky.lu.get(i, j)), 1E-6f);
            }
        }

        float[] b = {0.5f, 0.5f, 0.5f};
        float[] x = {-0.2027027f, 0.8783784f, 0.4729730f};

        float[] x2 = cholesky.solve(b);
        assertEquals(x.length, x2.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], x2[i], 1E-6f);
        }

        float[][] B = {
                {0.5f, 0.2f},
                {0.5f, 0.8f},
                {0.5f, 0.3f}
        };
        float[][] X = {
                {-0.2027027f, -1.2837838f},
                { 0.8783784f,  2.2297297f},
                { 0.4729730f,  0.6621622f}
        };

        FloatMatrix X2 = new FloatMatrix(B);
        cholesky.solve(X2);
        assertEquals(X.length, X2.nrow());
        assertEquals(X[0].length, X2.ncol());
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], X2.get(i, j), 1E-6f);
            }
        }
    }

    @Test
    public void testSymmEVD() {
        System.out.println("EVD of symmetric matrix");
        float[][] A = {
                {0.9000f, 0.4000f, 0.7000f},
                {0.4000f, 0.5000f, 0.3000f},
                {0.7000f, 0.3000f, 0.8000f}
        };
        float[][] eigenVectors = {
                {0.6881997f, -0.07121225f, 0.7220180f},
                {0.3700456f, 0.89044952f, -0.2648886f},
                {0.6240573f, -0.44947578f, -0.6391588f}
        };
        float[] eigenValues = {1.7498382f, 0.3165784f, 0.1335834f};

        FloatMatrix a = new FloatMatrix(A);
        a.uplo(UPLO.LOWER);
        FloatMatrix.EVD eig = a.eigen().sort();
        assertTrue(MathEx.equals(eigenValues, eig.wr, 1E-6f));

        assertEquals(eigenVectors.length, eig.Vr.nrow());
        assertEquals(eigenVectors[0].length, eig.Vr.ncol());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(eig.Vr.get(i, j)), 1E-6f);
            }
        }

        eig = a.eigen(false, false, true).sort();
        for (int i = 0; i < eigenValues.length; i++) {
            assertEquals(eigenValues[i], eig.wr[i], 1E-6f);
        }
        assertNull(eig.wi);
        assertNull(eig.Vl);
        assertNull(eig.Vr);
    }

    @Test
    public void testEVD() {
        System.out.println("EVD ");
        float[][] A = {
                {0.9000f, 0.4000f, 0.7000f},
                {0.4000f, 0.5000f, 0.3000f},
                {0.8000f, 0.3000f, 0.8000f}
        };
        float[][] eigenVectors = {
                {0.6706167f, 0.05567643f, 0.6876103f},
                {0.3584968f, -0.88610002f, -0.1508644f},
                {0.6494254f, 0.46013791f, -0.7102337f}
        };
        float[] eigenValues = {1.79171122f, 0.31908143f, 0.08920735f};


        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix.EVD eig = a.eigen().sort();
        assertTrue(MathEx.equals(eigenValues, eig.wr, 1E-6f));

        assertEquals(eigenVectors.length,    eig.Vr.nrow());
        assertEquals(eigenVectors[0].length, eig.Vr.ncol());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(eig.Vr.get(i, j)), 1E-6f);
            }
        }

        eig = a.eigen(false, false, true).sort();
        for (int i = 0; i < eigenValues.length; i++) {
            assertEquals(eigenValues[i], eig.wr[i], 1E-6f);
        }
        for (int i = 0; i < eigenValues.length; i++) {
            assertEquals(0.0f, eig.wi[i], 1E-6f);
        }

        assertNull(eig.Vl);
        assertNull(eig.Vr);
    }
    
    @Test
    public void testSVD1() {
        System.out.println("SVD symm");
        float[][] A = {
                {0.9000f, 0.4000f, 0.7000f},
                {0.4000f, 0.5000f, 0.3000f},
                {0.7000f, 0.3000f, 0.8000f}
        };

        float[] s = {1.7498382f, 0.3165784f, 0.1335834f};

        float[][] U = {
                {0.6881997f, -0.07121225f,  0.7220180f},
                {0.3700456f,  0.89044952f, -0.2648886f},
                {0.6240573f, -0.44947578f, -0.6391588f}
        };

        float[][] V = {
                {0.6881997f, -0.07121225f,  0.7220180f},
                {0.3700456f,  0.89044952f, -0.2648886f},
                {0.6240573f, -0.44947578f, -0.6391588f}
        };

        FloatMatrix matrix = new FloatMatrix(A);
        matrix.uplo(UPLO.LOWER);
        FloatMatrix.SVD svd = matrix.svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-6f));

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6f);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6f);
            }
        }
    }

    @Test
    public void testSVD2() {
        System.out.println("SVD asymm");
        float[][] A = {
                { 1.19720880f, -1.8391378f,  0.3019585f, -1.1165701f, -1.7210814f,  0.4918882f, -0.04247433f},
                { 0.06605075f,  1.0315583f,  0.8294362f, -0.3646043f, -1.6038017f, -0.9188110f, -0.63760340f},
                {-1.02637715f,  1.0747931f, -0.8089055f, -0.4726863f, -0.2064826f, -0.3325532f,  0.17966051f},
                {-1.45817729f, -0.8942353f,  0.3459245f,  1.5068363f, -2.0180708f, -0.3696350f, -1.19575563f},
                {-0.07318103f, -0.2783787f,  1.2237598f,  0.1995332f,  0.2545336f, -0.1392502f, -1.88207227f},
                { 0.88248425f, -0.9360321f,  0.1393172f,  0.1393281f, -0.3277873f, -0.5553013f,  1.63805985f},
                { 0.12641406f, -0.8710055f, -0.2712301f,  0.2296515f,  1.1781535f, -0.2158704f, -0.27529472f}
        };

        float[] s = {3.8589375f, 3.4396766f, 2.6487176f, 2.2317399f, 1.5165054f, 0.8109055f, 0.2706515f};

        float[][] U = {
                {-0.3082776f,  0.77676231f,  0.01330514f,  0.23231424f, -0.47682758f,  0.13927109f,  0.02640713f},
                {-0.4013477f, -0.09112050f,  0.48754440f,  0.47371793f,  0.40636608f,  0.24600706f, -0.37796295f},
                { 0.0599719f, -0.31406586f,  0.45428229f, -0.08071283f, -0.38432597f,  0.57320261f,  0.45673993f},
                {-0.7694214f, -0.12681435f, -0.05536793f, -0.62189972f, -0.02075522f, -0.01724911f, -0.03681864f},
                {-0.3319069f, -0.17984404f, -0.54466777f,  0.45335157f,  0.19377726f,  0.12333423f,  0.55003852f},
                { 0.1259351f,  0.49087824f,  0.16349687f, -0.32080176f,  0.64828744f,  0.20643772f,  0.38812467f},
                { 0.1491884f,  0.01768604f, -0.47884363f, -0.14108924f,  0.03922507f,  0.73034065f, -0.43965505f}
        };

        float[][] V = {
                {-0.2122609f, -0.54650056f,  0.08071332f, -0.43239135f, -0.2925067f,  0.1414550f,  0.59769207f},
                {-0.1943605f,  0.63132116f, -0.54059857f, -0.37089970f, -0.1363031f,  0.2892641f,  0.17774114f},
                { 0.3031265f, -0.06182488f,  0.18579097f, -0.38606409f, -0.5364911f,  0.2983466f, -0.58642548f},
                { 0.1844063f,  0.24425278f,  0.25923756f,  0.59043765f, -0.4435443f,  0.3959057f,  0.37019098f},
                {-0.7164205f,  0.30694911f,  0.58264743f, -0.07458095f, -0.1142140f, -0.1311972f, -0.13124764f},
                {-0.1103067f, -0.10633600f,  0.18257905f, -0.03638501f,  0.5722925f,  0.7784398f, -0.09153611f},
                {-0.5156083f, -0.36573746f, -0.47613340f,  0.41342817f, -0.2659765f,  0.1654796f, -0.32346758f}
        };

        FloatMatrix.SVD svd = new FloatMatrix(A).svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-4f));

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-4f);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-4f);
            }
        }
    }

    @Test
    public void testSVD3() {
        System.out.println("SVD m = n+1");
        float[][] A = {
                { 1.19720880f, -1.8391378f,  0.3019585f, -1.1165701f, -1.7210814f,  0.4918882f},
                { 0.06605075f,  1.0315583f,  0.8294362f, -0.3646043f, -1.6038017f, -0.9188110f},
                {-1.02637715f,  1.0747931f, -0.8089055f, -0.4726863f, -0.2064826f, -0.3325532f},
                {-1.45817729f, -0.8942353f,  0.3459245f,  1.5068363f, -2.0180708f, -0.3696350f},
                {-0.07318103f, -0.2783787f,  1.2237598f,  0.1995332f,  0.2545336f, -0.1392502f},
                { 0.88248425f, -0.9360321f,  0.1393172f,  0.1393281f, -0.3277873f, -0.5553013f},
                { 0.12641406f, -0.8710055f, -0.2712301f,  0.2296515f,  1.1781535f, -0.2158704f}
        };

        float[] s = {3.6447007f, 3.1719019f, 2.4155022f, 1.6952749f, 1.0349052f, 0.6735233f};

        float[][] U = {
                {-0.66231606f,  0.51980064f, -0.26908096f, -0.33255132f,  0.1998343961f,  0.25344461f},
                {-0.30950323f, -0.38356363f, -0.57342388f,  0.43584295f, -0.2842953084f,  0.06773874f},
                { 0.17209598f, -0.40152786f, -0.25549740f, -0.47194228f, -0.1795895194f,  0.60960160f},
                {-0.58855512f, -0.52801793f,  0.59486615f, -0.13721651f, -0.0004042427f, -0.01414006f},
                {-0.06838272f,  0.03221968f,  0.14785619f,  0.64819245f,  0.3955572924f,  0.53374206f},
                {-0.23683786f,  0.25613876f,  0.07459517f,  0.19208798f, -0.7235935956f, -0.10586201f},
                { 0.16959559f,  0.27570548f,  0.39014092f,  0.02900709f, -0.4085787191f,  0.51310416f}
        };

        float[][] V = {
                {-0.08624942f,  0.642381656f, -0.35639657f,  0.2600624f, -0.303192728f, -0.5415995f},
                { 0.46728106f, -0.567452824f, -0.56054543f,  0.1717478f,  0.067268188f, -0.3337846f},
                {-0.26399674f, -0.005897261f, -0.02438536f,  0.8302504f,  0.448103782f,  0.1989057f},
                {-0.03389306f, -0.296652409f,  0.68563317f,  0.2309273f, -0.145824242f, -0.6051146f},
                { 0.83642784f,  0.352498963f,  0.29305340f,  0.2264531f,  0.006202435f,  0.1973149f},
                { 0.06127719f,  0.230326187f,  0.04693098f, -0.3300697f,  0.825499232f, -0.3880689f}
        };

        FloatMatrix.SVD svd = new FloatMatrix(A).svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-6f));

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6f);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6f);
            }
        }
    }

    @Test
    public void testSVD4() {
        System.out.println("SVD m = n+2");
        float[][] A = {
                { 1.19720880f, -1.8391378f,  0.3019585f, -1.1165701f, -1.7210814f},
                { 0.06605075f,  1.0315583f,  0.8294362f, -0.3646043f, -1.6038017f},
                {-1.02637715f,  1.0747931f, -0.8089055f, -0.4726863f, -0.2064826f},
                {-1.45817729f, -0.8942353f,  0.3459245f,  1.5068363f, -2.0180708f},
                {-0.07318103f, -0.2783787f,  1.2237598f,  0.1995332f,  0.2545336f},
                { 0.88248425f, -0.9360321f,  0.1393172f,  0.1393281f, -0.3277873f},
                { 0.12641406f, -0.8710055f, -0.2712301f,  0.2296515f,  1.1781535f}
        };

        float[] s = {3.6392869f, 3.0965326f, 2.4131673f, 1.6285557f, 0.7495616f};

        float[][] U = {
                {-0.68672751f, -0.47077690f, -0.27062524f,  0.30518577f,  0.35585700f},
                {-0.28422169f,  0.33969351f, -0.56700359f, -0.38788214f, -0.15789372f},
                { 0.18880503f,  0.39049353f, -0.26448028f,  0.50872376f,  0.42411327f},
                {-0.56957699f,  0.56761727f,  0.58111879f,  0.11662686f, -0.01347444f},
                {-0.06682433f, -0.04559753f,  0.15586923f, -0.68802278f,  0.60990585f},
                {-0.23677832f, -0.29935481f,  0.09428368f, -0.03224480f, -0.50781217f},
                { 0.16440378f, -0.31082218f,  0.40550635f,  0.09794049f,  0.19627380f}
        };

        float[][] V = {
                {-0.10646320f, -0.668422750f, -0.33744231f, -0.1953744f, -0.6243702f},
                { 0.48885825f,  0.546411345f, -0.57018018f, -0.2348795f, -0.2866678f},
                {-0.26164973f,  0.002221196f, -0.01788181f, -0.9049532f,  0.3350739f},
                {-0.02353895f,  0.306904408f,  0.68247803f, -0.2353931f, -0.6197333f},
                { 0.82502638f, -0.400562630f,  0.30810911f, -0.1797507f,  0.1778750f}
        };

        FloatMatrix.SVD svd = new FloatMatrix(A).svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-4f));

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-4f);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-4f);
            }
        }
    }

    @Test
    public void testSVD5() {
        System.out.println("SVD m = n+3");
        float[][] A = {
                { 1.19720880f, -1.8391378f,  0.3019585f, -1.1165701f},
                { 0.06605075f,  1.0315583f,  0.8294362f, -0.3646043f},
                {-1.02637715f,  1.0747931f, -0.8089055f, -0.4726863f},
                {-1.45817729f, -0.8942353f,  0.3459245f,  1.5068363f},
                {-0.07318103f, -0.2783787f,  1.2237598f,  0.1995332f},
                { 0.88248425f, -0.9360321f,  0.1393172f,  0.1393281f},
                { 0.12641406f, -0.8710055f, -0.2712301f,  0.2296515f}
        };

        float[] s = {3.2188437f, 2.5504483f, 1.7163918f, 0.9212875f};

        float[][] U = {
                {-0.7390710f,  0.15540183f,  0.093738524f, -0.60555964f},
                { 0.1716777f,  0.26405327f, -0.616548935f, -0.11171885f},
                { 0.4583068f,  0.19615535f,  0.365025826f, -0.56118537f},
                { 0.1185448f, -0.88710768f, -0.004538332f, -0.24629659f},
                {-0.1055393f, -0.19831478f, -0.634814754f, -0.26986239f},
                {-0.3836089f, -0.06331799f,  0.006896881f,  0.41026537f},
                {-0.2047156f, -0.19326474f,  0.273456965f,  0.06389058f}
        };

        float[][] V = {
                {-0.5820171f,  0.4822386f, -0.12201399f,  0.6432842f},
                { 0.7734720f,  0.4993237f, -0.27962029f,  0.2724507f},
                {-0.1670058f, -0.1563235f, -0.94966302f, -0.2140379f},
                { 0.1873664f, -0.7026270f, -0.07117046f,  0.6827473f}
        };

        FloatMatrix.SVD svd = new FloatMatrix(A).svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-6f));

        assertEquals(U.length, svd.U.nrow());
        assertEquals(U[0].length, svd.U.ncol());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6f);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        assertEquals(V[0].length, svd.V.ncol());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6f);
            }
        }
    }

    @Test
    public void testSVD6() {
        System.out.println("SVD m = n-1");
        float[][] A = {
                { 1.19720880f, -1.8391378f,  0.3019585f, -1.1165701f, -1.7210814f,  0.4918882f, -0.04247433f},
                { 0.06605075f,  1.0315583f,  0.8294362f, -0.3646043f, -1.6038017f, -0.9188110f, -0.63760340f},
                {-1.02637715f,  1.0747931f, -0.8089055f, -0.4726863f, -0.2064826f, -0.3325532f,  0.17966051f},
                {-1.45817729f, -0.8942353f,  0.3459245f,  1.5068363f, -2.0180708f, -0.3696350f, -1.19575563f},
                {-0.07318103f, -0.2783787f,  1.2237598f,  0.1995332f,  0.2545336f, -0.1392502f, -1.88207227f},
                { 0.88248425f, -0.9360321f,  0.1393172f,  0.1393281f, -0.3277873f, -0.5553013f,  1.63805985f}
        };

        float[] s = {3.8244094f, 3.4392541f, 2.3784254f, 2.1694244f, 1.5150752f, 0.4743856f};

        float[][] U = {
                { 0.31443091f,  0.77409564f, -0.06404561f,  0.2362505f,  0.48411517f,  0.08732402f},
                { 0.37429954f, -0.08997642f,  0.33948894f,  0.7403030f, -0.37663472f, -0.21598420f},
                {-0.08460683f, -0.30944648f,  0.49768196f,  0.1798789f,  0.40657776f,  0.67211259f},
                { 0.78096534f, -0.13597601f,  0.27845058f, -0.5407806f,  0.01748391f, -0.03632677f},
                { 0.35762337f, -0.18789909f, -0.68652942f,  0.1670810f, -0.20242003f,  0.54459792f},
                {-0.12678093f,  0.49307962f,  0.29000123f, -0.2083774f, -0.64590538f,  0.44281315f}
        };

        float[][] V = {
                {-0.2062642f,  0.54825338f, -0.27956720f,  0.3408979f, -0.2925761f, -0.412469519f},
                {-0.2516350f, -0.62127194f,  0.28319612f,  0.5322251f, -0.1297528f, -0.410270389f},
                { 0.3043552f,  0.05848409f, -0.35475338f,  0.2434904f, -0.5456797f,  0.040325347f},
                { 0.2047160f, -0.24974630f,  0.01491918f, -0.6588368f, -0.4616580f, -0.465507184f},
                {-0.6713331f, -0.30795185f, -0.57548345f, -0.1976943f, -0.1242132f,  0.261610893f},
                {-0.1122210f,  0.10728081f, -0.28476779f, -0.1527923f,  0.5474147f, -0.612188492f},
                {-0.5443460f,  0.37590198f,  0.55072289f, -0.2115256f, -0.2675392f, -0.003003781f}
        };

        FloatMatrix.SVD svd = new FloatMatrix(A).svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-6f));

        assertEquals(U.length, svd.U.nrow());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-4f);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-4f);
            }
        }
    }

    @Test
    public void testSVD7() {
        System.out.println("SVD m = n-2");
        float[][] A = {
                { 1.19720880f, -1.8391378f,  0.3019585f, -1.1165701f, -1.7210814f,  0.4918882f, -0.04247433f},
                { 0.06605075f,  1.0315583f,  0.8294362f, -0.3646043f, -1.6038017f, -0.9188110f, -0.63760340f},
                {-1.02637715f,  1.0747931f, -0.8089055f, -0.4726863f, -0.2064826f, -0.3325532f,  0.17966051f},
                {-1.45817729f, -0.8942353f,  0.3459245f,  1.5068363f, -2.0180708f, -0.3696350f, -1.19575563f},
                {-0.07318103f, -0.2783787f,  1.2237598f,  0.1995332f,  0.2545336f, -0.1392502f, -1.88207227f}
        };

        float[] s = {3.8105658f, 3.0883849f, 2.2956507f, 2.0984771f, 0.9019027f};

        float[][] U = {
                { 0.4022505f, -0.8371341f,  0.218900330f, -0.01150020f,  0.29891712f},
                { 0.3628648f,  0.1788073f,  0.520476180f,  0.66921454f, -0.34294833f},
                {-0.1204081f,  0.3526074f,  0.512685919f, -0.03159520f,  0.77286790f},
                { 0.7654028f,  0.3523577f, -0.005786511f, -0.53518467f, -0.05955197f},
                { 0.3258590f,  0.1369180f, -0.646766462f,  0.51439164f,  0.43836489f}
        };

        float[][] V = {
                {-0.1340510f, -0.6074832f, -0.07579249f,  0.38390363f, -0.4471462f},
                {-0.3332981f,  0.5665840f,  0.37922383f,  0.48268873f, -0.1570301f},
                { 0.3105522f, -0.0324612f, -0.30945577f,  0.48678825f, -0.3365301f},
                { 0.1820803f,  0.4083420f, -0.35471238f, -0.43842294f, -0.6389961f},
                {-0.7114696f,  0.1311251f, -0.64046888f,  0.07815179f,  0.1194533f},
                {-0.1112159f, -0.2728406f, -0.19551704f, -0.23056606f,  0.1841538f},
                {-0.4720051f, -0.2247534f,  0.42477493f, -0.36219292f, -0.4534882f}
        };

        FloatMatrix.SVD svd = new FloatMatrix(A).svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-6f));

        assertEquals(U.length, svd.U.nrow());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6f);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6f);
            }
        }
    }

    @Test
    public void testSVD8() {
        System.out.println("SVD m = n-3");
        float[][] A = {
                { 1.19720880f, -1.8391378f,  0.3019585f, -1.1165701f, -1.7210814f,  0.4918882f, -0.04247433f},
                { 0.06605075f,  1.0315583f,  0.8294362f, -0.3646043f, -1.6038017f, -0.9188110f, -0.63760340f},
                {-1.02637715f,  1.0747931f, -0.8089055f, -0.4726863f, -0.2064826f, -0.3325532f,  0.17966051f},
                {-1.45817729f, -0.8942353f,  0.3459245f,  1.5068363f, -2.0180708f, -0.3696350f, -1.19575563f}
        };

        float[] s = {3.668957f, 3.068763f, 2.179053f, 1.293110f};

        float[][] U = {
                {-0.4918880f,  0.7841689f, -0.1533124f,  0.34586230f},
                {-0.3688033f, -0.2221466f, -0.8172311f, -0.38310353f},
                { 0.1037476f, -0.3784190f, -0.3438745f,  0.85310363f},
                {-0.7818356f, -0.4387814f,  0.4363243f,  0.07632262f}
        };

        float[][] V = {
                { 0.11456074f,  0.63620515f, -0.23901163f, -0.4625536f},
                { 0.36382542f, -0.54930940f, -0.60614838f, -0.1412273f},
                {-0.22044591f,  0.06740501f, -0.13539726f, -0.6782114f},
                {-0.14811938f, -0.41609019f,  0.59161667f, -0.4135324f},
                { 0.81615679f, -0.00968160f,  0.35107473f, -0.2405136f},
                { 0.09577622f,  0.28606564f,  0.28844835f,  0.1625626f},
                { 0.32967585f,  0.18412070f, -0.02567023f,  0.2254902f}
        };

        FloatMatrix.SVD svd = new FloatMatrix(A).svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-6f));

        assertEquals(U.length, svd.U.nrow());
        for (int i = 0; i < U.length; i++) {
            for (int j = 0; j < U[i].length; j++) {
                assertEquals(Math.abs(U[i][j]), Math.abs(svd.U.get(i, j)), 1E-6f);
            }
        }

        assertEquals(V.length, svd.V.nrow());
        for (int i = 0; i < V.length; i++) {
            for (int j = 0; j < V[i].length; j++) {
                assertEquals(Math.abs(V[i][j]), Math.abs(svd.V.get(i, j)), 1E-6f);
            }
        }
    }

    @Test
    public void testSVD9() {
        System.out.println("SVD sparse matrix");
        float[][] A = {
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

        float[] s = {3.34088f, 2.5417f, 2.35394f, 1.64453f, 1.50483f, 1.30638f, 0.845903f, 0.560134f, 0.363677f};

        float[][] Vt = {
                { 0.197393f,   0.60599f,   0.462918f,   0.542114f,   0.279469f,  0.00381521f,  0.0146315f, 0.0241368f,   0.0819574f},
                { 0.0559135f, -0.165593f,  0.127312f,   0.231755f,  -0.106775f,  -0.192848f,  -0.437875f, -0.615122f,   -0.529937f},
                {-0.11027f,    0.497326f, -0.207606f,  -0.569921f,   0.50545f,   -0.0981842f, -0.192956f, -0.252904f,   -0.0792731f},
                {-0.949785f,  -0.0286489f, 0.0416092f,  0.267714f,   0.150035f,   0.0150815f,  0.0155072f, 0.010199f,   -0.0245549f},
                {-0.0456786f,  0.206327f, -0.378336f,   0.205605f,  -0.327194f,  -0.394841f,  -0.349485f, -0.149798f,    0.601993f},
                {-0.0765936f, -0.256475f,  0.7244f,    -0.368861f,   0.034813f,  -0.300161f,  -0.212201f,  9.74342e-05f, 0.362219f},
                {-0.177318f,   0.432984f,  0.23689f,   -0.2648f,    -0.672304f,   0.34084f,    0.152195f, -0.249146f,   -0.0380342f},
                {-0.0143933f,  0.0493053f, 0.0088255f, -0.0194669f, -0.0583496f,  0.454477f,  -0.761527f,  0.449643f,   -0.0696375f},
                {-0.0636923f,  0.242783f,  0.0240769f, -0.0842069f, -0.262376f,  -0.619847f,  0.0179752f,  0.51989f,    -0.453507f}
        };

        float[][] Ut = {
                { 0.221351f,   0.197645f,   0.24047f,   0.403599f,    0.644481f,  0.265037f,   0.265037f,   0.300828f,  0.205918f,  0.0127462f, 0.0361358f,    0.0317563f},
                { 0.11318f,    0.0720878f, -0.043152f, -0.0570703f,   0.167301f, -0.10716f,   -0.10716f,    0.14127f,  -0.273647f, -0.490162f, -0.622785f,    -0.450509f},
                {-0.288958f,  -0.13504f,    0.164429f,  0.337804f,   -0.361148f,  0.425998f,   0.425998f,  -0.330308f,  0.177597f, -0.23112f,  -0.223086f,    -0.141115f},
                {-0.414751f,  -0.55224f,   -0.594962f,  0.0991137f,   0.333462f,  0.0738122f,  0.0738122f,  0.188092f, -0.0323519f, 0.024802f,  0.000700072f, -0.00872947f},
                { 0.106275f,  -0.281769f,   0.106755f, -0.331734f,    0.158955f, -0.0803194f, -0.0803194f, -0.114785f,  0.53715f,  -0.59417f,   0.0682529f,    0.300495f},
                {-0.340983f,   0.495878f,  -0.254955f,  0.384832f,   -0.206523f, -0.169676f,  -0.169676f,   0.272155f,  0.080944f, -0.392125f,  0.114909f,     0.277343f},
                {-0.522658f,   0.0704234f,  0.30224f,  -0.00287218f,  0.165829f, -0.282916f,  -0.282916f,  -0.0329941f, 0.466898f,  0.288317f, -0.159575f,    -0.339495f},
                {-0.0604501f, -0.00994004f, 0.062328f, -0.000390504f, 0.034272f, -0.0161465f, -0.0161465f, -0.018998f, -0.0362988f, 0.254568f, -0.681125f,     0.6784180f},
                {-0.406678f,  -0.10893f,    0.492444f,  0.0123293f,   0.270696f, -0.0538747f, -0.0538747f, -0.165339f, -0.579426f, -0.225424f,  0.231961f,     0.182535f}
        };

        FloatMatrix.SVD svd = new FloatMatrix(A).svd();
        assertTrue(MathEx.equals(s, svd.s, 1E-5f));

        assertEquals(Ut[0].length, svd.U.nrow());
        assertEquals(Ut.length, svd.U.ncol());
        for (int i = 0; i < Ut.length; i++) {
            for (int j = 0; j < Ut[i].length; j++) {
                assertEquals(Math.abs(Ut[i][j]), Math.abs(svd.U.get(j, i)), 1E-5f);
            }
        }

        assertEquals(Vt[0].length, svd.V.nrow());
        assertEquals(Vt.length, svd.V.ncol());
        for (int i = 0; i < Vt.length; i++) {
            for (int j = 0; j < Vt[i].length; j++) {
                assertEquals(Math.abs(Vt[i][j]), Math.abs(svd.V.get(j, i)), 1E-5f);
            }
        }
    }

    @Test
    public void testPinv() {
        System.out.println("SVD pinv");
        float[][] A = {
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

        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix pinv = a.svd().pinv();

        FloatMatrix x = pinv.mm(a).mm(pinv);
        assertTrue(x.equals(pinv, 1E-5f));

        x = a.mm(pinv).mm(a);
        assertTrue(x.equals(a, 1E-5f));
    }

    @Test
    public void testSVDSolve() {
        System.out.println("SVD solve");
        float[][] A = {
            {0.9000f, 0.4000f, 0.7000f},
            {0.4000f, 0.5000f, 0.3000f},
            {0.7000f, 0.3000f, 0.8000f}
        };
        float[] b = {0.5f, 0.5f, 0.5f};
        float[] x = {-0.2027027f, 0.8783784f, 0.4729730f};

        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix.SVD svd = a.svd();
        float[] x2 = svd.solve(b);
        assertEquals(x.length, x2.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], x2[i], 1E-6f);
        }
    }
}
