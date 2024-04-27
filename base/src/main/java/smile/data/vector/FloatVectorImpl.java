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

import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import smile.data.measure.CategoricalMeasure;
import smile.data.measure.Measure;
import smile.data.type.StructField;

/**
 * An immutable float vector.
 *
 * @author Haifeng Li
 */
class FloatVectorImpl implements FloatVector {
    /** The name of vector. */
    private final String name;
    /** Optional measure. */
    private final Measure measure;
    /** The vector data. */
    private final float[] vector;

    /** Constructor. */
    public FloatVectorImpl(String name, float[] vector) {
        this.name = name;
        this.measure = null;
        this.vector = vector;
    }

    /** Constructor. */
    public FloatVectorImpl(StructField field, float[] vector) {
        if (field.measure instanceof CategoricalMeasure) {
            throw new IllegalArgumentException(String.format("Invalid measure %s for %s", field.measure, type()));
        }

        this.name = field.name;
        this.measure = field.measure;
        this.vector = vector;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Measure measure() {
        return measure;
    }

    @Override
    public float[] array() {
        return vector;
    }

    @Override
    public double[] toDoubleArray(double[] a) {
        for (int i = 0; i < a.length; i++) a[i] = vector[i];
        return a;
    }

    @Override
    public float getFloat(int i) {
        return vector[i];
    }

    @Override
    public Float get(int i) {
        return vector[i];
    }

    @Override
    public FloatVector get(int... index) {
        float[] v = new float[index.length];
        for (int i = 0; i < index.length; i++) v[i] = vector[index[i]];
        return new FloatVectorImpl(field(), v);
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public DoubleStream stream() {
        return IntStream.range(0, vector.length).mapToDouble(i -> vector[i]);
    }

    @Override
    public String toString() {
        return toString(10);
    }

    @Override
    public void fillna(float value) {
        for (int i = 0; i < vector.length; i++) {
            if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                vector[i] = value;
            }
        }
    }
}