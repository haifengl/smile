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
public class BandMatrixTest {

    public BandMatrixTest() {
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
     * Test of solve method, of class BandMatrix.
     */
    @Test
    public void testSolve() {
        System.out.println("solve");
        double[][] A = {
            {0.9000, 0.4000, 0.0000},
            {0.4000, 0.5000, 0.3000},
            {0.0000, 0.3000, 0.8000}
        };
        double[] b = {0.5, 0.5, 0.5};

        DenseMatrix a = Matrix.of(A);
        LU lu = a.lu();
        double[] x = b.clone();
        lu.solve(x);

        BandMatrix instance = new BandMatrix(3, 1, 1);
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++)
                if (A[i][j] != 0.0)
                    instance.set(i, j, A[i][j]);
        }

        instance.decompose();
        double[] result = new double[b.length];
        instance.solve(b, result);

        assertEquals(result.length, x.length);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-7);
        }

        instance.improve(b, result);
        for (int i = 0; i < x.length; i++) {
            assertEquals(result[i], x[i], 1E-15);
        }
    }
}