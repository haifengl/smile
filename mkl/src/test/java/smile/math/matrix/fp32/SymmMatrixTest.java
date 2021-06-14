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

package smile.math.matrix.fp32;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.blas.UPLO;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class SymmMatrixTest {

    public SymmMatrixTest() {
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
    public void test() {
        System.out.println("SymmMatrix");
        float[][] A = {
            {0.9000f, 0.4000f, 0.0000f},
            {0.4000f, 0.5000f, 0.3000f},
            {0.0000f, 0.3000f, 0.8000f}
        };
        float[] b = {0.5f, 0.5f, 0.5f};

        Matrix a = Matrix.of(A);
        Matrix.LU lu = a.lu();
        float[] x = lu.solve(b);

        SymmMatrix symm = new SymmMatrix(UPLO.LOWER, A);

        float[] y = a.mv(x);
        float[] y2 = symm.mv(x);
        for (int i = 0; i < y.length; i++) {
            assertEquals(y[i], y2[i], 1E-7f);
        }

        SymmMatrix.BunchKaufman bk = symm.bk();
        float[] bkx = bk.solve(b);

        // determinant
        assertEquals(lu.det(), bk.det(), 1E-7f);
        // solution vector
        assertEquals(x.length, bkx.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], bkx[i], 1E-7f);
        }

        SymmMatrix.Cholesky cholesky = symm.cholesky();
        float[] choleskyx = cholesky.solve(b);

        // determinant
        assertEquals(lu.det(), cholesky.det(), 1E-7f);
        // solution vector
        assertEquals(choleskyx.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], choleskyx[i], 1E-6f);
        }

        // Upper symmetric matrix
        symm = new SymmMatrix(UPLO.UPPER, A);

        y = a.mv(x);
        y2 = symm.mv(x);
        for (int i = 0; i < y.length; i++) {
            assertEquals(y[i], y2[i], 1E-7f);
        }

        bk = symm.bk();
        bkx = bk.solve(b);

        // determinant
        assertEquals(lu.det(), bk.det(), 1E-7f);
        // solution vector
        assertEquals(x.length, bkx.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], bkx[i], 1E-6f);
        }

        cholesky = symm.cholesky();
        choleskyx = cholesky.solve(b);

        // determinant
        assertEquals(lu.det(), cholesky.det(), 1E-7f);
        // solution vector
        assertEquals(choleskyx.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], choleskyx[i], 1E-7f);
        }
    }
}