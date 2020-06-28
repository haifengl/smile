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

package smile.math.blas.openblas;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.MathEx;
import smile.math.blas.UPLO;
import smile.math.matrix.FloatMatrix;
import static smile.math.matrix.FloatMatrix.EVD;

/**
 *
 * @author Haifeng Li
 */
public class EVDTest {
    float[][] A = {
        {0.9000f, 0.4000f, 0.7000f},
        {0.4000f, 0.5000f, 0.3000f},
        {0.7000f, 0.3000f, 0.8000f}
    };
    float[][] eigenVectors = {
        {0.6881997f, -0.07121225f,  0.7220180f},
        {0.3700456f,  0.89044952f, -0.2648886f},
        {0.6240573f, -0.44947578f, -0.6391588f}
    };
    float[] eigenValues = {1.7498382f, 0.3165784f, 0.1335834f};
    
    float[][] B = {
        {0.9000f, 0.4000f, 0.7000f},
        {0.4000f, 0.5000f, 0.3000f},
        {0.8000f, 0.3000f, 0.8000f}
    };
    float[][] eigenVectorsB = {
        {0.6706167f,  0.05567643f,  0.6876103f},
        {0.3584968f, -0.88610002f, -0.1508644f},
        {0.6494254f,  0.46013791f, -0.7102337f}
    };
    float[] eigenValuesB = {1.79171122f, 0.31908143f, 0.08920735f};

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

    @Test
    public void testDecompose() {
        System.out.println("decompose");
        FloatMatrix a = new FloatMatrix(A);
        a.uplo(UPLO.LOWER);
        EVD result = a.eigen();
        assertTrue(MathEx.equals(eigenValues, result.wr, 1E-7f));

        assertEquals(eigenVectors.length,    result.Vr.nrows());
        assertEquals(eigenVectors[0].length, result.Vr.ncols());
        for (int i = 0; i < eigenVectors.length; i++) {
            for (int j = 0; j < eigenVectors[i].length; j++) {
                assertEquals(Math.abs(eigenVectors[i][j]), Math.abs(result.Vr.get(i, j)), 1E-7f);
            }
        }
    }

    @Test
    public void testDecompose2() {
        System.out.println("decompose");
        FloatMatrix a = new FloatMatrix(A);
        a.uplo(UPLO.LOWER);
        EVD result = a.eigen(false, false);

        for (int i = 0; i < eigenValues.length; i++) {
            assertEquals(eigenValues[i], result.wr[i], 1E-7f);
        }
        assertEquals(null, result.wi);
    }

    @Test
    public void testDecompose3() {
        System.out.println("decompose");
        FloatMatrix a = new FloatMatrix(B);
        EVD result = a.eigen();
        assertTrue(MathEx.equals(eigenValuesB, result.wr, 1E-7f));

        assertEquals(eigenVectorsB.length,    result.Vr.nrows());
        assertEquals(eigenVectorsB[0].length, result.Vr.ncols());
        for (int i = 0; i < eigenVectorsB.length; i++) {
            for (int j = 0; j < eigenVectorsB[i].length; j++) {
                assertEquals(Math.abs(eigenVectorsB[i][j]), Math.abs(result.Vr.get(i, j)), 1E-7f);
            }
        }
    }

    @Test
    public void testDecompose4() {
        System.out.println("decompose");
        FloatMatrix a = new FloatMatrix(B);
        EVD result = a.eigen(false, false);
        for (int i = 0; i < eigenValuesB.length; i++) {
            assertEquals(eigenValuesB[i], result.wr[i], 1E-7f);
        }
        assertEquals(null, result.wi);
    }
}