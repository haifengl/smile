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
import smile.math.matrix.SparseMatrix;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class BinarySparseDatasetTest {

    public BinarySparseDatasetTest() {

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
    public void testParse() throws Exception {
        System.out.println("from");
        try {
            BinarySparseDataset data = BinarySparseDataset.from(smile.util.Paths.getTestData("transaction/kosarak.dat"));
            assertEquals(990002, data.size());
            assertEquals(41271, data.ncols());
            assertEquals(8018988, data.length());
            assertEquals(1, data.get(0, 1));
            assertEquals(1, data.get(0, 2));
            assertEquals(1, data.get(0, 3));
            assertEquals(0, data.get(0, 4));
            assertEquals(1, data.get(990001, 1056));

            SparseMatrix sm = data.toMatrix();
            assertEquals(990002, sm.nrows());
            assertEquals(41271, sm.ncols());
            assertEquals(8018988, sm.length());
            assertEquals(1, sm.get(0, 1), 1E-16);
            assertEquals(1, sm.get(0, 2), 1E-16);
            assertEquals(1, sm.get(0, 3), 1E-16);
            assertEquals(0, sm.get(0, 4), 1E-16);
            assertEquals(1, sm.get(990001, 1056), 1E-16);
        } catch (Exception ex) {
            assertTrue(String.format("Unexpected exception: %s", ex), false);
        }
    }
}