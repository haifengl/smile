/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import java.util.Arrays;
import org.junit.jupiter.api.*;
import smile.linalg.UPLO;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class EigenTest {
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
    
    public EigenTest() {
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
    public void testPower() {
        System.out.println("Power");
        double[] v = new double[3];
        Arrays.fill(v, 1.0);

        DenseMatrix matrix = DenseMatrix.of(A);
        double eigenvalue = Eigen.power(matrix, Vector.column(v));
        assertEquals(eigenValues[0], eigenvalue, 1E-4);

        double ratio = Math.abs(eigenVectors[0][0]/v[0]);
        for (int i = 1; i < 3; i++) {
            assertEquals(ratio, Math.abs(eigenVectors[i][0]/v[i]), 1E-4);
        }

        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++) {
                A[i][j] = -A[i][j];
            }
        }

        Arrays.fill(v, 1.0);
        matrix = DenseMatrix.of(A);
        eigenvalue = Eigen.power(matrix, Vector.column(v), 0.22, 1E-4, 4);
        assertEquals(-eigenValues[0], eigenvalue, 1E-3);

        ratio = Math.abs(eigenVectors[0][0]/v[0]);
        for (int i = 1; i < 3; i++) {
            assertEquals(ratio, Math.abs(eigenVectors[i][0]/v[i]), 1E-3);
        }
    }

    @Test
    public void testLanczos() {
        System.out.println("Lanczos(3)");
        DenseMatrix a = DenseMatrix.of(A);
        a.withUplo(UPLO.LOWER);
        EVD result = Eigen.of(a, 3);
        for (int i = 0; i < eigenValues.length; i++) {
            assertEquals(eigenValues[i], result.wr().get(i), 1E-7);
        }

        assertEquals(eigenVectors.length,    result.Vr().nrow());
        assertEquals(eigenVectors[0].length, result.Vr().ncol());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(result.Vr().get(i, j)), 1E-7);
            }
        }
    }

    @Test
    public void testLanczos1() {
        System.out.println("Lanczos(1)");
        DenseMatrix a = DenseMatrix.of(A);
        a.withUplo(UPLO.LOWER);
        EVD result = Eigen.of(a, 1);
        assertEquals(eigenValues[0], result.wr().get(0), 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(result.Vr().get(i, 0)), 1E-4);
        }
    }

    @Test
    public void testLanczos2() {
        System.out.println("Lanczos lambda = 2");
        double[][] A = new double[500][500];
        A[0][0] = A[1][1] = A[2][2] = A[3][3] = 2.0;
        for (int i = 4; i < 500; i++) {
            A[i][i] = (500 - i) / 500.0;
        }
        DenseMatrix a = DenseMatrix.of(A);
        a.withUplo(UPLO.LOWER);
        EVD result = Eigen.of(a, 6);
        assertEquals(2.0, result.wr().get(0), 1E-4);
        assertEquals(2.0, result.wr().get(1), 1E-4);
        assertEquals(2.0, result.wr().get(2), 1E-4);
        assertEquals(2.0, result.wr().get(3), 1E-4);
        assertEquals(0.992, result.wr().get(4), 1E-4);
        assertEquals(0.990, result.wr().get(5), 1E-4);
    }
}
