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
package smile.sort;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link Sort} interface utility methods: swap, siftUp, siftDown.
 * These methods underpin all heap-based data structures in the package.
 *
 * @author Haifeng Li
 */
public class SortTest {

    // -----------------------------------------------------------------------
    // Sort.swap — int[]
    // -----------------------------------------------------------------------

    @Test
    public void testSwapInt() {
        System.out.println("Sort.swap int[]");
        int[] a = {1, 2, 3, 4, 5};
        Sort.swap(a, 1, 3);
        assertArrayEquals(new int[]{1, 4, 3, 2, 5}, a);
    }

    @Test
    public void testSwapIntSameIndex() {
        System.out.println("Sort.swap int[] same index");
        int[] a = {10, 20, 30};
        Sort.swap(a, 1, 1);
        assertArrayEquals(new int[]{10, 20, 30}, a);
    }

    @Test
    public void testSwapIntEnds() {
        System.out.println("Sort.swap int[] first and last");
        int[] a = {7, 2, 3, 4, 9};
        Sort.swap(a, 0, 4);
        assertArrayEquals(new int[]{9, 2, 3, 4, 7}, a);
    }

    // -----------------------------------------------------------------------
    // Sort.swap — float[]
    // -----------------------------------------------------------------------

    @Test
    public void testSwapFloat() {
        System.out.println("Sort.swap float[]");
        float[] a = {1.0f, 2.0f, 3.0f};
        Sort.swap(a, 0, 2);
        assertEquals(3.0f, a[0], 1E-6f);
        assertEquals(2.0f, a[1], 1E-6f);
        assertEquals(1.0f, a[2], 1E-6f);
    }

    @Test
    public void testSwapFloatSameIndex() {
        System.out.println("Sort.swap float[] same index");
        float[] a = {5.5f, 6.6f};
        Sort.swap(a, 0, 0);
        assertEquals(5.5f, a[0], 1E-6f);
    }

    // -----------------------------------------------------------------------
    // Sort.swap — double[]
    // -----------------------------------------------------------------------

    @Test
    public void testSwapDouble() {
        System.out.println("Sort.swap double[]");
        double[] a = {1.0, 2.0, 3.0, 4.0};
        Sort.swap(a, 0, 3);
        assertEquals(4.0, a[0], 1E-10);
        assertEquals(1.0, a[3], 1E-10);
    }

    @Test
    public void testSwapDoubleSameIndex() {
        System.out.println("Sort.swap double[] same index");
        double[] a = {3.14, 2.72};
        Sort.swap(a, 1, 1);
        assertEquals(2.72, a[1], 1E-10);
    }

    // -----------------------------------------------------------------------
    // Sort.swap — Object[]
    // -----------------------------------------------------------------------

    @Test
    public void testSwapObject() {
        System.out.println("Sort.swap Object[]");
        String[] a = {"alpha", "beta", "gamma"};
        Sort.swap(a, 0, 2);
        assertEquals("gamma", a[0]);
        assertEquals("beta",  a[1]);
        assertEquals("alpha", a[2]);
    }

    @Test
    public void testSwapObjectSameIndex() {
        System.out.println("Sort.swap Object[] same index");
        Integer[] a = {1, 2, 3};
        Sort.swap(a, 1, 1);
        assertEquals(Integer.valueOf(2), a[1]);
    }

    // -----------------------------------------------------------------------
    // Sort.siftUp — int[] (1-based max-heap)
    // -----------------------------------------------------------------------

    @Test
    public void testSiftUpIntSingleElement() {
        System.out.println("Sort.siftUp int[] single element (k=1)");
        int[] heap = {0, 42}; // heap[0] unused; root at heap[1]
        Sort.siftUp(heap, 1); // already at root, nothing to do
        assertEquals(42, heap[1]);
    }

    @Test
    public void testSiftUpIntAlreadyInPlace() {
        System.out.println("Sort.siftUp int[] already correct");
        // Max-heap: [_, 10, 5, 3]  — root 10 is largest
        int[] heap = {0, 10, 5, 3};
        Sort.siftUp(heap, 3); // heap[3]=3 < heap[1]=10, nothing to move
        assertArrayEquals(new int[]{0, 10, 5, 3}, heap);
    }

    @Test
    public void testSiftUpIntViolation() {
        System.out.println("Sort.siftUp int[] bubbles up");
        // Start: [_, 5, 3, 99] — heap[3]=99 > heap[1]=5, must bubble up
        int[] heap = {0, 5, 3, 99};
        Sort.siftUp(heap, 3);
        // After sift: 99 should be at root
        assertEquals(99, heap[1]);
    }

    // -----------------------------------------------------------------------
    // Sort.siftUp — double[]
    // -----------------------------------------------------------------------

    @Test
    public void testSiftUpDouble() {
        System.out.println("Sort.siftUp double[]");
        double[] heap = {0.0, 5.0, 3.0, 99.0};
        Sort.siftUp(heap, 3);
        assertEquals(99.0, heap[1], 1E-10);
    }

    // -----------------------------------------------------------------------
    // Sort.siftUp — float[]
    // -----------------------------------------------------------------------

    @Test
    public void testSiftUpFloat() {
        System.out.println("Sort.siftUp float[]");
        float[] heap = {0.0f, 5.0f, 3.0f, 99.0f};
        Sort.siftUp(heap, 3);
        assertEquals(99.0f, heap[1], 1E-6f);
    }

