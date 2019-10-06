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

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;

/**
 * An immutable short vector.
 *
 * @author Haifeng Li
 */
public interface ShortVector extends BaseVector<Short, Integer, IntStream> {
    @Override
    default DataType type() {
        return DataTypes.ShortType;
    }

    @Override
    short[] array();

    @Override
    default byte getByte(int i) {
        throw new UnsupportedOperationException("cast short to byte");
    }

    @Override
    default int getInt(int i) {
        return getShort(i);
    }

    @Override
    default long getLong(int i) {
        return getShort(i);
    }

    @Override
    default float getFloat(int i) {
        return getShort(i);
    }

    @Override
    default double getDouble(int i) {
        return getShort(i);
    }

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).mapToObj(i -> measure().map(m -> m.toString(i)).orElseGet(() -> String.valueOf(i))).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named short integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static ShortVector of(String name, short[] vector) {
        return new ShortVectorImpl(name, vector);
    }

    /** Creates a named short integer vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     */
    static ShortVector of(StructField field, short[] vector) {
        return new ShortVectorImpl(field, vector);
    }
}