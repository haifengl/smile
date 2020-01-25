/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import smile.data.measure.DiscreteMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
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
public interface DataFrame extends Dataset<Tuple>, Iterable<BaseVector> {
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


    /** Returns the column measures. */
    default Measure[] measures() {
        StructField[] fields = schema().fields();
        return Arrays.stream(fields)
                .map(field -> field.measure)
                .toArray(Measure[]::new);
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
        List<BaseVector> vectors = Arrays.asList(
                Vector.of("Column", String.class, names()),
                Vector.of("Type", DataType.class, types()),
                Vector.of("Measure", Measure.class, measures())
        );

        return new DataFrameImpl(vectors);
    }

    /** Returns a new data frame without rows that have null/missing values. */
    default DataFrame omitNullRows() {
        return DataFrame.of(stream().filter(r -> !r.hasNull()), schema().unboxed());
    }

    /** Returns the cell at (i, j). */
    default Object get(int i, int j) {
        return get(i).get(j);
    }

    /** Returns the cell at (i, j). */
    default Object get(int i, String field) {
        return get(i).get(field);
    }

    /** Returns a new data frame with row indexing. */
    default DataFrame of(int... index) {
        return new IndexDataFrame(this, index);
    }

    /** Returns a new data frame with boolean indexing. */
    default DataFrame of(boolean... index) {
        int[] idx = IntStream.range(0, index.length).filter(i -> index[i]).toArray();
        return new IndexDataFrame(this, idx);
    }

    /**
     * Copies the specified range into a new data frame.
     * @param from the initial index of the range to be copied, inclusive
     * @param to the final index of the range to be copied, exclusive.
     */
    default DataFrame slice(int from, int to) {
        return IntStream.range(from, to).mapToObj(i -> get(i)).collect(collect());
    }

    /** Checks whether the value at position (i, j) is null. */
    default boolean isNullAt(int i, int j) {
        return get(i).isNullAt(j);
    }

    /** Checks whether the field value is null. */
    default boolean isNullAt(int i, String field) {
        return get(i).isNullAt(field);
    }

    /**
     * Returns the value at position (i, j) as a primitive boolean.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default boolean getBoolean(int i, int j) {
        return get(i).getBoolean(j);
    }

    /**
     * Returns the field value as a primitive boolean.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default boolean getBoolean(int i, String field) {
        return get(i).getBoolean(field);
    }

    /**
     * Returns the value at position (i, j) as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default char getChar(int i, int j) {
        return get(i).getChar(j);
    }

    /**
     * Returns the field value as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default char getChar(int i, String field) {
        return get(i).getChar(field);
    }

    /**
     * Returns the value at position (i, j) as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default byte getByte(int i, int j) {
        return get(i).getByte(j);
    }

    /**
     * Returns the field value as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default byte getByte(int i, String field) {
        return get(i).getByte(field);
    }

    /**
     * Returns the value at position (i, j) as a primitive short.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default short getShort(int i, int j) {
        return get(i).getShort(j);
    }

    /**
     * Returns the field value as a primitive short.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default short getShort(int i, String field) {
        return get(i).getShort(field);
    }

    /**
     * Returns the value at position (i, j) as a primitive int.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default int getInt(int i, int j) {
        return get(i).getInt(j);
    }

    /**
     * Returns the field value as a primitive int.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default int getInt(int i, String field) {
        return get(i).getInt(field);
    }

    /**
     * Returns the value at position (i, j) as a primitive long.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default long getLong(int i, int j) {
        return get(i).getLong(j);
    }

    /**
     * Returns the field value as a primitive long.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default long getLong(int i, String field) {
        return get(i).getLong(field);
    }

    /**
     * Returns the value at position (i, j) as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default float getFloat(int i, int j) {
        return get(i).getFloat(j);
    }

    /**
     * Returns the field value as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default float getFloat(int i, String field) {
        return get(i).getFloat(field);
    }

    /**
     * Returns the value at position (i, j) as a primitive double.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default double getDouble(int i, int j) {
        return get(i).getDouble(j);
    }

    /**
     * Returns the field value as a primitive double.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default double getDouble(int i, String field) {
        return get(i).getDouble(field);
    }

    /**
     * Returns the value at position (i, j) as a String object.
     *
     * @throws ClassCastException when data type does not match.
     */
    default String getString(int i, int j) {
        return get(i).getString(j);
    }

