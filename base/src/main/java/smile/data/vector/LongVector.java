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
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import smile.data.measure.NumericalMeasure;
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
        super(checkMeasure(field, NumericalMeasure.class));
        this.vector = vector;
    }

    @Override
    int length() {
        return vector.length;
    }

    @Override
    public LongStream asLongStream() {
        if (nullMask == null) {
            if (index == null) {
                return Arrays.stream(vector);
            } else {
                return index.stream().mapToLong(i -> vector[i]);
            }
        } else {
            return indexStream().filter(i -> !nullMask.get(i)).mapToLong(i -> vector[i]);
        }
    }

    @Override
    public DoubleStream asDoubleStream() {
        return asLongStream().mapToDouble(i -> i);
    }

    @Override
    public void set(int i, Object value) {
        vector[at(i)] = ((Number) value).longValue();
    }

    @Override
    public LongVector get(Index index) {
        LongVector copy = new LongVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public Long get(int i) {
        int index = at(i);
        if (nullMask == null) {
            return vector[index];
        } else {
            return nullMask.get(index) ? null : vector[index];
        }
    }

    @Override
    public long getLong(int i) {
        return vector[at(i)];
    }

    @Override
    public boolean getBoolean(int i) {
        return getLong(i) != 0;
    }

    @Override
    public char getChar(int i) {
        return (char) getLong(i);
    }

    @Override
    public byte getByte(int i) {
        return (byte) getLong(i);
    }

    @Override
    public short getShort(int i) {
        return (short) getLong(i);
    }

    @Override
    public int getInt(int i) {
        return (int) getLong(i);
    }

    @Override
    public float getFloat(int i) {
        return getLong(i);
    }

    @Override
    public double getDouble(int i) {
        return getLong(i);
    }
}