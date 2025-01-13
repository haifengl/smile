/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
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

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import smile.data.type.DataTypes;
import smile.data.type.ObjectType;
import smile.data.type.StructField;
import smile.util.Index;

/**
 * A number object vector.
 *
 * @param <T> a subclass of Number.
 *
 * @author Haifeng Li
 */
public class NumberVector<T extends Number> extends ObjectVector<T> {
    /**
     * Constructor.
     * @param field the struct field of vector.
     * @param vector the elements of vector.
     */
    public NumberVector(StructField field, T[] vector) {
        super(field, vector);
        if (!field.dtype().isNumeric()) {
            throw new IllegalArgumentException("Invalid data type: " + field);
        }
    }

    /**
     * Fill null/NaN/Inf values using the specified value.
     * @param value the value to replace NAs.
     */
    @SuppressWarnings("unchecked")
    public void fillna(double value) {
        Number number = switch (dtype().id()) {
            case Byte -> (byte) value;
            case Short -> (short) value;
            case Int -> (int) value;
            case Long -> (long) value;
            case Float -> (float) value;
            case Double -> value;
            default -> throw new UnsupportedOperationException("Unsupported type: " + dtype().id());
        };

        for (int i = 0; i < vector.length; i++) {
            if (vector[i] == null) {
                vector[i] = (T) number;
            } else {
                var x = vector[i].doubleValue();
                if (Double.isNaN(x) || Double.isInfinite(x)) {
                    vector[i] = (T) number;
                }
            }
        }
    }

    @Override
    public NumberVector<T> get(Index index) {
        ObjectType dtype = (ObjectType) field.dtype();
        @SuppressWarnings("unchecked")
        T[] subset = (T[]) java.lang.reflect.Array.newInstance(dtype.getObjectClass(), index.size());
        for (int i = 0; i < subset.length; i++) {
            subset[i] = vector[index.apply(i)];
        }
        return new NumberVector<>(field, subset);
    }

    @Override
    public IntStream asIntStream() {
        return Arrays.stream(vector).filter(Objects::nonNull).mapToInt(Number::intValue);
    }

    @Override
    public LongStream asLongStream() {
        return Arrays.stream(vector).filter(Objects::nonNull).mapToLong(Number::longValue);
    }

    @Override
    public DoubleStream asDoubleStream() {
        return Arrays.stream(vector).filter(Objects::nonNull).mapToDouble(Number::doubleValue);
    }
}
