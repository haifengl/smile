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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.vector;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import smile.data.measure.CategoricalMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.measure.OrdinalScale;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
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
@SuppressWarnings("unchecked")
public interface ValueVector extends Serializable {
    /**
     * Returns the struct field of the vector.
     * @return the struct field.
     */
    StructField field();

    /**
     * Returns the number of elements in the vector.
     * @return the number of elements in the vector.
     */
    int size();

    /**
     * Returns the vector with the new name.
     * @param name the new name.
     * @return the vector with the new name.
     */
    ValueVector withName(String name);

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
     * Returns the result of equality comparison.
     * @param other the reference object with which to compare.
     * @return the result of equality comparison.
     */
    default boolean[] eq(Object other) {
        boolean[] result = new boolean[size()];
        if (other instanceof ValueVector vector) {
            if (vector.size() != size()) {
                throw new IllegalArgumentException("Vector size mismatch");
            }
            for (int i = 0; i < result.length; i++) {
                result[i] = vector.get(i).equals(get(i));
            }
        } else {
            for (int i = 0; i < result.length; i++) {
                result[i] = other.equals(get(i));
            }
        }
        return result;
    }

    /**
     * Returns the result of non-equality comparison.
     * @param other the reference object with which to compare.
     * @return the result of non-equality comparison.
     */
    default boolean[] ne(Object other) {
        boolean[] result = new boolean[size()];
        if (other instanceof ValueVector vector) {
            if (vector.size() != size()) {
                throw new IllegalArgumentException("Vector size mismatch");
            }
            for (int i = 0; i < result.length; i++) {
                result[i] = !vector.get(i).equals(get(i));
            }
        } else {
            for (int i = 0; i < result.length; i++) {
                result[i] = !other.equals(get(i));
            }
        }
        return result;
    }

    /**
     * Returns the result of less-than comparison.
     * @param other the reference object with which to compare.
     * @param <T> the type of the vector elements.
     * @return the result of less-than comparison.
     */
    default <T extends Comparable<T>> boolean[] lt(T other) {
        boolean[] result = new boolean[size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = other.compareTo((T) get(i)) > 0;
        }
        return result;
    }

    /**
     * Returns the result of less-than or equal comparison.
     * @param other the reference object with which to compare.
     * @param <T> the type of the vector elements.
     * @return the result of less-than or equal comparison.
     */
    default <T extends Comparable<T>> boolean[] le(T other) {
        boolean[] result = new boolean[size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = other.compareTo((T) get(i)) >= 0;
        }
        return result;
    }

    /**
     * Returns the result of greater-than comparison.
     * @param other the reference object with which to compare.
     * @param <T> the type of the vector elements.
     * @return the result of greater-than comparison.
     */
    default <T extends Comparable<T>> boolean[] gt(T other) {
        boolean[] result = new boolean[size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = other.compareTo((T) get(i)) < 0;
        }
        return result;
    }

    /**
     * Returns the result of greater-than or equal comparison.
     * @param other the reference object with which to compare.
     * @param <T> the type of the vector elements.
     * @return the result of greater-than or equal comparison.
     */
    default <T extends Comparable<T>> boolean[] ge(T other) {
        boolean[] result = new boolean[size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = other.compareTo((T) get(i)) <= 0;
        }
        return result;
    }

    /**
     * Returns true if there are any null/missing values in this vector.
     * @return true if there are any null/missing values in this vector.
     */
    default boolean anyNull() {
        return getNullCount() != 0;
    }

    /**
     * Returns whether each element is null/missing.
     * @return whether each element is null/missing.
     */
    default boolean[] isNull() {
        boolean[] result = new boolean[size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = isNullAt(i);
        }
        return result;
    }