    /**
     * Returns the field value as a String object.
     *
     * @throws ClassCastException when data type does not match.
     */
    default String getString(int i, String field) {
        return get(i).getString(field);
    }

    /**
     * Returns the string representation of the value at position (i, j).
     */
    default String toString(int i, int j) {
        Object o = get(i, j);
        if (o == null) return "null";

        if (o instanceof String) {
            return (String) o;
        } else {
            return schema().field(j).toString(o);
        }
    }

    /**
     * Returns the string representation of the field value.
     */
    default String toString(int i, String field) {
        return toString(i, columnIndex(field));
    }

    /**
     * Returns the value at position (i, j) of decimal type as java.math.BigDecimal.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.math.BigDecimal getDecimal(int i, int j) {
        return get(i).getDecimal(j);
    }

    /**
     * Returns the field value of decimal type as java.math.BigDecimal.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.math.BigDecimal getDecimal(int i, String field) {
        return get(i).getDecimal(field);
    }

    /**
     * Returns the value at position (i, j) of date type as java.time.LocalDate.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalDate getDate(int i, int j) {
        return get(i).getDate(j);
    }

    /**
     * Returns the field value of date type as java.time.LocalDate.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalDate getDate(int i, String field) {
        return get(i).getDate(field);
    }

    /**
     * Returns the value at position (i, j) of date type as java.time.LocalTime.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalTime getTime(int i, int j) {
        return get(i).getTime(j);
    }

    /**
     * Returns the field value of date type as java.time.LocalTime.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalTime getTime(int i, String field) {
        return get(i).getTime(field);
    }

    /**
     * Returns the value at position (i, j) as java.time.LocalDateTime.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalDateTime getDateTime(int i, int j) {
        return get(i).getDateTime(j);
    }

    /**
     * Returns the field value as java.time.LocalDateTime.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.time.LocalDateTime getDateTime(int i, String field) {
        return get(i).getDateTime(field);
    }

    /**
     * Returns the value at position (i, j) of NominalScale or OrdinalScale.
     *
     * @throws ClassCastException when the data is not nominal or ordinal.
     */
    default String getScale(int i, int j) {
        int x = getInt(i, j);
        Measure measure = schema().field(j).measure;
        return (measure instanceof DiscreteMeasure) ? ((DiscreteMeasure) measure).toString(x) : String.valueOf(x);
    }

    /**
     * Returns the field value of NominalScale or OrdinalScale.
     *
     * @throws ClassCastException when the data is not nominal or ordinal.
     */
    default String getScale(int i, String field) {
        return getScale(i, columnIndex(field));
    }

    /**
     * Returns the value at position (i, j) of array type.
     *
     * @throws ClassCastException when data type does not match.
     */
    default <T> T[] getArray(int i, int j) {
        return get(i).getArray(j);
    }

    /**
     * Returns the field value of array type.
     *
     * @throws ClassCastException when data type does not match.
     */
    default <T> T[] getArray(int i, String field) {
        return get(i).getArray(field);
    }

    /**
     * Returns the value at position (i, j) of struct type.
     *
     * @throws ClassCastException when data type does not match.
     */
    default Tuple getStruct(int i, int j) {
        return get(i).getStruct(j);
    }

