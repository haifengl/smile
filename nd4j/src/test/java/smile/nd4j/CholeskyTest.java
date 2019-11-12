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

package smile.nd4j;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.math.matrix.Cholesky;

/**
 *
 * @author Haifeng Li
 */
public class CholeskyTest {
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
    double[] b = {0.5, 0.5, 0.5};
    double[] x = {-0.2027027, 0.8783784, 0.4729730};
    double[][] B = {
        {0.5, 0.2},
        {0.5, 0.8},
        {0.5, 0.3}
    };
    double[][] X = {
        {-0.2027027, -1.2837838},
        {0.8783784, 2.2297297},
        {0.4729730, 0.6621622}
    };

    public CholeskyTest() {
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
        NDMatrix a = new NDMatrix(A);
        Cholesky cholesky = a.cholesky();
        for (int i = 0; i < a.nrows(); i++) {
            for (int j = 0; j <= i; j++) {
                assertEquals(Math.abs(L[i][j]), Math.abs(a.get(i, j)), 1E-7);
            }
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSolve() {
        System.out.println("solve");
        NDMatrix a = new NDMatrix(A);
        Cholesky cholesky = a.cholesky();
        cholesky.solve(b);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], b[i], 1E-7);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSolveMatrix() {
        System.out.println("solve");
        NDMatrix a = new NDMatrix(A);
        Cholesky cholesky = a.cholesky();
        NDMatrix b = new NDMatrix(B);
        cholesky.solve(b);
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], b.get(i, j), 1E-7);
            }
        }
    }
}