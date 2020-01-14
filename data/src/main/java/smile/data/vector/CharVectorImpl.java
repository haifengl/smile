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

import smile.data.type.StructField;

import java.util.stream.IntStream;

/**
 * An immutable char vector.
 *
 * @author Haifeng Li
 */
class CharVectorImpl implements CharVector {
    /** The name of vector. */
    private String name;
    /** The vector data. */
    private char[] vector;

    /** Constructor. */
    public CharVectorImpl(String name, char[] vector) {
        this.name = name;
        this.vector = vector;
    }

    /** Constructor. */
    public CharVectorImpl(StructField field, char[] vector) {
        if (field.measure != null) {
            throw new IllegalArgumentException(String.format("Invalid measure %s for %s", field.measure, type()));
        }

        this.name = field.name;
        this.vector = vector;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public char[] array() {
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
    public char getChar(int i) {
        return vector[i];
    }

    @Override
    public Character get(int i) {
        return vector[i];
    }

    @Override
    public CharVector get(int... index) {
        char[] v = new char[index.length];
        for (int i = 0; i < index.length; i++) v[i] = vector[index[i]];
        return new CharVectorImpl(name, v);
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