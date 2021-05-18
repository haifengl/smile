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

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;

/**
 * An immutable char vector.
 *
 * @author Haifeng Li
 */
public interface CharVector extends BaseVector<Character, Integer, IntStream> {
    @Override
    default DataType type() {
        return DataTypes.CharType;
    }

    @Override
    char[] array();

    @Override
    CharVector get(int... index);

    /**
     * Returns the value at position i.
     * @param i the index.
     * @return the value.
     */
    char getChar(int i);

    @Override
    default byte getByte(int i) {
        throw new UnsupportedOperationException("cast char to byte");
    }

    @Override
    default short getShort(int i) {
        throw new UnsupportedOperationException("cast char to byte");
    }

    @Override
    default int getInt(int i) {
        return getChar(i);
    }

    @Override
    default long getLong(int i) {
        return getChar(i);
    }

    @Override
    default float getFloat(int i) {
        return getChar(i);
    }

    @Override
    default double getDouble(int i) {
        return getChar(i);
    }

    /**
     * Returns the string representation of vector.
     * @param n the number of elements to show.
     * @return the string representation of vector.
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).mapToObj(String::valueOf).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named char vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static CharVector of(String name, char[] vector) {
        return new CharVectorImpl(name, vector);
    }

    /** Creates a named char vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static CharVector of(StructField field, char[] vector) {
        return new CharVectorImpl(field, vector);
    }
}