/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data.vector;

import java.util.Arrays;
import java.util.stream.IntStream;
import smile.data.measure.NumericalMeasure;
import smile.data.measure.Measure;
import smile.data.type.StructField;

/**
 * An immutable integer vector.
 *
 * @author Haifeng Li
 */
class IntVectorImpl implements IntVector {
    /** The name of vector. */
    private final String name;
    /** Optional measure. */
    private final Measure measure;
    /** The vector data. */
    private final int[] vector;

    /** Constructor. */
    public IntVectorImpl(String name, int[] vector) {
        this.name = name;
        this.measure = null;
        this.vector = vector;
    }

    /** Constructor. */
    public IntVectorImpl(StructField field, int[] vector) {
        if (field.measure instanceof NumericalMeasure) {
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
    public int[] array() {
        return vector;
    }

    @Override
    public int[] toIntArray() {
        return vector;
    }

    @Override
    public int[] toIntArray(int[] a) {
        System.arraycopy(vector, 0, a, 0, vector.length);
        return vector;
    }

    @Override
    public double[] toDoubleArray(double[] a) {
        for (int i = 0; i < a.length; i++) a[i] = vector[i];
        return a;
    }

    @Override
    public int getInt(int i) {
        return vector[i];
    }

    @Override
    public Integer get(int i) {
        return vector[i];
    }

    @Override
    public IntVector get(int... index) {
        int[] v = new int[index.length];
        for (int i = 0; i < index.length; i++) v[i] = vector[index[i]];
        return new IntVectorImpl(field(), v);
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public IntStream stream() {
        return Arrays.stream(vector);
    }

    @Override
    public String toString() {
        return toString(10);
    }
}