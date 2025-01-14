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
     * Returns a stream consisting of the elements of this vector.
     * @return a stream consisting of the elements of this vector.
     */
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
        @SuppressWarnings("unchecked")
        T[] data = (T[]) java.lang.reflect.Array.newInstance(vector.getClass().getComponentType(), index.size());
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
