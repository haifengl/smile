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
import smile.data.measure.NumericalMeasure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A nullable boolean vector.
 *
 * @author Haifeng Li
 */
public class NullableBooleanVector extends NullablePrimitiveVector {
    /** The vector data. */
    private final BitSet vector;
    /** The length of vector. BitSet's length and size methods have different semantics. */
    private final int size;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableBooleanVector(String name, boolean[] vector, BitSet nullMask) {
        this(new StructField(name, DataTypes.NullableBooleanType), vector, nullMask);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableBooleanVector(StructField field, boolean[] vector, BitSet nullMask) {
        this(field, vector.length, bitSet(vector), nullMask);
    }

    /**
     * Constructor.
     * @param name the name of vector.
     * @param size the length of vector.
     * @param bits the bit map of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableBooleanVector(String name, int size, BitSet bits, BitSet nullMask) {
        this(new StructField(name, DataTypes.NullableBooleanType), size, bits, nullMask);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param size the length of vector.
     * @param bits the bit map of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableBooleanVector(StructField field, int size, BitSet bits, BitSet nullMask) {
        if (field.dtype() != DataTypes.NullableBooleanType) {
            throw new IllegalArgumentException("Invalid data type: " + field);
        }
        if (field.measure() instanceof NumericalMeasure) {
            throw new IllegalArgumentException("Invalid measure: " + field.measure());
        }
        super(field, nullMask);
        this.size = size;
        this.vector = bits;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public NullableBooleanVector withName(String name) {
        return new NullableBooleanVector(field.withName(name), size, vector, nullMask);
    }

    @Override
    public IntStream intStream() {
        return index().map(i -> nullMask.get(i) ? Integer.MIN_VALUE : (vector.get(i) ? 1 : 0));
    }

    @Override
    public void set(int i, Object value) {
        if (value == null) {
            nullMask.set(i);
        } else if (value instanceof Boolean bool) {
            vector.set(i, bool);
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public NullableBooleanVector get(Index index) {
        int n = index.size();
        BitSet data = new BitSet(n);
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            int idx = index.apply(i);
            data.set(i, vector.get(idx));
            mask.set(i, nullMask.get(idx));
        }
        return new NullableBooleanVector(field, n, data, mask);
    }

    @Override
    public Boolean get(int i) {
        return nullMask.get(i) ? null : vector.get(i);
    }

    @Override
    public boolean getBoolean(int i) {
        return vector.get(i);
    }

    @Override
    public char getChar(int i) {
        return vector.get(i) ? 'T' : 'F';
    }

    @Override
    public byte getByte(int i) {
        return vector.get(i) ? (byte) 1 : 0;
    }

    @Override
    public short getShort(int i) {
        return vector.get(i) ? (short) 1 : 0;
    }

    @Override
    public int getInt(int i) {
        return vector.get(i) ? 1 : 0;
    }

    @Override
    public long getLong(int i) {
        return vector.get(i) ? 1 : 0;
    }

    @Override
    public float getFloat(int i) {
        return vector.get(i) ? 1 : 0;
    }

    @Override
    public double getDouble(int i) {
        return vector.get(i) ? 1 : 0;
    }
}
