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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.vector;

import java.util.BitSet;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A nullable long vector.
 *
 * @author Haifeng Li
 */
public class NullableLongVector extends NullablePrimitiveVector {
    /** The vector data. */
    private final long[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableLongVector(String name, long[] vector, BitSet nullMask) {
        this(new StructField(name, DataTypes.NullableLongType), vector, nullMask);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableLongVector(StructField field, long[] vector, BitSet nullMask) {
        if (field.dtype() != DataTypes.NullableLongType) {
            throw new IllegalArgumentException("Invalid data type: " + field);
        }
        super(field, nullMask);
        this.vector = vector;
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public NullableLongVector withName(String name) {
        return new NullableLongVector(field.withName(name), vector, nullMask);
    }

    @Override
    public LongStream longStream() {
        return index().mapToLong(i -> nullMask.get(i) ? Long.MIN_VALUE : vector[i]);
    }

    @Override
    public DoubleStream doubleStream() {
        return longStream().mapToDouble(i -> i);
    }

    @Override
    public void set(int i, Object value) {
        if (value == null) {
            nullMask.set(i);
        } else if (value instanceof Number n) {
            vector[i] = n.longValue();
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public NullableLongVector get(Index index) {
        int n = index.size();
        long[] data = new long[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            int idx = index.apply(i);
            data[i] = vector[idx];
            mask.set(i, nullMask.get(idx));
        }
        return new NullableLongVector(field, data, mask);
    }

    @Override
    public Long get(int i) {
        return nullMask.get(i) ? null : vector[i];
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
        return nullMask.get(i) ? Float.NaN : vector[i];
    }

    @Override
    public double getDouble(int i) {
        return nullMask.get(i) ? Double.NaN : vector[i];
    }
}
