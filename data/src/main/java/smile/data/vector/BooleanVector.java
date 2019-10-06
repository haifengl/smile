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

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import smile.data.measure.Measure;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;

/**
 * An immutable boolean vector.
 *
 * @author Haifeng Li
 */
public interface BooleanVector extends BaseVector<Boolean, Integer, IntStream> {
    @Override
    default DataType type() {
        return DataTypes.BooleanType;
    }

    @Override
    default Optional<Measure> measure() {
        return Optional.empty();
    }

    @Override
    boolean[] array();

    /**
     * Returns the value at position i.
     */
    boolean getBoolean(int i);

    @Override
    default byte getByte(int i) {
        return getBoolean(i) ? (byte) 1 : 0;
    }

    @Override
    default short getShort(int i) {
        return getBoolean(i)  ? (short) 1 : 0;
    }

    @Override
    default int getInt(int i) {
        return getBoolean(i)  ? 1 : 0;
    }

    @Override
    default long getLong(int i) {
        return getBoolean(i)  ? 1 : 0;
    }

    @Override
    default float getFloat(int i) {
        return getBoolean(i)  ? 1 : 0;
    }

    @Override
    default double getDouble(int i) {
        return getBoolean(i)  ? 1 : 0;
    }

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).mapToObj(i -> measure().map(m -> m.toString(i)).orElseGet(() -> String.valueOf(i))).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named boolean vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static BooleanVector of(String name, boolean[] vector) {
        return new BooleanVectorImpl(name, vector);
    }

    /** Creates a named boolean vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     */
    static BooleanVector of(StructField field, boolean[] vector) {
        return new BooleanVectorImpl(field, vector);
    }
}