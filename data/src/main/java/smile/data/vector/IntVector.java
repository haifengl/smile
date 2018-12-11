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
import java.util.stream.IntStream;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.DiscreteMeasure;

/**
 * An immutable integer vector.
 *
 * @author Haifeng Li
 */
public interface IntVector extends BaseVector<Integer, Integer, IntStream> {
    @Override
    default DataType type() {
        return DataTypes.IntegerType;
    }

    @Override
    int[] array();

    /** Returns the scale of measure. Returns null if unknown. */
    DiscreteMeasure getScale();

    /** Sets the (optional) scale of measure. */
    void setScale(DiscreteMeasure scale);

    @Override
    default byte getByte(int i) {
        throw new UnsupportedOperationException("cast int to byte");
    }

    @Override
    default short getShort(int i) {
        throw new UnsupportedOperationException("cast int to short");
    }

    @Override
    default long getLong(int i) {
        return getInt(i);
    }

    @Override
    default float getFloat(int i) {
        return getInt(i);
    }

    @Override
    default double getDouble(int i) {
        return getInt(i);
    }

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).mapToObj(String::valueOf).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static IntVector of(String name, int[] vector) {
        return new IntVectorImpl(name, vector);
    }
}