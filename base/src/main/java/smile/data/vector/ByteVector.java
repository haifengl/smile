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

import java.util.stream.IntStream;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A byte vector.
 *
 * @author Haifeng Li
 */
public class ByteVector extends PrimitiveVector {
    /** The vector data. */
    private final byte[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public ByteVector(String name, byte[] vector) {
        this(new StructField(name, DataTypes.ByteType), vector);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public ByteVector(StructField field, byte[] vector) {
        if (field.dtype() != DataTypes.ByteType) {
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
    public ByteVector withName(String name) {
        return new ByteVector(field.withName(name), vector);
    }

    @Override
    public IntStream intStream() {
        return index().map(i -> vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        if (value instanceof Number n) {
            vector[i] = n.byteValue();
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public ByteVector get(Index index) {
        int n = index.size();
        byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            data[i] = vector[index.apply(i)];
        }
        return new ByteVector(field, data);
    }

    @Override
    public Byte get(int i) {
        return vector[i];
    }

    @Override
    public byte getByte(int i) {
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
    public short getShort(int i) {
        return vector[i];
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
