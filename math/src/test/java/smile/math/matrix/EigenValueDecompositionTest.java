/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.math.matrix;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.Math;

/**
 *
 * @author Haifeng Li
 */
public class EigenValueDecompositionTest {
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
    
    double[][] A2 = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.8000, 0.3000, 0.8000}
    };
    double[][] eigenVectors2 = {
        {0.7178958,  0.05322098,  0.6812010},
        {0.3837711, -0.84702111, -0.1494582},
        {0.6952105,  0.43984484, -0.7036135}
    };
    double[] eigenValues2 = {1.79171122, 0.31908143, 0.08920735};

    public EigenValueDecompositionTest() {
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
    public void testDecompose() {
        System.out.println("decompose");
        EigenValueDecomposition result = new EigenValueDecomposition(new ColumnMajorMatrix(A), true);
        assertTrue(Math.equals(eigenValues, result.getEigenValues(), 1E-7));

        assertEquals(eigenVectors.length,    result.getEigenVectors().nrows());
        assertEquals(eigenVectors[0].length, result.getEigenVectors().ncols());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(result.getEigenVectors().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testDecompose2() {
        System.out.println("decompose");
        EigenValueDecomposition result = new EigenValueDecomposition(new ColumnMajorMatrix(A), true, true);
        assertTrue(Math.equals(eigenValues, result.getEigenValues(), 1E-7));
        assertTrue(null == result.getEigenVectors());
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testDecompose3() {
        System.out.println("decompose");
        EigenValueDecomposition result = new EigenValueDecomposition(A2);
        assertTrue(Math.equals(eigenValues2, result.getEigenValues(), 1E-7));

        assertEquals(eigenVectors2.length,    result.getEigenVectors().nrows());
        assertEquals(eigenVectors2[0].length, result.getEigenVectors().ncols());
        for (int i = 0; i < eigenVectors2.length; i++) {
            for (int j = 0; j < eigenVectors2[i].length; j++) {
                assertEquals(Math.abs(eigenVectors2[i][j]), Math.abs(result.getEigenVectors().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testDecompose4() {
        System.out.println("decompose");
        EigenValueDecomposition result = new EigenValueDecomposition(A2, false, true);
        assertTrue(Math.equals(eigenValues2, result.getEigenValues(), 1E-7));
        assertTrue(null == result.getEigenVectors());
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testLanczos() {
        System.out.println("Lanczos");
        EigenValueDecomposition result = Lanczos.eigen(Matrix.newInstance(A), 3);
        assertTrue(Math.equals(eigenValues, result.getEigenValues(), 1E-7));

        assertEquals(eigenVectors.length,    result.getEigenVectors().nrows());
        assertEquals(eigenVectors[0].length, result.getEigenVectors().ncols());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(result.getEigenVectors().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testLanczos1() {
        System.out.println("Lanczos1");
        EigenValueDecomposition result = Lanczos.eigen(Matrix.newInstance(A), 1);
        assertEquals(eigenValues[0], result.getEigenValues()[0], 1E-4);

        for (int i = 0; i < 3; i++) {
            assertEquals(Math.abs(eigenVectors[i][0]), Math.abs(result.getEigenVectors().get(i, 0)), 1E-4);
        }
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testLanczos2() {
        System.out.println("Lanczos2");
        A = new double[500][500];
        A[0][0] = A[1][1] = A[2][2] = A[3][3] = 2.0;
        for (int i = 4; i < 500; i++)
            A[i][i] = (500 - i) / 500.0;
        EigenValueDecomposition result = Lanczos.eigen(Matrix.newInstance(A), 6);
        assertEquals(2.0, result.getEigenValues()[0], 1E-4);
        assertEquals(2.0, result.getEigenValues()[1], 1E-4);
        assertEquals(2.0, result.getEigenValues()[2], 1E-4);
        assertEquals(2.0, result.getEigenValues()[3], 1E-4);
        assertEquals(0.992, result.getEigenValues()[4], 1E-4);
        assertEquals(0.990, result.getEigenValues()[5], 1E-4);
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testEigen() {
        System.out.println("Eigen");
        double[] v = new double[3];
        for (int i = 0; i < v.length; i++)
            v[i] = 1.0;

        double eigenvalue = PowerIteration.eigen(Matrix.newInstance(A), v, 1E-6);
        assertEquals(eigenValues[0], eigenvalue, 1E-4);

        double ratio = Math.abs(eigenVectors[0][0]/v[0]);
        for (int i = 1; i < 3; i++) {
            assertEquals(ratio, Math.abs(eigenVectors[i][0]/v[i]), 1E-4);
        }

        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++)
                A[i][j] = -A[i][j];
        }

        for (int i = 0; i < v.length; i++)
            v[i] = 1.0;

        eigenvalue = PowerIteration.eigen(Matrix.newInstance(A), v, 0.22, 1E-4, 4);
        assertEquals(-eigenValues[0], eigenvalue, 1E-3);

        ratio = Math.abs(eigenVectors[0][0]/v[0]);
        for (int i = 1; i < 3; i++) {
            assertEquals(ratio, Math.abs(eigenVectors[i][0]/v[i]), 1E-3);
        }
    }
}