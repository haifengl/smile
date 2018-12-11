/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.data.measure.Measure;
import smile.data.type.*;
import smile.data.vector.*;
import smile.data.vector.Vector;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.util.Strings;

/**
 * An immutable collection of data organized into named columns.
 *
 * @author Haifeng Li
 */
public interface DataFrame extends Dataset<Tuple> {
    /** Returns the schema of DataFrame. */
    StructType schema();

    /** Returns the column names. */
    default String[] names() {
        StructField[] fields = schema().fields();
        return Arrays.stream(fields)
                .map(field -> field.name)
                .collect(Collectors.toList())
                .toArray(new String[fields.length]);
    }

    /** Returns the column types. */
    default DataType[] types() {
        StructField[] fields = schema().fields();
        return Arrays.stream(fields)
                .map(field -> field.type)
                .collect(Collectors.toList())
                .toArray(new DataType[fields.length]);
    }

    /**
     * Returns the number of rows.
     */
    default int nrows() {
        return size();
    }

    /**
     * Returns the number of columns.
     */
    int ncols();

    /** Returns the structure of data frame. */
    default DataFrame structure() {
        if (schema().measure().isEmpty()) {
            List<BaseVector> vectors = Arrays.asList(
                    Vector.of("Column", String.class, names()),
                    Vector.of("Type", DataType.class, types())
            );

            return new DataFrameImpl(vectors);

        } else {
            Measure[] measures = new Measure[ncols()];
            for (Map.Entry<String, Measure> e : schema().measure().entrySet()) {
                measures[columnIndex(e.getKey())] = e.getValue();
            }

            List<BaseVector> vectors = Arrays.asList(
                    Vector.of("Column", String.class, names()),
                    Vector.of("Type", DataType.class, types()),
                    Vector.of("Measure", Measure.class, measures)
            );

            return new DataFrameImpl(vectors);
        }
    }

    /** Returns the cell at (i, j). */
    default Object get(int i, int j) {
        return get(i).get(j);
    }

    /**
     * Returns the index of a given column name.
     * @throws IllegalArgumentException when a field `name` does not exist.
     */
    int columnIndex(String name);

    /** Selects column based on the column name and return it as a Column. */
    default BaseVector apply(String colName) {
        return column(colName);
    }

    /** Selects column using an enum value. */
    default BaseVector apply(Enum<?> e) {
        return column(e.toString());
    }

    /** Selects column based on the column index. */
    BaseVector column(int i);

