/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import smile.math.matrix.SparseMatrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class BinarySparseDatasetTest {

    public BinarySparseDatasetTest() {

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
    public void testParse() {
        System.out.println("from");
        try {
            BinarySparseDataset data = BinarySparseDataset.from(smile.io.Paths.getTestData("transaction/kosarak.dat"));
            assertEquals(990002, data.size());
            assertEquals(41271, data.ncol());
            assertEquals(8018988, data.length());
            assertEquals(1, data.get(0, 1));
            assertEquals(1, data.get(0, 2));
            assertEquals(1, data.get(0, 3));
            assertEquals(0, data.get(0, 4));
            assertEquals(1, data.get(990001, 1056));

            SparseMatrix sm = data.toMatrix();
            assertEquals(990002, sm.nrow());
            assertEquals(41271, sm.ncol());
            assertEquals(8018988, sm.size());
            assertEquals(1, sm.get(0, 1), 1E-16);
            assertEquals(1, sm.get(0, 2), 1E-16);
            assertEquals(1, sm.get(0, 3), 1E-16);
            assertEquals(0, sm.get(0, 4), 1E-16);
            assertEquals(1, sm.get(990001, 1056), 1E-16);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}