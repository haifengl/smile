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
package smile.data;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import smile.math.SparseArray;
import smile.math.matrix.SparseMatrix;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class SparseDatasetTest {

    SparseMatrix sm;
    double[][] A = {
        {0.9000, 0.4000, 0.0000},
        {0.4000, 0.5000, 0.3000},
        {0.0000, 0.3000, 0.8000}
    };
    double[] b = {0.5, 0.5, 0.5};

    public SparseDatasetTest() {
        List<SparseArray> rows = new ArrayList<>();
        for (int i = 0; i < A.length; i++) {
            SparseArray row = new SparseArray();
            for (int j = 0; j < A[i].length; j++) {
                row.append(j, A[i][j]);
            }

            rows.add(row);
        }

        SparseDataset lil = SparseDataset.of(rows);
        sm = lil.toMatrix();
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

    @Test
    public void testParse() throws Exception {
        System.out.println("from");
        try {
            SparseDataset data = SparseDataset.from(smile.util.Paths.getTestData("text/kos.txt"), 1);
            assertEquals(3430, data.size());
            assertEquals(6906, data.ncols());
            assertEquals(353160, data.length());
            assertEquals(2.0, data.get(0, 60), 1E-7);
            assertEquals(1.0, data.get(1, 1062), 1E-7);
            assertEquals(0.0, data.get(1, 1063), 1E-7);
            assertEquals(1.0, data.get(3429, 6821), 1E-7);

            SparseMatrix sm = data.toMatrix();
            assertEquals(3430, sm.nrows());
            assertEquals(6906, sm.ncols());
            assertEquals(353160, sm.length());
            assertEquals(2.0, sm.get(0, 60), 1E-7);
            assertEquals(1.0, sm.get(1, 1062), 1E-7);
            assertEquals(0.0, sm.get(1, 1063), 1E-7);
            assertEquals(1.0, sm.get(3429, 6821), 1E-7);
        } catch (Exception ex) {
            assertTrue(String.format("Unexpected exception: %s", ex), false);
        }
    }
}