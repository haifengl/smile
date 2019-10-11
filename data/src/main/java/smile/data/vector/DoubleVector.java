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
import java.util.stream.DoubleStream;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;

/**
 * An immutable double vector.
 *
 * @author Haifeng Li
 */
public interface DoubleVector extends BaseVector<Double, Double, DoubleStream> {
    @Override
    default DataType type() {
        return DataTypes.DoubleType;
    }

    @Override
    double[] array();

    @Override
    default byte getByte(int i) {
        throw new UnsupportedOperationException("cast double to byte");
    }

    @Override
    default short getShort(int i) {
        throw new UnsupportedOperationException("cast double to short");
    }

    @Override
    default int getInt(int i) {
        throw new UnsupportedOperationException("cast double to int");
    }

    @Override
    default long getLong(int i) {
        throw new UnsupportedOperationException("cast double to long");
    }

    @Override
    default float getFloat(int i) {
        throw new UnsupportedOperationException("cast double to float");
    }

    /**
     * Returns the string representation of vector.
     * @param n Number of elements to show
     */
    default String toString(int n) {
        String suffix = n >= size() ? "]" : String.format(", ... %,d more]", size() - n);
        return stream().limit(n).mapToObj(i -> measure().map(m -> m.toString(i)).orElseGet(() -> String.valueOf(i))).collect(Collectors.joining(", ", "[", suffix));
    }

    /** Creates a named double vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     */
    static DoubleVector of(String name, double[] vector) {
        return new DoubleVectorImpl(name, vector);
    }

    /** Creates a named double vector.
     *
     * @param name the name of vector.
     * @param stream the data stream of vector.
     */
    static DoubleVector of(String name, DoubleStream stream) {
        return new DoubleVectorImpl(name, stream.toArray());
    }

    /** Creates a named double vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     */
    static DoubleVector of(StructField field, double[] vector) {
        return new DoubleVectorImpl(field, vector);
    }

    /** Creates a named double vector.
     *
     * @param field the struct field of vector.
     * @param stream the data stream of vector.
     */
    static DoubleVector of(StructField field, DoubleStream stream) {
        return new DoubleVectorImpl(field, stream.toArray());
    }
}