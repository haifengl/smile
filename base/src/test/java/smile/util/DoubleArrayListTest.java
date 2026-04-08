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

public class DoubleArrayListTest {

    @Test
    public void testBasicAddAndGet() {
        DoubleArrayList a = new DoubleArrayList();
        assertTrue(a.isEmpty());
        a.add(1.0); a.add(2.0); a.add(3.0);
        assertEquals(3, a.size());
        assertEquals(1.0, a.get(0), 1e-15);
        assertEquals(2.0, a.get(1), 1e-15);
        assertEquals(3.0, a.get(2), 1e-15);
        assertFalse(a.isEmpty());
    }

    @Test
    public void testConstructorFromArray() {
        double[] src = {4.0, 5.0, 6.0};
        DoubleArrayList a = new DoubleArrayList(src);
        assertEquals(3, a.size());
        assertArrayEquals(src, a.toArray(), 1e-15);
    }

    @Test
    public void testGetBoundsCheck() {
        DoubleArrayList a = new DoubleArrayList();
        a.add(1.0);
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(1));
    }

    @Test
    public void testSetBoundsCheck() {
        DoubleArrayList a = new DoubleArrayList();
        a.add(1.0);
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1, 0.0));
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(1, 0.0));
        a.set(0, 99.0);
        assertEquals(99.0, a.get(0), 1e-15);
    }

    @Test
    public void testRemoveFront() {
        DoubleArrayList a = new DoubleArrayList(new double[]{1, 2, 3});
        assertEquals(1.0, a.remove(0), 1e-15);
        assertEquals(2, a.size());
        assertEquals(2.0, a.get(0), 1e-15);
    }

    @Test
    public void testRemoveMiddle() {
        DoubleArrayList a = new DoubleArrayList(new double[]{1, 2, 3});
        assertEquals(2.0, a.remove(1), 1e-15);
        assertArrayEquals(new double[]{1, 3}, a.toArray(), 1e-15);
    }

    @Test
    public void testRemoveLast() {
        DoubleArrayList a = new DoubleArrayList(new double[]{1, 2, 3});
        assertEquals(3.0, a.remove(2), 1e-15);
        assertArrayEquals(new double[]{1, 2}, a.toArray(), 1e-15);
    }

    @Test
    public void testRemoveBoundsCheck() {
        DoubleArrayList a = new DoubleArrayList(new double[]{1, 2});
        assertThrows(IndexOutOfBoundsException.class, () -> a.remove(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.remove(2));
    }

    @Test
    public void testClear() {
        DoubleArrayList a = new DoubleArrayList(new double[]{1, 2, 3});
        a.clear();
        assertTrue(a.isEmpty());
        assertEquals(0, a.size());
    }

    @Test
    public void testTrim() {
        DoubleArrayList a = new DoubleArrayList(100);
        a.add(1.0); a.add(2.0);
        a.trim();
        assertEquals(2, a.size());
        assertArrayEquals(new double[]{1.0, 2.0}, a.toArray(), 1e-15);
    }

    @Test
    public void testAddArrayAndStream() {
        DoubleArrayList a = new DoubleArrayList();
        a.add(new double[]{1, 2, 3, 4, 5});
        assertEquals(5, a.stream().count());
        assertEquals(15.0, a.stream().sum(), 1e-15);
    }

    @Test
    public void testEnsureCapacityGrowth() {
        DoubleArrayList a = new DoubleArrayList(2);
        for (int i = 0; i < 50; i++) a.add(i);
        assertEquals(50, a.size());
        assertEquals(49.0, a.get(49), 1e-15);
    }

    @Test
    public void testToArrayWithDest() {
        DoubleArrayList a = new DoubleArrayList(new double[]{1, 2, 3});
        double[] dest = new double[5];
        double[] result = a.toArray(dest);
        assertSame(dest, result);
        assertEquals(1.0, result[0], 1e-15);
        // toArray(null) allocates a new array
        double[] dest2 = null;
        double[] result2 = a.toArray(dest2);
        assertArrayEquals(new double[]{1, 2, 3}, result2, 1e-15);
    }

    @Test
    public void testToStringLong() {
        DoubleArrayList a = new DoubleArrayList();
        for (int i = 0; i < 15; i++) a.add(i);
        assertTrue(a.toString().endsWith("...]"));
    }

    @Test
    public void testToStringShort() {
        DoubleArrayList a = new DoubleArrayList(new double[]{1, 2, 3});
        assertTrue(a.toString().startsWith("["));
        assertTrue(a.toString().endsWith("]"));
        assertFalse(a.toString().contains("..."));
    }

    @Test
    public void testSerialization() throws Exception {
        DoubleArrayList a = new DoubleArrayList(new double[]{1.1, 2.2, 3.3});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(a);
        DoubleArrayList b = (DoubleArrayList) new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray())).readObject();
        assertArrayEquals(a.toArray(), b.toArray(), 1e-15);
    }
}
