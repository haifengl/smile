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

package smile.math;

/**
 * A resizeable, array-backed list of integer primitives.
 *
 * @author Haifeng Li
 */

public final class IntArrayList {

    /**
     * The data of the list.
     */
    private int[] data;

    /**
     * The index after the last entry in the list.
     */
    private int size;

    /**
     * The default capacity for new lists.
     */
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * Constructs an empty list.
     */
    public IntArrayList() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Constructs an empty list with the specified initial capacity.
     *
     * @param capacity the initial size of array list.
     */
    public IntArrayList(int capacity) {
        data = new int[capacity];
        size = 0;
    }

    /**
     *  Constructs a list containing the values of the specified array.
     *
     * @param values the initial values of array list.
     */
    public IntArrayList(int[] values) {
        this(Math.max(values.length, DEFAULT_CAPACITY));
        add(values);
    }

    /**
     * Increases the capacity, if necessary, to ensure that it can hold
     * at least the number of values specified by the minimum capacity
     * argument. 
     *
     * @param capacity the desired minimum capacity.
     */
    public void ensureCapacity(int capacity) {
        if (capacity > data.length) {
            int newCap = Math.max(data.length << 1, capacity);
            int[] tmp = new int[newCap];
            System.arraycopy(data, 0, tmp, 0, data.length);
            data = tmp;
        }
    }

    /**
     * Returns the number of values in the list.
     *
     * @return the number of values in the list
     */
    public int size() {
        return size;
    }

    /**
     * Returns true if this list contains no values. 
     *
     * @return true if the list is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Trims the capacity to be the list's current size.
     */
    public void trimToSize() {
        if (data.length > size()) {
            int[] tmp = toArray();
            data = tmp;
        }
    }

    /**
     * Appends the specified value to the end of this list. 
     *
     * @param val a value to be appended to this list.
     */
    public void add(int val) {
        ensureCapacity(size + 1);
        data[size++] = val;
    }

    /**
     * Appends an array to the end of this list.
     *
     * @param vals an array to be appended to this list.
     */
    public void add(int[] vals) {
        ensureCapacity(size + vals.length);
        System.arraycopy(vals, 0, data, size, vals.length);
        size += vals.length;
    }

    /**
     * Returns the value at the specified position in this list.
     *
     * @param index index of the value to return 
     * @return the value at the specified position in this list 
     */
    public int get(int index) {
        return data[index];
    }

    /**
     * Replaces the value at the specified position in this list with the
     * specified value. 
     *
     * @param index index of the value to replace
     * @param val value to be stored at the specified position 
     * @throws IndexOutOfBoundsException if the index is out of range (index &lt; 0 || index &ge; size())
     */
    public IntArrayList set(int index, int val) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        data[index] = val;
        return this;
    }

    /**
     * Removes all of the value from this list. The list will
     * be empty after this call returns. 
     */
    public void clear() {
        size = 0;
    }

    /**
     * Removes the value at specified index from the list.
     *
     * @param index index of the element to remove.
     * @return the value previously stored at specified index
     * @throws IndexOutOfBoundsException if the index is out of range (index &lt; 0 || index &ge; size())
     */
    public int remove(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }

        int old = get(index);
        
        if (index == 0) {
            // data at the front
            System.arraycopy(data, 1, data, 0, size - 1);
        } else if (size - 1 == index) {
            // no copy to make, decrementing pos "deletes" values at
            // the end
        } else {
            // data in the middle
            System.arraycopy(data, index + 1, data, index, size - (index + 1));
        }
        
        size--;
        return old;
    }

    /**
     * Returns an array containing all of the values in this list in
     * proper sequence (from first to last value). 
     * The caller is thus free to modify the returned array. 
     */
    public int[] toArray() {
        return toArray(null);
    }

    /**
     * Returns an array containing all of the values in this list in
     * proper sequence (from first to last value). If the list fits
     * in the specified array, it is returned therein. Otherwise, a new
     * array is allocated with the size of this list. 
     *
     * @param dest the array into which the values of the list are to
     * be stored, if it is big enough; otherwise, a new array is allocated
     * for this purpose. 
     * @return an array containing the values of the list 
     */
    public int[] toArray(int[] dest) {
        if (dest == null || dest.length < size()) {
            dest = new int[size];
        }
        
        System.arraycopy(data, 0, dest, 0, size);
        return dest;
    }
}