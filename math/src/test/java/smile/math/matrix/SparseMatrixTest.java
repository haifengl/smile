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
    public void testNvals() {
        System.out.println("nvals");
        assertEquals(7, sm.size());
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
        assertEquals(c.size(), 9);
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
        assertEquals(c.size(), 9);
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
}
