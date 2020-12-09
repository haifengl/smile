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

import java.util.stream.IntStream;
import smile.data.measure.NumericalMeasure;
import smile.data.measure.Measure;
import smile.data.type.StructField;

/**
 * An immutable byte vector.
 *
 * @author Haifeng Li
 */
class ByteVectorImpl implements ByteVector {
    /** The name of vector. */
    private final String name;
    /** Optional measure. */
    private final Measure measure;
    /** The vector data. */
    private final byte[] vector;

    /** Constructor. */
    public ByteVectorImpl(String name, byte[] vector) {
        this.name = name;
        this.measure = null;
        this.vector = vector;
    }

    /** Constructor. */
    public ByteVectorImpl(StructField field, byte[] vector) {
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
    public byte[] array() {
        return vector;
    }

    @Override
    public int[] toIntArray(int[] a) {
        for (int i = 0; i < a.length; i++) a[i] = vector[i];
        return a;
    }

    @Override
    public double[] toDoubleArray(double[] a) {
        for (int i = 0; i < a.length; i++) a[i] = vector[i];
        return a;
    }

    @Override
    public byte getByte(int i) {
        return vector[i];
    }

    @Override
    public Byte get(int i) {
        return vector[i];
    }

    @Override
    public ByteVector get(int... index) {
        byte[] v = new byte[index.length];
        for (int i = 0; i < index.length; i++) v[i] = vector[index[i]];
        return new ByteVectorImpl(field(), v);
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public IntStream stream() {
        return IntStream.range(0, vector.length).map(i -> vector[i]);
    }

    @Override
    public String toString() {
        return toString(10);
    }
}