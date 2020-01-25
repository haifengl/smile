/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.data.vector;

import smile.data.measure.DiscreteMeasure;
import smile.data.measure.Measure;
import smile.data.type.StructField;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.DoubleStream;

/**
 * An immutable double vector.
 *
 * @author Haifeng Li
 */
class DoubleVectorImpl implements DoubleVector {
    /** The name of vector. */
    private String name;
    /** Optional measure. */
    private Measure measure;
    /** The vector data. */
    private double[] vector;

    /** Constructor. */
    public DoubleVectorImpl(String name, double[] vector) {
        this.name = name;
        this.measure = null;
        this.vector = vector;
    }

    /** Constructor. */
    public DoubleVectorImpl(StructField field, double[] vector) {
        if (field.measure instanceof DiscreteMeasure) {
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
    public Optional<Measure> measure() {
        return Optional.ofNullable(measure);
    }

    @Override
    public double[] array() {
        return vector;
    }

    @Override
    public double[] toDoubleArray() {
        return vector;
    }

    @Override
    public double[] toDoubleArray(double[] a) {
        System.arraycopy(vector, 0, a, 0, vector.length);
        return a;
    }

    @Override
    public double getDouble(int i) {
        return vector[i];
    }

    @Override
    public Double get(int i) {
        return vector[i];
    }

    @Override
    public DoubleVector get(int... index) {
        double[] v = new double[index.length];
        for (int i = 0; i < index.length; i++) v[i] = vector[index[i]];
        return new DoubleVectorImpl(name, v);
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public DoubleStream stream() {
        return Arrays.stream(vector);
    }

    @Override
    public String toString() {
        return toString(10);
    }
}