    /**
     * Returns the field value of struct type.
     *
     * @throws ClassCastException when data type does not match.
     */
    default Tuple getStruct(int i, String field) {
        return get(i).getStruct(field);
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

    /** Selects column based on the column index. */
    StringVector stringVector(int i);

    /** Selects column based on the column name. */
    default StringVector stringVector(String colName) {
        return stringVector(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default StringVector stringVector(Enum<?> e) {
        return stringVector(columnIndex(e.toString()));
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
     * Returns a new DataFrame with given string columns converted to nominal.
     *
     * @param cols string column names. If empty, all string columns
     *             in the data frame will be converted.
     * @return a new DataFrame.
     */
    default DataFrame factorize(String... cols) {
        if (cols.length == 0) {
            cols = Arrays.stream(schema().fields())
                    .filter(field -> field.type.isString())
                    .map(field -> field.name)
                    .toArray(String[]::new);
        }

        HashSet<String> set = new HashSet<>(Arrays.asList(cols));
        BaseVector[] vectors = Arrays.stream(names()).map(col -> {
            if (set.contains(col)) {
                StringVector c = stringVector(col);
                NominalScale scale = c.nominal();
                return c.factorize(scale);
            } else return column(col);
        }).toArray(BaseVector[]::new);

        return of(vectors);
    }

    /**
     * Return an array obtained by converting all the variables
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Nominal and ordinal variables are
     * replaced by their internal codes. Missing values/nulls will be
     * encoded as Double.NaN.
     */
    double[][] toArray();

    /**
     * Return a matrix obtained by converting all the variables
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
        Measure[] measures = measures();
        String[] col = new String[ncols];
        double[] min = new double[ncols];
        double[] max = new double[ncols];
        double[] avg = new double[ncols];
        long[] count = new long[ncols];

        int k = 0;
        for (int j = 0; j < ncols; j++) {
            if (measures[j] != null && measures[j] instanceof DiscreteMeasure) continue;

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

        return new DataFrameImpl(
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
        StringBuilder sb = new StringBuilder(schema().toString());
        sb.append('\n');

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
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     */
    default String[][] toStrings(int numRows) {
        return toStrings(numRows, true);
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     * @param truncate Whether truncate long strings.
     */
    default String[][] toStrings(final int numRows, final boolean truncate) {
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
        return stream().limit(numRows).map( row -> {
            String[] cells = new String[numCols];
            for (int i = 0; i < numCols; i++) {
                String str = row.toString(i);
                cells[i] = (truncate && str.length() > maxColumnWidth) ? str.substring(0, maxColumnWidth - 3) + "..." : str;
            }
            return cells;
        }).toArray(String[][]::new);
    }

    /**
     * Creates a DataFrame from a set of vectors.
     * @param vectors The column vectors.
     */
    static DataFrame of(BaseVector... vectors) {
        return new DataFrameImpl(vectors);
    }

    /**
     * Creates a DataFrame from a 2-dimensional array.
     * @param data The data array.
     * @param names the name of columns.
     */
    static DataFrame of(double[][] data, String... names) {
        int p = data[0].length;
        if (names == null || names.length == 0) {
            names = IntStream.range(1, p+1).mapToObj(i -> "V"+i).toArray(String[]::new);
        }

        DoubleVector[] vectors = new DoubleVector[p];
        for (int j = 0; j < p; j++) {
            double[] x = new double[data.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = data[i][j];
            }
            vectors[j] = DoubleVector.of(names[j], x);
        }
        return DataFrame.of(vectors);
    }

    /**
     * Creates a DataFrame from a 2-dimensional array.
     * @param data The data array.
     * @param names the name of columns.
     */
    static DataFrame of(int[][] data, String... names) {
        int p = data[0].length;
        if (names == null || names.length == 0) {
            names = IntStream.range(1, p+1).mapToObj(i -> "V"+i).toArray(String[]::new);
        }

        IntVector[] vectors = new IntVector[p];
        for (int j = 0; j < p; j++) {
            int[] x = new int[data.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = data[i][j];
            }
            vectors[j] = IntVector.of(names[j], x);
        }
        return DataFrame.of(vectors);
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
     * Creates a DataFrame from a stream of tuples.
     * @param data The data stream.
     */
    static DataFrame of(Stream<Tuple> data) {
        return new DataFrameImpl(data);
    }

    /**
     * Creates a DataFrame from a stream of tuples.
     * @param data The data stream.
     */
    static DataFrame of(Stream<Tuple> data, StructType schema) {
        return new DataFrameImpl(data, schema);
    }

    /**
     * Creates a DataFrame from a set of tuples.
     * @param data The data collection.
     */
    static DataFrame of(List<Tuple> data) {
        return new DataFrameImpl(data);
    }

    /**
     * Creates a DataFrame from a set of tuples.
     * @param data The data collection.
     */
    static DataFrame of(List<Tuple> data, StructType schema) {
        return new DataFrameImpl(data, schema);
    }

    /**
     * Creates a DataFrame from a set of Maps.
     * @param data The data collection.
     */
    static <T> DataFrame of(Collection<Map<String, T>> data, StructType schema) {
        List<Tuple> rows = data.stream().map(map -> {
            Object[] row = new Object[schema.length()];
            for (int i = 0; i < row.length; i++) {
                row[i] = map.get(schema.fieldName(i));
            }
            return Tuple.of(row, schema);
        }).collect(Collectors.toList());
        return of(rows, schema);
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
    static Collector<Tuple, List<Tuple>, DenseMatrix> matrix() {
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
                    DenseMatrix m = Matrix.zeros(nrows, ncols);
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
