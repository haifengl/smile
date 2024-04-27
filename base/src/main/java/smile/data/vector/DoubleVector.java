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
import java.util.stream.DoubleStream;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.MathEx;

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
    DoubleVector get(int... index);

    @Override
    default boolean getBoolean(int i) {
        return MathEx.isZero(getDouble(i));
    }

    @Override
    default char getChar(int i) {
        return (char) getDouble(i);
    }

    @Override
    default byte getByte(int i) {
        return (byte) getDouble(i);
    }

    @Override
    default short getShort(int i) {
        return (short) getDouble(i);
    }

    @Override
    default int getInt(int i) {
        return (int) getDouble(i);
    }

    @Override
    default long getLong(int i) {
        return (long) getDouble(i);
    }

    @Override
    default float getFloat(int i) {
        return (float) getDouble(i);
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

    /**
     * Fills NaN/Inf values using the specified value.
     * @param value the value to replace NAs.
     */
    void fillna(double value);

    /** Creates a named double vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static DoubleVector of(String name, double[] vector) {
        return new DoubleVectorImpl(name, vector);
    }

    /** Creates a named double vector.
     *
     * @param name the name of vector.
     * @param stream the data stream of vector.
     * @return the vector.
     */
    static DoubleVector of(String name, DoubleStream stream) {
        return new DoubleVectorImpl(name, stream.toArray());
    }

    /** Creates a named double vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static DoubleVector of(StructField field, double[] vector) {
        return new DoubleVectorImpl(field, vector);
    }

    /** Creates a named double vector.
     *
     * @param field the struct field of vector.
     * @param stream the data stream of vector.
     * @return the vector.
     */
    static DoubleVector of(StructField field, DoubleStream stream) {
        return new DoubleVectorImpl(field, stream.toArray());
    }
}