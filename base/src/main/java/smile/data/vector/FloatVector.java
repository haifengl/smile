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
 * An immutable float vector.
 *
 * @author Haifeng Li
 */
public interface FloatVector extends BaseVector<Float, Double, DoubleStream> {
    @Override
    default DataType type() {
        return DataTypes.FloatType;
    }

    @Override
    float[] array();

    @Override
    FloatVector get(int... index);

    @Override
    default boolean getBoolean(int i) {
        return MathEx.isZero(getFloat(i));
    }

    @Override
    default char getChar(int i) {
        return (char) getFloat(i);
    }

    @Override
    default byte getByte(int i) {
        return (byte) getFloat(i);
    }

    @Override
    default short getShort(int i) {
        return (short) getFloat(i);
    }

    @Override
    default int getInt(int i) {
        return (int) getFloat(i);
    }

    @Override
    default long getLong(int i) {
        return (long) getFloat(i);
    }

    @Override
    default double getDouble(int i) {
        return getFloat(i);
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
    void fillna(float value);

    /** Creates a named float vector.
     *
     * @param name the name of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static FloatVector of(String name, float[] vector) {
        return new FloatVectorImpl(name, vector);
    }

    /** Creates a named float vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static FloatVector of(StructField field, float[] vector) {
        return new FloatVectorImpl(field, vector);
    }
}