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
        if (field.dtype() != DataTypes.FloatType) {
            throw new IllegalArgumentException("Invalid data type: " + field);
        }
        if (field.measure() instanceof CategoricalMeasure) {
            throw new IllegalArgumentException("Invalid measure: " + field.measure());
        }
        super(field);
        this.vector = vector;
    }

    /**
     * Fills NaN/Inf values with the specified value.
     * @param value the value to replace NAs.
     */
    public void fillna(float value) {
        for (int i = 0; i < vector.length; i++) {
            if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                vector[i] = value;
            }
        }
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public FloatVector withName(String name) {
        return new FloatVector(field.withName(name), vector);
    }

    @Override
    public DoubleStream doubleStream() {
        return index().mapToDouble(i -> vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        if (value instanceof Number n) {
            vector[i] = n.floatValue();
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public FloatVector get(Index index) {
        int n = index.size();
        float[] data = new float[n];
        for (int i = 0; i < n; i++) {
            data[i] = vector[index.apply(i)];
        }
        return new FloatVector(field, data);
    }

    @Override
    public boolean isNullAt(int i) {
        return Float.isNaN(vector[i]);
    }

    @Override
    public Float get(int i) {
        return vector[i];
    }

    @Override
    public float getFloat(int i) {
        return vector[i];
    }

    @Override
    public boolean getBoolean(int i) {
        return MathEx.isZero(vector[i]);
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
    public short getShort(int i) {
        return (short) vector[i];
    }

    @Override
    public int getInt(int i) {
        return (int) vector[i];
    }

    @Override
    public long getLong(int i) {
        return (long) vector[i];
    }

    @Override
    public double getDouble(int i) {
        return vector[i];
    }
}
