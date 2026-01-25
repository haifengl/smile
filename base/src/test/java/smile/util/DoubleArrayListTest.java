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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DoubleArrayListTest {
    public DoubleArrayListTest() {
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
    public void test() {
        System.out.println("DoubleArrayList");
        DoubleArrayList a = new DoubleArrayList();
        assertTrue(a.isEmpty());

        a.add(1);
        a.add(2);
        assertEquals(2, a.size());
        assertEquals(1, a.get(0), 1E-15);
        assertEquals(2, a.get(1), 1E-15);
        assertFalse(a.isEmpty());

        a.remove(0);
        assertEquals(1, a.size());
        assertEquals(2, a.get(0), 1E-15);
        assertFalse(a.isEmpty());


        a.remove(0);
        assertEquals(0, a.size());
        assertTrue(a.isEmpty());

        a.add(new double[]{1, 2, 3, 4});
        assertEquals(4, a.size());
        assertEquals(3, a.get(2), 1E-15);
        assertFalse(a.isEmpty());
        assertEquals(4, a.stream().count());

        a.set(2, 4);
        assertEquals(4, a.get(2), 1E-15);

        double[] b = a.toArray();
        assertArrayEquals(new double[]{1, 2, 4, 4}, b, 1E-15);
    }
}
