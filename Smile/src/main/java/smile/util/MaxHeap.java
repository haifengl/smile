/**
 * ****************************************************************************
 * Copyright (c) 2010 Haifeng Li
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * *****************************************************************************
 */
package smile.util;

import java.util.Arrays;


/**
 *
 * A fixed-size heap track k-smallest elements of a one-pass stream.
 * The top of heap is the biggest elements in the heap.
 * This heap only contains k or less than k smallest elements in stream.
 *
 *
 * @author Qiyang Zuo
 */
public class MaxHeap<E extends Comparable<? super E>> {

    /**
     * The heap size.
     */
    private int k;

    /**
     * The number of objects that have been added into heap.
     */
    private int n;

    /**
     * The max heap array.
     * Top of the heap is 0th element of the heap.
     */
    private E[] heap;

    /**
     * Constructor.
     *
     * @param heap the array to store smallest values to track.
     */
    public MaxHeap(E[] heap) {
        this.heap = heap;
        k = heap.length;
        n = 0;
    }

    /**
     * add to heap
     * @param e element add to heap
     */
    public void add(E e) {
        if (n < k) {
            heap[n++] = e;
            bubbleUp();
        } else {
            if (e.compareTo(top()) < 0) {
                heap[0] = e;
                bubbleDown();
            }
        }
    }

    /**
     * get but don't remove the top element of the heap
     *
     * @return
     */
    public E top() {
        if (n > 0) {
            return heap[0];
        } else {
            return null;
        }
    }

    /**
     * Get the heap data sorted increasingly
     * @return elements array in heap in increasing order.
     */
    @SuppressWarnings("unchecked")
    public E[] toSortedArray() {
        E[] arr = Arrays.copyOfRange(heap, 0, n);
        Arrays.sort(arr, 0, n);
        return arr;
    }

    private int lchild(int i) {
        return i * 2 + 1;
    }

    private int rchild(int i) {
        return (i + 1) * 2;
    }

    private int parent(int i) {
        return (i - 1) / 2;
    }

    private void bubbleUp() {
        int i = n - 1;
        E e = heap[i];
        while (i > 0 && heap[parent(i)].compareTo(e) < 0) {
            heap[i] = heap[parent(i)];
            i = parent(i);
        }
        heap[i] = e;
    }

    private void bubbleDown() {
        E e = heap[0];
        int i = 0;
        while (lchild(i) < n) {
            int maxChild = rchild(i) < n && heap[lchild(i)].compareTo(heap[rchild(i)]) < 0 ? rchild(i) : lchild(i);
            if (e.compareTo(heap[maxChild]) < 0) {
                heap[i] = heap[maxChild];
                i = maxChild;
            } else {
                break;
            }
        }
        heap[i] = e;
    }
}