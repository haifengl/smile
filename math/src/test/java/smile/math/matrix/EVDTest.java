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
public class EVDTest {
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
    
    double[][] B = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.8000, 0.3000, 0.8000}
    };
    // Note that this result is different from that of Netlib
    // Both are correct as the eigen vectors are not unique
    // (linear combination of eigen vectors are still eigen vectors).
    // We can verify that both results are correct by A * V = lambda * V.
    double[][] eigenVectorsB = {
        {0.7178958,  0.05322098,  0.6812010},
        {0.3837711, -0.84702111, -0.1494582},
        {0.6952105,  0.43984484, -0.7036135}
    };
    double[] eigenValuesB = {1.79171122, 0.31908143, 0.08920735};

    public EVDTest() {
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
        DenseMatrix a = new JMatrix(A);
        a.setSymmetric(true);
        EVD result = a.eigen();
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
        DenseMatrix a = new JMatrix(A);
        a.setSymmetric(true);
        double[] result = a.eig();
        assertEquals(2*a.nrows(), result.length);
        for (int i = 0; i < eigenValues.length; i++)
            assertEquals(eigenValues[i], result[i], 1E-7);
        for (int i = eigenValues.length; i < result.length; i++)
            assertEquals(0.0, result[i], 1E-7);
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testDecompose3() {
        System.out.println("decompose");
        DenseMatrix a = new JMatrix(B);
        EVD result = a.eigen();
        assertTrue(Math.equals(eigenValuesB, result.getEigenValues(), 1E-7));

        assertEquals(eigenVectorsB.length,    result.getEigenVectors().nrows());
        assertEquals(eigenVectorsB[0].length, result.getEigenVectors().ncols());
        for (int i = 0; i < eigenVectorsB.length; i++) {
            for (int j = 0; j < eigenVectorsB[i].length; j++) {
                assertEquals(Math.abs(eigenVectorsB[i][j]), Math.abs(result.getEigenVectors().get(i, j)), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class EigenValueDecomposition.
     */
    @Test
    public void testDecompose4() {
        System.out.println("decompose");
        DenseMatrix a = new JMatrix(B);
        double[] result = a.eig();
        assertEquals(2*a.nrows(), result.length);
        for (int i = 0; i < eigenValuesB.length; i++)
            assertEquals(eigenValuesB[i], result[i], 1E-7);
        for (int i = eigenValuesB.length; i < result.length; i++)
            assertEquals(0.0, result[i], 1E-7);
    }
}