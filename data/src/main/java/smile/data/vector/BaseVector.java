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

import java.io.Serializable;
import java.util.stream.BaseStream;

import smile.data.measure.Measure;
import smile.data.type.DataType;

/**
 * Base interface for immutable named vectors, which are sequences of elements supporting
 * random access and sequential stream operations.
 *
 * @param <T>  the type of vector elements.
 * @param <TS> the type of stream elements.
 * @param <S>  the type of stream.
 *
 * @author Haifeng Li
 */
public interface BaseVector<T, TS, S extends BaseStream<TS, S>> extends Serializable {
    /** Return the (optional) name associated with vector. */
    String name();

    /** Returns the element type. */
    DataType type();

    /** Returns the (optional) level of measurements. Only valid for number types. */
    default Measure measure() {
        return null;
    }

    /** Number of elements in the vector. */
    int size();

    /**
     * Returns the array that backs this vector.
     * This is mostly for smile internal use for high performance.
     * The application developers should not use this method.
     */
    Object array();

    /**
     * Returns a double array of this vector.
     */
    default double[] toDoubleArray() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns an integer array of this vector.
     */
    default int[] toIntArray() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the value at position i, which may be null.
     */
    T get(int i);

    /**
     * Returns the byte value at position i.
     */
    byte getByte(int i);

    /**
     * Returns the short value at position i.
     */
    short getShort(int i);

    /**
     * Returns the integer value at position i.
     */
    int getInt(int i);

    /**
     * Returns the long value at position i.
     */
    long getLong(int i);

    /**
     * Returns the float value at position i.
     */
    float getFloat(int i);

    /**
     * Returns the double value at position i.
     */
    double getDouble(int i);

    /**
     * Returns the value at position i, which may be null.
     */
    default T apply(int i) {
        return get(i);
    }

    /**
     * Returns a (possibly parallel) Stream with this vector as its source.
     *
     * @return a (possibly parallel) Stream with this vector as its source.
     */
    S stream();
}