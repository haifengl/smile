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

import java.util.Scanner;

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
public class SparseMatrixTest {

    SparseMatrix sm;
    double[][] A = {
        {0.9000, 0.4000, 0.0000},
        {0.4000, 0.5000, 0.3000},
        {0.0000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};
    double[][] C = {
        {0.97, 0.56, 0.12},
        {0.56, 0.50, 0.39},
        {0.12, 0.39, 0.73}
    };

    public SparseMatrixTest() {
        int[] rowIndex = {0, 1, 0, 1, 2, 1, 2};
        int[] colIndex = {0, 2, 5, 7};
        double[] val = {0.9, 0.4, 0.4, 0.5, 0.3, 0.3, 0.8};
        sm = new SparseMatrix(3, 3, val, rowIndex, colIndex);
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
     * Test of nrows method, of class SparseMatrix.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(3, sm.nrows());
    }

    /**
     * Test of ncols method, of class SparseMatrix.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(3, sm.ncols());
    }

    /**
     * Test of size method, of class SparseMatrix.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        assertEquals(7, sm.length());
    }

    /**
     * Test of get method, of class SparseMatrix.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(0.9, sm.get(0, 0), 1E-7);
        assertEquals(0.8, sm.get(2, 2), 1E-7);
        assertEquals(0.5, sm.get(1, 1), 1E-7);
        assertEquals(0.0, sm.get(2, 0), 1E-7);
        assertEquals(0.0, sm.get(0, 2), 1E-7);
        assertEquals(0.4, sm.get(0, 1), 1E-7);
    }

    /**
     * Test of times method, of class SparseMatrix.
     */
    @Test
    public void testTimes() {
        System.out.println("times");
        SparseMatrix c = sm.abmm(sm);
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
        assertEquals(c.length(), 9);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Test of AAT method, of class SparseMatrix.
     */
    @Test
    public void testAAT() {
        System.out.println("AAT");
        SparseMatrix c = sm.aat();
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
        assertEquals(c.length(), 9);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }

    /**
     * Load sparse matrix.
     */
    @SuppressWarnings("resource")
    SparseMatrix loadMatrix(String file) {
        int nrows, ncols;
        int[] colIndex, rowIndex;
        double[] data;

        Scanner scanner = new Scanner(getClass().getResourceAsStream(file));

        nrows = scanner.nextInt();
        ncols = scanner.nextInt();
        int n = scanner.nextInt();

        colIndex = new int[ncols + 1];
        rowIndex = new int[n];
        data = new double[n];

        for (int i = 0; i <= ncols; i++) {
            colIndex[i] = scanner.nextInt() - 1;
        }

        for (int i = 0; i < n; i++) {
            rowIndex[i] = scanner.nextInt() - 1;
        }

        for (int i = 0; i < n; i++) {
            data[i] = scanner.nextDouble();
        }

        SparseMatrix matrix = new SparseMatrix(nrows, ncols, data, rowIndex, colIndex);
        return matrix;
    }

    /**
     * Test of text method, of class SparseMatrix.
     */
    @Test(expected = Test.None.class)
    public void testText() throws Exception {
        System.out.println("text");
        SparseMatrix data = SparseMatrix.text(smile.util.Paths.getTestData("matrix/08blocks.txt"));
        assertEquals(592, data.length());
        assertEquals(300, data.nrows());
        assertEquals(300, data.ncols());
        assertEquals(94.0, data.get(36, 0), 1E-7);
        assertEquals(1.0, data.get(0, 1), 1E-7);
        assertEquals(33.0, data.get(36, 1), 1E-7);
        assertEquals(95.0, data.get(299, 299), 1E-7);
    }

    /**
     * Test of harwell method, of class SparseMatrix.
     */
    @Test(expected = Test.None.class)
    public void testHarwell() throws Exception {
        System.out.println("HB exchange format");
        SparseMatrix data = SparseMatrix.harwell(smile.util.Paths.getTestData("matrix/5by5_rua.hb"));
        assertEquals(13, data.length());
        assertEquals(5, data.nrows());
        assertEquals(5, data.ncols());
        assertEquals(11.0, data.get(0, 0), 1E-7);
        assertEquals(31.0, data.get(2, 0), 1E-7);
        assertEquals(51.0, data.get(4, 0), 1E-7);
        assertEquals(55.0, data.get(4, 4), 1E-7);
    }
}
