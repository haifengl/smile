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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import smile.data.measure.CategoricalMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.*;
import smile.data.vector.*;
import smile.math.MathEx;
import smile.tensor.DenseMatrix;
import smile.util.Index;
import smile.util.Strings;
import static smile.tensor.ScalarType.*;

/**
 * Two-dimensional, potentially heterogeneous tabular data.
 *
 * @param schema the schema of DataFrame.
 * @param columns the columns of DataFrame.
 * @param index the optional row index.
 *
 * @author Haifeng Li
 */
public record DataFrame(StructType schema, List<ValueVector> columns, RowIndex index) implements Iterable<Row>, Serializable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DataFrame.class);

    /** Constructor. */
    public DataFrame {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Columns must not be empty");
        }

        int size = index != null ? index.size() : columns.getFirst().size();
        for (var column : columns) {
            if (column.size() != size) {
                throw new IllegalArgumentException("Columns must have the same size.");
            }
        }
    }

    /**
     * Constructor.
     * @param columns the columns of DataFrame.
     */
    public DataFrame(ValueVector... columns) {
        this(null, columns);
    }

    /**
     * Constructor.
     * @param index the row index.
     * @param columns the columns of DataFrame.
     */
    public DataFrame(RowIndex index, ValueVector... columns) {
        this(StructType.of(columns), new ArrayList<>(Arrays.asList(columns)), index);
    }

    @Override
    public String toString() {
        return head(10);
    }

    /**
     * Returns the column names.
     * @return the column names.
     */
    public String[] names() {
        return schema.names();
    }

    /**
     * Returns the column data types.
     * @return the column data types.
     */
    public DataType[] dtypes() {
        return schema.dtypes();
    }

    /**
     * Returns the column's level of measurements.
     * @return the column's level of measurements.
     */
    public Measure[] measures() {
        return schema.measures();
    }

    /**
     * Returns the size of given dimension.
     * For pandas user's convenience.
     * @param dim the dimension index.
     * @return the size of given dimension.
     */
    public int shape(int dim) {
        return switch (dim) {
            case 0 -> columns.getFirst().size();
            case 1 -> columns.size();
            default -> throw new IllegalArgumentException("Invalid dim: " + dim);
        };
    }

    /**
     * Returns the number of rows.
     * This is an alias to {@link #nrow() nrow} for Java's convention.
     * @return the number of rows.
     */
    public int size() {
        return columns.getFirst().size();
    }

    /**
     * Returns the number of rows.
     * @return the number of rows.
     */
    public int nrow() {
        return columns.getFirst().size();
    }

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    public int ncol() {
        return columns.size();
    }

    /**
     * Returns true if the data frame is empty.
     * @return true if the data frame is empty.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Sets the DataFrame index using existing column. The index column
     * will be removed from the DataFrame.
     * @param column the name of column that will be used as row index.
     * @return a new DataFrame with the row index.
     */
    public DataFrame setIndex(String column) {
        int n = size();
        ValueVector vector = apply(column);
        Object[] index = new Object[n];
        for (int i = 0; i < n; i++) {
            index[i] = vector.get(i);
        }

        // Remove the column to be used as the new index.
        var data = columns.stream()
                .filter(c -> !c.name().equals(column))
                .toArray(ValueVector[]::new);
        return new DataFrame(new RowIndex(index), data);
    }

    /**
     * Sets the DataFrame index.
     * @param index the row index values.
     * @return a new DataFrame with the row index.
     */
    public DataFrame setIndex(Object[] index) {
        if (index.length != size()) {
            throw new IllegalArgumentException("Index array must have the same size as data frame.");
        }
        return new DataFrame(schema, columns, new RowIndex(index));
    }

    /**
     * Returns the j-th column.
     * @param j the column index.
     * @return the column vector.
     */
    public ValueVector column(int j) {
        return columns.get(j);
    }

    /**
     * Returns the column of given name.
     * @param name the column name.
     * @return the column vector.
     */
    public ValueVector column(String name) {
        return columns.get(schema.indexOf(name));
    }

    /**
     * Returns the column of given name.
     * This is an alias to {@link #column(String) column} for Scala's convenience.
     * @param name the column name.
     * @return the column vector.
     */
    public ValueVector apply(String name) {
        return column(name);
    }

    /**
     * Returns a new DataFrame with selected columns.
     * This is an alias to {@link #select(String...) select} for Scala's convenience.
     * @param names the column names.
     * @return a new DataFrame with selected columns.
     */
    public DataFrame apply(String... names) {
        return select(names);
    }

    /**
     * Returns the row at the specified index.
     * @param i the row index.
     * @return the i-th row.
     */
    public Tuple get(int i) {
        return new Row(this, i);
    }

    /**
     * Returns the row at the specified index.
     * This is an alias to {@link #get(int) get} for Scala's convenience.
     * @param i the row index.
     * @return the i-th row.
     */
    public Tuple apply(int i) {
        return get(i);
    }

    /**
     * Returns the row with the specified index.
     * @param row the row index.
     * @return the row with the specified index.
     */
    public Tuple loc(Object row) {
        return new Row(this, index.apply(row));
    }

    /**
     * Returns a new data frame with specified rows.
     * @param rows the row indices.
     * @return a new data frame with specified rows.
     */
    public DataFrame loc(Object... rows) {
        int n = rows.length;
        int[] index = new int[n];
        for (int i = 0; i < n; i++) {
            index[i] = this.index.apply(rows[i]);
        }
        return get(Index.of(index));
    }

    /**
     * Returns a new data frame with row indexing.
     * @param index the row indexing.
     * @return the data frame of selected rows.
     */
    public DataFrame get(Index index) {
        var rowIndex = this.index != null ? this.index.get(index) : null;
        return new DataFrame(schema, columns.stream()
                .map(column -> column.get(index))
                .toList(), rowIndex);
    }

    /**
     * Returns a new data frame with row indexing.
     * This is an alias to {@link #get(Index) get} for Scala's convenience.
     * @param index the row indexing.
     * @return the data frame of selected rows.
     */
    public DataFrame apply(Index index) {
        return get(index);
    }

    /**
     * Returns a new data frame with boolean indexing.
     * @param index the boolean indexing.
     * @return the data frame of selected rows.
     */
    public DataFrame get(boolean[] index) {
        var idx = Index.of(index);
        var rowIndex = this.index != null ? this.index.get(idx) : null;
        return new DataFrame(schema, columns.stream()
                .map(column -> column.get(idx))
                .toList(), rowIndex);
    }

    /**
     * Returns a new data frame with boolean indexing.
     * This is an alias to {@link #get(boolean[]) get} for Scala's convenience.
     * @param index the boolean indexing.
     * @return the data frame of selected rows.
     */
    public DataFrame apply(boolean[] index) {
        return get(index);
    }

    /**
     * Checks whether the value at position (i, j) is null or missing value.
     * @param i the row index.
     * @param j the column index.
     * @return true if the cell value is null.
     */
    public boolean isNullAt(int i, int j) {
        return columns.get(j).isNullAt(i);
    }

    /**
     * Returns the cell at (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the cell value.
     */
    public Object get(int i, int j) {
        return columns.get(j).get(i);
    }

    /**
     * Returns the cell at (i, j).
     * This is an alias to {@link #get(int, int) get} for Scala's convenience.
     * @param i the row index.
     * @param j the column index.
     * @return the cell value.
     */
    public Object apply(int i, int j) {
        return get(i, j);
    }

    /**
     * Returns the int value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the int value of cell.
     */
    public int getInt(int i, int j) {
        return columns.get(j).getInt(i);
    }

    /**
     * Returns the long value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the long value of cell.
     */
    public long getLong(int i, int j) {
        return columns.get(j).getLong(i);
    }

    /**
     * Returns the float value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the float value of cell.
     */
    public float getFloat(int i, int j) {
        return columns.get(j).getFloat(i);
    }

    /**
     * Returns the double value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the double value of cell.
     */
    public double getDouble(int i, int j) {
        return columns.get(j).getDouble(i);
    }

    /**
     * Returns the string representation of the value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the string representation of cell value.
     */
    public String getString(int i, int j) {
        return columns.get(j).getString(i);
    }

    /**
     * Returns the value at position (i, j) of NominalScale or OrdinalScale.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when the data is not nominal or ordinal.
     * @return the cell scale.
     */
    public String getScale(int i, int j) {
        return columns.get(j).getScale(i);
    }

    /**
     * Sets the value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @param value the new value.
     */
    public void set(int i, int j, Object value) {
        columns.get(j).set(i, value);
    }

    /**
     * Updates the value at position (i, j).
     * This is an alias to {@link #set(int, int, Object) set} for Scala's convenience.
     * @param i the row index.
     * @param j the column index.
     * @param value the new value.
     */
    public void update(int i, int j, Object value) {
        set(i, j, value);
    }

    /**
     * Returns a (possibly parallel) Stream of rows.
     *
     * @return a (possibly parallel) Stream of rows.
     */
    public Stream<Row> stream() {
        return IntStream.range(0, size()).mapToObj(i -> new Row(this, i));
    }

    @Override
    public Iterator<Row> iterator() {
        return stream().iterator();
    }

    /**
     * Returns the <code>List</code> of rows.
     * @return the <code>List</code> of rows.
     */
    public List<Row> toList() {
        return stream().toList();
    }

    /**
     * Returns a new data frame without rows that have null/missing values.
     * @return the data frame without null/missing values.
     */
    public DataFrame dropna() {
        boolean[] nonNull = new boolean[size()];
        for (int i = 0; i < nonNull.length; i++) {
            nonNull[i] = !get(i).anyNull();
        }
        return get(Index.of(nonNull));
    }

    /**
     * Fills null/NaN/Inf values of numeric columns with the specified value.
     * @param value the value to replace NAs.
     * @return this data frame.
     */
    public DataFrame fillna(double value) {
        for (var column : columns) {
            if (column instanceof FloatVector vector) {
                vector.fillna((float) value);
            } else if (column instanceof DoubleVector vector) {
                vector.fillna(value);
            } else if (column instanceof NullablePrimitiveVector vector) {
                vector.fillna(value);
            } else if (column instanceof NumberVector<?> vector) {
                vector.fillna(value);
            }
        }
        return this;
    }

    /**
     * Returns a new DataFrame with selected columns.
     * @param indices the column indices.
     * @return a new DataFrame with selected columns.
     */
    public DataFrame select(int... indices) {
        return new DataFrame(index, Arrays.stream(indices)
                .mapToObj(columns::get)
                .toArray(ValueVector[]::new));
    }

    /**
     * Returns a new DataFrame with selected columns.
     * @param names the column names.
     * @return a new DataFrame with selected columns.
     */
    public DataFrame select(String... names) {
        return new DataFrame(index, Arrays.stream(names)
                .map(this::column)
                .toArray(ValueVector[]::new));
    }

    /**
     * Returns a new DataFrame without selected columns.
     * @param indices the column indices.
     * @return a new DataFrame without selected columns.
     */
    public DataFrame drop(int... indices) {
        Set<Integer> set = new HashSet<>();
        for (var index : indices) {
            set.add(index);
        }

        return new DataFrame(index, IntStream.range(0, columns.size())
                .filter(j -> !set.contains(j))
                .mapToObj(columns::get)
                .toArray(ValueVector[]::new));
    }

    /**
     * Returns a new DataFrame without selected columns.
     * @param names the column names.
     * @return a new DataFrame without selected columns.
     */
    public DataFrame drop(String... names) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, names);

        return new DataFrame(index, columns.stream()
                .filter(column -> !set.contains(column.name()))
                .toArray(ValueVector[]::new));
    }

    /**
     * Adds columns to this data frame.
     *
     * @param vectors the columns to add.
     * @return this dataframe.
     */
    public DataFrame add(ValueVector... vectors) {
        for (var vector : vectors) {
            if (vector.size() != size()) {
                throw new IllegalArgumentException("Add a column with different size: " + size() + " vs " + vector.size());
            }

            if (schema.index().containsKey(vector.name())) {
                throw new IllegalArgumentException("Add a column with clashing name: " + vector.name());
            }
        }

        for (var vector : vectors) {
            schema.add(vector.field());
            columns.add(vector);
        }
        return this;
    }

    /**
     * Sets the column values. If the column does not exist, adds it as
     * the last column of the dataframe.
     *
     * @param name the column name.
     * @param column the new column value.
     * @return this dataframe.
     */
    public DataFrame set(String name, ValueVector column) {
        if (column.size() != size()) {
            throw new IllegalArgumentException("column size mismatch");
        }

        if (!name.equals(column.name())) {
            column = column.withName(name);
        }

        int j = schema.index().getOrDefault(name, ncol());
        if (j < ncol()) {
            columns.set(j, column);
        } else {
            schema.add(column.field());
            columns.add(column);
        }
        return this;
    }

    /**
     * Sets the column values. If the column does not exist, adds it as
     * the last column of the dataframe.
     * This is an alias to {@link #set(String, ValueVector) set} for Scala's convenience.
     *
     * @param name the column name.
     * @param column the new column value.
     * @return this dataframe.
     */
    public DataFrame update(String name, ValueVector column) {
        return set(name, column);
    }

    /**
     * Joins two data frames on their index. If either dataframe has no index,
     * merges them horizontally by columns.
     * @param other the data frames to merge.
     * @return a new data frame with combined columns.
     */
    public DataFrame join(DataFrame other) {
        if (index == null || other.index == null) {
            return merge(other);
        }

        int k = 0;
        int n = size();
        boolean[] inner = new boolean[n];
        int[] right = new int[n];
        for (int i = 0; i < n; i++) {
            var id = index.values()[i];
            var j = other.index().loc().get(id);
            if (j != null) {
                inner[i] = true;
                right[k++] = j;
            }
        }
        var left = get(Index.of(inner));
        other = other.get(Index.of(Arrays.copyOf(right, k)));
        return left.merge(other);
    }

    /**
     * Merges data frames horizontally by columns. If there are columns
     * with the same name, the latter ones will be renamed with suffix
     * such as _2, _3, etc.
     * @param dataframes the data frames to merge.
     * @return a new data frame with combined columns.
     */
    public DataFrame merge(DataFrame... dataframes) {
        for (DataFrame df : dataframes) {
            if (df.size() != size()) {
                throw new IllegalArgumentException("Merge data frames with different size: " + size() + " vs " + df.size());
            }
        }

        List<ValueVector> data = new ArrayList<>(columns);
        Set<String> names = new HashSet<>();
        Collections.addAll(names, names());
        int order = 2;
        for (DataFrame df : dataframes) {
            var suffix = "_" + order++;
            for (var column : df.columns) {
                if (!names.contains(column.name())) {
                    data.add(column);
                    names.add(column.name());
                } else {
                    var name = column.name() + suffix;
                    data.add(column.withName(name));
                    names.add(name);
                }
            }
        }

        return new DataFrame(index, data.toArray(ValueVector[]::new));
    }

    /**
     * Concatenates data frames vertically by rows.
     * @param dataframes the data frames to concatenate.
     * @return a new data frame that combines all the rows.
     */
    public DataFrame concat(DataFrame... dataframes) {
        boolean hasIndex = index != null;
        for (var df : dataframes) {
            if (!schema.equals(df.schema())) {
                throw new IllegalArgumentException("Union data frames with different schema: " + schema + " vs " + df.schema());
            }
            hasIndex &= df.index != null;
        }

        var rows = Stream.concat(Stream.of(this), Stream.of(dataframes))
                .flatMap(DataFrame::stream);
        var df = DataFrame.of(schema, rows);

        if (hasIndex) {
            var index = Stream.concat(Stream.of(this), Stream.of(dataframes))
                    .flatMap(data -> Arrays.stream(data.index.values())).toArray();
            df = df.setIndex(index);
        }
        return df;
    }

    /**
     * Returns a new DataFrame with given columns converted to nominal.
     *
     * @param names column names. If empty, all object columns
     *              in the data frame will be converted.
     * @return a new DataFrame.
     */
    public DataFrame factorize(String... names) {
        if (names.length == 0) {
            names = schema().fields().stream()
                    .filter(field -> field.dtype().isString())
                    .map(StructField::name)
                    .toArray(String[]::new);
        }

        int n = size();
        HashSet<String> set = new HashSet<>();
        Collections.addAll(set, names);

        ValueVector[] vectors = columns.stream().map(column -> {
            if (!set.contains(column.name())) return column;

            List<String> levels = IntStream.range(0, n)
                    .mapToObj(column::getString)
                    .distinct().sorted()
                    .toList();

            NominalScale scale = new NominalScale(levels);

            int[] data = new int[n];
            for (int i = 0; i < n; i++) {
                String s = column.getString(i);
                data[i] = s == null ? (byte) -1 : scale.valueOf(s).intValue();
            }

            StructField field = new StructField(column.name(), DataTypes.IntType, scale);
            return new IntVector(field, data);
        }).toArray(ValueVector[]::new);

        return new DataFrame(index, vectors);
    }

    /**
     * Return an array obtained by converting the columns
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Missing values/nulls will be
     * encoded as Double.NaN. No bias term and uses level encoding
     * for categorical variables.
     * @param columns the columns to export. If empty, all columns will be used.
     * @return the numeric array.
     */
    public double[][] toArray(String... columns) {
        return toArray(false, CategoricalEncoder.LEVEL, columns);
    }

    /**
     * Return an array obtained by converting the columns
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Missing values/nulls will be
     * encoded as Double.NaN.
     *
     * @param bias if true, add the first column of all 1's.
     * @param encoder the categorical variable encoder.
     * @param names the columns to export. If empty, all columns will be used.
     * @return the numeric array.
     */
    public double[][] toArray(boolean bias, CategoricalEncoder encoder, String... names) {
        int nrow = size();
        if (names.length == 0) {
            names = schema.names();
        }

        ArrayList<String> colNames = new ArrayList<>();
        if (bias) colNames.add("Intercept");
        for (String name : names) {
            var column = column(name);
            StructField field = column.field();
            Measure measure = field.measure();

            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure cat) {
                int n = cat.size();

                if (encoder == CategoricalEncoder.DUMMY) {
                    for (int k = 1; k < n; k++) {
                        colNames.add(String.format("%s_%s", name, cat.level(k)));
                    }
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    for (int k = 0; k < n; k++) {
                        colNames.add(String.format("%s_%s", name, cat.level(k)));
                    }
                }
            } else {
                colNames.add(name);
            }
        }

        double[][] matrix = new double[nrow][colNames.size()];

        int j = 0;
        if (bias) {
            j++;
            for (int i = 0; i < nrow; i++) {
                matrix[i][0] = 1.0;
            }
        }

        for (String name : names) {
            var column = column(name);
            StructField field = column.field();
            Measure measure = field.measure();

            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure cat) {
                if (encoder == CategoricalEncoder.DUMMY) {
                    for (int i = 0; i < nrow; i++) {
                        int k = cat.factor(column.getInt(i));
                        if (k > 0) matrix[i][j + k - 1] = 1.0;
                    }
                    j += cat.size() - 1;
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    for (int i = 0; i < nrow; i++) {
                        int k = cat.factor(column.getInt(i));
                        matrix[i][j + k] = 1.0;
                    }
                    j += cat.size();
                }
            } else {
                for (int i = 0; i < nrow; i++) {
                    matrix[i][j] = column.getDouble(i);
                }
                j++;
            }
        }

        return matrix;
    }

    /**
     * Return a matrix obtained by converting all the variables
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Missing values/nulls will be
     * encoded as Double.NaN.
     * @return the numeric matrix.
     */
    public DenseMatrix toMatrix() {
        return toMatrix(false, CategoricalEncoder.LEVEL, null);
    }

    /**
     * Return a matrix obtained by converting all the variables
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Missing values/nulls will be
     * encoded as Double.NaN. No bias term and uses level encoding
     * for categorical variables.
     *
     * @param bias if true, add the first column of all 1's.
     * @param encoder the categorical variable encoder.
     * @param rowNames the column to be used as row names.
     * @return the numeric matrix.
     */
    public DenseMatrix toMatrix(boolean bias, CategoricalEncoder encoder, String rowNames) {
        int nrow = size();
        int ncol = columns.size();

        ArrayList<String> colNames = new ArrayList<>();
        if (bias) colNames.add("Intercept");
        for (var column : columns) {
            StructField field = column.field();
            if (field.name().equals(rowNames)) continue;

            Measure measure = field.measure();
            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure cat) {
                int n = cat.size();

                if (encoder == CategoricalEncoder.DUMMY) {
                    for (int k = 1; k < n; k++) {
                        colNames.add(String.format("%s_%s", field.name(), cat.level(k)));
                    }
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    for (int k = 0; k < n; k++) {
                        colNames.add(String.format("%s_%s", field.name(), cat.level(k)));
                    }
                }
            } else {
                colNames.add(field.name());
            }
        }

        DenseMatrix matrix = DenseMatrix.zeros(Float64, nrow, colNames.size());
        matrix.withColNames(colNames.toArray(new String[0]));
        if (rowNames != null) {
            int j = schema.indexOf(rowNames);
            String[] rows = new String[nrow];
            for (int i = 0; i < nrow; i++) {
                rows[i] = getString(i, j);
            }
            matrix.withRowNames(rows);
        }

        int j = 0;
        if (bias) {
            j++;
            for (int i = 0; i < nrow; i++) {
                matrix.set(i, 0, 1.0);
            }
        }

        for (var column : columns) {
            StructField field = column.field();
            if (field.name().equals(rowNames)) continue;

            Measure measure = field.measure();
            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure cat) {
                if (encoder == CategoricalEncoder.DUMMY) {
                    for (int i = 0; i < nrow; i++) {
                        int k = cat.factor(column.getInt(i));
                        if (k > 0) matrix.set(i, j + k - 1, 1.0);
                    }
                    j += cat.size() - 1;
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    for (int i = 0; i < nrow; i++) {
                        int k = cat.factor(column.getInt(i));
                        matrix.set(i, j + k, 1.0);
                    }
                    j += cat.size();
                }
            } else {
                for (int i = 0; i < nrow; i++) {
                    matrix.set(i, j, column.getDouble(i));
                }
                j++;
            }
        }

        return matrix;
    }

    /**
     * Returns the data structure and statistics.
     * @return the data structure and statistics.
     */
    public DataFrame describe() {
        int ncol = columns.size();
        DataType[] dtypes = dtypes();
        Measure[] measures = measures();
        int[] count = new int[ncol];
        Object[] mode = new Object[ncol];
        double[] mean = new double[ncol];
        double[] std = new double[ncol];
        double[] min = new double[ncol];
        double[] q1 = new double[ncol];
        double[] median = new double[ncol];
        double[] q3 = new double[ncol];
        double[] max = new double[ncol];
        Arrays.fill(mean, Double.NaN);
        Arrays.fill(std, Double.NaN);
        Arrays.fill(min, Double.NaN);
        Arrays.fill(q1, Double.NaN);
        Arrays.fill(median, Double.NaN);
        Arrays.fill(q3, Double.NaN);
        Arrays.fill(max, Double.NaN);

        for (int j = 0; j < ncol; j++) {
            DataType dtype = dtypes[j];
            if (measures[j] instanceof CategoricalMeasure measure) {
                int[] data = columns.get(j).intStream()
                        .filter(x -> x != Integer.MIN_VALUE)
                        .toArray();
                count[j] = data.length;
                mode[j] = measure.toString(MathEx.mode(data));
                min[j] = MathEx.min(data);
                q1[j] = MathEx.q1(data);
                median[j] = MathEx.median(data);
                q3[j] = MathEx.q3(data);
                max[j] = MathEx.max(data);
            } else if (dtype.isLong()) {
                double[] data = columns.get(j).longStream()
                        .filter(x -> x != Long.MIN_VALUE)
                        .mapToDouble(x -> x).toArray();
                count[j] = data.length;
                mode[j] = Double.NaN;
                mean[j] = MathEx.mean(data);
                std[j] = MathEx.stdev(data);
                min[j] = MathEx.min(data);
                q1[j] = MathEx.q1(data);
                median[j] = MathEx.median(data);
                q3[j] = MathEx.q3(data);
                max[j] = MathEx.max(data);
            } else if (dtype.isIntegral()) {
                int[] data = columns.get(j).intStream()
                        .filter(x -> x != Integer.MIN_VALUE)
                        .toArray();
                count[j] = data.length;
                mode[j] = MathEx.mode(data);
                mean[j] = MathEx.mean(data);
                std[j] = MathEx.stdev(data);
                min[j] = MathEx.min(data);
                q1[j] = MathEx.q1(data);
                median[j] = MathEx.median(data);
                q3[j] = MathEx.q3(data);
                max[j] = MathEx.max(data);
            } else if (dtype.isFloating() || dtype.isDecimal()) {
                double[] data = columns.get(j).doubleStream()
                        .filter(Double::isFinite)
                        .toArray();
                count[j] = data.length;
                mode[j] = Double.NaN;
                mean[j] = MathEx.mean(data);
                std[j] = MathEx.stdev(data);
                min[j] = MathEx.min(data);
                q1[j] = MathEx.q1(data);
                median[j] = MathEx.median(data);
                q3[j] = MathEx.q3(data);
                max[j] = MathEx.max(data);
            } else {
                count[j] = (int) columns.get(j).stream().filter(Objects::nonNull).count();
                mode[j] = columns.get(j).stream().filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.groupingBy(Function.identity(), java.util.stream.Collectors.counting()))
                        .entrySet()
                        .stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
            }
        }

        return new DataFrame(
                new StringVector("column", names()),
                new ObjectVector<>("type", dtypes),
                new ObjectVector<>("measure", measures),
                new IntVector("count", count),
                new ObjectVector<>("mode", mode),
                new DoubleVector("mean", mean),
                new DoubleVector("std", std),
                new DoubleVector("min", min),
                new DoubleVector("25%", q1),
                new DoubleVector("50%", median),
                new DoubleVector("75%", q3),
                new DoubleVector("max", max)
        );
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows the number of rows to show.
     * @return the string representation of top rows.
     */
    public String head(int numRows) {
        return toString(0, numRows, true);
    }

    /**
     * Returns the string representation of bottom rows.
     * @param numRows the number of rows to show.
     * @return the string representation of bottom rows.
     */
    public String tail(int numRows) {
        return toString(Math.max(0, size() - numRows), size(), true);
    }

    /**
     * Returns the string representation of rows in specified range.
     * @param from the initial index of the range to show, inclusive
     * @param to the final index of the range to show, exclusive.
     * @param truncate Whether truncate long strings and align cells right.
     * @return the string representation of rows in specified range.
     */
    public String toString(int from, int to, boolean truncate) {
        if (from < 0 || from >= size()) {
            throw new IllegalArgumentException("from: " + from + ", size: " + size());
        }

        if (to <= from) {
            throw new IllegalArgumentException("'to' must be greater than 'from'");
        }

        to = Math.min(to, size());
        StringBuilder sb = new StringBuilder();
        boolean hasMoreData = from == 0 && size() > to;
        int numCols = ncol() + 1;
        String[] names = new String[numCols];
        names[0] = ""; // Index column has no name.
        System.arraycopy(names(), 0, names, 1, ncol());
        final int maxColWidth = switch (numCols) {
            case 1 -> 78;
            case 2 -> 38;
            default -> 20;
        };

        // Initialize the width of each column to a minimum value of '3'
        int[] colWidths = new int[numCols];
        for (int i = 0; i < numCols; i++) {
            colWidths[i] = Math.max(names[i].length(), 3);
        }

        // For array values, replace Seq and Array with square brackets
        // For cells that are beyond maxColumnWidth characters, truncate it with "..."
        int numRows = to - from;
        String[][] rows = new String[numRows][numCols];
        for (int i = 0; i < numRows; i++) {
            int row = from + i;
            String[] cells = rows[i];
            cells[0] = index == null ? Integer.toString(row): index.values()[row].toString();
            for (int j = 1; j < numCols; j++) {
                String str = columns.get(j-1).getString(row);
                cells[j] = (truncate && str.length() > maxColWidth) ? str.substring(0, maxColWidth - 3) + "..." : str;
            }
        }

        // Compute the width of each column
        for (String[] row : rows) {
            for (int i = 0; i < numCols; i++) {
                colWidths[i] = Math.max(colWidths[i], row[i].length());
            }
        }

        // Create SeparateLine
        String sep = IntStream.of(colWidths)
                .mapToObj(w -> Strings.fill('-', w))
                .collect(java.util.stream.Collectors.joining("+", "+", "+\n"));
        sb.append(sep);

        // column names
        sb.append(line(names, colWidths, truncate));
        sb.append(sep);

        // data
        for (String[] row : rows) {
            sb.append(line(row, colWidths, truncate));
        }

        sb.append(sep);

        // For Data that has more than "numRows" records
        if (hasMoreData) {
            int rest = size() - to;
            if (rest > 0) {
                String rowsString = (rest == 1) ? "row" : "rows";
                sb.append(String.format("%d more %s...\n", rest, rowsString));
            }
        }

        return sb.toString();
    }

    /** Returns a pretty-print line. */
    private StringBuilder line(String[] row, int[] colWidths, boolean truncate) {
        StringBuilder line = new StringBuilder();
        // Index column
        line.append('|');
        line.append(Strings.leftPad(row[0], colWidths[0], ' '));
        line.append('|');

        // Data columns
        for (int i = 1; i < colWidths.length; i++) {
            if (truncate) {
                line.append(Strings.leftPad(row[i], colWidths[i], ' '));
            } else {
                line.append(Strings.rightPad(row[i], colWidths[i], ' '));
            }
            line.append('|');
        }
        line.append('\n');
        return line;
    }

    /**
     * Creates a DataFrame from a 2-dimensional array.
     * @param data the data array.
     * @param names the name of columns.
     * @return the data frame.
     */
    public static DataFrame of(double[][] data, String... names) {
        int p = data[0].length;
        if (names == null || names.length == 0) {
            names = IntStream.range(1, p+1).mapToObj(i -> "V"+i).toArray(String[]::new);
        }

        DoubleVector[] columns = new DoubleVector[p];
        for (int j = 0; j < p; j++) {
            double[] x = new double[data.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = data[i][j];
            }
            columns[j] = new DoubleVector(names[j], x);
        }

        return new DataFrame(columns);
    }

    /**
     * Creates a DataFrame from a 2-dimensional array.
     * @param data the data array.
     * @param names the name of columns.
     * @return the data frame.
     */
    public static DataFrame of(float[][] data, String... names) {
        int p = data[0].length;
        if (names == null || names.length == 0) {
            names = IntStream.range(1, p+1).mapToObj(i -> "V"+i).toArray(String[]::new);
        }

        FloatVector[] columns = new FloatVector[p];
        for (int j = 0; j < p; j++) {
            float[] x = new float[data.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = data[i][j];
            }
            columns[j] = new FloatVector(names[j], x);
        }
        return new DataFrame(columns);
    }

    /**
     * Creates a DataFrame from a 2-dimensional array.
     * @param data the data array.
     * @param names the name of columns.
     * @return the data frame.
     */
    public static DataFrame of(int[][] data, String... names) {
        int p = data[0].length;
        if (names == null || names.length == 0) {
            names = IntStream.range(1, p+1).mapToObj(i -> "V"+i).toArray(String[]::new);
        }

        IntVector[] columns = new IntVector[p];
        for (int j = 0; j < p; j++) {
            int[] x = new int[data.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = data[i][j];
            }
            columns[j] = new IntVector(names[j], x);
        }
        return new DataFrame(columns);
    }

    /**
     * Creates a DataFrame from a collection of objects.
     * @param data The data collection.
     * @param clazz The class type of elements.
     * @param <T> The data type of elements.
     * @return the data frame.
     */
    public static <T> DataFrame of(Class<T> clazz, List<T> data) {
        try {
            int n = data.size();
            Property[] props = Property.of(clazz);
            List<ValueVector> columns = new ArrayList<>();
            for (var prop : props) {
                StructField field = prop.field();
                Method read = prop.accessor();
                Class<?> type = prop.type();

                int i = 0;
                if (type == int.class) {
                    int[] values = new int[n];
                    for (T datum : data) values[i++] = (int) read.invoke(datum);
                    IntVector vector = new IntVector(field, values);
                    columns.add(vector);
                } else if (type == double.class) {
                    double[] values = new double[n];
                    for (T datum : data) values[i++] = (double) read.invoke(datum);
                    DoubleVector vector = new DoubleVector(field, values);
                    columns.add(vector);
                } else if (type == boolean.class) {
                    boolean[] values = new boolean[n];
                    for (T datum : data) values[i++] = (boolean) read.invoke(datum);
                    BooleanVector vector = new BooleanVector(field, values);
                    columns.add(vector);
                } else if (type == short.class) {
                    short[] values = new short[n];
                    for (T datum : data) values[i++] = (short) read.invoke(datum);
                    ShortVector vector = new ShortVector(field, values);
                    columns.add(vector);
                } else if (type == long.class) {
                    long[] values = new long[n];
                    for (T datum : data) values[i++] = (long) read.invoke(datum);
                    LongVector vector = new LongVector(field, values);
                    columns.add(vector);
                } else if (type == float.class) {
                    float[] values = new float[n];
                    for (T datum : data) values[i++] = (float) read.invoke(datum);
                    FloatVector vector = new FloatVector(field, values);
                    columns.add(vector);
                } else if (type == byte.class) {
                    byte[] values = new byte[n];
                    for (T datum : data) values[i++] = (byte) read.invoke(datum);
                    ByteVector vector = new ByteVector(field, values);
                    columns.add(vector);
                } else if (type == char.class) {
                    char[] values = new char[n];
                    for (T datum : data) values[i++] = (char) read.invoke(datum);
                    CharVector vector = new CharVector(field, values);
                    columns.add(vector);
                } else if (type == String.class) {
                    String[] values = new String[n];
                    for (T datum : data) values[i++] = (String) read.invoke(datum);
                    StringVector vector = new StringVector(field, values);
                    columns.add(vector);
                } else if (type.isEnum()) {
                    Object[] levels = type.getEnumConstants();
                    if (levels.length < Byte.MAX_VALUE + 1) {
                        byte[] values = new byte[n];
                        for (T datum : data) values[i++] = (byte) ((Enum<?>) read.invoke(datum)).ordinal();
                        ByteVector vector = new ByteVector(field, values);
                        columns.add(vector);
                    } else if (levels.length < Short.MAX_VALUE + 1) {
                        short[] values = new short[n];
                        for (T datum : data) values[i++] = (short) ((Enum<?>) read.invoke(datum)).ordinal();
                        ShortVector vector = new ShortVector(field, values);
                        columns.add(vector);
                    } else {
                        int[] values = new int[n];
                        for (T datum : data) values[i++] = ((Enum<?>) read.invoke(datum)).ordinal();
                        IntVector vector = new IntVector(field, values);
                        columns.add(vector);
                    }
                } else if (Number.class.isAssignableFrom(type)) {
                    Number[] values = new Number[n];
                    for (T datum : data) values[i++] = (Number) read.invoke(datum);
                    NumberVector<?> vector = new NumberVector<>(field, values);
                    columns.add(vector);
                } else {
                    Object[] values = new Object[n];
                    for (T datum : data) values[i++] = read.invoke(datum);
                    ObjectVector<?> vector = new ObjectVector<>(field, values);
                    columns.add(vector);
                }
            }

            return new DataFrame(StructType.of(props), columns, null);
        } catch (java.beans.IntrospectionException ex) {
            logger.error("Failed to introspect a bean: ", ex);
            throw new RuntimeException(ex);
        } catch (ReflectiveOperationException ex) {
            logger.error("Failed to call property read method: ", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * Creates a DataFrame from a stream of tuples.
     * @param schema the schema of data frame.
     * @param data the data stream.
     * @return the data frame.
     */
    public static DataFrame of(StructType schema, Stream<? extends Tuple> data) {
        return DataFrame.of(schema, data.toList());
    }

    /**
     * Creates a DataFrame from a set of tuples.
     * @param data The data collection.
     * @param schema The schema of tuple.
     * @return the data frame.
     */
    public static DataFrame of(StructType schema, List<? extends Tuple> data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty tuple collections");
        }

        int n = data.size();
        var fields = schema.fields();
        List<ValueVector> columns = new ArrayList<>(fields.size());

        for (int j = 0; j < fields.size(); j++) {
            BitSet nullMask = new BitSet(n);
            StructField field = fields.get(j);
            columns.add(switch (field.dtype().id()) {
                case Int -> {
                    int[] values = new int[n];
                    for (int i = 0; i < n; i++) {
                        Tuple datum = data.get(i);
                        if (datum.isNullAt(j)) {
                            nullMask.set(i);
                            values[i] = Integer.MIN_VALUE;
                        } else {
                            values[i] = datum.getInt(j);
                        }
                    }
                    yield field.dtype().isNullable() ? new NullableIntVector(field, values, nullMask) : new IntVector(field, values);
                }

                case Long -> {
                    long[] values = new long[n];
                    for (int i = 0; i < n; i++) {
                        Tuple datum = data.get(i);
                        if (datum.isNullAt(j)) {
                            nullMask.set(i);
                            values[i] = Long.MIN_VALUE;
                        } else {
                            values[i] = datum.getLong(j);
                        }
                    }
                    yield field.dtype().isNullable() ? new NullableLongVector(field, values, nullMask) : new LongVector(field, values);
                }

                case Double -> {
                    double[] values = new double[n];
                    for (int i = 0; i < n; i++) {
                        Tuple datum = data.get(i);
                        if (datum.isNullAt(j)) {
                            nullMask.set(i);
                            values[i] = Double.NaN;
                        } else {
                            values[i] = datum.getDouble(j);
                        }
                    }
                    yield field.dtype().isNullable() ? new NullableDoubleVector(field, values, nullMask) : new DoubleVector(field, values);
                }

                case Float -> {
                    float[] values = new float[n];
                    for (int i = 0; i < n; i++) {
                        Tuple datum = data.get(i);
                        if (datum.isNullAt(j)) {
                            nullMask.set(i);
                            values[i] = Float.NaN;
                        } else {
                            values[i] = datum.getFloat(j);
                        }
                    }
                    yield field.dtype().isNullable() ? new NullableFloatVector(field, values, nullMask) : new FloatVector(field, values);
                }

                case Boolean -> {
                    boolean[] values = new boolean[n];
                    for (int i = 0; i < n; i++) {
                        Tuple datum = data.get(i);
                        if (datum.isNullAt(j)) {
                            nullMask.set(i);
                        } else {
                            values[i] = datum.getBoolean(j);
                        }
                    }
                    yield field.dtype().isNullable() ? new NullableBooleanVector(field, values, nullMask) : new BooleanVector(field, values);
                }

                case Byte -> {
                    byte[] values = new byte[n];
                    for (int i = 0; i < n; i++) {
                        Tuple datum = data.get(i);
                        if (datum.isNullAt(j)) {
                            nullMask.set(i);
                            values[i] = Byte.MIN_VALUE;
                        } else {
                            values[i] = datum.getByte(j);
                        }
                    }
                    yield field.dtype().isNullable() ? new NullableByteVector(field, values, nullMask) : new ByteVector(field, values);
                }

                case Short -> {
                    short[] values = new short[n];
                    for (int i = 0; i < n; i++) {
                        Tuple datum = data.get(i);
                        if (datum.isNullAt(j)) {
                            nullMask.set(i);
                            values[i] = Short.MIN_VALUE;
                        } else {
                            values[i] = datum.getShort(j);
                        }
                    }
                    yield field.dtype().isNullable() ? new NullableShortVector(field, values, nullMask) : new ShortVector(field, values);
                }

                case Char -> {
                    char[] values = new char[n];
                    for (int i = 0; i < n; i++) {
                        Tuple datum = data.get(i);
                        if (datum.isNullAt(j)) {
                            nullMask.set(i);
                            values[i] = 0;
                        } else {
                            values[i] = datum.getChar(j);
                        }
                    }
                    yield field.dtype().isNullable() ? new NullableCharVector(field, values, nullMask) : new CharVector(field, values);
                }

                case String -> {
                    String[] values = new String[n];
                    for (int i = 0; i < n; i++) {
                        values[i] = data.get(i).getString(j);
                    }
                    yield new StringVector(field, values);
                }

                case Decimal -> {
                    BigDecimal[] values = new BigDecimal[n];
                    for (int i = 0; i < n; i++) {
                        values[i] = (BigDecimal) data.get(i).get(j);
                    }
                    yield new NumberVector<>(field, values);
                }

                default -> {
                    Object[] values = new Object[n];
                    for (int i = 0; i < n; i++) {
                        values[i] = data.get(i).get(j);
                    }
                    yield new ObjectVector<>(field, values);
                }
            });
        }

        return new DataFrame(schema, columns, null);
    }

    /**
     * Creates a DataFrame from a JDBC ResultSet.
     * @param rs The JDBC result set.
     * @throws SQLException when JDBC operation fails.
     * @return the data frame.
     */
    public static DataFrame of(ResultSet rs) throws SQLException {
        StructType schema = StructType.of(rs);
        List<Tuple> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(Tuple.of(schema, rs));
        }

        return DataFrame.of(schema, rows);
    }
}
