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
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A long vector.
 *
 * @author Haifeng Li
 */
public class LongVector extends PrimitiveVector {
    /** The vector data. */
    private final long[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public LongVector(String name, long[] vector) {
        this(new StructField(name, DataTypes.LongType), vector);    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public LongVector(StructField field, long[] vector) {
        if (field.dtype() != DataTypes.LongType) {
            throw new IllegalArgumentException("Invalid data type: ");
        }
        super(field);
        this.vector = vector;
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public LongVector withName(String name) {
        return new LongVector(field.withName(name), vector);
    }

    @Override
    public LongStream longStream() {
        return Arrays.stream(vector);
    }

    @Override
    public DoubleStream doubleStream() {
        return longStream().mapToDouble(i -> i);
    }

    @Override
    public void set(int i, Object value) {
        if (value instanceof Number n) {
            vector[i] = n.longValue();
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public LongVector get(Index index) {
        var data = index.stream().mapToLong(i -> vector[i]).toArray();
        return new LongVector(field, data);
    }

    @Override
    public Long get(int i) {
        return vector[i];
    }

    @Override
    public long getLong(int i) {
        return vector[i];
    }

    @Override
    public boolean getBoolean(int i) {
        return vector[i] != 0;
    }

    @Override
    public char getChar(int i) {
        return (char) vector[i];
    }

    @Override
    public byte getByte(int i) {
        return (byte) vector[i];
    }

    @Override
    public short getShort(int i) {
        return (short) vector[i];
    }

    @Override
    public int getInt(int i) {
        return (int) vector[i];
    }

    @Override
    public float getFloat(int i) {
        return vector[i];
    }

    @Override
    public double getDouble(int i) {
        return vector[i];
    }
}
