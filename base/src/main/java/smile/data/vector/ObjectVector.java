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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A generic vector.
 *
 * @param <T> the data type of vector elements.
 *
 * @author Haifeng Li
 */
public class ObjectVector<T> extends AbstractVector {
    /** The vector data. */
    final T[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public ObjectVector(String name, T[] vector) {
        this(new StructField(name, DataTypes.object(vector.getClass().getComponentType())), vector);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public ObjectVector(StructField field, T[] vector) {
        super(field);
        this.vector = vector;
    }

    /**
     * Creates a boolean array vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    public static ObjectVector<boolean[]> of(String name, boolean[]... vector) {
        return new ObjectVector<>(new StructField(name, DataTypes.BooleanType), vector);
    }

    /**
     * Creates a char array vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    public static ObjectVector<char[]> of(String name, char[]... vector) {
        return new ObjectVector<>(new StructField(name, DataTypes.CharArrayType), vector);
    }

    /**
     * Creates a byte array vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    public static ObjectVector<byte[]> of(String name, byte[]... vector) {
        return new ObjectVector<>(new StructField(name, DataTypes.ByteArrayType), vector);
    }

    /**
     * Creates a short integer array vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    public static ObjectVector<short[]> of(String name, short[]... vector) {
        return new ObjectVector<>(new StructField(name, DataTypes.ShortArrayType), vector);
    }

    /**
     * Creates an integer array vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    public static ObjectVector<int[]> of(String name, int[]... vector) {
        return new ObjectVector<>(new StructField(name, DataTypes.IntArrayType), vector);
    }

    /**
     * Creates a long integer array vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    public static ObjectVector<long[]> of(String name, long[]... vector) {
        return new ObjectVector<>(new StructField(name, DataTypes.LongArrayType), vector);
    }

    /**
     * Creates a float array vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    public static ObjectVector<float[]> of(String name, float[]... vector) {
        return new ObjectVector<>(new StructField(name, DataTypes.FloatArrayType), vector);
    }

    /**
     * Creates a double array vector.
     *
     * @param name the name of vector.
     * @return the vector.
     * @param vector the data of vector.
     */
    public static ObjectVector<double[]> of(String name, double[]... vector) {
        return new ObjectVector<>(new StructField(name, DataTypes.DoubleArrayType), vector);
    }

    @Override
    public ObjectVector<T> withName(String name) {
        return new ObjectVector<>(field.withName(name), vector);
    }

    @Override
    public Stream<T> stream() {
        return Arrays.stream(vector);
    }

    /**
     * Returns the distinct values.
     * @return the distinct values.
     */
    public List<T> distinct() {
        return stream().distinct().toList();
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void set(int i, Object value) {
        vector[i] = (T) value;
    }

    @Override
    public ObjectVector<T> get(Index index) {
        T[] data = Arrays.copyOf(vector, index.size());
        for (int i = 0; i < data.length; i++) {
            data[i] = vector[index.apply(i)];
        }
        return new ObjectVector<>(field, data);
    }

    @Override
    public T get(int i) {
        return vector[i];
    }

    @Override
    public boolean getBoolean(int i) {
        return (Boolean) vector[i];
    }

    @Override
    public char getChar(int i) {
        return (Character) vector[i];
    }

    @Override
    public byte getByte(int i) {
        return ((Number) vector[i]).byteValue();
    }

    @Override
    public short getShort(int i) {
        return ((Number) vector[i]).shortValue();
    }

    @Override
    public int getInt(int i) {
        return ((Number) vector[i]).intValue();
    }

    @Override
    public long getLong(int i) {
        return ((Number) vector[i]).longValue();
    }

    @Override
    public float getFloat(int i) {
        if (vector[i] instanceof Number n) {
            return n.floatValue();
        }
        return Float.NaN;
    }

    @Override
    public double getDouble(int i) {
        if (vector[i] instanceof Number n) {
            return n.doubleValue();
        }
        return Double.NaN;
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public boolean isNullAt(int i) {
        return get(i) == null;
    }

    @Override
    public int getNullCount() {
        return (int) stream().filter(Objects::nonNull).count();
    }
}
