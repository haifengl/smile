/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.formula;

import java.math.BigDecimal;
import java.util.BitSet;
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
     * Returns the metadata of feature.
     * @return the metadata of feature.
     */
    StructField field();

    /**
     * Applies the term on a tuple.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    Object apply(Tuple tuple);

    /**
     * Applies the term on a data object and produces a double-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default double applyAsDouble(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces a float-valued result.
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
     * Applies the term on a data object and produces a long-valued result.
     * @param tuple the input tuple
     * @return the feature value.
     */
    default long applyAsLong(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces a boolean-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default boolean applyAsBoolean(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces a byte-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default byte applyAsByte(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces a short-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default short applyAsShort(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces a char-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default char applyAsChar(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces a string-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default String applyAsString(Tuple tuple) {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the term on a data object and produces a decimal-valued result.
     * @param tuple the input tuple.
     * @return the feature value.
     */
    default BigDecimal applyAsDecimal(Tuple tuple) {
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
    default ValueVector apply(DataFrame data) {
        StructField field = field();

        if (isVariable()) {
            return data.column(field.name());
        }

        int size = data.size();
        BitSet nullMask = new BitSet(size);
        var vector = switch (field.dtype().id()) {
            case Int -> {
                int[] values = new int[size];
                if (field.dtype().isNullable()) {
                    for (int i = 0; i < size; i++) {
                        var result = apply(data.get(i));
                        if (result == null) {
                            nullMask.set(i);
                            values[i] = Integer.MIN_VALUE;
                        } else {
                            values[i] = ((Number) result).intValue();
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        values[i] = applyAsInt(data.get(i));
                    }
                }

                yield nullMask.isEmpty() ? new IntVector(field, values) : new NullableIntVector(field, values, nullMask);
            }

            case Long -> {
                long[] values = new long[size];
                if (field.dtype().isNullable()) {
                    for (int i = 0; i < size; i++) {
                        var result = apply(data.get(i));
                        if (result == null) {
                            nullMask.set(i);
                            values[i] = Long.MIN_VALUE;
                        } else {
                            values[i] = ((Number) result).longValue();
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        values[i] = applyAsLong(data.get(i));
                    }
                }
                yield nullMask.isEmpty() ? new LongVector(field, values) : new NullableLongVector(field, values, nullMask);
            }

            case Double -> {
                double[] values = new double[size];
                if (field.dtype().isNullable()) {
                    for (int i = 0; i < size; i++) {
                        var result = apply(data.get(i));
                        if (result == null) {
                            nullMask.set(i);
                            values[i] = Double.NaN;
                        } else {
                            values[i] = ((Number) result).doubleValue();
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        values[i] = applyAsDouble(data.get(i));
                    }
                }
                yield nullMask.isEmpty() ? new DoubleVector(field, values) : new NullableDoubleVector(field, values, nullMask);
            }

            case Float -> {
                float[] values = new float[size];
                if (field.dtype().isNullable()) {
                    for (int i = 0; i < size; i++) {
                        var result = apply(data.get(i));
                        if (result == null) {
                            nullMask.set(i);
                            values[i] = Float.NaN;
                        } else {
                            values[i] = ((Number) result).floatValue();
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        values[i] = applyAsFloat(data.get(i));
                    }
                }
                yield nullMask.isEmpty() ? new FloatVector(field, values) : new NullableFloatVector(field, values, nullMask);
            }

            case Boolean -> {
                boolean[] values = new boolean[size];
                if (field.dtype().isNullable()) {
                    for (int i = 0; i < size; i++) {
                        var result = apply(data.get(i));
                        if (result == null) {
                            nullMask.set(i);
                        } else {
                            values[i] = (Boolean) result;
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        values[i] = applyAsBoolean(data.get(i));
                    }
                }
                yield nullMask.isEmpty() ? new BooleanVector(field, values) : new NullableBooleanVector(field, values, nullMask);
            }

            case Byte -> {
                byte[] values = new byte[size];
                if (field.dtype().isNullable()) {
                    for (int i = 0; i < size; i++) {
                        var result = apply(data.get(i));
                        if (result == null) {
                            nullMask.set(i);
                            values[i] = Byte.MIN_VALUE;
                        } else {
                            values[i] = ((Number) result).byteValue();
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        values[i] = applyAsByte(data.get(i));
                    }
                }
                yield nullMask.isEmpty() ? new ByteVector(field, values) : new NullableByteVector(field, values, nullMask);
            }

            case Short -> {
                short[] values = new short[size];
                if (field.dtype().isNullable()) {
                    for (int i = 0; i < size; i++) {
                        var result = apply(data.get(i));
                        if (result == null) {
                            nullMask.set(i);
                            values[i] = Short.MIN_VALUE;
                        } else {
                            values[i] = ((Number) result).shortValue();
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        values[i] = applyAsShort(data.get(i));
                    }
                }
                yield nullMask.isEmpty() ? new ShortVector(field, values) : new NullableShortVector(field, values, nullMask);
            }

            case Char -> {
                char[] values = new char[size];
                if (field.dtype().isNullable()) {
                    for (int i = 0; i < size; i++) {
                        var result = apply(data.get(i));
                        if (result == null) {
                            nullMask.set(i);
                            values[i] = 0;
                        } else {
                            values[i] = (Character) result;
                        }
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        values[i] = applyAsChar(data.get(i));
                    }
                }
                yield nullMask.isEmpty() ? new CharVector(field, values) : new NullableCharVector(field, values, nullMask);
            }

            case String -> {
                String[] values = new String[size];
                for (int i = 0; i < size; i++) values[i] = applyAsString(data.get(i));
                yield new StringVector(field, values);
            }

            case Decimal -> {
                BigDecimal[] values = new BigDecimal[size];
                for (int i = 0; i < size; i++) values[i] = applyAsDecimal(data.get(i));
                yield new NumberVector<>(field, values);
            }

            default -> {
                Object[] values = new Object[size];
                for (int i = 0; i < size; i++) values[i] = apply(data.get(i));
                yield new ObjectVector<>(field, values);
            }
        };

        return vector;
    }
}
