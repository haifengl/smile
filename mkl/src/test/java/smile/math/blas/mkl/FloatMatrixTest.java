/*******************************************************************************
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
 ******************************************************************************/

package smile.math.blas.mkl;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import smile.math.matrix.FloatMatrix;
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
        System.out.println("nrows");
        assertEquals(3, matrix.nrows());
    }

    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(3, matrix.ncols());
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
    public void testAx() {
        System.out.println("ax");
        float[] d = new float[matrix.nrows()];
        matrix.mv(b, d);
        assertEquals(0.65f, d[0], 1E-6f);
        assertEquals(0.60f, d[1], 1E-6f);
        assertEquals(0.55f, d[2], 1E-6f);
    }

    @Test
    public void testAxpy() {
        System.out.println("axpy");
        float[] d = new float[matrix.nrows()];
        for (int i = 0; i < d.length; i++) d[i] = 1.0f;
        matrix.mv(NO_TRANSPOSE, 1.0f, b, 1.0f, d);
        assertEquals(1.65f, d[0], 1E-6f);
        assertEquals(1.60f, d[1], 1E-6f);
        assertEquals(1.55f, d[2], 1E-6f);
    }

    @Test
    public void testAxpy2() {
        System.out.println("axpy b = 2");
        float[] d = new float[matrix.nrows()];
        for (int i = 0; i < d.length; i++) d[i] = 1.0f;
        matrix.mv(NO_TRANSPOSE, 1.0f, b, 2.0f, d);
        assertEquals(2.65f, d[0], 1E-6f);
        assertEquals(2.60f, d[1], 1E-6f);
        assertEquals(2.55f, d[2], 1E-6f);
    }

    @Test
    public void testAtx() {
        System.out.println("atx");
        float[] d = new float[matrix.nrows()];
        matrix.mv(TRANSPOSE, b, d);
        assertEquals(0.65f, d[0], 1E-6f);
        assertEquals(0.60f, d[1], 1E-6f);
        assertEquals(0.55f, d[2], 1E-6f);
    }

    @Test
    public void testAtxpy() {
        System.out.println("atxpy");
        float[] d = new float[matrix.nrows()];
        for (int i = 0; i < d.length; i++) d[i] = 1.0f;
        matrix.mv(TRANSPOSE, 1.0f, b, 1.0f, d);
        assertEquals(1.65f, d[0], 1E-6f);
        assertEquals(1.60f, d[1], 1E-6f);
        assertEquals(1.55f, d[2], 1E-6f);
    }

    @Test
    public void testAtxpy2() {
        System.out.println("atxpy b = 2");
        float[] d = new float[matrix.nrows()];
        for (int i = 0; i < d.length; i++) d[i] = 1.0f;
        matrix.mv(TRANSPOSE, 1.0f, b, 2.0f, d);
        assertEquals(2.65f, d[0], 1E-6f);
        assertEquals(2.60f, d[1], 1E-6f);
        assertEquals(2.55f, d[2], 1E-6f);
    }

    @Test
    public void testAAT() {
        System.out.println("AAT");
        FloatMatrix c = matrix.aat();
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
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
        a.add(b);
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
        a.sub(b);
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
        float[][] F = b.abmm(a).transpose().toArray();

        assertTrue(MathEx.equals(a.abmm(b).toArray(),   C, 1E-6f));
        assertTrue(MathEx.equals(a.abtmm(b).toArray(),  D, 1E-6f));
        assertTrue(MathEx.equals(a.atbmm(b).toArray(),  E, 1E-6f));
        assertTrue(MathEx.equals(a.atbtmm(b).toArray(), F, 1E-6f));
    }

    @Test
    public void testLU() {
        System.out.println("LU");
        float[][] A = {
                {0.9000f, 0.4000f, 0.7000f},
                {0.4000f, 0.5000f, 0.3000f},
                {0.7000f, 0.3000f, 0.8000f}
        };
        float[] B = {0.5f, 0.5f, 0.5f};
        float[] X = {-0.2027027f, 0.8783784f, 0.4729730f};
        float[][] B2 = {
                {0.5f, 0.2f},
                {0.5f, 0.8f},
                {0.5f, 0.3f}
        };
        float[][] X2 = {
                {-0.2027027f, -1.2837838f},
                { 0.8783784f,  2.2297297f},
                { 0.4729730f,  0.6621622f}
        };

        FloatMatrix a = new FloatMatrix(A);
        FloatMatrix.LU lu = a.lu();
        lu.solve(B);
        assertEquals(X.length, B.length);
        for (int i = 0; i < X.length; i++) {
            assertEquals(X[i], B[i], 1E-6f);
        }

        FloatMatrix x = new FloatMatrix(B2);
        lu.solve(x);
        assertEquals(X2.length, x.nrows());
        assertEquals(X2[0].length, x.ncols());
        for (int i = 0; i < X2.length; i++) {
            for (int j = 0; j < X2[i].length; j++) {
                assertEquals(X2[i][j], x.get(i, j), 1E-6f);
            }
        }
    }
}
