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

package smile.util;

import org.junit.*;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Haifeng Li
 */
public class SparseArrayTest {
    public SparseArrayTest() {
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
     * Test of all methods, of class SparseArrayTest.
     */
    @Test
    public void testAll() {
        System.out.println("SparseArray");
        SparseArray a = new SparseArray();
        assertEquals(true, a.isEmpty());

        a.set(1, 0.5);
        a.set(2, 1.0);
        assertEquals(2, a.size());
        assertEquals(0.0, a.get(0), 1E-15);
        assertEquals(0.5, a.get(1), 1E-15);
        assertEquals(1.0, a.get(2), 1E-15);
        assertEquals(false, a.isEmpty());

        a.remove(1);
        assertEquals(1, a.size());
        assertEquals(0.0, a.get(1), 1E-15);
        assertEquals(1.0, a.get(2), 1E-15);
        assertEquals(false, a.isEmpty());


        a.remove(1);
        assertEquals(1, a.size());
        assertEquals(0.0, a.get(1), 1E-15);
        assertEquals(1.0, a.get(2), 1E-15);
        assertEquals(false, a.isEmpty());

        SparseArray.Entry e = a.stream().findFirst().get();
        assertEquals(1, a.stream().count());
        assertEquals(2, e.i);
        assertEquals(1.0, e.x, 1E-15);

        a.set(0, 4);
        e = a.stream().findFirst().get();
        assertEquals(2, a.stream().count());
        assertEquals(2, e.i);
        assertEquals(1.0, e.x, 1E-15);

        a.sort();
        e = a.stream().findFirst().get();
        assertEquals(2, a.stream().count());
        assertEquals(0, e.i);
        assertEquals(4.0, e.x, 1E-15);
    }
}
