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
import java.util.stream.LongStream;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;

/**
 * An immutable long vector.
 *
 * @author Haifeng Li
 */
public interface LongVector extends BaseVector<Long, Long, LongStream> {
    @Override
    default DataType type() {
        return DataTypes.LongType;
    }

    @Override
    long[] array();

    @Override
    default byte getByte(int i) {
        throw new UnsupportedOperationException("cast long to byte");
    }

    @Override
    default short getShort(int i) {
        throw new UnsupportedOperationException("cast long to short");
    }

    @Override
    default int getInt(int i) {
        throw new UnsupportedOperationException("cast long to int");
    }

    @Override
    default float getFloat(int i) {
        return getLong(i);
    }

    @Override
    default double getDouble(int i) {
        return getLong(i);
    }

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).mapToObj(i -> measure().map(m -> m.toString(i)).orElseGet(() -> String.valueOf(i))).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named long vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static LongVector of(String name, long[] vector) {
        return new LongVectorImpl(name, vector);
    }

    /** Creates a named long integer vector.
     *
     * @param name the name of vector.
     * @param stream the data stream of vector.
     */
    static LongVector of(String name, LongStream stream) {
        return new LongVectorImpl(name, stream.toArray());
    }

    /** Creates a named long integer vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     */
    static LongVector of(StructField field, long[] vector) {
        return new LongVectorImpl(field, vector);
    }

    /** Creates a named long integer vector.
     *
     * @param field the struct field of vector.
     * @param stream the data stream of vector.
     */
    static LongVector of(StructField field, LongStream stream) {
        return new LongVectorImpl(field, stream.toArray());
    }
}