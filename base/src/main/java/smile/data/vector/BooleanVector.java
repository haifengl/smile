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

import java.util.BitSet;
import java.util.stream.IntStream;
import smile.data.measure.NumericalMeasure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A boolean vector.
 *
 * @author Haifeng Li
 */
public class BooleanVector extends PrimitiveVector {
    /** The vector data. */
    private final BitSet vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public BooleanVector(String name, boolean[] vector) {
        this(new StructField(name, DataTypes.ByteType), vector);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public BooleanVector(StructField field, boolean[] vector) {
        this(field, bitSet(vector));
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param bits the bit map of vector.
     */
    public BooleanVector(StructField field, BitSet bits) {
        super(checkMeasure(field, NumericalMeasure.class));
        this.vector = bits;
    }

    @Override
    int length() {
        return vector.size();
    }

    @Override
    public IntStream asIntStream() {
        if (nullMask == null) {
            return indexStream().map(i -> vector.get(i) ? 1 : 0);
        } else {
            return indexStream().filter(i -> !nullMask.get(i)).map(i -> vector.get(i) ? 1 : 0);
        }
    }

    @Override
    public void set(int i, Object value) {
        if ((Boolean) value) {
            vector.set(at(i));
        }
    }

    @Override
    public BooleanVector get(Index index) {
        BooleanVector copy = new BooleanVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public Boolean get(int i) {
        int index = at(i);
        if (nullMask == null) {
            return vector.get(index);
        } else {
            return nullMask.get(index) ? null : vector.get(index);
        }
    }

    @Override
    public boolean getBoolean(int i) {
        return vector.get(at(i));
    }

    @Override
    public char getChar(int i) {
        return getBoolean(i) ? 'T' : 'F';
    }

    @Override
    public byte getByte(int i) {
        return getBoolean(i) ? (byte) 1 : 0;
    }

    @Override
    public short getShort(int i) {
        return getBoolean(i)  ? (short) 1 : 0;
    }

    @Override
    public int getInt(int i) {
        return getBoolean(i)  ? 1 : 0;
    }

    @Override
    public long getLong(int i) {
        return getBoolean(i)  ? 1 : 0;
    }

    @Override
    public float getFloat(int i) {
        return getBoolean(i)  ? 1 : 0;
    }

    @Override
    public double getDouble(int i) {
        return getBoolean(i)  ? 1 : 0;
    }

    /**
     * Converts a boolean array to BitSet.
     * @param vector a boolean array.
     * @return the BitSet.
     */
    private static BitSet bitSet(boolean[] vector) {
        BitSet bits = new BitSet(vector.length);
        for (int i = 0; i < vector.length; i++) {
            if (vector[i]) {
                bits.set(i);
            }
        }
        return bits;
    }
}