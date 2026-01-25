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
public class SparseArrayTest {
    public SparseArrayTest() {
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
        System.out.println("SparseArray");
        SparseArray a = new SparseArray();
        assertTrue(a.isEmpty());

        a.set(1, 0.5);
        a.set(2, 1.0);
        assertEquals(2, a.size());
        assertEquals(0.0, a.get(0), 1E-15);
        assertEquals(0.5, a.get(1), 1E-15);
        assertEquals(1.0, a.get(2), 1E-15);
        assertFalse(a.isEmpty());

        a.remove(1);
        assertEquals(1, a.size());
        assertEquals(0.0, a.get(1), 1E-15);
        assertEquals(1.0, a.get(2), 1E-15);
        assertFalse(a.isEmpty());


        a.remove(1);
        assertEquals(1, a.size());
        assertEquals(0.0, a.get(1), 1E-15);
        assertEquals(1.0, a.get(2), 1E-15);
        assertFalse(a.isEmpty());

        SparseArray.Entry e = a.stream().findFirst().get();
        assertEquals(1, a.stream().count());
        assertEquals(2, e.index());
        assertEquals(1.0, e.value(), 1E-15);

        a.set(0, 4);
        e = a.stream().findFirst().get();
        assertEquals(2, a.stream().count());
        assertEquals(2, e.index());
        assertEquals(1.0, e.value(), 1E-15);

        a.sort();
        e = a.stream().findFirst().get();
        assertEquals(2, a.stream().count());
        assertEquals(0, e.index());
        assertEquals(4.0, e.value(), 1E-15);
    }
}
