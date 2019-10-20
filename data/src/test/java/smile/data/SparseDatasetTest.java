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

package smile.data;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import smile.util.SparseArray;
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

    @Test(expected = Test.None.class)
    public void testParse() throws Exception {
        System.out.println("from");
        SparseDataset data = SparseDataset.from(smile.util.Paths.getTestData("text/kos.txt"), 1);
        assertEquals(3430, data.size());
        assertEquals(6906, data.ncols());
        assertEquals(353160, data.nz());
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
    }
}