    /**
     * Returns whether each element is contained in values.
     * @param values the set of values.
     * @return whether each element is contained in values.
     */
    default boolean[] isin(String... values) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, values);
        boolean[] result = new boolean[size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = set.contains(getString(i));
        }
        return result;
    }

    /**
     * Returns a stream consisting of the elements of this vector.
     * @return a stream consisting of the elements of this vector.
     */
    default Stream<?> stream() {
        return intStream().mapToObj(this::get);
    }

    /**
     * Returns an IntStream consisting of the elements of this vector,
     * converted to integer.
     *
     * @return an IntStream consisting of the elements of this vector.
     */
    default IntStream intStream() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a LongStream consisting of the elements of this vector,
     * converted to long.
     *
     * @return a LongStream consisting of the elements of this vector.
     */
    default LongStream longStream() {
        return intStream().mapToLong(i -> i);
    }

    /**
     * Returns a DoubleStream consisting of the elements of this vector,
     * converted to double.
     *
     * @return a DoubleStream consisting of the elements of this vector.
     */
    default DoubleStream doubleStream() {
        return intStream().mapToDouble(i -> i);
    }

    /**
     * Returns an int array of this vector.
     * @return an int array.
     */
    default int[] toIntArray() {
        return intStream().toArray();
    }

    /**
     * Returns a long array of this vector.
     * @return a long array.
     */
    default long[] toLongArray() {
        return longStream().toArray();
    }

    /**
     * Returns a double array of this vector.
     * @return a double array.
     */
    default double[] toDoubleArray() {
        return doubleStream().toArray();
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

    /**
     * Creates a boolean vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static BooleanVector of(String name, boolean... vector) {
        return new BooleanVector(name, vector);
    }

    /**
     * Creates a nullable boolean vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NullableBooleanVector ofNullable(String name, Boolean... vector) {
        int n = vector.length;
        boolean[] data = new boolean[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (vector[i] == null) {
                mask.set(i);
            } else {
                data[i] = vector[i];
            }
        }
        return new NullableBooleanVector(name, data, mask);
    }

    /**
     * Creates a char vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static CharVector of(String name, char... vector) {
        return new CharVector(name, vector);
    }

    /**
     * Creates a nullable char vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NullableCharVector ofNullable(String name, Character... vector) {
        int n = vector.length;
        char[] data = new char[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (vector[i] == null) {
                mask.set(i);
                data[i] = 0;
            } else {
                data[i] = vector[i];
            }
        }
        return new NullableCharVector(name, data, mask);
    }

    /**
     * Creates a byte vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ByteVector of(String name, byte... vector) {
        return new ByteVector(name, vector);
    }

    /**
     * Creates a nullable byte vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NullableByteVector ofNullable(String name, Byte... vector) {
        int n = vector.length;
        byte[] data = new byte[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (vector[i] == null) {
                mask.set(i);
                data[i] = Byte.MIN_VALUE;
            } else {
                data[i] = vector[i];
            }
        }
        return new NullableByteVector(name, data, mask);
    }

    /**
     * Creates a short integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ShortVector of(String name, short... vector) {
        return new ShortVector(name, vector);
    }

    /**
     * Creates a nullable short integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NullableShortVector ofNullable(String name, Short... vector) {
        int n = vector.length;
        short[] data = new short[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (vector[i] == null) {
                mask.set(i);
                data[i] = Short.MIN_VALUE;
            } else {
                data[i] = vector[i];
            }
        }
        return new NullableShortVector(name, data, mask);
    }

    /**
     * Creates an integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static IntVector of(String name, int... vector) {
        return new IntVector(name, vector);
    }

    /**
     * Creates a nullable integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NullableIntVector ofNullable(String name, Integer... vector) {
        int n = vector.length;
        int[] data = new int[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (vector[i] == null) {
                mask.set(i);
                data[i] = Integer.MIN_VALUE;
            } else {
                data[i] = vector[i];
            }
        }
        return new NullableIntVector(name, data, mask);
    }

    /**
     * Creates a long vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    static LongVector of(String name, long... vector) {
        return new LongVector(name, vector);
    }

    /**
     * Creates a nullable long integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NullableLongVector ofNullable(String name, Long... vector) {
        int n = vector.length;
        long[] data = new long[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (vector[i] == null) {
                mask.set(i);
                data[i] = Long.MIN_VALUE;
            } else {
                data[i] = vector[i];
            }
        }
        return new NullableLongVector(name, data, mask);
    }

    /**
     * Creates a float vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static FloatVector of(String name, float... vector) {
        return new FloatVector(name, vector);
    }

    /**
     * Creates a nullable float vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NullableFloatVector ofNullable(String name, Float... vector) {
        int n = vector.length;
        float[] data = new float[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (vector[i] == null) {
                mask.set(i);
                data[i] = Float.NaN;
            } else {
                data[i] = vector[i];
            }
        }
        return new NullableFloatVector(name, data, mask);
    }

    /**
     * Creates a named double vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static DoubleVector of(String name, double... vector) {
        return new DoubleVector(name, vector);
    }

    /**
     * Creates a nullable double vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NullableDoubleVector ofNullable(String name, Double... vector) {
        int n = vector.length;
        double[] data = new double[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            if (vector[i] == null) {
                mask.set(i);
                data[i] = Double.NaN;
            } else {
                data[i] = vector[i];
            }
        }
        return new NullableDoubleVector(name, data, mask);
    }

    /**
     * Creates a string vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static StringVector of(String name, String... vector) {
        return new StringVector(name, vector);
    }

    /**
     * Creates a decimal vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NumberVector<BigDecimal> of(String name, BigDecimal... vector) {
        var field = new StructField(name, DataTypes.DecimalType);
        return new NumberVector<>(field, vector);
    }

    /**
     * Creates a timestamp vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ObjectVector<Timestamp> of(String name, Timestamp... vector) {
        var field = new StructField(name, DataTypes.DateTimeType);
        return new ObjectVector<>(field, vector);
    }

    /**
     * Creates an instant vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ObjectVector<Instant> of(String name, Instant... vector) {
        var field = new StructField(name, DataTypes.DateTimeType);
        return new ObjectVector<>(field, vector);
    }

    /**
     * Creates a datetime vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ObjectVector<LocalDateTime> of(String name, LocalDateTime... vector) {
        var field = new StructField(name, DataTypes.DateTimeType);
        return new ObjectVector<>(field, vector);
    }

    /**
     * Creates a datetime vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ObjectVector<ZonedDateTime> of(String name, ZonedDateTime... vector) {
        var field = new StructField(name, DataTypes.DateTimeType);
        return new ObjectVector<>(field, vector);
    }

    /**
     * Creates a date vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ObjectVector<LocalDate> of(String name, LocalDate... vector) {
        var field = new StructField(name, DataTypes.DateType);
        return new ObjectVector<>(field, vector);
    }

    /**
     * Creates a time vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ObjectVector<LocalTime> of(String name, LocalTime... vector) {
        var field = new StructField(name, DataTypes.TimeType);
        return new ObjectVector<>(field, vector);
    }

    /**
     * Creates a time vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ObjectVector<OffsetTime> of(String name, OffsetTime... vector) {
        var field = new StructField(name, DataTypes.TimeType);
        return new ObjectVector<>(field, vector);
    }

    /**
     * Creates a nominal value vector.
     *
     * @param name the name of vector.
     * @param vector the enum data of vector.
     * @return the vector.
     */
    static ValueVector nominal(String name, Enum<?>... vector) {
        var clazz = vector.getClass().getComponentType();
        var values = clazz.getEnumConstants();
        var dtype = DataTypes.category(values.length);
        var measure = new NominalScale((Class<? extends Enum<?>>) clazz);
        var field = new StructField(name, dtype, measure);
        return category(field, vector);
    }

    /**
     * Creates a nominal value vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ValueVector nominal(String name, String... vector) {
        Set<String> values = new TreeSet<>();
        Collections.addAll(values, vector);
        var dtype = DataTypes.category(values.size());
        var measure = new NominalScale(values.toArray(new String[0]));
        var field = new StructField(name, dtype, measure);
        return category(field, vector);
    }

    /**
     * Creates a nominal value vector.
     *
     * @param name the name of vector.
     * @param vector the enum data of vector.
     * @return the vector.
     */
    static ValueVector ordinal(String name, Enum<?>... vector) {
        var clazz = vector.getClass().getComponentType();
        var values = clazz.getEnumConstants();
        var dtype = DataTypes.category(values.length);
        var measure = new OrdinalScale((Class<? extends Enum<?>>) clazz);
        var field = new StructField(name, dtype, measure);
        return category(field, vector);
    }

    /**
     * Creates a nominal value vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static ValueVector ordinal(String name, String... vector) {
        Set<String> values = new TreeSet<>();
        Collections.addAll(values, vector);
        var dtype = DataTypes.category(values.size());
        var measure = new OrdinalScale(values.toArray(new String[0]));
        var field = new StructField(name, dtype, measure);
        return category(field, vector);
    }

    /**
     * Creates a categorical value vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    private static ValueVector category(StructField field, Enum<?>[] vector) {
        int n = vector.length;
        var dtype = field.dtype();
        var measure = field.measure();
        return switch (dtype.id()) {
            case Byte -> {
                byte[] data = new byte[n];
                for (int i = 0; i < n; i++) data[i] = (byte) vector[i].ordinal();
                yield new ByteVector(field, data);
            }
            case Short -> {
                short[] data = new short[n];
                for (int i = 0; i < n; i++) data[i] = (short) vector[i].ordinal();
                yield new ShortVector(field, data);
            }
            case Int -> {
                int[] data = new int[n];
                for (int i = 0; i < n; i++) data[i] = vector[i].ordinal();
                yield new IntVector(field, data);
            }
            default ->
                    throw new IllegalStateException("Invalid categorical data type: " + dtype);
        };
    }

    /**
     * Creates a categorical value vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    private static ValueVector category(StructField field, String[] vector) {
        int n = vector.length;
        var dtype = field.dtype();
        var measure = field.measure();
        return switch (field.dtype().id()) {
            case Byte -> {
                byte[] data = new byte[n];
                for (int i = 0; i < n; i++) data[i] = (byte) measure.valueOf(vector[i]);
                yield new ByteVector(field, data);
            }
            case Short -> {
                short[] data = new short[n];
                for (int i = 0; i < n; i++) data[i] = (short) measure.valueOf(vector[i]);
                yield new ShortVector(field, data);
            }
            case Int -> {
                int[] data = new int[n];
                for (int i = 0; i < n; i++) data[i] = (int) measure.valueOf(vector[i]);
                yield new IntVector(field, data);
            }
            default ->
                    throw new IllegalStateException("Invalid categorical data type: " + dtype);
        };
    }
}
