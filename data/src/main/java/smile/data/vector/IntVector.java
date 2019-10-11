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
        return stream().limit(n).mapToObj(i -> measure().map(m -> m.toString(i)).orElseGet(() -> String.valueOf(i))).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named integer vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static IntVector of(String name, int[] vector) {
        return new IntVectorImpl(name, vector);
    }

    /** Creates a named integer vector.
     *
     * @param name the name of vector.
     * @param stream the data stream of vector.
     */
    static IntVector of(String name, IntStream stream) {
        return new IntVectorImpl(name, stream.toArray());
    }

    /** Creates a named integer vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     */
    static IntVector of(StructField field, int[] vector) {
        return new IntVectorImpl(field, vector);
    }

    /** Creates a named integer vector.
     *
     * @param field the struct field of vector.
     * @param stream the data stream of vector.
     */
    static IntVector of(StructField field, IntStream stream) {
        return new IntVectorImpl(field, stream.toArray());
    }
}