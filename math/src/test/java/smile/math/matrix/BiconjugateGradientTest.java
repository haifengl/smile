/*
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
 */

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

        Matrix a = new Matrix(A);
        Matrix.LU lu = a.lu();
        x = lu.solve(b);
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

        Matrix naive = new Matrix(A);
        double[] result = new double[3];
        BiconjugateGradient.solve(naive, b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], result[i], 1E-7);
        }
    }

    @Test
    public void testSolveSparseMatrix() {
        System.out.println("sparse matrix");
        SparseMatrix sparse = new SparseMatrix(A, 1E-8);

        double[] result = new double[3];
        BiconjugateGradient.solve(sparse, b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i], result[i], 1E-7);
        }
    }
}
