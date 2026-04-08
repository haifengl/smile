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

import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntArrayListTest {

    @Test
    public void testBasicAddAndGet() {
        IntArrayList a = new IntArrayList();
        assertTrue(a.isEmpty());
        a.add(10); a.add(20); a.add(30);
        assertEquals(3, a.size());
        assertEquals(10, a.get(0));
        assertEquals(20, a.get(1));
        assertEquals(30, a.get(2));
        assertFalse(a.isEmpty());
    }

    @Test
    public void testConstructorFromArray() {
        int[] src = {1, 2, 3, 4};
        IntArrayList a = new IntArrayList(src);
        assertEquals(4, a.size());
        assertArrayEquals(src, a.toArray());
    }

    @Test
    public void testGetBoundsCheck() {
        IntArrayList a = new IntArrayList();
        a.add(1);
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(1));
    }

    @Test
    public void testSetBoundsCheck() {
        IntArrayList a = new IntArrayList();
        a.add(1);
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(1, 0));
        a.set(0, 42);
        assertEquals(42, a.get(0));
    }

    @Test
    public void testRemoveFront() {
        IntArrayList a = new IntArrayList(new int[]{1, 2, 3});
        assertEquals(1, a.remove(0));
        assertArrayEquals(new int[]{2, 3}, a.toArray());
    }

    @Test
    public void testRemoveMiddle() {
        IntArrayList a = new IntArrayList(new int[]{1, 2, 3});
        assertEquals(2, a.remove(1));
        assertArrayEquals(new int[]{1, 3}, a.toArray());
    }

    @Test
    public void testRemoveLast() {
        IntArrayList a = new IntArrayList(new int[]{1, 2, 3});
        assertEquals(3, a.remove(2));
        assertArrayEquals(new int[]{1, 2}, a.toArray());
    }

    @Test
    public void testRemoveBoundsCheck() {
        IntArrayList a = new IntArrayList(new int[]{1, 2});
        assertThrows(IndexOutOfBoundsException.class, () -> a.remove(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.remove(2));
    }

    @Test
    public void testAddVarargs() {
        IntArrayList a = new IntArrayList();
        a.add(1, 2, 3, 4, 5);
        assertEquals(5, a.size());
        assertEquals(5, a.get(4));
    }

    @Test
    public void testAddIntArrayList() {
        IntArrayList a = new IntArrayList(new int[]{1, 2});
        IntArrayList b = new IntArrayList(new int[]{3, 4});
        a.add(b);
        assertArrayEquals(new int[]{1, 2, 3, 4}, a.toArray());
    }

    @Test
    public void testStream() {
        IntArrayList a = new IntArrayList(new int[]{1, 2, 3, 4, 5});
        assertEquals(15, a.stream().sum());
        assertEquals(5, a.stream().count());
    }

    @Test
    public void testClearAndTrim() {
        IntArrayList a = new IntArrayList(new int[]{1, 2, 3});
        a.clear();
        assertTrue(a.isEmpty());
        IntArrayList b = new IntArrayList(100);
        b.add(1); b.add(2);
        b.trim();
        assertEquals(2, b.size());
    }

    @Test
    public void testGrowth() {
        IntArrayList a = new IntArrayList(2);
        for (int i = 0; i < 100; i++) a.add(i);
        assertEquals(100, a.size());
        assertEquals(99, a.get(99));
    }

    @Test
    public void testToArrayWithDest() {
        IntArrayList a = new IntArrayList(new int[]{7, 8, 9});
        int[] dest = new int[5];
        int[] result = a.toArray(dest);
        assertSame(dest, result);
        assertEquals(7, result[0]);
        int[] dest2 = null;
        int[] result2 = a.toArray(dest2);
        assertArrayEquals(new int[]{7, 8, 9}, result2);
    }

    @Test
    public void testSerialization() throws Exception {
        IntArrayList a = new IntArrayList(new int[]{11, 22, 33});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(a);
        IntArrayList b = (IntArrayList) new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray())).readObject();
        assertArrayEquals(a.toArray(), b.toArray());
    }
}
