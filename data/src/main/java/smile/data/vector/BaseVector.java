/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.data.vector;

import java.io.Serializable;
import java.util.stream.BaseStream;
import smile.data.measure.Measure;
import smile.data.type.DataType;
import smile.data.type.StructField;

/**
 * Base interface for immutable named vectors, which are sequences of elements supporting
 * random access and sequential stream operations.
 *
 * @param <T>  the type of vector elements.
 * @param <TS> the type of stream elements.
 * @param <S>  the type of stream.
 *
 * @author Haifeng Li
 */
public interface BaseVector<T, TS, S extends BaseStream<TS, S>> extends Serializable {
    /**
     * Returns the (optional) name of vector.
     * @return the (optional) name of vector.
     */
    String name();

    /**
     * Returns the data type of elements.
     * @return the data type of elements.
     */
    DataType type();

    /**
     * Returns the (optional) level of measurements. Only valid for number types.
     * @return the (optional) level of measurements.
     */
    default Measure measure() {
        return null;
    }

    /**
     * Returns a struct field corresponding to this vector.
     * @return the struct field.
     */
    default StructField field() {
        return new StructField(name(), type(), measure());
    }

    /**
     * Returns the number of elements in the vector.
     * @return the number of elements in the vector.
     */
    int size();

    /**
     * Returns the array that backs this vector.
     * This is mostly for smile internal use for high performance.
     * The application developers should not use this method.
     * @return the array that backs this vector.
     */
    Object array();

    /**
     * Returns a double array of this vector.
     * @return the double array.
     */
    default double[] toDoubleArray() {
        return toDoubleArray(new double[size()]);
    }

    /**
     * Copies the vector value as double to the given array.
     * @param a the array to copy into.
     * @return the input array <code>a</code>.
     */
    default double[] toDoubleArray(double[] a) {
        throw new UnsupportedOperationException(name() + ":" + type());
    }

    /**
     * Returns an int array of this vector.
     * @return the int array.
     */
    default int[] toIntArray() {
        return toIntArray(new int[size()]);
    }

    /**
     * Copies the vector value as int to the given array.
     * @param a the array to copy into.
     * @return the input array <code>a</code>.
     */
    default int[] toIntArray(int[] a) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a string array of this vector.
     * @return the string array.
     */
    default String[] toStringArray() {
        return toStringArray(new String[size()]);
    }

    /**
     * Copies the vector value as string to the given array.
     * @param a the array to copy into.
     * @return the input array <code>a</code>.
     */
    default String[] toStringArray(String[] a) {
        int n = a.length;
        for (int i = 0; i < n; i++) {
            a[i] = field().toString(get(i));
        }
        return a;
    }

    /**
     * Returns the value at position i, which may be null.
     * @param i the index.
     * @return the value.
     */
    T get(int i);

    /**
     * Returns a new vector with selected entries.
     * @param index the index of selected entries.
     * @return the new vector of selected entries.
     */
    BaseVector<T, TS, S> get(int... index);

    /**
     * Returns the byte value at position i.
     * @param i the index.
     * @return the value.
     */
    byte getByte(int i);

    /**
     * Returns the short value at position i.
     * @param i the index.
     * @return the value.
     */
    short getShort(int i);

    /**
     * Returns the integer value at position i.
     * @param i the index.
     * @return the value.
     */
    int getInt(int i);

    /**
     * Returns the long value at position i.
     * @param i the index.
     * @return the value.
     */
    long getLong(int i);

    /**
     * Returns the float value at position i.
     * @param i the index.
     * @return the value.
     */
    float getFloat(int i);

    /**
     * Returns the double value at position i.
     * @param i the index.
     * @return the value.
     */
    double getDouble(int i);

    /**
     * Returns the value at position i, which may be null.
     * @param i the index.
     * @return the value.
     */
    default T apply(int i) {
        return get(i);
    }

    /**
     * Returns a new vector with selected entries.
     * @param index the index of selected entries.
     * @return the new vector of selected entries.
     */
    default BaseVector<T, TS, S> apply(int... index) {
        return get(index);
    }

    /**
     * Returns a stream of vector elements.
     *
     * @return the stream of vector elements.
     */
    S stream();
}