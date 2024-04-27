/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.matrix;

import smile.math.blas.UPLO;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BandMatrixTest {

    public BandMatrixTest() {
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
    public void test() {
        System.out.println("BandMatrix");
        double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
        };
        double[] b = {0.5, 0.5, 0.5};

        Matrix a = Matrix.of(A);
        Matrix.LU lu = a.lu();
        double[] x = lu.solve(b);

        BandMatrix band = new BandMatrix(3, 3, 1, 1);
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++) {
                if (A[i][j] != 0.0f) {
                    band.set(i, j, A[i][j]);
                }
            }
        }

        double[] y = a.mv(x);
        double[] y2 = band.mv(x);
        for (int i = 0; i < y.length; i++) {
            assertEquals(y[i], y2[i], 1E-7f);
        }

        y = a.tv(x);
        y2 = band.tv(x);
        for (int i = 0; i < y.length; i++) {
            assertEquals(y[i], y2[i], 1E-7f);
        }

        BandMatrix.LU bandlu = band.lu();
        double[] lux = bandlu.solve(b);

        // determinant
        assertEquals(lu.det(), bandlu.det(), 1E-7f);
        // solution vector
        assertEquals(x.length, lux.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], lux[i], 1E-7f);
        }

        // Upper band matrix
        band.uplo(UPLO.UPPER);
        BandMatrix.Cholesky cholesky = band.cholesky();
        double[] choleskyx = cholesky.solve(b);

        // determinant
        assertEquals(lu.det(), cholesky.det(), 1E-7f);
        // solution vector
        assertEquals(choleskyx.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], choleskyx[i], 1E-6f);
        }

        // Lower band matrix
        band.uplo(UPLO.LOWER);
        cholesky = band.cholesky();
        choleskyx = cholesky.solve(b);

        // determinant
        assertEquals(lu.det(), cholesky.det(), 1E-7f);
        // solution vector
        assertEquals(choleskyx.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], choleskyx[i], 1E-6f);
        }
    }
}