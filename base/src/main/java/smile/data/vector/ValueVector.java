/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.vector;

import java.util.stream.*;
import smile.data.measure.CategoricalMeasure;
import smile.data.measure.Measure;
import smile.data.type.DataType;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * ValueVector interface is an abstraction that is used to store a sequence
 * of values having the same type in an individual column of data frame.
 * The implementation should support random access and sequential stream
 * operations.
 *
 * @author Haifeng Li
 */
public interface ValueVector {
    /**
     * Returns the struct field of the vector.
     * @return the struct field.
     */
    StructField field();

    /**
     * Returns the name of vector.
     * @return the name of vector.
     */
    default String name() {
        return field().name();
    }

    /**
     * Returns the data type of elements.
     * @return the data type of elements.
     */
    default DataType dtype() {
        return field().dtype();
    }

    /**
     * Returns the (optional) level of measurements. Only valid for number types.
     * @return the (optional) level of measurements.
     */
    default Measure measure() {
        return field().measure();
    }

    /**
     * Returns the number of elements in the vector.
     * @return the number of elements in the vector.
     */
    int size();

    /**
     * Returns true if the values of vector may be null.
     * @return true if the values of vector may be null.
     */
    boolean isNullable();

    /**
     * Returns true if the value at the given index is null/missing.
     * @param i the index.
     * @return true if the value at the given index is null/missing.
     */
    boolean isNullAt(int i);

    /**
     * Returns the number of null/missing values in this vector.
     * @return the number of null/missing values in this vector.
     */
    int getNullCount();

    /**
     * Returns true if there are any null/missing values in this vector.
     * @return true if there are any null/missing values in this vector.
     */
    default boolean anyNull() {
        return getNullCount() != 0;
    }

    /**
     * Returns an IntStream consisting of the elements of this vector,
     * converted to integer.
     *
     * @return an IntStream consisting of the elements of this vector.
     */
    default IntStream asIntStream() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a LongStream consisting of the elements of this vector,
     * converted to long.
     *
     * @return a LongStream consisting of the elements of this vector.
     */
    default LongStream asLongStream() {
        return asIntStream().mapToLong(i -> i);
    }

    /**
     * Returns a DoubleStream consisting of the elements of this vector,
     * converted to double.
     *
     * @return a DoubleStream consisting of the elements of this vector.
     */
    default DoubleStream asDoubleStream() {
        return asIntStream().mapToDouble(i -> i);
    }

    /**
     * Returns an int array of this vector.
     * @return an int array.
     */
    default int[] toIntArray() {
        return asIntStream().toArray();
    }

    /**
     * Returns a long array of this vector.
     * @return a long array.
     */
    default long[] toLongArray() {
        return asLongStream().toArray();
    }

    /**
     * Returns a double array of this vector.
     * @return a double array.
     */
    default double[] toDoubleArray() {
        return asDoubleStream().toArray();
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
        int n = Math.min(a.length, size());
        var field = field();
        for (int i = 0; i < n; i++) {
            a[i] = field.toString(get(i));
        }
        return a;
    }

    /**
     * Returns the value at position i, which may be null.
     * @param i the index.
     * @return the value.
     */
    Object get(int i);

    /**
     * Returns the value at position i, which may be null.
     * This is an alias to {@link #get(int) get} for Scala's convenience.
     * @param i the index.
     * @return the value.
     */
    default Object apply(int i) {
        return get(i);
    }

    /**
     * Sets the value at position i.
     * @param i the index.
     * @param value the new value.
     */
    void set(int i, Object value);

    /**
     * Updates the value at position i.
     * This is an alias to {@link #set(int, Object) set} for Scala's convenience.
     * @param i the index.
     * @param value the new value.
     */
    default void update(int i, Object value) {
        set(i, value);
    }

    /**
     * Returns a new vector with selected entries.
     * @param index the index of selected entries.
     * @return the new vector of selected entries.
     */
    ValueVector get(Index index);

    /**
     * Returns a new vector with selected entries.
     * @param indices the index of selected entries.
     * @return the new vector of selected entries.
     */
    default ValueVector get(int... indices) {
        return get(Index.of(indices));
    }

    /**
     * Returns a new vector with selected entries.
     * This is an alias to {@link #get(int...) get} for Scala's convenience.
     * @param indices the index of selected entries.
     * @return the new vector of selected entries.
     */
    default ValueVector apply(int... indices) {
        return get(indices);
    }

    /**
     * Returns the slice index for [start, end) with step 1.
     *
     * @param start the start index.
     * @param end the end index.
     * @return the slice.
     */
    default ValueVector slice(int start, int end) {
        return slice(start, end, 1);
    }

    /**
     * Returns the slice index for [start, end) with step 1.
     *
     * @param start the start index.
     * @param end the end index.
     * @param step the incremental step.
     * @return the slice.
     */
    default ValueVector slice(int start, int end, int step) {
        return get(Index.range(start, end, step));
    }

    /**
     * Returns the string representation of the value at position i.
     * @param i the index.
     * @return string representation.
     */
    default String getString(int i) {
        return field().toString(get(i));
    }

    /**
     * Returns the value at position i of NominalScale or OrdinalScale.
     *
     * @param i the index.
     * @throws ClassCastException when the data is not nominal or ordinal.
     * @return the value scale.
     */
    default String getScale(int i) {
        int x = getInt(i);
        var measure = measure();
        if (measure instanceof CategoricalMeasure cat) {
            return cat.toString(x);
        } else {
            return String.valueOf(x);
        }
    }

    /**
     * Returns the boolean value at position i.
     * @param i the index.
     * @return the value.
     */
    boolean getBoolean(int i);

    /**
     * Returns the character value at position i.
     * @param i the index.
     * @return the value.
     */
    char getChar(int i);

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
}
