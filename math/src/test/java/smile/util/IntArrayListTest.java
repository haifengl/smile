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
import static org.junit.Assert.assertArrayEquals;

/**
 *
 * @author Haifeng Li
 */
public class IntArrayListTest {
    public IntArrayListTest() {
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
     * Test of all methods, of class IntArrayList.
     */
    @Test
    public void testAll() {
        System.out.println("IntArrayList");
        IntArrayList a = new IntArrayList();
        assertEquals(true, a.isEmpty());

        a.add(1);
        a.add(2);
        assertEquals(2, a.size());
        assertEquals(1, a.get(0));
        assertEquals(2, a.get(1));
        assertEquals(false, a.isEmpty());

        a.remove(0);
        assertEquals(1, a.size());
        assertEquals(2, a.get(0));
        assertEquals(false, a.isEmpty());


        a.remove(0);
        assertEquals(0, a.size());
        assertEquals(true, a.isEmpty());

        a.add(new int[]{1, 2, 3, 4});
        assertEquals(4, a.size());
        assertEquals(3, a.get(2));
        assertEquals(false, a.isEmpty());
        assertEquals(4, a.stream().count());

        a.set(2, 4);
        assertEquals(4, a.get(2));

        int[] b = a.toArray();
        assertArrayEquals(new int[]{1, 2, 4, 4}, b);
    }
}
