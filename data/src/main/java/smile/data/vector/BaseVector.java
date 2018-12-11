/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.data.vector;

import java.io.Serializable;
import java.util.stream.BaseStream;

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

    /** Number of elements in the vector. */
    int size();

    /**
     * Returns the array that backs this vector.
     * This is mostly for smile internal use for high performance.
     * The application developers should not use this method.
     */
    Object array();

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