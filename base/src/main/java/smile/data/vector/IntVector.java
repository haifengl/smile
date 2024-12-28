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
import java.util.stream.IntStream;
import smile.data.measure.NumericalMeasure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * An integer vector.
 *
 * @author Haifeng Li
 */
public class IntVector extends PrimitiveVector {
    /** The vector data. */
    private final int[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public IntVector(String name, int[] vector) {
        this(new StructField(name, DataTypes.IntegerType), vector);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public IntVector(StructField field, int[] vector) {
        super(checkMeasure(field, NumericalMeasure.class));
        this.vector = vector;
    }

    @Override
    int length() {
        return vector.length;
    }

    @Override
    public int[] array() {
        return vector;
    }

    @Override
    public IntStream asIntStream() {
        if (nullMask == null) {
            if (index == null) {
                return Arrays.stream(vector);
            } else {
                return index.stream().map(i -> vector[i]);
            }
        } else {
            return indexStream().filter(i -> !nullMask.get(i)).map(i -> vector[i]);
        }
    }

    @Override
    public void set(int i, Object value) {
        vector[at(i)] = ((Number) value).intValue();
    }

    @Override
    public IntVector get(Index index) {
        IntVector copy = new IntVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public Integer get(int i) {
        return vector[at(i)];
    }

    @Override
    public int getInt(int i) {
        return vector[at(i)];
    }

    @Override
    public boolean getBoolean(int i) {
        return getInt(i) != 0;
    }

    @Override
    public char getChar(int i) {
        return (char) getInt(i);
    }

    @Override
    public byte getByte(int i) {
        return (byte) getInt(i);
    }

    @Override
    public short getShort(int i) {
        return (short) getInt(i);
    }

    @Override
    public long getLong(int i) {
        return getInt(i);
    }

    @Override
    public float getFloat(int i) {
        return getInt(i);
    }

    @Override
    public double getDouble(int i) {
        return getInt(i);
    }
}