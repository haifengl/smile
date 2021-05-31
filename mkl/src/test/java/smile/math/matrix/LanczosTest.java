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
import smile.math.MathEx;
import smile.math.blas.UPLO;

/**
 *
 * @author Haifeng Li
 */
public class LanczosTest {
    double[][] A = {
            {0.9000, 0.4000, 0.7000},
            {0.4000, 0.5000, 0.3000},
            {0.7000, 0.3000, 0.8000}
    };
    double[][] eigenVectors = {
            {0.6881997, -0.07121225,  0.7220180},
            {0.3700456,  0.89044952, -0.2648886},
            {0.6240573, -0.44947578, -0.6391588}
    };
    double[] eigenValues = {1.7498382, 0.3165784, 0.1335834};

    public LanczosTest() {
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
    public void testLanczos() {
        System.out.println("eigen");
        Matrix a = Matrix.of(A);
        a.uplo(UPLO.LOWER);
        Matrix.EVD result = Lanczos.eigen(a, 3);
        assertTrue(MathEx.equals(eigenValues, result.wr, 1E-7));

        assertEquals(eigenVectors.length,    result.Vr.nrow());
        assertEquals(eigenVectors[0].length, result.Vr.ncol());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(result.Vr.get(i, j)), 1E-7);
            }
        }
    }

    @Test
    public void testEigen1() {
        System.out.println("eigen1");
        Matrix a = Matrix.of(A);
        a.uplo(UPLO.LOWER);
        Matrix.EVD result = Lanczos.eigen(a, 1);
        assertEquals(eigenValues[0], result.wr[0], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(result.Vr.get(i, 0)), 1E-4);
        }
    }

    @Test
    public void testEigen2() {
        System.out.println("eigen2");
        A = new double[500][500];
        A[0][0] = A[1][1] = A[2][2] = A[3][3] = 2.0;
        for (int i = 4; i < 500; i++)
            A[i][i] = (500 - i) / 500.0;
        Matrix a = Matrix.of(A);
        a.uplo(UPLO.LOWER);
        Matrix.EVD result = Lanczos.eigen(a, 6);
        assertEquals(2.0, result.wr[0], 1E-4);
        assertEquals(2.0, result.wr[1], 1E-4);
        assertEquals(2.0, result.wr[2], 1E-4);
        assertEquals(2.0, result.wr[3], 1E-4);
        assertEquals(0.992, result.wr[4], 1E-4);
        assertEquals(0.990, result.wr[5], 1E-4);
    }
}