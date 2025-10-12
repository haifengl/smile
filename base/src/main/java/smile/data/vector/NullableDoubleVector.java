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

import java.util.BitSet;
import java.util.stream.DoubleStream;
import smile.data.measure.CategoricalMeasure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.MathEx;
import smile.util.Index;

/**
 * A nullable double vector.
 *
 * @author Haifeng Li
 */
public class NullableDoubleVector extends NullablePrimitiveVector {
    /** The vector data. */
    private final double[] vector;

    /**
     * Constructor.
     * @param name the name of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableDoubleVector(String name, double[] vector, BitSet nullMask) {
        this(new StructField(name, DataTypes.NullableDoubleType), vector, nullMask);
    }

    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     * @param nullMask The null bitmap. The bit is 1 if the value is null.
     */
    public NullableDoubleVector(StructField field, double[] vector, BitSet nullMask) {
        if (field.dtype() != DataTypes.NullableDoubleType) {
            throw new IllegalArgumentException("Invalid data type: " + field);
        }
        if (field.measure() instanceof CategoricalMeasure) {
            throw new IllegalArgumentException("Invalid measure: " + field.measure());
        }
        super(field, nullMask);
        this.vector = vector;
    }

    /**
     * Fills NaN/Inf values using the specified value.
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
    public NullableDoubleVector withName(String name) {
        return new NullableDoubleVector(field.withName(name), vector, nullMask);
    }

    @Override
    public DoubleStream doubleStream() {
        return index().mapToDouble(i -> nullMask.get(i) ? Double.NaN : vector[i]);
    }

    @Override
    public void set(int i, Object value) {
        if (value == null) {
            nullMask.set(i);
        } else if (value instanceof Number n) {
            vector[i] = n.doubleValue();
        } else {
            throw new IllegalArgumentException("Invalid value type: " + value.getClass());
        }
    }

    @Override
    public NullableDoubleVector get(Index index) {
        int n = index.size();
        double[] data = new double[n];
        BitSet mask = new BitSet(n);
        for (int i = 0; i < n; i++) {
            int idx = index.apply(i);
            data[i] = vector[idx];
            mask.set(i, nullMask.get(idx));
        }
        return new NullableDoubleVector(field, data, mask);
    }

    @Override
    public Double get(int i) {
        return nullMask.get(i) ? null : vector[i];
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
