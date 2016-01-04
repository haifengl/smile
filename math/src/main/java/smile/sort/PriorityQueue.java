/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.sort;

/**
 * Priority Queue for index items.
 *
 * @author Haifeng Li
 */
public class PriorityQueue {

    /**
     * The number of items in the queue.
     */
    private int n;
    /**
     * The d-ary heap or d-heap is a generalization of the binary heap data
     * structure whose non-leaf nodes have d children, instead of 2. Thus,
     * a binary heap is a 2-heap.
     */
    private int d;
    /**
     * External array of priority.
     */
    private double[] a;
    /**
     * The array of item indices.
     */
    private int[] pq;
    /**
     * The inverse array qp allows the priority-queue to treat the array indices
     * as handles.
     */
    private int[] qp;

    /**
     * Priority comparison of item i and j.
     * @param i item index
     * @param j item index
     */
    private boolean less(int i, int j) {
        return a[pq[i]] < a[pq[j]];
    }

    /**
     * Swap i and j items of pq and qp.
     * @param i item index
     * @param j item index
     */
    private void swap(int i, int j) {
        int t = pq[i];
        pq[i] = pq[j];
        pq[j] = t;
        qp[pq[i]] = i;
        qp[pq[j]] = j;
    }

    /**
     * fix up.
     */
    private void swim(int k) {
        while (k > 1 && less(k, (k + d - 2) / d)) {
            swap(k, (k + d - 2) / d);
            k = (k + d - 2) / d;
        }
    }

    /**
     * fix down.
     */
    private void sink(int k, int N) {
        int j;
        while ((j = d * (k - 1) + 2) <= N) {
            for (int i = j + 1; i < j + d && i <= N; i++) {
                if (less(i, j)) {
                    j = i;
                }
            }
            if (!(less(j, k))) {
                break;
            }
            swap(k, j);
            k = j;
        }
    }

    /**
     * Constructor. Default use a 3-heap.
     * @param a external array of priority. Lower value means higher priority.
     */
    public PriorityQueue(double[] a) {
        this(3, a);
    }

    /**
     * Constructor.
     * @param d d-heap.
     * @param a external array of priority. Lower value means higher priority.
     */
    public PriorityQueue(int d, double[] a) {
        this.d = d;
        this.a = a;
        this.n = 0;
        pq = new int[a.length + 1];
        qp = new int[a.length + 1];
    }

    /**
     * Returns true if the queue is empty.
     */
    public boolean empty() {
        return n == 0;
    }

    /**
     * Insert a new item into queue.
     * @param v the index of item.
     */
    public void insert(int v) {
        pq[++n] = v;
        qp[v] = n;
        swim(n);
    }

    /**
     * Removes and returns the index of item with minimum value (highest priority).
     */
    public int poll() {
        swap(1, n);
        sink(1, n - 1);
        return pq[n--];
    }

    /**
     * The value of item k is lower (higher priority) now.
     */
    public void lower(int k) {
        swim(qp[k]);
    }

    /**
     * The priority of item k has changed.
     */
    public void change(int k) {
        swim(qp[k]);
        sink(qp[k], n);
    }
}
