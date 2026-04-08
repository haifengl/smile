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

public class SparseIntArrayTest {

    @Test
    public void testEmptyArray() {
        SparseIntArray a = new SparseIntArray();
        assertTrue(a.isEmpty());
        assertEquals(0, a.size());
        assertEquals(0, a.get(5));   // absent key returns 0
    }

    @Test
    public void testSetAndGet() {
        SparseIntArray a = new SparseIntArray();
        a.set(3, 10);
        a.set(7, 20);
        assertEquals(2, a.size());
        assertEquals(10, a.get(3));
        assertEquals(20, a.get(7));
        assertEquals(0, a.get(0));
    }

    @Test
    public void testSetZeroRemovesEntry() {
        SparseIntArray a = new SparseIntArray();
        a.set(1, 5);
        assertEquals(1, a.size());
        a.set(1, 0);   // zero → should be removed
        assertEquals(0, a.size());
        assertEquals(0, a.get(1));
    }

    @Test
    public void testUpdateExistingEntry() {
        SparseIntArray a = new SparseIntArray();
        a.set(4, 100);
        a.set(4, 200);
        assertEquals(1, a.size());
        assertEquals(200, a.get(4));
    }

    @Test
    public void testAppend() {
        SparseIntArray a = new SparseIntArray();
        a.append(1, 5);
        a.append(2, 10);
        a.append(0, 0);   // zero is not stored
        assertEquals(2, a.size());
    }

    @Test
    public void testRemove() {
        SparseIntArray a = new SparseIntArray();
        a.set(1, 10); a.set(2, 20); a.set(3, 30);
        a.remove(2);
        assertEquals(2, a.size());
        assertEquals(0, a.get(2));
        assertEquals(10, a.get(1));
        assertEquals(30, a.get(3));
    }

    @Test
    public void testRemoveAbsentIsNoOp() {
        SparseIntArray a = new SparseIntArray();
        a.set(1, 10);
        a.remove(99);   // no-op
        assertEquals(1, a.size());
    }

    @Test
    public void testSort() {
        SparseIntArray a = new SparseIntArray();
        a.append(5, 50); a.append(1, 10); a.append(3, 30);
        a.sort();
        int[] indices = a.indexStream().toArray();
        int[] sorted = indices.clone();
        Arrays.sort(sorted);
        assertArrayEquals(sorted, indices);
    }

    @Test
    public void testStream() {
        SparseIntArray a = new SparseIntArray();
        a.set(1, 1); a.set(2, 2); a.set(3, 3);
        assertEquals(3, a.stream().count());
        int sum = a.valueStream().sum();
        assertEquals(6, sum);
    }

    @Test
    public void testIterator() {
        SparseIntArray a = new SparseIntArray();
        a.set(10, 100); a.set(20, 200);
        int count = 0;
        for (SparseIntArray.Entry e : a) {
            assertTrue(e.index() == 10 || e.index() == 20);
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testCollectionConstructor() {
        List<SparseIntArray.Entry> entries = List.of(
                new SparseIntArray.Entry(1, 10),
                new SparseIntArray.Entry(2, 20));
        SparseIntArray a = new SparseIntArray(entries);
        assertEquals(2, a.size());
        assertEquals(10, a.get(1));
        assertEquals(20, a.get(2));
    }

    @Test
    public void testStreamConstructor() {
        SparseIntArray a = new SparseIntArray(
                java.util.stream.Stream.of(
                        new SparseIntArray.Entry(3, 30),
                        new SparseIntArray.Entry(5, 50)));
        assertEquals(2, a.size());
        assertEquals(30, a.get(3));
    }

    @Test
    public void testForEach() {
        SparseIntArray a = new SparseIntArray();
        a.set(1, 10); a.set(2, 20);
        int[] total = {0};
        a.forEach((idx, val) -> total[0] += val);
        assertEquals(30, total[0]);
    }

    @Test
    public void testMapAndUpdate() {
        SparseIntArray a = new SparseIntArray();
        a.set(1, 3); a.set(2, 4);
        // map: return index + value for each entry (DoubleStream)
        double[] mapped = a.map((idx, val) -> idx + val).toArray();
        java.util.Arrays.sort(mapped);
        assertArrayEquals(new double[]{4.0, 6.0}, mapped, 1e-9);

        // update: double each value
        a.update((idx, val) -> val * 2);
        assertEquals(6, a.get(1));
        assertEquals(8, a.get(2));
    }

    @Test
    public void testToString() {
        SparseIntArray a = new SparseIntArray();
        a.set(1, 10);
        String s = a.toString();
        assertTrue(s.startsWith("["));
        assertTrue(s.contains("1:10"));
    }
}

