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
import smile.math.matrix.EVD;

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
    double[][] eigenVectorsB = {
        {0.6706167,  0.05567643,  0.6876103},
        {0.3584968, -0.88610002, -0.1508644},
        {0.6494254,  0.46013791, -0.7102337}
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
        NLMatrix a = new NLMatrix(A);
        a.setSymmetric(true);
        EVD result = a.eigen();
        assertTrue(MathEx.equals(eigenValues, result.getEigenValues(), 1E-7));

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
        NLMatrix a = new NLMatrix(A);
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
        NLMatrix a = new NLMatrix(B);
        EVD result = a.eigen();
        assertTrue(MathEx.equals(eigenValuesB, result.getEigenValues(), 1E-7));

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
        NLMatrix a = new NLMatrix(B);
        double[] result = a.eig();
        assertEquals(2*a.nrows(), result.length);
        for (int i = 0; i < eigenValuesB.length; i++)
            assertEquals(eigenValuesB[i], result[i], 1E-7);
        for (int i = eigenValuesB.length; i < result.length; i++)
            assertEquals(0.0, result[i], 1E-7);
    }
}