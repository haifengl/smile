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

import static org.junit.Assert.assertEquals;

/**
 * @author Haifeng Li
 */
public class BiconjugateGradientTest {
    double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};

    double[] x = new double[b.length];

    public BiconjugateGradientTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        System.out.println("setUp");

        DenseMatrix a = Matrix.of(A);
        LU lu = a.lu();
        x = b.clone();
        lu.solve(x);
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of solve method on Matrix.
     */
    @Test
    public void testSolveMatrix() {
        System.out.println("naive matrix");

        DenseMatrix naive = Matrix.of(A);
        double[] result = new double[3];
        BiconjugateGradient.getInstance().solve(naive, b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-7);
        }
    }

    /**
     * Test of solve method on SparseMatrix.
     */
    @Test
    public void testSolveSparseMatrix() {
        System.out.println("naive matrix");
        int[] rowIndex = {0, 1, 0, 1, 2, 1, 2};
        int[] colIndex = {0, 2, 5, 7};
        double[] val = {0.9, 0.4, 0.4, 0.5, 0.3, 0.3, 0.8};
        SparseMatrix sparse = new SparseMatrix(3, 3, val, rowIndex, colIndex);

        double[] result = new double[3];
        BiconjugateGradient.getInstance().solve(sparse, b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-7);
        }
    }
}
