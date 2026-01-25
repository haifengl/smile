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

import java.util.BitSet;
import java.util.stream.IntStream;
import smile.data.measure.NumericalMeasure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A nullable char vector.
 *
 * @author Haifeng Li
 */
public class NullableCharVector extends NullablePrimitiveVector {
    /** The vector data. */
    private final char[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableCharVector(String name, char[] vector, BitSet nullMask) {
        this(new StructField(name, DataTypes.NullableCharType), vector, nullMask);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableCharVector(StructField field, char[] vector, BitSet nullMask) {
        if (field.dtype() != DataTypes.NullableCharType) {
            throw new IllegalArgumentException("Invalid data type: " + field);
        }
        if (field.measure() instanceof NumericalMeasure) {
            throw new IllegalArgumentException("Invalid measure: " + field.measure());
        }
        super(field, nullMask);
        this.vector = vector;
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public NullableCharVector withName(String name) {
        return new NullableCharVector(field.withName(name), vector, nullMask);
    }

    @Override
    public IntStream intStream() {
        return index().map(i -> nullMask.get(i) ? 0 : vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        if (value == null) {
            nullMask.set(i);
        } else if (value instanceof Character c) {
            vector[i] = c;
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public NullableCharVector get(Index index) {
        int n = index.size();
        char[] data = new char[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            int idx = index.apply(i);
            data[i] = vector[idx];
            mask.set(i, nullMask.get(idx));
        }
        return new NullableCharVector(field, data, mask);
    }

    @Override
    public Character get(int i) {
        return nullMask.get(i) ? null : vector[i];
    }

    @Override
    public char getChar(int i) {
        return vector[i];
    }

    @Override
    public boolean getBoolean(int i) {
        return vector[i] == 'T';
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
        return vector[i];
    }

    @Override
    public long getLong(int i) {
        return vector[i];
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
