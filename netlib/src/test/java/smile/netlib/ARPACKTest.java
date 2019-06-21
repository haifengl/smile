/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.netlib;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.EVD;

/**
 *
 * @author Haifeng Li
 */
public class ARPACKTest {
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

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testARPACK() {
        System.out.println("ARPACK");
        DenseMatrix a = Matrix.of(A);
        a.setSymmetric(true);
        EVD result = ARPACK.eigen(a, 2, ARPACK.Ritz.SA);
        assertEquals(eigenValues[1], result.getEigenValues()[0], 1E-4);
        assertEquals(eigenValues[2], result.getEigenValues()[1], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][1]), Math.abs(result.getEigenVectors().get(i, 0)), 1E-4);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][2]), Math.abs(result.getEigenVectors().get(i, 1)), 1E-4);
        }
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testARPACK1() {
        System.out.println("ARPACK1");
        DenseMatrix a = Matrix.of(A);
        a.setSymmetric(true);
        EVD result = ARPACK.eigen(a, 1, ARPACK.Ritz.LA);
        assertEquals(eigenValues[0], result.getEigenValues()[0], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(result.getEigenVectors().get(i, 0)), 1E-4);
        }
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testARPACK2() {
        System.out.println("ARPACK2");
        A = new double[500][500];
        A[0][0] = A[1][1] = A[2][2] = A[3][3] = 2.0;
        for (int i = 4; i < 500; i++)
            A[i][i] = (500 - i) / 500.0;
        DenseMatrix a = Matrix.of(A);
        a.setSymmetric(true);
        EVD result = ARPACK.eigen(a, 6, ARPACK.Ritz.LA);
        assertEquals(2.0, result.getEigenValues()[0], 1E-4);
        assertEquals(2.0, result.getEigenValues()[1], 1E-4);
        assertEquals(2.0, result.getEigenValues()[2], 1E-4);
        assertEquals(2.0, result.getEigenValues()[3], 1E-4);
        assertEquals(0.992, result.getEigenValues()[4], 1E-4);
        assertEquals(0.990, result.getEigenValues()[5], 1E-4);
    }
}