/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data.vector;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;

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

    @Override
    IntVector get(int... index);

    @Override
    default boolean getBoolean(int i) {
        return getInt(i) != 0;
    }

    @Override
    default char getChar(int i) {
        return (char) getInt(i);
    }

    @Override
    default byte getByte(int i) {
        return (byte) getInt(i);
    }

    @Override
    default short getShort(int i) {
        return (short) getInt(i);
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
     * @param n the number of elements to show.
     * @return the string representation of vector.
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).mapToObj(field()::toString).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static IntVector of(String name, int[] vector) {
        return new IntVectorImpl(name, vector);
    }

    /** Creates a named integer vector.
     *
     * @param name the name of vector.
     * @param stream the data stream of vector.
     * @return the vector.
     */
    static IntVector of(String name, IntStream stream) {
        return new IntVectorImpl(name, stream.toArray());
    }

    /** Creates a named integer vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static IntVector of(StructField field, int[] vector) {
        return new IntVectorImpl(field, vector);
    }

    /** Creates a named integer vector.
     *
     * @param field the struct field of vector.
     * @param stream the data stream of vector.
     * @return the vector.
     */
    static IntVector of(StructField field, IntStream stream) {
        return new IntVectorImpl(field, stream.toArray());
    }
}