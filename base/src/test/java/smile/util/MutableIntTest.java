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
package smile.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MutableIntTest {

    @Test
    public void testDefaultConstructorStartsAtOne() {
        MutableInt m = new MutableInt();
        assertEquals(1, m.value);
    }

    @Test
    public void testValueConstructor() {
        MutableInt m = new MutableInt(42);
        assertEquals(42, m.value);
    }

    @Test
    public void testIncrement() {
        MutableInt m = new MutableInt(5);
        assertEquals(6, m.increment());
        assertEquals(6, m.value);
    }

    @Test
    public void testIncrementByAmount() {
        MutableInt m = new MutableInt(10);
        assertEquals(15, m.increment(5));
        assertEquals(15, m.value);
    }

    @Test
    public void testDecrement() {
        MutableInt m = new MutableInt(5);
        assertEquals(4, m.decrement());
        assertEquals(4, m.value);
    }

    @Test
    public void testDecrementByAmount() {
        MutableInt m = new MutableInt(10);
        assertEquals(7, m.decrement(3));
        assertEquals(7, m.value);
    }

    @Test
    public void testCompareTo() {
        MutableInt a = new MutableInt(3);
        MutableInt b = new MutableInt(5);
        MutableInt c = new MutableInt(3);
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(c));
    }

    @Test
    public void testDirectFieldMutation() {
        // MutableInt.value is public — mutation by field access is the primary use-case
        MutableInt m = new MutableInt(0);
        m.value = 99;
        assertEquals(99, m.value);
    }
}

