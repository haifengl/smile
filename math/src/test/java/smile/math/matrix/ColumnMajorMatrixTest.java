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
public class ColumnMajorMatrixTest {

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

    ColumnMajorMatrix matrix = new ColumnMajorMatrix(A);

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
     * Test of nrows method, of class ColumnMajorMatrix.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(3, matrix.nrows());
    }

    /**
     * Test of ncols method, of class ColumnMajorMatrix.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(3, matrix.ncols());
    }

    /**
     * Test of get method, of class ColumnMajorMatrix.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(0.9, matrix.get(0, 0), 1E-7);
        assertEquals(0.8, matrix.get(2, 2), 1E-7);
        assertEquals(0.5, matrix.get(1, 1), 1E-7);
        assertEquals(0.0, matrix.get(2, 0), 1E-7);
        assertEquals(0.0, matrix.get(0, 2), 1E-7);
        assertEquals(0.4, matrix.get(0, 1), 1E-7);
    }

    /**
     * Test of ax method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAx() {
        System.out.println("ax");
        double[] d = new double[matrix.nrows()];
        matrix.ax(b, d);
        assertEquals(0.65, d[0], 1E-10);
        assertEquals(0.60, d[1], 1E-10);
        assertEquals(0.55, d[2], 1E-10);
    }

    /**
     * Test of atx method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAtx() {
        System.out.println("atx");
        double[] d = new double[matrix.nrows()];
        matrix.atx(b, d);
        assertEquals(0.65, d[0], 1E-10);
        assertEquals(0.60, d[1], 1E-10);
        assertEquals(0.55, d[2], 1E-10);
    }

    /**
     * Test of AAT method, of class ColumnMajorMatrix.
     */
    @Test
    public void testAAT() {
        System.out.println("AAT");
        ColumnMajorMatrix c = matrix.aat();
        assertEquals(c.nrows(), 3);
        assertEquals(c.ncols(), 3);
        for (int i = 0; i < C.length; i++) {
            for (int j = 0; j < C[i].length; j++) {
                assertEquals(C[i][j], c.get(i, j), 1E-7);
            }
        }
    }
}
