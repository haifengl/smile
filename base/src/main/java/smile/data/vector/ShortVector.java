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
 * An immutable short vector.
 *
 * @author Haifeng Li
 */
public class ShortVector extends PrimitiveVector {
    /** The vector data. */
    private final short[] vector;

    /** Constructor. */
    public ShortVector(String name, short[] vector) {
        this(new StructField(name, DataTypes.ShortType), vector);
    }

    /** Constructor. */
    public ShortVector(StructField field, short[] vector) {
        super(checkMeasure(field, NumericalMeasure.class));
        this.vector = vector;
    }

    @Override
    int length() {
        return vector.length;
    }

    @Override
    public short[] array() {
        return vector;
    }

    @Override
    public IntStream asIntStream() {
        return indexStream().map(i -> vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        vector[at(i)] = ((Number) value).shortValue();
    }

    @Override
    public ShortVector get(Index index) {
        ShortVector copy = new ShortVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public Short get(int i) {
        return vector[at(i)];
    }

    @Override
    public short getShort(int i) {
        return vector[at(i)];
    }

    @Override
    public boolean getBoolean(int i) {
        return getShort(i) != 0;
    }

    @Override
    public char getChar(int i) {
        return (char) getShort(i);
    }

    @Override
    public byte getByte(int i) {
        return (byte) getShort(i);
    }

    @Override
    public int getInt(int i) {
        return getShort(i);
    }

    @Override
    public long getLong(int i) {
        return getShort(i);
    }

    @Override
    public float getFloat(int i) {
        return getShort(i);
    }

    @Override
    public double getDouble(int i) {
        return getShort(i);
    }
}