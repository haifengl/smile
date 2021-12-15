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

package smile.feature.imputation;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import smile.data.DataFrame;
import smile.data.transform.Transform;
import smile.data.Tuple;
import smile.data.type.*;
import smile.data.vector.*;

/**
 * Simple algorithm replaces missing values with the constant value
 * along each column.
 *
 * @author Haifeng Li
 */
public class SimpleImputer implements Transform {
    /** The map of column name to the constant value. */
    private final Map<String, Object> values;

    /**
     * Constructor.
     * @param values the map of column name to the constant value.
     */
    public SimpleImputer(Map<String, Object> values) {
        this.values = values;
    }

    @Override
    public Tuple apply(Tuple x) {
        StructType schema = x.schema();
        return new smile.data.AbstractTuple() {
            @Override
            public Object get(int i) {
                Object xi = x.get(i);
                return xi != null ? xi : values.get(schema.field(i).name);
            }

            @Override
            public StructType schema() {
                return schema;
            }
        };
    }

    @Override
    public DataFrame apply(DataFrame data) {
        int n = data.nrow();
        StructType schema = data.schema();
        BaseVector<?, ?, ?>[] vectors = new BaseVector[schema.length()];
        IntStream.range(0, schema.length()).parallel().forEach(j -> {
            StructField field = schema.field(j);
            Object value = values.get(field.name);
            if (value != null) {
                if (field.type.id() == DataType.ID.Double) {
                    double x = ((Number) value).doubleValue();
                    double[] column = data.doubleVector(j).array();
                    double[] vector = new double[n];
                    for (int i = 0; i < n; i++) {
                        vector[i] = Double.isNaN(column[i]) ? x : column[i];
                    }
                    vectors[j] = DoubleVector.of(field, vector);
                } else if (field.type.id() == DataType.ID.Float) {
                    float x = ((Number) value).floatValue();
                    float[] column = data.floatVector(j).array();
                    float[] vector = new float[n];
                    for (int i = 0; i < n; i++) {
                        vector[i] = Float.isNaN(column[i]) ? x : column[i];
                    }
                    vectors[j] = FloatVector.of(field, vector);
                } else if (field.type.isPrimitive()) {
                    throw new IllegalArgumentException("Impute non-floating primitive types");
                } else {
                    if (field.type == DataTypes.BooleanObjectType) {
                        boolean x = (Boolean) value;
                        boolean[] vector = new boolean[n];
                        for (int i = 0; i < n; i++) {
                            Boolean cell = (Boolean) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = BooleanVector.of(field, vector);
                    } else if (field.type == DataTypes.ByteObjectType) {
                        byte x = (Byte) value;
                        byte[] vector = new byte[n];
                        for (int i = 0; i < n; i++) {
                            Byte cell = (Byte) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = ByteVector.of(field, vector);
                    } else if (field.type == DataTypes.CharObjectType) {
                        char x = (Character) value;
                        char[] vector = new char[n];
                        for (int i = 0; i < n; i++) {
                            Character cell = (Character) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = CharVector.of(field, vector);
                    } else if (field.type == DataTypes.DoubleObjectType) {
                        double x = (Double) value;
                        double[] vector = new double[n];
                        for (int i = 0; i < n; i++) {
                            Double cell = (Double) data.get(i, j);
                            vector[i] = cell == null || cell.isNaN() ? x : cell;
                        }
                        vectors[j] = DoubleVector.of(field, vector);
                    } else if (field.type == DataTypes.FloatObjectType) {
                        float x = (Float) value;
                        float[] vector = new float[n];
                        for (int i = 0; i < n; i++) {
                            Float cell = (Float) data.get(i, j);
                            vector[i] = cell == null || cell.isNaN() ? x : cell;
                        }
                        vectors[j] = FloatVector.of(field, vector);
                    } else if (field.type == DataTypes.IntegerObjectType) {
                        int x = (Integer) value;
                        int[] vector = new int[n];
                        for (int i = 0; i < n; i++) {
                            Integer cell = (Integer) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = IntVector.of(field, vector);
                    } else if (field.type == DataTypes.LongObjectType) {
                        long x = (Long) value;
                        long[] vector = new long[n];
                        for (int i = 0; i < n; i++) {
                            Long cell = (Long) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = LongVector.of(field, vector);
                    } else if (field.type == DataTypes.ShortObjectType) {
                        short x = (Short) value;
                        short[] vector = new short[n];
                        for (int i = 0; i < n; i++) {
                            Short cell = (Short) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = ShortVector.of(field, vector);
                    } else {
                        Object[] vector = (Object[]) java.lang.reflect.Array.newInstance(value.getClass(), n);
                        for (int i = 0; i < n; i++) {
                            Object cell = data.get(i, j);
                            vector[i] = cell == null ? value : cell;
                        }
                        vectors[j] = Vector.of(field, vector);
                    }
                }
            } else {
                vectors[j] = data.column(j);
            }
        });
        return DataFrame.of(vectors);
    }

    @Override
    public String toString() {
        return values.keySet().stream()
                .map(key -> key + " -> " + values.get(key))
                .collect(Collectors.joining(",\n  ", "SimpleImputer(\n  ", "\n)"));
    }
}
