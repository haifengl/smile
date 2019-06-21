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
public class LUTest {
    double[][] A = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
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

    /**
     * Test of solve method, of class LUDecomposition.
     */
    @Test
    public void testSolve() {
        System.out.println("solve a vector");
        JMatrix a = new JMatrix(A);
        LU result = a.lu();
        result.solve(b);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], b[i], 1E-7);
        }
    }

    /**
     * Test of solve method, of class LUDecomposition.
     */
    @Test
    public void testSolveMatrix() {
        System.out.println("solve a matrix");
        JMatrix a = new JMatrix(A);
        LU result = a.lu();
        JMatrix b = new JMatrix(B);
        result.solve(b);
        for (int i = 0; i < X.length; i++) {
            for (int j = 0; j < X[i].length; j++) {
                assertEquals(X[i][j], b.get(i, j), 1E-7);
            }
        }
    }
}