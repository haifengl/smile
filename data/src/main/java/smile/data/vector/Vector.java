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

import java.util.stream.Collectors;
import java.util.stream.Stream;
import smile.data.type.DataType;

/**
 * An immutable generic vector.
 *
 * @author Haifeng Li
 */
public interface Vector<T> extends BaseVector<T, T, Stream<T>> {

    @Override
    default byte getByte(int i) {
        return ((Number) get(i)).byteValue();
    }

    @Override
    default short getShort(int i) {
        return ((Number) get(i)).shortValue();
    }

    @Override
    default int getInt(int i) {
        return ((Number) get(i)).intValue();
    }

    @Override
    default long getLong(int i) {
        return ((Number) get(i)).longValue();
    }

    @Override
    default float getFloat(int i) {
        Number x = (Number) get(i);
        return x == null ? Float.NaN : x.floatValue();
    }

    @Override
    default double getDouble(int i) {
        Number x = (Number) get(i);
        return x == null ? Double.NaN : x.doubleValue();
    }

    /** Checks whether the value at position i is null. */
    default boolean isNullAt(int i) {
        return get(i) == null;
    }

    /** Returns true if there are any NULL values in this row. */
    default boolean anyNull() {
        for (int i = 0; i < size(); i++) {
            if (isNullAt(i)) return true;
        }
        return false;
    }

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).map(Object::toString).collect(Collectors.joining(", ", "[", suffix));
    }

    /**
     * Creates a named vector.
     *
     * @param name the name of vector.
     * @param clazz the class of data type.
     * @param vector the data of vector.
     */
    static <T> Vector<T> of(String name, Class clazz, T[] vector) {
        return new VectorImpl<>(name, clazz, vector);
    }

    /**
     * Creates a named vector.
     *
     * @param name the name of vector.
     * @param type the data type of vector.
     * @param vector the data of vector.
     */
    static <T> Vector<T> of(String name, DataType type, T[] vector) {
        return new VectorImpl<>(name, type, vector);
    }
}