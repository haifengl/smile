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
 * An immutable char vector.
 *
 * @author Haifeng Li
 */
public class CharVector extends PrimitiveVector {
    /** The vector data. */
    private final char[] vector;

    /** Constructor. */
    public CharVector(String name, char[] vector) {
        this(new StructField(name, DataTypes.CharType), vector);
    }

    /** Constructor. */
    public CharVector(StructField field, char[] vector) {
        super(checkMeasure(field, NumericalMeasure.class));
        this.vector = vector;
    }

    @Override
    int length() {
        return vector.length;
    }

    @Override
    public char[] array() {
        return vector;
    }

    @Override
    public IntStream asIntStream() {
        return indexStream().map(i -> vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        vector[at(i)] = (Character) value;
    }

    @Override
    public CharVector get(Index index) {
        CharVector copy = new CharVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public Character get(int i) {
        return vector[at(i)];
    }

    @Override
    public char getChar(int i) {
        return vector[at(i)];
    }

    @Override
    public boolean getBoolean(int i) {
        return getChar(i) == 'T';
    }

    @Override
    public byte getByte(int i) {
        return (byte) getChar(i);
    }

    @Override
    public short getShort(int i) {
        return (short) getChar(i);
    }

    @Override
    public int getInt(int i) {
        return getChar(i);
    }

    @Override
    public long getLong(int i) {
        return getChar(i);
    }

    @Override
    public float getFloat(int i) {
        return getChar(i);
    }

    @Override
    public double getDouble(int i) {
        return getChar(i);
    }
}