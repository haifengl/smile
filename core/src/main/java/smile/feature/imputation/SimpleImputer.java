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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import smile.data.DataFrame;
import smile.data.measure.NominalScale;
import smile.data.transform.Transform;
import smile.data.Tuple;
import smile.data.type.*;
import smile.data.vector.*;
import smile.math.MathEx;
import smile.sort.IQAgent;

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

    /** Return true if x is null or NaN. */
    static boolean isMissing(Object x) {
        if (x == null) return true;
        if (x instanceof Number n) {
            return Double.isNaN(n.doubleValue());
        }
        return false;
    }

    /**
     * Return true if the tuple x has missing values.
     * @param x a tuple.
     * @return true if the tuple x has missing values.
     */
    public static boolean hasMissing(Tuple x) {
        int n = x.length();
        for (int i = 0; i < n; i++) {
            if (isMissing(x.get(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Tuple apply(Tuple x) {
        StructType schema = x.schema();
        return new smile.data.AbstractTuple() {
            @Override
            public Object get(int i) {
                Object xi = x.get(i);
                return isMissing(xi) ? values.get(schema.field(i).name) : xi;
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
                } else if (field.type.isObject()) {
                    if (field.type == DataTypes.BooleanObjectType) {
                        boolean x = (Boolean) value;
                        boolean[] vector = new boolean[n];
                        for (int i = 0; i < n; i++) {
                            Boolean cell = (Boolean) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = BooleanVector.of(field, vector);
                    } else if (field.type == DataTypes.ByteObjectType) {
                        byte x = ((Number) value).byteValue();
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
                        double x = ((Number) value).doubleValue();
                        double[] vector = new double[n];
                        for (int i = 0; i < n; i++) {
                            Double cell = (Double) data.get(i, j);
                            vector[i] = cell == null || cell.isNaN() ? x : cell;
                        }
                        vectors[j] = DoubleVector.of(field, vector);
                    } else if (field.type == DataTypes.FloatObjectType) {
                        float x = ((Number) value).floatValue();
                        float[] vector = new float[n];
                        for (int i = 0; i < n; i++) {
                            Float cell = (Float) data.get(i, j);
                            vector[i] = cell == null || cell.isNaN() ? x : cell;
                        }
                        vectors[j] = FloatVector.of(field, vector);
                    } else if (field.type == DataTypes.IntegerObjectType) {
                        int x = ((Number) value).intValue();
                        int[] vector = new int[n];
                        for (int i = 0; i < n; i++) {
                            Integer cell = (Integer) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = IntVector.of(field, vector);
                    } else if (field.type == DataTypes.LongObjectType) {
                        long x = ((Number) value).longValue();
                        long[] vector = new long[n];
                        for (int i = 0; i < n; i++) {
                            Long cell = (Long) data.get(i, j);
                            vector[i] = cell == null ? x : cell;
                        }
                        vectors[j] = LongVector.of(field, vector);
                    } else if (field.type == DataTypes.ShortObjectType) {
                        short x = ((Number) value).shortValue();
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
            }

            if (vectors[j] == null) {
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

    /**
     * Fits the missing value imputation values. Impute all the numeric
     * columns with median, boolean/nominal columns with mode, and text
     * columns with empty string.
     * @param data the training data.
     * @param columns the columns to impute.
     *                If empty, impute all the applicable columns.
     * @return the imputer.
     */
    public static SimpleImputer fit(DataFrame data, String... columns) {
        return fit(data, 0.5, 0.5, columns);
    }

    /**
     * Fits the missing value imputation values. Impute all the numeric
     * columns with the mean of values in the range [lower, upper],
     * boolean/nominal columns with mode, and text columns with empty string.
     * @param data the training data.
     * @param lower the lower limit in terms of percentiles of the original
     *              distribution (e.g. 5th percentile).
     * @param upper the upper limit in terms of percentiles of the original
     *              distribution (e.g. 95th percentile).
     * @param columns the columns to impute.
     *                If empty, impute all the applicable columns.
     * @return the imputer.
     */
    public static SimpleImputer fit(DataFrame data, double lower, double upper, String... columns) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        if (lower < 0.0) {
            throw new IllegalArgumentException("Invalid lower: " + lower);
        }

        if (upper > 1.0) {
            throw new IllegalArgumentException("Invalid upper: " + upper);
        }

        if (lower > upper) {
            throw new IllegalArgumentException(String.format("Invalid lower=%f > upper=%f", lower, upper));
        }

        StructType schema = data.schema();
        if (columns.length == 0) {
            columns = data.names();
        }

        Map<String, Object> values = new HashMap<>();
        for (String column : columns) {
            StructField field = schema.field(column);

            if (field.type.isString()) {
                values.put(field.name, "");
            } else if (field.type.isBoolean()) {
                int[] vector = MathEx.omit(data.column(column).toIntArray(), Integer.MIN_VALUE);
                int mode = MathEx.mode(vector);
                values.put(field.name, mode != 0);
            } else if (field.type.isChar()) {
                int[] vector = MathEx.omit(data.column(column).toIntArray(), Integer.MIN_VALUE);
                int mode = MathEx.mode(vector);
                values.put(field.name, (char) mode);
            } else if (field.measure instanceof NominalScale) {
                int[] vector = MathEx.omit(data.column(column).toIntArray(), Integer.MIN_VALUE);
                int mode = MathEx.mode(vector);
                values.put(field.name, mode);
            } else if (field.type.isNumeric()) {
                double[] vector = MathEx.omitNaN(data.column(column).toDoubleArray());
                IQAgent agent = new IQAgent();
                for (double xi : vector) {
                    agent.add(xi);
                }

                if (lower == upper) {
                    values.put(field.name, agent.quantile(lower));
                } else {
                    double lo = agent.quantile(lower);
                    double hi = agent.quantile(upper);

                    int n = 0;
                    double sum = 0.0;
                    for (double xi : vector) {
                        if (xi >= lo && xi <= hi) {
                            n++;
                            sum += xi;
                        }
                    }

                    values.put(field.name, sum/n);
                }
            }
        }

        return new SimpleImputer(values);
    }

    /**
     * Impute the missing values with column averages.
     * @param data data with missing values.
     * @return the imputed data.
     * @throws IllegalArgumentException when the whole row or column is missing.
     */
    public static double[][] impute(double[][] data) {
        int d = data[0].length;
        int[] count = new int[d];
        for (int i = 0; i < data.length; i++) {
            int missing = 0;
            for (int j = 0; j < d; j++) {
                if (Double.isNaN(data[i][j])) {
                    missing++;
                    count[j]++;
                }
            }

            if (missing == d) {
                throw new IllegalArgumentException("The whole row " + i + " is missing");
            }
        }

        for (int i = 0; i < d; i++) {
            if (count[i] == data.length) {
                throw new IllegalArgumentException("The whole column " + i + " is missing");
            }
        }

        double[] mean = new double[d];
        int[] n = new int[d];
        for (double[] x : data) {
            for (int j = 0; j < d; j++) {
                if (!Double.isNaN(x[j])) {
                    n[j]++;
                    mean[j] += x[j];
                }
            }
        }

        for (int j = 0; j < d; j++) {
            if (n[j] != 0) {
                mean[j] /= n[j];
            }
        }

        double[][] full = MathEx.clone(data);
        for (double[] x : full) {
            for (int j = 0; j < d; j++) {
                if (Double.isNaN(x[j])) {
                    x[j] = mean[j];
                }
            }
        }

        return full;
    }
}
