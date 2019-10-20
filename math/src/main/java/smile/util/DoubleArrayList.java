/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.util;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * A resizeable, array-backed list of double primitives.
 *
 * @author Haifeng Li
 */

public final class DoubleArrayList implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Format for toString. */
    private static DecimalFormat format = new DecimalFormat("#.######");

    /**
     * The data of the list.
     */
    double[] data;

    /**
     * The index after the last entry in the list.
     */
    private int size;

    /**
     * Constructs an empty list.
     */
    public DoubleArrayList() {
        this(10);
    }

    /**
     * Constructs an empty list with the specified initial capacity.
     *
     * @param capacity the initial size of array list.
     */
    public DoubleArrayList(int capacity) {
        data = new double[capacity];
        size = 0;
    }

    /**
     *  Constructs a list containing the values of the specified array.
     *
     * @param values the initial values of array list.
     */
    public DoubleArrayList(double[] values) {
        this(Math.max(values.length, 10));
        add(values);
    }

    @Override
    public String toString() {
        return Arrays.stream(data).limit(size).mapToObj(format::format).collect(Collectors.joining(", ", "[", "]"));
    }

    /** Returns the stream of the array list. */
    public DoubleStream stream() {
        return DoubleStream.of(data).limit(size);
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
            double[] tmp = new double[newCap];
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
            double[] tmp = toArray();
            data = tmp;
        }
    }

    /**
     * Appends the specified value to the end of this list. 
     *
     * @param val a value to be appended to this list.
     */
    public void add(double val) {
        ensureCapacity(size + 1);
        data[size++] = val;
    }

    /**
     * Appends an array to the end of this list.
     *
     * @param vals an array to be appended to this list.
     */
    public void add(double[] vals) {
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
    public double get(int index) {
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
    public DoubleArrayList set(int index, double val) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        data[index] = val;
        return this;
    }

    /**
     * Removes all of the values from this list. The list will
     * be empty after this call returns. 
     */
    public void clear() {
        size = 0;
    }

    /**
     * Removes the value at specified index from the list.
     *
     * @param index index of the value to remove.
     * @return the value previously stored at specified index
     * @throws IndexOutOfBoundsException if the index is out of range (index &lt; 0 || index &ge; size())
     */
    public double remove(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }

        double old = get(index);
        
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
    public double[] toArray() {
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
    public double[] toArray(double[] dest) {
        if (dest == null || dest.length < size()) {
            dest = new double[size];
        }
        
        System.arraycopy(data, 0, dest, 0, size);
        return dest;
    }
}