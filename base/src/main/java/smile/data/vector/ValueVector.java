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

import java.io.Serializable;
import java.util.BitSet;
import java.util.Optional;
import java.util.stream.*;
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
public interface ValueVector extends Serializable {
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
    default Optional<Measure> measure() {
        return Optional.ofNullable(field().measure());
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
     * Returns the array that backs this vector.
     * This is mostly for smile internal use for high performance.
     * The application developers should not use this method.
     * @return the array that backs this vector.
     */
    Object array();

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
     * Returns a LongStream consisting of the elements of this vector,
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
     * Returns the value at position i, which may be null.
     * This is an alias to {@link #get(int) get} for Scala's convenience.
     * @param i the index.
     * @return the value.
     */
    default Object apply(int i) {
        return get(i);
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

    /**
     * Creates a named boolean vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static BooleanVector of(String name, boolean[] vector) {
        return new BooleanVector(name, vector);
    }

    /** Creates a named boolean vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static BooleanVector of(StructField field, boolean[] vector) {
        return new BooleanVector(field, vector);
    }

    /** Creates a named boolean vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static BooleanVector of(StructField field, BitSet vector) {
        return new BooleanVector(field, vector);
    }

    /** Creates a named char vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static CharVector of(String name, char[] vector) {
        return new CharVector(name, vector);
    }

    /** Creates a named char vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static CharVector of(StructField field, char[] vector) {
        return new CharVector(field, vector);
    }

    /** Creates a named byte vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ByteVector of(String name, byte[] vector) {
        return new ByteVector(name, vector);
    }

    /** Creates a named byte vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ByteVector of(StructField field, byte[] vector) {
        return new ByteVector(field, vector);
    }

    /** Creates a named short integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ShortVector of(String name, short[] vector) {
        return new ShortVector(name, vector);
    }

    /** Creates a named short integer vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ShortVector of(StructField field, short[] vector) {
        return new ShortVector(field, vector);
    }

    /** Creates a named integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static IntVector of(String name, int[] vector) {
        return new IntVector(name, vector);
    }

    /** Creates a named integer vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static IntVector of(StructField field, int[] vector) {
        return new IntVector(field, vector);
    }

    /** Creates a named long vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    static LongVector of(String name, long[] vector) {
        return new LongVector(name, vector);
    }

    /** Creates a named long integer vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static LongVector of(StructField field, long[] vector) {
        return new LongVector(field, vector);
    }

    /** Creates a named float vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static FloatVector of(String name, float[] vector) {
        return new FloatVector(name, vector);
    }

    /** Creates a named float vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static FloatVector of(StructField field, float[] vector) {
        return new FloatVector(field, vector);
    }

    /** Creates a named double vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static DoubleVector of(String name, double[] vector) {
        return new DoubleVector(name, vector);
    }

    /** Creates a named double vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static DoubleVector of(StructField field, double[] vector) {
        return new DoubleVector(field, vector);
    }

    /**
     * Creates a named vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @param <T> the data type of vector elements.
     * @return the vector.
     */
    static <T> ObjectVector<T> of(String name, T[] vector) {
        return new ObjectVector<>(name, vector);
    }

    /** Creates a named vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @param <T> the data type of vector elements.
     * @return the vector.
     */
    static <T> ObjectVector<T> of(StructField field, T[] vector) {
        return new ObjectVector<>(field, vector);
    }

    /** Creates a named number vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static <T extends Number> NumberVector<T> of(StructField field, T[] vector) {
        return new NumberVector<T>(field, vector);
    }

    /**
     * Creates a named string vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    static StringVector of(String name, String... vector) {
        return new StringVector(name, vector);
    }

    /** Creates a named string vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static StringVector of(StructField field, String... vector) {
        return new StringVector(field, vector);
    }
}
