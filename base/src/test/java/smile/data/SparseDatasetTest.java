/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import smile.util.SparseArray;
import smile.tensor.SparseMatrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

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

    public SparseDatasetTest() {
        SparseArray[] rows = new SparseArray[A.length];
        for (int i = 0; i < A.length; i++) {
            double[] a = A[i];
            SparseArray row = new SparseArray();
            for (int j = 0; j < a.length; j++) {
                row.append(j, a[j]);
            }

            rows[i] = row;
        }

        SparseDataset lil = SparseDataset.of(rows);
        sm = lil.toMatrix();
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testNrows() {
        System.out.println("nrow");
        assertEquals(3, sm.nrow());
    }

    @Test
    public void testNcols() {
        System.out.println("ncol");
        assertEquals(3, sm.ncol());
    }

    @Test
    public void testLength() {
        System.out.println("length");
        assertEquals(7, sm.length());
    }

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
        SparseDataset data = SparseDataset.from(smile.io.Paths.getTestData("sparse/kos.txt"), 1);
        assertEquals(3430, data.size());
        assertEquals(6906, data.ncol());
        assertEquals(353160, data.nz());
        assertEquals(2.0, data.get(0, 60), 1E-7);
        assertEquals(1.0, data.get(1, 1062), 1E-7);
        assertEquals(0.0, data.get(1, 1063), 1E-7);
        assertEquals(1.0, data.get(3429, 6821), 1E-7);

        SparseMatrix sm = data.toMatrix();
        assertEquals(3430, sm.nrow());
        assertEquals(6906, sm.ncol());
        assertEquals(353160, sm.length());
        assertEquals(2.0, sm.get(0, 60), 1E-7);
        assertEquals(1.0, sm.get(1, 1062), 1E-7);
        assertEquals(0.0, sm.get(1, 1063), 1E-7);
        assertEquals(1.0, sm.get(3429, 6821), 1E-7);
    }

    @Test
    public void testNcolAutoExpansion() {
        System.out.println("ncol auto expansion");
        // Pass ncol=2 but data has entries at column index 5 → ncol should grow
        smile.util.SparseArray row0 = new smile.util.SparseArray();
        row0.append(0, 1.0);
        row0.append(5, 2.0);   // column index 5 exceeds initial ncol=2

        smile.util.SparseArray row1 = new smile.util.SparseArray();
        row1.append(3, 3.0);

        SparseDataset<Void> dataset = SparseDataset.of(
                new smile.util.SparseArray[]{row0, row1}, 2 /*too small*/);

        // ncol must have expanded to at least 6
        assertTrue(dataset.ncol() >= 6);
        assertEquals(1.0, dataset.get(0, 0), 1e-10);
        assertEquals(2.0, dataset.get(0, 5), 1e-10);
        assertEquals(3.0, dataset.get(1, 3), 1e-10);
    }

    @Test
    public void testSparseDatasetNcolGrows() {
        System.out.println("SparseDataset ncol grows with data");
        smile.util.SparseArray row = new smile.util.SparseArray();
        row.append(0, 1.0);
        row.append(9, 2.0); // column 9 exceeds ncol=2
        var dataset = smile.data.SparseDataset.of(
                new smile.util.SparseArray[]{row}, 2);
        assertTrue(dataset.ncol() >= 10);
        assertEquals(2.0, dataset.get(0, 9), 1e-10);
    }
}
