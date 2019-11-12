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
import smile.math.matrix.LU;

/**
 *
 * @author Haifeng Li
 */
public class LUTest {
    double[][] A = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
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

    public LUTest() {
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

    @Test(expected = UnsupportedOperationException.class)
    public void testSolve() {
        System.out.println("solve");
        NDMatrix a = new NDMatrix(A);
        LU result = a.lu();
        double[] x = B.clone();
        result.solve(x);
        assertEquals(X.length, x.length);
        for (int i = 0; i < X.length; i++) {
            assertEquals(X[i], x[i], 1E-7);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSolveMatrix() {
        System.out.println("solve");
        NDMatrix a = new NDMatrix(A);
        LU result = a.lu();
        NDMatrix x = new NDMatrix(B2);
        result.solve(x);
        assertEquals(X2.length, x.nrows());
        assertEquals(X2[0].length, x.ncols());
        for (int i = 0; i < X2.length; i++) {
            for (int j = 0; j < X2[i].length; j++) {
                assertEquals(X2[i][j], x.get(i, j), 1E-7);
            }
        }
    }
}