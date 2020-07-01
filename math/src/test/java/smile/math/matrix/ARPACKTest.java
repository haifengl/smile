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
public class ARPACKTest {

    public ARPACKTest() {
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
    public void testSparse500() {
        System.out.println("500 x 500 sparse matrix");
        double[][] A = new double[500][500];
        A[0][0] = A[1][1] = A[2][2] = A[3][3] = 2.0;
        for (int i = 4; i < 500; i++) {
            A[i][i] = (500 - i) / 500.0;
        }
        
        SparseMatrix a = new SparseMatrix(A, 1E-7);
        Matrix.EVD eig = ARPACK.syev(a, 6, ARPACK.SymmWhich.LA);
        assertEquals(2.0, eig.wr[0], 1E-4);
        assertEquals(2.0, eig.wr[1], 1E-4);
        assertEquals(2.0, eig.wr[2], 1E-4);
        assertEquals(2.0, eig.wr[3], 1E-4);
        assertEquals(0.992, eig.wr[4], 1E-4);
        assertEquals(0.990, eig.wr[5], 1E-4);

        // non-symmetric
        eig = ARPACK.eigen(a, 6, ARPACK.AsymmWhich.LM);
        assertEquals(2.0, eig.wr[0], 1E-4);
        assertEquals(2.0, eig.wr[1], 1E-4);
        assertEquals(2.0, eig.wr[2], 1E-4);
        assertEquals(2.0, eig.wr[3], 1E-4);
        assertEquals(0.992, eig.wr[4], 1E-4);
        assertEquals(0.990, eig.wr[5], 1E-4);
        for (int i = 0; i < eig.wi.length; i++) {
            assertEquals(0.0, eig.wi[i], 1E-4);
        }
    }
}