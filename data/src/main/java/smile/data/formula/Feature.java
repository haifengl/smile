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

package smile.data.formula;

import smile.data.Tuple;
import smile.data.type.StructField;
import smile.data.vector.*;
import smile.data.DataFrame;

/**
 * A feature in the formula once bound to a schema. A feature returns a single value
 * when applied to a data object (e.g. Tuple).
 *
 * @author Haifeng Li
 */
public interface Feature {
    /**
     * Returns the meta data of feature.
     * @return the meta data of feature.
     */
    StructField field();

    /**
     * Applies the term on a tuple.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    Object apply(Tuple tuple);

    /**
     * Applies the term on a data object and produces an double-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default double applyAsDouble(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces an float-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default float applyAsFloat(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces an int-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default int applyAsInt(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces an long-valued result.
     * @param tuple the input tuple
     * @return the feature value.
     */
    default long applyAsLong(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces an boolean-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default boolean applyAsBoolean(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces an byte-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default byte applyAsByte(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces an short-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default short applyAsShort(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces an char-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default char applyAsChar(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if the term represents a plain variable/column in the data frame.
     * @return true if the term represents a plain variable/column in the data frame.
     */
    default boolean isVariable() {
        return false;
    }

    /**
     * Applies the term on a data frame.
     * @param data the data frame.
     * @return the feature vector.
     */
    default BaseVector apply(DataFrame data) {
        StructField field = field();

        if (isVariable()) {
            return data.column(field.name);
        }

        int size = data.size();
        switch (field.type.id()) {
            case Integer: {
                int[] values = new int[size];
                for (int i = 0; i < size; i++) values[i] = applyAsInt(data.get(i));
                return IntVector.of(field, values);
            }

            case Long: {
                long[] values = new long[size];
                for (int i = 0; i < size; i++) values[i] = applyAsLong(data.get(i));
                return LongVector.of(field, values);
            }

            case Double: {
                double[] values = new double[size];
                for (int i = 0; i < size; i++) values[i] = applyAsDouble(data.get(i));
                return DoubleVector.of(field, values);
            }

            case Float: {
                float[] values = new float[size];
                for (int i = 0; i < size; i++) values[i] = applyAsFloat(data.get(i));
                return FloatVector.of(field, values);
            }

            case Boolean: {
                boolean[] values = new boolean[size];
                for (int i = 0; i < size; i++) values[i] = applyAsBoolean(data.get(i));
                return BooleanVector.of(field, values);
            }

            case Byte: {
                byte[] values = new byte[size];
                for (int i = 0; i < size; i++) values[i] = applyAsByte(data.get(i));
                return ByteVector.of(field, values);
            }

            case Short: {
                short[] values = new short[size];
                for (int i = 0; i < size; i++) values[i] = applyAsShort(data.get(i));
                return ShortVector.of(field, values);
            }

            case Char: {
                char[] values = new char[size];
                for (int i = 0; i < size; i++) values[i] = applyAsChar(data.get(i));
                return CharVector.of(field, values);
            }

            default: {
                Object[] values = new Object[size];
                for (int i = 0; i < size; i++) values[i] = apply(data.get(i));
                return Vector.of(field, values);
            }
        }
    }
}
