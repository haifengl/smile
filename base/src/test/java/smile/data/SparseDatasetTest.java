/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data;

import java.util.ArrayList;
import java.util.List;
import smile.util.SparseArray;
import smile.math.matrix.SparseMatrix;
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
        assertEquals(7, sm.size());
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
        SparseDataset data = SparseDataset.from(smile.util.Paths.getTestData("sparse/kos.txt"), 1);
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
        assertEquals(353160, sm.size());
        assertEquals(2.0, sm.get(0, 60), 1E-7);
        assertEquals(1.0, sm.get(1, 1062), 1E-7);
        assertEquals(0.0, sm.get(1, 1063), 1E-7);
        assertEquals(1.0, sm.get(3429, 6821), 1E-7);
    }
}