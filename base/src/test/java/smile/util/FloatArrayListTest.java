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

public class FloatArrayListTest {

    @Test
    public void testBasicAddAndGet() {
        FloatArrayList a = new FloatArrayList();
        assertTrue(a.isEmpty());
        a.add(1.0f); a.add(2.0f); a.add(3.0f);
        assertEquals(3, a.size());
        assertEquals(1.0f, a.get(0), 1e-6f);
        assertEquals(2.0f, a.get(1), 1e-6f);
        assertFalse(a.isEmpty());
    }

    @Test
    public void testConstructorFromArray() {
        float[] src = {4.0f, 5.0f, 6.0f};
        FloatArrayList a = new FloatArrayList(src);
        assertEquals(3, a.size());
        assertArrayEquals(src, a.toArray(), 1e-6f);
    }

    @Test
    public void testGetBoundsCheck() {
        FloatArrayList a = new FloatArrayList();
        a.add(1.0f);
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.get(1));
    }

    @Test
    public void testSetBoundsCheck() {
        FloatArrayList a = new FloatArrayList();
        a.add(1.0f);
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(-1, 0.0f));
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(1, 0.0f));
        a.set(0, 99.0f);
        assertEquals(99.0f, a.get(0), 1e-6f);
    }

    @Test
    public void testRemoveFront() {
        FloatArrayList a = new FloatArrayList(new float[]{1, 2, 3});
        assertEquals(1.0f, a.remove(0), 1e-6f);
        assertEquals(2, a.size());
        assertEquals(2.0f, a.get(0), 1e-6f);
    }

    @Test
    public void testRemoveMiddle() {
        FloatArrayList a = new FloatArrayList(new float[]{1, 2, 3});
        assertEquals(2.0f, a.remove(1), 1e-6f);
        assertArrayEquals(new float[]{1, 3}, a.toArray(), 1e-6f);
    }

    @Test
    public void testRemoveLast() {
        FloatArrayList a = new FloatArrayList(new float[]{1, 2, 3});
        assertEquals(3.0f, a.remove(2), 1e-6f);
        assertArrayEquals(new float[]{1, 2}, a.toArray(), 1e-6f);
    }

    @Test
    public void testRemoveBoundsCheck() {
        FloatArrayList a = new FloatArrayList(new float[]{1, 2});
        assertThrows(IndexOutOfBoundsException.class, () -> a.remove(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> a.remove(2));
    }

    @Test
    public void testClear() {
        FloatArrayList a = new FloatArrayList(new float[]{1, 2, 3});
        a.clear();
        assertTrue(a.isEmpty());
    }

    @Test
    public void testStreamValues() {
        FloatArrayList a = new FloatArrayList(new float[]{1.0f, 2.0f, 3.0f});
        assertEquals(3, a.stream().count());
        assertEquals(6.0, a.stream().sum(), 1e-6);
    }

    @Test
    public void testEnsureCapacityGrowth() {
        FloatArrayList a = new FloatArrayList(2);
        for (int i = 0; i < 50; i++) a.add((float) i);
        assertEquals(50, a.size());
        assertEquals(49.0f, a.get(49), 1e-6f);
    }

    @Test
    public void testToArrayWithDest() {
        FloatArrayList a = new FloatArrayList(new float[]{1, 2, 3});
        float[] dest = new float[5];
        float[] result = a.toArray(dest);
        assertSame(dest, result);
        float[] dest2 = null;
        float[] result2 = a.toArray(dest2);
        assertArrayEquals(new float[]{1, 2, 3}, result2, 1e-6f);
    }

    @Test
    public void testTrim() {
        FloatArrayList a = new FloatArrayList(100);
        a.add(1.0f); a.add(2.0f);
        a.trim();
        assertEquals(2, a.size());
    }

    @Test
    public void testSerialization() throws Exception {
        FloatArrayList a = new FloatArrayList(new float[]{1.1f, 2.2f});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(a);
        FloatArrayList b = (FloatArrayList) new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray())).readObject();
        assertArrayEquals(a.toArray(), b.toArray(), 1e-6f);
    }
}
