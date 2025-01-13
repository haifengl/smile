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
package smile.data;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import smile.data.measure.CategoricalMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.*;
import smile.data.vector.*;
import smile.math.matrix.Matrix;
import smile.util.Index;
import smile.util.Strings;

/**
 * Two-dimensional, potentially heterogeneous tabular data.
 *
 * @param schema the schema of DataFrame.
 * @param columns the columns of DataFrame.
 *
 * @author Haifeng Li
 */
public record DataFrame(StructType schema, ValueVector[] columns) implements Iterable<Row> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DataFrame.class);

    /**
     * Constructor.
     * @param columns the columns of DataFrame.
     */
    public DataFrame(ValueVector... columns) {
        this(StructType.of(columns), columns);
    }

    @Override
    public String toString() {
        return toString(10, true);
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
            case 0 -> columns[0].size();
            case 1 -> columns.length;
            default -> throw new IllegalArgumentException("Invalid dim: " + dim);
        };
    }

    /**
     * Returns the number of rows.
     * This is an alias to {@link #nrow() nrow} for Java's convention.
     * @return the number of rows.
     */
    public int size() {
        return columns[0].size();
    }

    /**
     * Returns the number of rows.
     * @return the number of rows.
     */
    public int nrow() {
        return columns[0].size();
    }

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    public int ncol() {
        return columns.length;
    }

    /**
     * Returns true if the data frame is empty.
     * @return true if the data frame is empty.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the j-th column.
     * @param j the column index.
     * @return the column vector.
     */
    public ValueVector column(int j) {
        return columns[j];
    }

    /**
     * Returns the column of given name.
     * @param name the column name.
     * @return the column vector.
     */
    public ValueVector column(String name) {
        return columns[schema.indexOf(name)];
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
     * Returns a new data frame with row indexing.
     * @param index the row indices.
     * @return the data frame of selected rows.
     */
    public DataFrame get(Index index) {
        return new DataFrame(schema, Arrays.stream(columns)
                .map(column -> column.get(index))
                .toArray(ValueVector[]::new));
    }

    /**
     * Returns a new data frame with row indexing.
     * This is an alias to {@link #get(Index) get} for Scala's convenience.
     * @param index the row indices.
     * @return the data frame of selected rows.
     */
    public DataFrame apply(Index index) {
        return get(index);
    }

    /**
     * Checks whether the value at position (i, j) is null.
     * @param i the row index.
     * @param j the column index.
     * @return true if the cell value is null.
     */
    public boolean isNullAt(int i, int j) {
        return columns[j].isNullAt(i);
    }

    /**
     * Returns the cell at (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the cell value.
     */
    public Object get(int i, int j) {
        return columns[j].get(i);
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
        return columns[j].getInt(i);
    }

    /**
     * Returns the long value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the long value of cell.
     */
    public long getLong(int i, int j) {
        return columns[j].getLong(i);
    }

    /**
     * Returns the float value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the float value of cell.
     */
    public float getFloat(int i, int j) {
        return columns[j].getFloat(i);
    }

    /**
     * Returns the double value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the double value of cell.
     */
    public double getDouble(int i, int j) {
        return columns[j].getDouble(i);
    }

    /**
     * Returns the string representation of the value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the string representation of cell value.
     */
    public String getString(int i, int j) {
        return columns[j].getString(i);
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
        return columns[j].getScale(i);
    }

    /**
     * Sets the value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @param value the new value.
     */
    public void set(int i, int j, Object value) {
        columns[j].set(i, value);
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
    @javax.annotation.Nonnull
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
     * Returns the structure of data frame.
     * @return the structure of data frame.
     */
    public DataFrame structure() {
        ValueVector[] vectors = {
                new StringVector("Column", names()),
                new ObjectVector<>("Type", dtypes()),
                new ObjectVector<>("Measure", measures())
        };

        return new DataFrame(vectors);
    }

    /**
     * Returns a new data frame without rows that have null/missing values.
     * @return the data frame without nulls.
     */
    public DataFrame omitNullRows() {
        boolean[] noNulls = new boolean[size()];
        for (int i = 0; i < noNulls.length; i++) {
            noNulls[i] = !get(i).anyNull();
        }
        return get(Index.of(noNulls));
    }

    /**
     * Fills NaN/Inf values of floating number columns using the specified value.
     * @param value the value to replace NAs.
     * @return this data frame.
     */
    public DataFrame fillna(double value) {
        for (var column : columns) {
            if (column instanceof FloatVector vector) {
                vector.fillna((float) value);
            } else if (column instanceof DoubleVector vector) {
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
        return new DataFrame(Arrays.stream(indices)
                .mapToObj(j -> columns[j])
                .toArray(ValueVector[]::new));
    }

    /**
     * Returns a new DataFrame with selected columns.
     * @param names the column names.
     * @return a new DataFrame with selected columns.
     */
    public DataFrame select(String... names) {
        return new DataFrame(Arrays.stream(names)
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

        return new DataFrame(IntStream.range(0, columns.length)
                .filter(j -> !set.contains(j))
                .mapToObj(j -> columns[j])
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

        return new DataFrame(Arrays.stream(columns)
                .filter(column -> !set.contains(column.name()))
                .toArray(ValueVector[]::new));
    }

    /**
     * Merges data frames horizontally by columns.
     * @param dataframes the data frames to merge.
     * @return a new data frame that combines this DataFrame
     * with one more other DataFrames by columns.
     */
    public DataFrame merge(DataFrame... dataframes) {
        for (DataFrame df : dataframes) {
            if (df.size() != size()) {
                throw new IllegalArgumentException("Merge data frames with different size: " + size() + " vs " + df.size());
            }
        }

        List<ValueVector> all = new ArrayList<>();
        Collections.addAll(all, columns);
        for (DataFrame df : dataframes) {
            Collections.addAll(all, df.columns);
        }

        return new DataFrame(all.toArray(ValueVector[]::new));
    }

    /**
     * Merges vectors with this data frame.
     * @param vectors the vectors to merge.
     * @return a new data frame that combines this DataFrame
     * with one more additional vectors.
     */
    public DataFrame merge(ValueVector... vectors) {
        for (var vector : vectors) {
            if (vector.size() != size()) {
                throw new IllegalArgumentException("Merge ValueVectors with different size: " + size() + " vs " + vector.size());
            }
        }

        List<ValueVector> all = new ArrayList<>();
        Collections.addAll(all, columns);
        Collections.addAll(all, vectors);

        return new DataFrame(all.toArray(ValueVector[]::new));

    }

    /**
     * Unions data frames vertically by rows.
     * @param dataframes the data frames to union.
     * @return a new data frame that combines all the rows.
     */
    public DataFrame union(DataFrame... dataframes) {
        for (var df : dataframes) {
            if (!schema.equals(df.schema())) {
                throw new IllegalArgumentException("Union data frames with different schema: " + schema + " vs " + df.schema());
            }
        }

        var rows = Stream.concat(Stream.of(this), Stream.of(dataframes))
                .flatMap(DataFrame::stream);
        return DataFrame.of(schema, rows);
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
            names = Arrays.stream(schema().fields())
                    .filter(field -> field.dtype().isObject())
                    .map(StructField::name)
                    .toArray(String[]::new);
        }

        int n = size();
        HashSet<String> set = new HashSet<>();
        Collections.addAll(set, names);

        ValueVector[] vectors = Arrays.stream(columns).map(column -> {
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

        return new DataFrame(vectors);
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
    public Matrix toMatrix() {
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
    public Matrix toMatrix(boolean bias, CategoricalEncoder encoder, String rowNames) {
        int nrow = size();
        int ncol = columns.length;

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

        Matrix matrix = new Matrix(nrow, colNames.size());
        matrix.colNames(colNames.toArray(new String[0]));
        if (rowNames != null) {
            int j = schema.indexOf(rowNames);
            String[] rows = new String[nrow];
            for (int i = 0; i < nrow; i++) {
                rows[i] = getString(i, j);
            }
            matrix.rowNames(rows);
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
     * Returns the statistic summary of numeric columns.
     * @return the statistic summary of numeric columns.
     */
    public DataFrame summary() {
        int ncol = columns.length;
        String[] names = names();
        DataType[] dtypes = dtypes();
        Measure[] measures = measures();
        String[] col = new String[ncol];
        double[] min = new double[ncol];
        double[] max = new double[ncol];
        double[] avg = new double[ncol];
        long[] count = new long[ncol];

        int k = 0;
        for (int j = 0; j < ncol; j++) {
            if (measures[j] instanceof CategoricalMeasure) continue;

            DataType dtype = dtypes[j];
            if (dtype.isLong()) {
                LongSummaryStatistics s = columns[j].asLongStream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            } else if (dtype.isIntegral()) {
                IntSummaryStatistics s = columns[j].asIntStream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            } else if (dtype.isFloating()) {
                DoubleSummaryStatistics s = columns[j].asDoubleStream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            }
        }

        return new DataFrame(
                new StringVector("column", Arrays.copyOf(col, k)),
                new LongVector("count", Arrays.copyOf(count, k)),
                new DoubleVector("min", Arrays.copyOf(min, k)),
                new DoubleVector("avg", Arrays.copyOf(avg, k)),
                new DoubleVector("max", Arrays.copyOf(max, k))
        );
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows the number of rows to show
     * @return the string representation of top rows.
     */
    public String toString(int numRows) {
        return toString(numRows, true);
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     * @param truncate Whether truncate long strings and align cells right.
     * @return the string representation of top rows.
     */
    public String toString(final int numRows, final boolean truncate) {
        StringBuilder sb = new StringBuilder();
        boolean hasMoreData = size() > numRows;
        String[] names = names();
        int numCols = names.length;
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
        List<String[]> rows = stream().limit(numRows).map( row -> {
            String[] cells = new String[numCols];
            for (int i = 0; i < numCols; i++) {
                String str = row.toString(i);
                cells[i] = (truncate && str.length() > maxColWidth) ? str.substring(0, maxColWidth - 3) + "..." : str;
            }
            return cells;
        }).toList();

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
        StringBuilder header = new StringBuilder();
        header.append('|');
        for (int i = 0; i < numCols; i++) {
            if (truncate) {
                header.append(Strings.leftPad(names[i], colWidths[i], ' '));
            } else {
                header.append(Strings.rightPad(names[i], colWidths[i], ' '));
            }
            header.append('|');
        }
        header.append('\n');
        sb.append(header);
        sb.append(sep);

        // data
        for (String[] row : rows) {
            StringBuilder line = new StringBuilder();
            line.append('|');
            for (int i = 0; i < numCols; i++) {
                if (truncate) {
                    line.append(Strings.leftPad(row[i], colWidths[i], ' '));
                } else {
                    line.append(Strings.rightPad(row[i], colWidths[i], ' '));
                }
                line.append('|');
            }
            line.append('\n');
            sb.append(line);
        }

        sb.append(sep);

        // For Data that has more than "numRows" records
        if (hasMoreData) {
            int rest = size() - numRows;
            if (rest > 0) {
                String rowsString = (rest == 1) ? "row" : "rows";
                sb.append(String.format("%d more %s...\n", rest, rowsString));
            }
        }

        return sb.toString();
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     * @return the string representation of top rows.
     */
    public String[][] toStrings(int numRows) {
        return toStrings(numRows, true);
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     * @param truncate Whether truncate long strings.
     * @return the string representation of top rows.
     */
    public String[][] toStrings(final int numRows, final boolean truncate) {
        String[] names = names();
        int numCols = names.length;
        int maxColWidth = numCols == 1 ? 78 : (numCols == 2 ? 38 : 20);

        // For array values, replace Seq and Array with square brackets
        // For cells that are beyond maxColumnWidth characters, truncate it with "..."
        return stream().limit(numRows).map( row -> {
            String[] cells = new String[numCols];
            for (int i = 0; i < numCols; i++) {
                String str = row.toString(i);
                cells[i] = (truncate && str.length() > maxColWidth) ? str.substring(0, maxColWidth - 3) + "..." : str;
            }
            return cells;
        }).toArray(String[][]::new);
    }

    /**
     * Creates a DataFrame from a 2-dimensional array.
     * @param data The data array.
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
     * @param data The data array.
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
     * @param data The data array.
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
     * Creates a DataFrame from a collection.
     * @param data The data collection.
     * @param clazz The class type of elements.
     * @param <T> The data type of elements.
     * @return the data frame.
     */
    public static <T> DataFrame of(Class<T> clazz, List<T> data) {
        try {
            BeanInfo info = Introspector.getBeanInfo(clazz);
            PropertyDescriptor[] props = info.getPropertyDescriptors();
            StructField[] fields = Arrays.stream(props)
                    .filter(prop -> !prop.getName().equals("class"))
                    .map(StructField::of)
                    .toArray(StructField[]::new);

            int n = data.size();
            var schema = new StructType(fields);
            List<ValueVector> columns = new ArrayList<>();

            for (PropertyDescriptor prop : props) {
                if (prop.getName().equals("class")) continue;

                String name = prop.getName();
                Class<?> type = prop.getPropertyType();
                Method read = prop.getReadMethod();
                StructField field = Arrays.stream(fields)
                        .filter(f -> f.name().equals(name))
                        .findFirst().orElseThrow(NoSuchElementException::new);

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

            return new DataFrame(schema, columns.toArray(ValueVector[]::new));
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
     * @param data The data stream.
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
        StructField[] fields = schema.fields();
        ValueVector[] columns = new ValueVector[fields.length];

        for (int j = 0; j < fields.length; j++) {
            BitSet nullMask = new BitSet(n);
            StructField field = fields[j];
            columns[j] = switch (field.dtype().id()) {
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
            };
        }

        return new DataFrame(schema, columns);
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
