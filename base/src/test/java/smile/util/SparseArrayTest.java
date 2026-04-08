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

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SparseArrayTest {

    @Test
    public void testBasicSetAndGet() {
        SparseArray a = new SparseArray();
        assertTrue(a.isEmpty());
        a.set(1, 0.5); a.set(2, 1.0);
        assertEquals(2, a.size());
        assertEquals(0.0, a.get(0), 1e-15);
        assertEquals(0.5, a.get(1), 1e-15);
        assertEquals(1.0, a.get(2), 1e-15);
        assertFalse(a.isEmpty());
    }

    @Test
    public void testSetZeroRemovesEntry() {
        SparseArray a = new SparseArray();
        a.set(3, 2.5);
        assertEquals(1, a.size());
        a.set(3, 0.0);  // zero → entry removed
        assertEquals(0, a.size());
        assertEquals(0.0, a.get(3), 1e-15);
    }

    @Test
    public void testUpdateExistingEntry() {
        SparseArray a = new SparseArray();
        a.set(5, 1.0);
        a.set(5, 9.9);
        assertEquals(1, a.size());
        assertEquals(9.9, a.get(5), 1e-15);
    }

    @Test
    public void testRemove() {
        SparseArray a = new SparseArray();
        a.set(1, 0.5); a.set(2, 1.0); a.set(3, 1.5);
        a.remove(2);
        assertEquals(2, a.size());
        assertEquals(0.0, a.get(2), 1e-15);
        assertEquals(0.5, a.get(1), 1e-15);
        assertEquals(1.5, a.get(3), 1e-15);
    }

    @Test
    public void testRemoveAbsentIsNoOp() {
        SparseArray a = new SparseArray();
        a.set(1, 1.0);
        a.remove(99);
        assertEquals(1, a.size());
    }

    @Test
    public void testAppend() {
        SparseArray a = new SparseArray();
        a.append(0, 1.0);
        a.append(1, 2.0);
        a.append(2, 0.0);  // zero → not stored
        assertEquals(2, a.size());
    }

    @Test
    public void testSort() {
        SparseArray a = new SparseArray();
        a.append(5, 5.0); a.append(1, 1.0); a.append(3, 3.0);
        a.sort();
        int[] indices = a.indexStream().toArray();
        int[] expected = indices.clone();
        Arrays.sort(expected);
        assertArrayEquals(expected, indices);
    }

    @Test
    public void testStream() {
        SparseArray a = new SparseArray();
        a.set(1, 10.0); a.set(2, 20.0); a.set(3, 30.0);
        assertEquals(3, a.stream().count());
        assertEquals(60.0, a.valueStream().sum(), 1e-15);
    }

    @Test
    public void testIterator() {
        SparseArray a = new SparseArray();
        a.set(10, 1.0); a.set(20, 2.0);
        int count = 0;
        for (SparseArray.Entry e : a) {
            assertTrue(e.index() == 10 || e.index() == 20);
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testForEachAndMapAndUpdate() {
        SparseArray a = new SparseArray();
        a.set(1, 1.0); a.set(2, 2.0);
        // forEach
        double[] sum = {0};
        a.forEach((idx, val) -> sum[0] += val);
        assertEquals(3.0, sum[0], 1e-15);
        // map
        double[] mapped = a.map((idx, val) -> idx * val).toArray();
        Arrays.sort(mapped);
        assertArrayEquals(new double[]{1.0, 4.0}, mapped, 1e-15);
        // update: triple each value
        a.update((idx, val) -> val * 3);
        assertEquals(3.0, a.get(1), 1e-15);
        assertEquals(6.0, a.get(2), 1e-15);
    }

    @Test
    public void testCollectionConstructor() {
        List<SparseArray.Entry> entries = List.of(
                new SparseArray.Entry(1, 1.5),
                new SparseArray.Entry(2, 2.5));
        SparseArray a = new SparseArray(entries);
        assertEquals(2, a.size());
        assertEquals(1.5, a.get(1), 1e-15);
    }

    @Test
    public void testStreamConstructor() {
        SparseArray a = new SparseArray(
                java.util.stream.Stream.of(
                        new SparseArray.Entry(7, 7.7),
                        new SparseArray.Entry(8, 8.8)));
        assertEquals(2, a.size());
        assertEquals(7.7, a.get(7), 1e-15);
    }

    @Test
    public void testEntryCompareTo() {
        SparseArray.Entry small = new SparseArray.Entry(1, 1.0);
        SparseArray.Entry large = new SparseArray.Entry(2, 5.0);
        assertTrue(small.compareTo(large) < 0);
        assertTrue(large.compareTo(small) > 0);
        assertEquals(0, small.compareTo(new SparseArray.Entry(99, 1.0)));
    }

    @Test
    public void testToStringTruncation() {
        SparseArray a = new SparseArray();
        for (int i = 0; i < 15; i++) a.set(i, i * 0.1);
        assertTrue(a.toString().endsWith("...]"));
    }
}
