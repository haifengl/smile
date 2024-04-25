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

import smile.data.type.DataType;
import smile.data.type.StructField;

/**
 * An immutable vector.
 *
 * @author Haifeng Li
 */
class NumberVectorImpl extends VectorImpl<Number> implements NumberVector {
    /** Constructor. */
    public NumberVectorImpl(String name, Class<?> clazz, Number[] vector) {
        super(name, clazz, vector);
    }

    /** Constructor. */
    public NumberVectorImpl(String name, DataType type, Number[] vector) {
        super(name, type, vector);
    }

    /** Constructor. */
    public NumberVectorImpl(StructField field, Number[] vector) {
        super(field, vector);
    }

    @Override
    public NumberVector get(int... index) {
        Number[] v = new Number[index.length];
        for (int i = 0; i < index.length; i++) v[i] = vector[index[i]];
        return new NumberVectorImpl(field(), v);
    }

    @Override
    public void fillna(double value) {
        Number number = switch (type().id()) {
            case Byte -> (byte) value;
            case Short -> (short) value;
            case Integer -> (int) value;
            case Long -> (long) value;
            case Float -> (float) value;
            case Double -> value;
            default -> throw new UnsupportedOperationException("Unsupported type: " + type().id());
        };

        for (int i = 0; i < vector.length; i++) {
            if (vector[i] == null) {
                vector[i] = number;
            } else {
                var x = vector[i].doubleValue();
                if (Double.isNaN(x) || Double.isInfinite(x)) {
                    vector[i] = number;
                }
            }
        }
    }
}