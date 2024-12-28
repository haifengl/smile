/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
    /** Constructor. */
    public NumberVector(String name, T[] vector) {
        super(name, vector);
    }

    /** Constructor. */
    public NumberVector(StructField field, T[] vector) {
        super(field, vector);
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
            case Integer -> (int) value;
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
        NumberVector<T> copy = new NumberVector<>(field, vector);
        return slice(copy, index);
    }

}