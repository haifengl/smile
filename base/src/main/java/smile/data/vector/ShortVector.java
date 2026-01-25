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

import java.util.stream.IntStream;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A short vector.
 *
 * @author Haifeng Li
 */
public class ShortVector extends PrimitiveVector {
    /** The vector data. */
    private final short[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public ShortVector(String name, short[] vector) {
        this(new StructField(name, DataTypes.ShortType), vector);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public ShortVector(StructField field, short[] vector) {
        if (field.dtype() != DataTypes.ShortType) {
            throw new IllegalArgumentException("Invalid data type: " + field);
        }
        super(field);
        this.vector = vector;
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public ShortVector withName(String name) {
        return new ShortVector(field.withName(name), vector);
    }

    @Override
    public IntStream intStream() {
        return index().map(i -> vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        if (value instanceof Number n) {
            vector[i] = n.shortValue();
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public ShortVector get(Index index) {
        int n = index.size();
        short[] data = new short[n];
        for (int i = 0; i < n; i++) {
            data[i] = vector[index.apply(i)];
        }
        return new ShortVector(field, data);
    }

    @Override
    public Short get(int i) {
        return vector[i];
    }

    @Override
    public short getShort(int i) {
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
    public int getInt(int i) {
        return vector[i];
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
