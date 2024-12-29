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

import java.util.stream.DoubleStream;
import smile.data.measure.CategoricalMeasure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.MathEx;
import smile.util.Index;

/**
 * A float vector.
 *
 * @author Haifeng Li
 */
public class FloatVector extends PrimitiveVector {
    /** The vector data. */
    private final float[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public FloatVector(String name, float[] vector) {
        this(new StructField(name, DataTypes.FloatType), vector);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public FloatVector(StructField field, float[] vector) {
        super(checkMeasure(field, CategoricalMeasure.class));
        this.vector = vector;
    }

    /**
     * Fills NaN/Inf values using the specified value.
     * @param value the value to replace NAs.
     */
    public void fillna(float value) {
        if (index == null) {
            for (int i = 0; i < vector.length; i++) {
                if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                    vector[i] = value;
                }
            }
        } else {
            indexStream().filter(i -> Float.isNaN(vector[at(i)]))
                    .forEach(i -> vector[at(i)] = value);
        }
    }

    @Override
    int length() {
        return vector.length;
    }

    @Override
    public DoubleStream asDoubleStream() {
        if (nullMask == null) {
            return indexStream().mapToDouble(i -> vector[i]);
        } else {
            return indexStream().filter(i -> !nullMask.get(i)).mapToDouble(i -> vector[i]);
        }
    }

    @Override
    public void set(int i, Object value) {
        vector[at(i)] = ((Number) value).floatValue();
    }

    @Override
    public FloatVector get(Index index) {
        FloatVector copy = new FloatVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public Float get(int i) {
        int index = at(i);
        if (nullMask == null) {
            return vector[index];
        } else {
            return nullMask.get(index) ? null : vector[index];
        }
    }

    @Override
    public float getFloat(int i) {
        return vector[at(i)];
    }

    @Override
    public boolean getBoolean(int i) {
        return MathEx.isZero(getFloat(i));
    }

    @Override
    public char getChar(int i) {
        return (char) getFloat(i);
    }

    @Override
    public byte getByte(int i) {
        return (byte) getFloat(i);
    }

    @Override
    public short getShort(int i) {
        return (short) getFloat(i);
    }

    @Override
    public int getInt(int i) {
        return (int) getFloat(i);
    }

    @Override
    public long getLong(int i) {
        return (long) getFloat(i);
    }

    @Override
    public double getDouble(int i) {
        return getFloat(i);
    }
}