    // -----------------------------------------------------------------------
    // Sort.siftUp — T[] (generic)
    // -----------------------------------------------------------------------

    @Test
    public void testSiftUpGeneric() {
        System.out.println("Sort.siftUp T[]");
        Integer[] heap = {null, 5, 3, 99};
        Sort.siftUp(heap, 3);
        assertEquals(Integer.valueOf(99), heap[1]);
    }

    // -----------------------------------------------------------------------
    // Sort.siftDown — int[] (1-based max-heap)
    // -----------------------------------------------------------------------

    @Test
    public void testSiftDownIntAlreadyInPlace() {
        System.out.println("Sort.siftDown int[] already correct");
        // [_, 10, 8, 5, 3, 2]  — valid max-heap
        int[] heap = {0, 10, 8, 5, 3, 2};
        Sort.siftDown(heap, 1, 5);
        assertArrayEquals(new int[]{0, 10, 8, 5, 3, 2}, heap);
    }

    @Test
    public void testSiftDownIntViolation() {
        System.out.println("Sort.siftDown int[] sinks down");
        // Replace root with small value: [_, 1, 8, 5]
        int[] heap = {0, 1, 8, 5};
        Sort.siftDown(heap, 1, 3);
        // 8 (largest child) should float to root
        assertEquals(8, heap[1]);
    }

    @Test
    public void testSiftDownIntOneChild() {
        System.out.println("Sort.siftDown int[] single child");
        // n=2: heap[1] has one child heap[2]
        int[] heap = {0, 1, 9};
        Sort.siftDown(heap, 1, 2);
        assertEquals(9, heap[1]);
        assertEquals(1, heap[2]);
    }

    // -----------------------------------------------------------------------
    // Sort.siftDown — double[]
    // -----------------------------------------------------------------------

    @Test
    public void testSiftDownDouble() {
        System.out.println("Sort.siftDown double[]");
        double[] heap = {0.0, 1.0, 8.0, 5.0};
        Sort.siftDown(heap, 1, 3);
        assertEquals(8.0, heap[1], 1E-10);
    }

    // -----------------------------------------------------------------------
    // Sort.siftDown — float[]
    // -----------------------------------------------------------------------

    @Test
    public void testSiftDownFloat() {
        System.out.println("Sort.siftDown float[]");
        float[] heap = {0.0f, 1.0f, 8.0f, 5.0f};
        Sort.siftDown(heap, 1, 3);
        assertEquals(8.0f, heap[1], 1E-6f);
    }

    // -----------------------------------------------------------------------
    // Sort.siftDown — T[] (generic)
    // -----------------------------------------------------------------------

    @Test
    public void testSiftDownGeneric() {
        System.out.println("Sort.siftDown T[]");
        Integer[] heap = {null, 1, 8, 5};
        Sort.siftDown(heap, 1, 3);
        assertEquals(Integer.valueOf(8), heap[1]);
    }

    // -----------------------------------------------------------------------
    // Round-trip: build max-heap via siftUp then verify heap property
    // -----------------------------------------------------------------------

    @Test
    public void testBuildMaxHeapViasSiftUp() {
        System.out.println("Sort: build max-heap via siftUp");
        int[] values = {3, 1, 4, 1, 5, 9, 2, 6, 5};
        int[] heap = new int[values.length + 1];
        int n = 0;
        for (int v : values) {
            heap[++n] = v;
            Sort.siftUp(heap, n);
        }
        // Root (heap[1]) must be the maximum
        assertEquals(9, heap[1]);
        // Verify max-heap property: heap[k] >= heap[2k] and heap[2k+1]
        for (int k = 1; k <= n / 2; k++) {
            if (2 * k <= n)     assertTrue(heap[k] >= heap[2 * k],
                    "Heap violation at k=" + k);
            if (2 * k + 1 <= n) assertTrue(heap[k] >= heap[2 * k + 1],
                    "Heap violation at k=" + k + " right child");
        }
    }

    @Test
    public void testBuildMaxHeapDoubleSiftUp() {
        System.out.println("Sort: build double max-heap via siftUp");
        double[] values = {3.3, 1.1, 4.4, 1.5, 9.9, 2.2};
        double[] heap = new double[values.length + 1];
        int n = 0;
        for (double v : values) {
            heap[++n] = v;
            Sort.siftUp(heap, n);
        }
        assertEquals(9.9, heap[1], 1E-10);
        for (int k = 1; k <= n / 2; k++) {
            if (2 * k <= n)     assertTrue(heap[k] >= heap[2 * k]);
            if (2 * k + 1 <= n) assertTrue(heap[k] >= heap[2 * k + 1]);
        }
    }

    // -----------------------------------------------------------------------
    // siftDown after replacing root (classic heap-pop simulation)
    // -----------------------------------------------------------------------

    @Test
    public void testHeapPopSimulationInt() {
        System.out.println("Sort: heap-pop simulation int[]");
        // Build heap first
        int[] heap = {0, 9, 6, 5, 1, 4, 2, 3};
        int n = 7;
        // Pop max: move last element to root, siftDown
        heap[1] = heap[n--];
        Sort.siftDown(heap, 1, n);
        // New root must be <= 9 and the heap property must hold
        for (int k = 1; k <= n / 2; k++) {
            if (2 * k <= n)     assertTrue(heap[k] >= heap[2 * k]);
            if (2 * k + 1 <= n) assertTrue(heap[k] >= heap[2 * k + 1]);
        }
    }
}

