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

import java.util.Arrays;
import java.util.stream.DoubleStream;
import smile.data.measure.CategoricalMeasure;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.MathEx;
import smile.util.Index;

/**
 * An immutable double vector.
 *
 * @author Haifeng Li
 */
public class DoubleVector extends PrimitiveVector {
    /** The vector data. */
    private final double[] vector;

    /** Constructor. */
    public DoubleVector(String name, double[] vector) {
        this(new StructField(name, DataTypes.DoubleType), vector);
    }

    /** Constructor. */
    public DoubleVector(StructField field, double[] vector) {
        super(checkMeasure(field, CategoricalMeasure.class));
        this.vector = vector;
    }

    @Override
    int length() {
        return vector.length;
    }

    @Override
    public double[] array() {
        return vector;
    }

    @Override
    public double getDouble(int i) {
        return vector[at(i)];
    }

    @Override
    public Double get(int i) {
        return vector[at(i)];
    }

    @Override
    public DoubleVector get(Index index) {
        DoubleVector copy = new DoubleVector(field, vector);
        return slice(copy, index);
    }

    @Override
    public DoubleStream asDoubleStream() {
        if (index == null) {
            return Arrays.stream(vector);
        } else {
            return index.stream().mapToDouble(i -> vector[i]);
        }
    }

    /**
     * Fills NaN/Inf values using the specified value.
     * @param value the value to replace NAs.
     */
    public void fillna(double value) {
        if (index == null) {
            for (int i = 0; i < vector.length; i++) {
                if (Double.isNaN(vector[i]) || Double.isInfinite(vector[i])) {
                    vector[i] = value;
                }
            }
        } else {
            indexStream().filter(i -> Double.isNaN(vector[at(i)]))
                    .forEach(i -> vector[at(i)] = value);
        }
    }

    @Override
    public boolean getBoolean(int i) {
        return MathEx.isZero(getDouble(i));
    }

    @Override
    public char getChar(int i) {
        return (char) getDouble(i);
    }

    @Override
    public byte getByte(int i) {
        return (byte) getDouble(i);
    }

    @Override
    public short getShort(int i) {
        return (short) getDouble(i);
    }

    @Override
    public int getInt(int i) {
        return (int) getDouble(i);
    }

    @Override
    public long getLong(int i) {
        return (long) getDouble(i);
    }

    @Override
    public float getFloat(int i) {
        return (float) getDouble(i);
    }
}