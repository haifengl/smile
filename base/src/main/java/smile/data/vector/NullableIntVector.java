/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.vector;

import java.util.BitSet;
import java.util.stream.IntStream;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A nullable integer vector.
 *
 * @author Haifeng Li
 */
public class NullableIntVector extends NullablePrimitiveVector {
    /** The vector data. */
    private final int[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableIntVector(String name, int[] vector, BitSet nullMask) {
        this(new StructField(name, DataTypes.NullableIntType), vector, nullMask);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableIntVector(StructField field, int[] vector, BitSet nullMask) {
        if (field.dtype() != DataTypes.NullableIntType) {
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
    public NullableIntVector withName(String name) {
        return new NullableIntVector(field.withName(name), vector, nullMask);
    }

    @Override
    public IntStream intStream() {
        return index().map(i -> nullMask.get(i) ? Integer.MIN_VALUE : vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        if (value == null) {
            nullMask.set(i);
        } else if (value instanceof Number n) {
            vector[i] = n.intValue();
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public NullableIntVector get(Index index) {
        int n = index.size();
        int[] data = new int[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            int idx = index.apply(i);
            data[i] = vector[idx];
            mask.set(i, nullMask.get(idx));
        }
        return new NullableIntVector(field, data, mask);
    }

    @Override
    public Integer get(int i) {
        return nullMask.get(i) ? null : vector[i];
    }

    @Override
    public int getInt(int i) {
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
    public long getLong(int i) {
        return vector[i];
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
