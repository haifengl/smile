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

import java.util.stream.IntStream;

/**
 * An immutable short vector.
 *
 * @author Haifeng Li
 */
class ShortVectorImpl implements ShortVector {
    /** The name of vector. */
    private String name;
    /** The vector data. */
    private short[] vector;

    /** Constructor. */
    public ShortVectorImpl(String name, short[] vector) {
        this.name = name;
        this.vector = vector;
    }

    @Override
    public short[] array() {
        return vector;
    }

    @Override
    public int[] toIntArray() {
        int[] a = new int[vector.length];
        for (int i = 0; i < a.length; i++) a[i] = vector[i];
        return a;
    }

    @Override
    public double[] toDoubleArray() {
        double[] a = new double[vector.length];
        for (int i = 0; i < a.length; i++) a[i] = vector[i];
        return a;
    }

    @Override
    public short getShort(int i) {
        return vector[i];
    }

    @Override
    public Short get(int i) {
        return vector[i];
    }

    @Override
    public String name() {
        return name;
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