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

/**
 *
 * @author Haifeng Li
 */
public class CholeskyDecompositionTest {
    double[][] A = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
    };
    double[][] L = {
        {0.9486833, 0.00000000, 0.0000000},
        {0.4216370, 0.56764621, 0.0000000},
        {0.7378648, -0.01957401, 0.5051459}
    };
    double[] B = {0.5, 0.5, 0.5};
    double[] X = {-0.2027027, 0.8783784, 0.4729730};
    double[][] B2 = {
        {0.5, 0.2},
        {0.5, 0.8},
        {0.5, 0.3}
    };
    double[][] X2 = {
        {-0.2027027, -1.2837838},
        {0.8783784, 2.2297297},
        {0.4729730, 0.6621622}
    };

    public CholeskyDecompositionTest() {
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
     * Test of decompose method, of class CholeskyDecomposition.
     */
    @Test
    public void testDecompose() {
        System.out.println("decompose");
        CholeskyDecomposition result = new CholeskyDecomposition(A);
        assertEquals(L.length, result.getL().length);
        assertEquals(L[2].length, result.getL()[2].length);
        for (int i = 0; i < result.getL().length; i++) {
            for (int j = 0; j < result.getL()[i].length; j++) {
                assertEquals(Math.abs(L[i][j]), Math.abs(result.getL()[i][j]), 1E-7);
            }
        }
    }

    /**
     * Test of decompose method, of class CholeskyDecomposition.
     */
    @Test
    public void testDecomposeoverwrite() {
        System.out.println("decompose in place");
        CholeskyDecomposition result = new CholeskyDecomposition(A, true);
        assertEquals(L.length, result.getL().length);
        assertEquals(L[0].length, result.getL()[0].length);
        for (int i = 0; i < L.length; i++) {
            for (int j = 0; j < L[i].length; j++) {
                assertEquals(Math.abs(L[i][j]), Math.abs(result.getL()[i][j]), 1E-7);
            }
        }
    }

    /**
     * Test of solve method, of class CholeskyDecomposition.
     */
    @Test
    public void testSolve() {
        System.out.println("solve");
        CholeskyDecomposition result = new CholeskyDecomposition(A);
        double[] x = new double[B.length];
        result.solve(B, x);
        assertEquals(X.length, x.length);
        for (int i = 0; i < X.length; i++) {
            assertEquals(X[i], x[i], 1E-7);
        }
    }

    /**
     * Test of solve method, of class CholeskyDecomposition.
     */
    @Test
    public void testSolveoverwrite() {
        System.out.println("solve in place");
        CholeskyDecomposition result = new CholeskyDecomposition(A);
        double[] x = B;
        result.solve(B, x);
        assertEquals(X.length, x.length);
        for (int i = 0; i < X.length; i++) {
            assertEquals(X[i], x[i], 1E-7);
        }
    }

    /**
     * Test of solve method, of class CholeskyDecomposition.
     */
    @Test
    public void testSolveMatrix() {
        System.out.println("solve");
        CholeskyDecomposition result = new CholeskyDecomposition(A);
        double[][] x = new double[B2.length][B2[0].length];
        result.solve(B2, x);
        assertEquals(X.length, x.length);
        assertEquals(X2[0].length, x[0].length);
        for (int i = 0; i < X2.length; i++) {
            for (int j = 0; j < X2[i].length; j++) {
                assertEquals(X2[i][j], x[i][j], 1E-7);
            }
        }
    }

    /**
     * Test of solve method, of class CholeskyDecomposition.
     */
    @Test
    public void testSolveMatrixOverwrite() {
        System.out.println("solve in place");
        CholeskyDecomposition result = new CholeskyDecomposition(A);
        double[][] x = B2;
        result.solve(B2, x);
        assertEquals(X2.length, x.length);
        assertEquals(X2[0].length, x[0].length);
        for (int i = 0; i < X2.length; i++) {
            for (int j = 0; j < X2[i].length; j++) {
                assertEquals(X2[i][j], x[i][j], 1E-7);
            }
        }
    }
}