/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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

import java.util.stream.IntStream;
import smile.data.measure.NumericalMeasure;
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

    /** Constructor. */
    public ByteVector(String name, byte[] vector) {
        this(new StructField(name, DataTypes.ByteType), vector);
    }

    /** Constructor. */
    public ByteVector(StructField field, byte[] vector) {
        super(checkMeasure(field, NumericalMeasure.class));
        this.vector = vector;
    }

    @Override
    int length() {
        return vector.length;
    }

    @Override
    public byte[] array() {
        return vector;
    }

    @Override
    public IntStream asIntStream() {
        return indexStream().map(i -> vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        vector[at(i)] = ((Number) value).byteValue();
    }

    @Override
    public ByteVector get(Index index) {
        ByteVector copy = new ByteVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public Byte get(int i) {
        return vector[at(i)];
    }

    @Override
    public byte getByte(int i) {
        return vector[at(i)];
    }

    @Override
    public boolean getBoolean(int i) {
        return getByte(i) != 0;
    }

    @Override
    public char getChar(int i) {
        return (char) getByte(i);
    }

    @Override
    public short getShort(int i) {
        return getByte(i);
    }

    @Override
    public int getInt(int i) {
        return getByte(i);
    }

    @Override
    public long getLong(int i) {
        return getByte(i);
    }

    @Override
    public float getFloat(int i) {
        return getByte(i);
    }

    @Override
    public double getDouble(int i) {
        return getByte(i);
    }
}