    /** Selects column based on the column name. */
    default BaseVector column(String colName) {
        return column(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default BaseVector column(Enum<?> e) {
        return column(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    <T> Vector<T> vector(int i);

    /** Selects column based on the column name. */
    default <T> Vector<T> vector(String colName) {
        return vector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default <T> Vector<T> vector(Enum<?> e) {
        return vector(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    BooleanVector booleanVector(int i);

    /** Selects column based on the column name. */
    default BooleanVector booleanVector(String colName) {
        return booleanVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default BooleanVector booleanVector(Enum<?> e) {
        return booleanVector(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    CharVector charVector(int i);

    /** Selects column based on the column name. */
    default CharVector charVector(String colName) {
        return charVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default CharVector charVector(Enum<?> e) {
        return charVector(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    ByteVector byteVector(int i);

    /** Selects column based on the column name. */
    default ByteVector byteVector(String colName) {
        return byteVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default ByteVector byteVector(Enum<?> e) {
        return byteVector(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    ShortVector shortVector(int i);

    /** Selects column based on the column name. */
    default ShortVector shortVector(String colName) {
        return shortVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default ShortVector shortVector(Enum<?> e) {
        return shortVector(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    IntVector intVector(int i);

    /** Selects column based on the column name. */
    default IntVector intVector(String colName) {
        return intVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default IntVector intVector(Enum<?> e) {
        return intVector(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    LongVector longVector(int i);

    /** Selects column based on the column name. */
    default LongVector longVector(String colName) {
        return longVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default LongVector longVector(Enum<?> e) {
        return longVector(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    FloatVector floatVector(int i);

    /** Selects column based on the column name. */
    default FloatVector floatVector(String colName) {
        return floatVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default FloatVector floatVector(Enum<?> e) {
        return floatVector(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    DoubleVector doubleVector(int i);

    /** Selects column based on the column name. */
    default DoubleVector doubleVector(String colName) {
        return doubleVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default DoubleVector doubleVector(Enum<?> e) {
        return doubleVector(columnIndex(e.toString()));
    }

    /** Selects a new DataFrame with given column indices. */
    DataFrame select(int... cols);

    /** Selects a new DataFrame with given column names. */
    default DataFrame select(String... cols) {
        int[] indices = Arrays.asList(cols).stream().mapToInt(this::columnIndex).toArray();
        return select(indices);
    }

    /** Returns a new DataFrame without given column indices. */
    DataFrame drop(int... cols);

    /**
     * Merges data frames horizontally by columns.
     * @return a new data frame that combines this DataFrame
     * with one more more other DataFrames by columns.
     */
    DataFrame merge(DataFrame... dataframes);

    /**
     * Merges data frames horizontally by columns.
     * @return a new data frame that combines this DataFrame
     * with one more more additional vectors.
     */
    DataFrame merge(BaseVector... vectors);

    /**
     * Merges data frames vertically by rows.
     * @return a new data frame that combines all the rows.
     */
    DataFrame union(DataFrame... dataframes);

    /** Returns a new DataFrame without given column names. */
    default DataFrame drop(String... cols) {
        int[] indices = Arrays.asList(cols).stream().mapToInt(this::columnIndex).toArray();
        return drop(indices);
    }

    /**
     * Creates a default columnar implementation of DataFrame by a formula.
     * @param formula The formula that transforms this DataFrame.
     */
    default DataFrame apply(smile.data.formula.Formula formula) {
        return map(formula);
    }

    /**
     * Creates a default columnar implementation of DataFrame by a formula.
     * @param formula The formula that transforms this DataFrame.
     */
    default DataFrame map(smile.data.formula.Formula formula) {
        return new DataFrameImpl(this, formula);
    }

    /**
     * Return the matrix obtained by converting all the variables
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Nominal and ordinal variables are
     * replaced by their internal codes. Missing values/nulls will be
     * encoded as Double.NaN.
     */
    DenseMatrix toMatrix();

    /** Returns the statistic summary of numeric columns. */
    default DataFrame summary() {
        int ncols = ncols();
        String[] names = names();
        DataType[] types = types();
        String[] col = new String[ncols];
        double[] min = new double[ncols];
        double[] max = new double[ncols];
        double[] avg = new double[ncols];
        long[] count = new long[ncols];

        int k = 0;
        for (int j = 0; j < ncols; j++) {
            DataType type = types[j];
            if (type.isInt()) {
                IntSummaryStatistics s = type.isObject() ?
                        this.<Integer>vector(j).stream().filter(Objects::nonNull).mapToInt(Integer::intValue).summaryStatistics() :
                        intVector(j).stream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            } else if (type.isLong()) {
                LongSummaryStatistics s = type.isObject() ?
                        this.<Long>vector(j).stream().filter(Objects::nonNull).mapToLong(Long::longValue).summaryStatistics() :
                        longVector(j).stream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            } else if (type.isFloat()) {
                DoubleSummaryStatistics s = type.isObject() ?
                        this.<Float>vector(j).stream().filter(Objects::nonNull).mapToDouble(Float::doubleValue).summaryStatistics() :
                        floatVector(j).stream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            } else if (type.isDouble()) {
                DoubleSummaryStatistics s = type.isObject() ?
                        this.<Double>vector(j).stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).summaryStatistics() :
                        doubleVector(j).stream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            } else if (type.isByte()) {
                IntSummaryStatistics s = type.isObject() ?
                        this.<Byte>vector(j).stream().filter(Objects::nonNull).mapToInt(Byte::intValue).summaryStatistics() :
                        byteVector(j).stream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            } else if (type.isShort()) {
                IntSummaryStatistics s = type.isObject() ?
                        this.<Short>vector(j).stream().filter(Objects::nonNull).mapToInt(Short::intValue).summaryStatistics() :
                        shortVector(j).stream().summaryStatistics();
                col[k] = names[j];
                min[k] = s.getMin();
                max[k] = s.getMax();
                avg[k] = s.getAverage();
                count[k++] = s.getCount();
            }
        }

        return DataFrame.of(
                Vector.of("column", String.class, Arrays.copyOf(col, k)),
                LongVector.of("count", Arrays.copyOf(count, k)),
                DoubleVector.of("min", Arrays.copyOf(min, k)),
                DoubleVector.of("avg", Arrays.copyOf(avg, k)),
                DoubleVector.of("max", Arrays.copyOf(max, k))
        );
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     */
    default String toString(int numRows) {
        return toString(numRows, true);
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     * @param truncate Whether truncate long strings and align cells right.
     */
    default String toString(final int numRows, final boolean truncate) {
        StringBuilder sb = new StringBuilder();
        boolean hasMoreData = size() > numRows;
        String[] names = names();
        int numCols = names.length;
        int maxColWidth = 20;
        switch (numCols) {
            case 1: maxColWidth = 78; break;
            case 2: maxColWidth = 38; break;
            default: maxColWidth = 20;
        }
        // To be used in lambda.
        final int maxColumnWidth = maxColWidth;

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
                cells[i] = (truncate && str.length() > maxColumnWidth) ? str.substring(0, maxColumnWidth - 3) + "..." : str;
            }
            return cells;
        }).collect(Collectors.toList());

        // Compute the width of each column
        for (String[] row : rows) {
            for (int i = 0; i < numCols; i++) {
                colWidths[i] = Math.max(colWidths[i], row[i].length());
            }
        }

        // Create SeparateLine
        String sep = IntStream.of(colWidths).mapToObj(w -> Strings.fill('-', w)).collect(Collectors.joining("+", "+", "+\n"));
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
        sb.append(header.toString());
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
            sb.append(line.toString());
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
     * Creates a DataFrame from a set of vectors.
     * @param vectors The column vectors.
     */
    static DataFrame of(BaseVector... vectors) {
        return new DataFrameImpl(Arrays.asList(vectors));
    }

    /**
     * Creates a DataFrame from a collection.
     * @param data The data collection.
     * @param clazz The class type of elements.
     * @param <T> The type of elements.
     */
    static <T> DataFrame of(List<T> data, Class<T> clazz) {
        return new DataFrameImpl(data, clazz);
    }

    /**
     * Creates a DataFrame from a set of tuples.
     * @param data The data collection.
     */
    static DataFrame of(List<Tuple> data) {
        return new DataFrameImpl(data);
    }

    /**
     * Creates a DataFrame from a JDBC ResultSet.
     * @param rs The JDBC result set.
     */
    static DataFrame of(ResultSet rs) throws SQLException {
        StructType schema = DataTypes.struct(rs);
        List<Tuple> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(Tuple.of(rs, schema));
        }

        return of(rows);
    }

    /**
     * Returns a stream collector that accumulates objects into a DataFrame.
     *
     * @param <T> the type of input elements to the reduction operation
     * @param clazz The class type of elements.
     */
    static <T> Collector<T, List<T>, DataFrame> collect(Class<T> clazz) {
        return Collector.of(
                // supplier
                () -> new ArrayList<T>(),
                // accumulator
                (container, t) -> container.add(t),
                // combiner
                (c1, c2) -> { c1.addAll(c2); return c1; },
                // finisher
                (container) -> DataFrame.of(container, clazz)
        );
    }

    /**
     * Returns a stream collector that accumulates tuples into a DataFrame.
     */
    static Collector<Tuple, List<Tuple>, DataFrame> collect() {
        return Collector.of(
                // supplier
                () -> new ArrayList<Tuple>(),
                // accumulator
                (container, t) -> container.add(t),
                // combiner
                (c1, c2) -> { c1.addAll(c2); return c1; },
                // finisher
                (container) -> DataFrame.of(container)
        );
    }

    /**
     * Returns a stream collector that accumulates tuples into a Matrix.
     */
    static Collector<Tuple, List<Tuple>, DenseMatrix> collectMatrix() {
        return Collector.of(
                // supplier
                () -> new ArrayList<Tuple>(),
                // accumulator
                (container, t) -> container.add(t),
                // combiner
                (c1, c2) -> { c1.addAll(c2); return c1; },
                // finisher
                (container) -> {
                    if (container.isEmpty()) {
                        throw new IllegalArgumentException("Empty list of tuples");
                    }
                    int nrows = container.size();
                    int ncols = container.get(0).length();
                    DenseMatrix m = Matrix.of(nrows, ncols, 0);
                    for (int i = 0; i < nrows; i++) {
                        for (int j = 0; j < ncols; j++) {
                            m.set(i, j, container.get(i).getDouble(j));
                        }
                    }
                    return m;
                }
        );
    }
}
