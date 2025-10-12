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

import java.util.Arrays;
import java.util.stream.DoubleStream;
import smile.data.measure.CategoricalMeasure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.MathEx;
import smile.util.Index;

/**
 * A double vector.
 *
 * @author Haifeng Li
 */
public class DoubleVector extends PrimitiveVector {
    /** The vector data. */
    private final double[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     */
    public DoubleVector(String name, double[] vector) {
        this(new StructField(name, DataTypes.DoubleType), vector);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public DoubleVector(StructField field, double[] vector) {
        if (field.dtype() != DataTypes.DoubleType) {
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
    public void fillna(double value) {
        for (int i = 0; i < vector.length; i++) {
            if (Double.isNaN(vector[i]) || Double.isInfinite(vector[i])) {
                vector[i] = value;
            }
        }
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public DoubleVector withName(String name) {
        return new DoubleVector(field.withName(name), vector);
    }

    @Override
    public DoubleStream doubleStream() {
        return Arrays.stream(vector);
    }

    @Override
    public void set(int i, Object value) {
        if (value instanceof Number n) {
            vector[i] = n.doubleValue();
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public DoubleVector get(Index index) {
        var data = index.stream().mapToDouble(i -> vector[i]).toArray();
        return new DoubleVector(field, data);
    }

    @Override
    public boolean isNullAt(int i) {
        return Double.isNaN(vector[i]);
    }

    @Override
    public Double get(int i) {
        return vector[i];
    }

    @Override
    public double getDouble(int i) {
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
    public float getFloat(int i) {
        return (float) vector[i];
    }
}
