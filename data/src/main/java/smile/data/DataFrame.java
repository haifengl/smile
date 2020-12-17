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

package smile.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import smile.data.measure.CategoricalMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.*;
import smile.data.vector.*;
import smile.data.vector.Vector;
import smile.math.matrix.Matrix;
import smile.util.Strings;

/**
 * An immutable collection of data organized into named columns.
 *
 * @author Haifeng Li
 */
public interface DataFrame extends Dataset<Tuple>, Iterable<BaseVector> {
    /**
     * Returns the schema of DataFrame.
     * @return the schema.
     */
    StructType schema();

    /**
     * Returns the column names.
     * @return the column names.
     */
    default String[] names() {
        StructField[] fields = schema().fields();
        return Arrays.stream(fields)
                .map(field -> field.name)
                .collect(java.util.stream.Collectors.toList())
                .toArray(new String[fields.length]);
    }

    /**
     * Returns the column data types.
     * @return the column data types.
     */
    default DataType[] types() {
        StructField[] fields = schema().fields();
        return Arrays.stream(fields)
                .map(field -> field.type)
                .collect(java.util.stream.Collectors.toList())
                .toArray(new DataType[fields.length]);
    }


    /**
     * Returns the column's level of measurements.
     * @return the column's level of measurements.
     */
    default Measure[] measures() {
        StructField[] fields = schema().fields();
        return Arrays.stream(fields)
                .map(field -> field.measure)
                .toArray(Measure[]::new);
    }

    /**
     * Returns the number of rows.
     * @return the number of rows.
     */
    default int nrow() {
        return size();
    }

    /**
     * Returns the number of columns.
     * @return the number of columns.
     */
    int ncol();

    /**
     * Returns the structure of data frame.
     * @return the structure of data frame.
     */
    default DataFrame structure() {
        List<BaseVector> vectors = Arrays.asList(
                Vector.of("Column", String.class, names()),
                Vector.of("Type", DataType.class, types()),
                Vector.of("Measure", Measure.class, measures())
        );

        return new DataFrameImpl(vectors);
    }

    /**
     * Returns a new data frame without rows that have null/missing values.
     * @return the data frame without nulls.
     */
    default DataFrame omitNullRows() {
        return DataFrame.of(stream().filter(r -> !r.hasNull()), schema().unboxed());
    }

    /**
     * Returns the cell at (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the cell value.
     */
    default Object get(int i, int j) {
        return get(i).get(j);
    }

    /**
     * Returns the cell at (i, j).
     * @param i the row index.
     * @param column the column name.
     * @return the cell value.
     */
    default Object get(int i, String column) {
        return get(i).get(column);
    }

    /**
     * Returns a new data frame with row indexing.
     * @param index the row indices.
     * @return the data frame of selected rows.
     */
    default DataFrame of(int... index) {
        return new IndexDataFrame(this, index);
    }

    /**
     * Returns a new data frame with boolean indexing.
     * @param index the boolean index.
     * @return the data frame of selected rows.
     */
    default DataFrame of(boolean... index) {
        return of(IntStream.range(0, index.length).filter(i -> index[i]).toArray());
    }

    /**
     * Copies the specified range into a new data frame.
     * @param from the initial index of the range to be copied, inclusive
     * @param to the final index of the range to be copied, exclusive.
     * @return the data frame of selected range of rows.
     */
    default DataFrame slice(int from, int to) {
        return IntStream.range(from, to).mapToObj(this::get).collect(Collectors.collect());
    }

    /**
     * Checks whether the value at position (i, j) is null.
     * @param i the row index.
     * @param j the column index.
     * @return true if the cell value is null.
     */
    default boolean isNullAt(int i, int j) {
        return get(i).isNullAt(j);
    }

    /**
     * Checks whether the field value is null.
     * @param i the row index.
     * @param column the column name.
     * @return true if the cell value is null.
     */
    default boolean isNullAt(int i, String column) {
        return get(i).isNullAt(column);
    }

    /**
     * Returns the value at position (i, j) as a primitive boolean.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default boolean getBoolean(int i, int j) {
        return get(i).getBoolean(j);
    }

    /**
     * Returns the field value as a primitive boolean.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default boolean getBoolean(int i, String column) {
        return get(i).getBoolean(column);
    }

    /**
     * Returns the value at position (i, j) as a primitive byte.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default char getChar(int i, int j) {
        return get(i).getChar(j);
    }

    /**
     * Returns the field value as a primitive byte.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default char getChar(int i, String column) {
        return get(i).getChar(column);
    }

    /**
     * Returns the value at position (i, j) as a primitive byte.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default byte getByte(int i, int j) {
        return get(i).getByte(j);
    }

    /**
     * Returns the field value as a primitive byte.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default byte getByte(int i, String column) {
        return get(i).getByte(column);
    }

    /**
     * Returns the value at position (i, j) as a primitive short.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default short getShort(int i, int j) {
        return get(i).getShort(j);
    }

    /**
     * Returns the field value as a primitive short.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default short getShort(int i, String column) {
        return get(i).getShort(column);
    }

    /**
     * Returns the value at position (i, j) as a primitive int.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default int getInt(int i, int j) {
        return get(i).getInt(j);
    }

    /**
     * Returns the field value as a primitive int.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default int getInt(int i, String column) {
        return get(i).getInt(column);
    }

    /**
     * Returns the value at position (i, j) as a primitive long.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default long getLong(int i, int j) {
        return get(i).getLong(j);
    }

    /**
     * Returns the field value as a primitive long.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default long getLong(int i, String column) {
        return get(i).getLong(column);
    }

    /**
     * Returns the value at position (i, j) as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default float getFloat(int i, int j) {
        return get(i).getFloat(j);
    }

    /**
     * Returns the field value as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default float getFloat(int i, String column) {
        return get(i).getFloat(column);
    }

    /**
     * Returns the value at position (i, j) as a primitive double.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default double getDouble(int i, int j) {
        return get(i).getDouble(j);
    }

    /**
     * Returns the field value as a primitive double.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     * @return the cell value.
     */
    default double getDouble(int i, String column) {
        return get(i).getDouble(column);
    }

    /**
     * Returns the value at position (i, j) as a String object.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default String getString(int i, int j) {
        return get(i).getString(j);
    }

    /**
     * Returns the field value as a String object.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default String getString(int i, String column) {
        return get(i).getString(column);
    }

    /**
     * Returns the string representation of the value at position (i, j).
     * @param i the row index.
     * @param j the column index.
     * @return the string representation of cell value.
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
     * @param i the row index.
     * @param column the column name.
     * @return the string representation of cell value.
     */
    default String toString(int i, String column) {
        return toString(i, indexOf(column));
    }

    /**
     * Returns the value at position (i, j) of decimal type as java.math.BigDecimal.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default java.math.BigDecimal getDecimal(int i, int j) {
        return get(i).getDecimal(j);
    }

    /**
     * Returns the field value of decimal type as java.math.BigDecimal.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default java.math.BigDecimal getDecimal(int i, String column) {
        return get(i).getDecimal(column);
    }

    /**
     * Returns the value at position (i, j) of date type as java.time.LocalDate.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default java.time.LocalDate getDate(int i, int j) {
        return get(i).getDate(j);
    }

    /**
     * Returns the field value of date type as java.time.LocalDate.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default java.time.LocalDate getDate(int i, String column) {
        return get(i).getDate(column);
    }

    /**
     * Returns the value at position (i, j) of date type as java.time.LocalTime.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default java.time.LocalTime getTime(int i, int j) {
        return get(i).getTime(j);
    }

    /**
     * Returns the field value of date type as java.time.LocalTime.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default java.time.LocalTime getTime(int i, String column) {
        return get(i).getTime(column);
    }

    /**
     * Returns the value at position (i, j) as java.time.LocalDateTime.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default java.time.LocalDateTime getDateTime(int i, int j) {
        return get(i).getDateTime(j);
    }

    /**
     * Returns the field value as java.time.LocalDateTime.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default java.time.LocalDateTime getDateTime(int i, String column) {
        return get(i).getDateTime(column);
    }

    /**
     * Returns the value at position (i, j) of NominalScale or OrdinalScale.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when the data is not nominal or ordinal.
     * @return the cell scale.
     */
    default String getScale(int i, int j) {
        int x = getInt(i, j);
        Measure measure = schema().field(j).measure;
        return (measure instanceof CategoricalMeasure) ? ((CategoricalMeasure) measure).toString(x) : String.valueOf(x);
    }

    /**
     * Returns the field value of NominalScale or OrdinalScale.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when the data is not nominal or ordinal.
     * @return the cell scale.
     */
    default String getScale(int i, String column) {
        return getScale(i, indexOf(column));
    }

    /**
     * Returns the value at position (i, j) of array type.
     *
     * @param i the row index.
     * @param j the column index.
     * @param <T> the data type of array elements.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default <T> T[] getArray(int i, int j) {
        return get(i).getArray(j);
    }

    /**
     * Returns the field value of array type.
     *
     * @param i the row index.
     * @param column the column name.
     * @param <T> the data type of array elements.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default <T> T[] getArray(int i, String column) {
        return get(i).getArray(column);
    }

    /**
     * Returns the value at position (i, j) of struct type.
     *
     * @param i the row index.
     * @param j the column index.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default Tuple getStruct(int i, int j) {
        return get(i).getStruct(j);
    }

    /**
     * Returns the field value of struct type.
     *
     * @param i the row index.
     * @param column the column name.
     * @throws ClassCastException when data type does not match.
     * @return the cell value.
     */
    default Tuple getStruct(int i, String column) {
        return get(i).getStruct(column);
    }

    /**
     * Returns the index of a given column name.
     * @param column the column name.
     * @throws IllegalArgumentException when a field `name` does not exist.
     * @return the index of column.
     */
    int indexOf(String column);

    /**
     * Selects column based on the column name and return it as a Column.
     * @param column the column name.
     * @return the column vector.
     */
    default BaseVector apply(String column) {
        return column(column);
    }

    /**
     * Selects column using an enum value.
     * @param column the field enum.
     * @return the column vector.
     */
    default BaseVector apply(Enum<?> column) {
        return column(column.toString());
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    BaseVector column(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default BaseVector column(String column) {
        return column(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default BaseVector column(Enum<?> column) {
        return column(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @param <T> the data type of column.
     * @return the column vector.
     */
    <T> Vector<T> vector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @param <T> the data type of column.
     * @return the column vector.
     */
    default <T> Vector<T> vector(String column) {
        return vector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @param <T> the data type of column.
     * @return the column vector.
     */
    default <T> Vector<T> vector(Enum<?> column) {
        return vector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    BooleanVector booleanVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default BooleanVector booleanVector(String column) {
        return booleanVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default BooleanVector booleanVector(Enum<?> column) {
        return booleanVector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    CharVector charVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default CharVector charVector(String column) {
        return charVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default CharVector charVector(Enum<?> column) {
        return charVector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    ByteVector byteVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default ByteVector byteVector(String column) {
        return byteVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default ByteVector byteVector(Enum<?> column) {
        return byteVector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    ShortVector shortVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default ShortVector shortVector(String column) {
        return shortVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default ShortVector shortVector(Enum<?> column) {
        return shortVector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    IntVector intVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default IntVector intVector(String column) {
        return intVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default IntVector intVector(Enum<?> column) {
        return intVector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    LongVector longVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default LongVector longVector(String column) {
        return longVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default LongVector longVector(Enum<?> column) {
        return longVector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    FloatVector floatVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default FloatVector floatVector(String column) {
        return floatVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default FloatVector floatVector(Enum<?> column) {
        return floatVector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    DoubleVector doubleVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default DoubleVector doubleVector(String column) {
        return doubleVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default DoubleVector doubleVector(Enum<?> column) {
        return doubleVector(indexOf(column.toString()));
    }

    /**
     * Selects column based on the column index.
     * @param i the column index.
     * @return the column vector.
     */
    StringVector stringVector(int i);

    /**
     * Selects column based on the column name.
     * @param column the column name.
     * @return the column vector.
     */
    default StringVector stringVector(String column) {
        return stringVector(indexOf(column));
    }

    /**
     * Selects column using an enum value.
     * @param column the column name.
     * @return the column vector.
     */
    default StringVector stringVector(Enum<?> column) {
        return stringVector(indexOf(column.toString()));
    }

    /**
     * Returns a new DataFrame with selected columns.
     * @param columns the column indices.
     * @return a new DataFrame with selected columns.
     */
    DataFrame select(int... columns);

    /**
     * Returns a new DataFrame with selected columns.
     * @param columns the column names.
     * @return a new DataFrame with selected columns.
     */
    default DataFrame select(String... columns) {
        int[] indices = Arrays.stream(columns).mapToInt(this::indexOf).toArray();
        return select(indices);
    }

    /**
     * Returns a new DataFrame without selected columns.
     * @param columns the column indices.
     * @return a new DataFrame without selected columns.
     */
    DataFrame drop(int... columns);

    /**
     * Returns a new DataFrame without selected columns.
     * @param columns the column names.
     * @return a new DataFrame without selected columns.
     */
    default DataFrame drop(String... columns) {
        int[] indices = Arrays.stream(columns).mapToInt(this::indexOf).toArray();
        return drop(indices);
    }

    /**
     * Merges data frames horizontally by columns.
     * @param dataframes the data frames to merge.
     * @return a new data frame that combines this DataFrame
     * with one more more other DataFrames by columns.
     */
    DataFrame merge(DataFrame... dataframes);

    /**
     * Merges vectors with this data frame.
     * @param vectors the vectors to merge.
     * @return a new data frame that combines this DataFrame
     * with one more more additional vectors.
     */
    DataFrame merge(BaseVector... vectors);

    /**
     * Unions data frames vertically by rows.
     * @param dataframes the data frames to union.
     * @return a new data frame that combines all the rows.
     */
    DataFrame union(DataFrame... dataframes);

    /**
     * Returns a new DataFrame with given columns converted to nominal.
     *
     * @param columns column names. If empty, all object columns
     *             in the data frame will be converted.
     * @return a new DataFrame.
     */
    default DataFrame factorize(String... columns) {
        if (columns.length == 0) {
            columns = Arrays.stream(schema().fields())
                    .filter(field -> field.type.isObject())
                    .map(field -> field.name)
                    .toArray(String[]::new);
        }

        int n = size();
        HashSet<String> set = new HashSet<>(Arrays.asList(columns));
        BaseVector[] vectors = Arrays.stream(names()).map(col -> {
            if (set.contains(col)) {
                int j = indexOf(col);
                List<String> levels = IntStream.range(0, n)
                        .mapToObj(i -> getString(i, j))
                        .distinct().sorted().collect(java.util.stream.Collectors.toList());
                NominalScale scale =  new NominalScale(levels);

                int[] data = new int[n];
                for (int i = 0; i < n; i++) {
                    String s = getString(i, j);
                    data[i] = s == null ? (byte) -1 : scale.valueOf(s).intValue();
                }

                return IntVector.of(new StructField(col, DataTypes.IntegerType, scale), data);
            } else return column(col);
        }).toArray(BaseVector[]::new);

        return of(vectors);
    }

    /**
     * Return an array obtained by converting all the variables
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Missing values/nulls will be
     * encoded as Double.NaN. No bias term and uses level encoding
     * for categorical variables.
     * @return the numeric array.
     */
    default double[][] toArray() {
        return toArray(false, CategoricalEncoder.LEVEL);
    }

    /**
     * Return an array obtained by converting all the variables
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Missing values/nulls will be
     * encoded as Double.NaN.
     *
     * @param bias if true, add the first column of all 1's.
     * @param encoder the categorical variable encoder.
     * @return the numeric array.
     */
    default double[][] toArray(boolean bias, CategoricalEncoder encoder) {
        int nrow = nrow();
        int ncol = ncol();
        StructType schema = schema();

        ArrayList<String> colNames = new ArrayList<>();
        if (bias) colNames.add("Intercept");
        for (int j = 0; j < ncol; j++) {
            StructField field = schema.field(j);

            Measure measure = field.measure;
            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure) {
                CategoricalMeasure cat = (CategoricalMeasure) measure;
                int n = cat.size();

                if (encoder == CategoricalEncoder.DUMMY) {
                    for (int k = 1; k < n; k++) {
                        colNames.add(String.format("%s_%s", field.name, cat.level(k)));
                    }
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    for (int k = 0; k < n; k++) {
                        colNames.add(String.format("%s_%s", field.name, cat.level(k)));
                    }
                }
            } else {
                colNames.add(field.name);
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

        for (int col = 0; col < ncol; col++) {
            StructField field = schema.field(col);

            Measure measure = field.measure;
            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure) {
                CategoricalMeasure cat = (CategoricalMeasure) measure;
                if (encoder == CategoricalEncoder.DUMMY) {
                    for (int i = 0; i < nrow; i++) {
                        int k = cat.factor(getInt(i, col));
                        if (k > 0) matrix[i][j + k - 1] = 1.0;
                    }
                    j += cat.size() - 1;
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    for (int i = 0; i < nrow; i++) {
                        int k = cat.factor(getInt(i, col));
                        matrix[i][j + k] = 1.0;
                    }
                    j += cat.size();
                }
            } else {
                for (int i = 0; i < nrow; i++) {
                    matrix[i][j] = getDouble(i, col);
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
    default Matrix toMatrix() {
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
    default Matrix toMatrix(boolean bias, CategoricalEncoder encoder, String rowNames) {
        int nrow = nrow();
        int ncol = ncol();
        StructType schema = schema();

        ArrayList<String> colNames = new ArrayList<>();
        if (bias) colNames.add("Intercept");
        for (int j = 0; j < ncol; j++) {
            StructField field = schema.field(j);
            if (field.name.equals(rowNames)) continue;

            Measure measure = field.measure;
            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure) {
                CategoricalMeasure cat = (CategoricalMeasure) measure;
                int n = cat.size();

                if (encoder == CategoricalEncoder.DUMMY) {
                    for (int k = 1; k < n; k++) {
                        colNames.add(String.format("%s_%s", field.name, cat.level(k)));
                    }
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    for (int k = 0; k < n; k++) {
                        colNames.add(String.format("%s_%s", field.name, cat.level(k)));
                    }
                }
            } else {
                colNames.add(field.name);
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

        for (int col = 0; col < ncol; col++) {
            StructField field = schema.field(col);
            if (field.name.equals(rowNames)) continue;

            Measure measure = field.measure;
            if (encoder != CategoricalEncoder.LEVEL && measure instanceof CategoricalMeasure) {
                CategoricalMeasure cat = (CategoricalMeasure) measure;
                if (encoder == CategoricalEncoder.DUMMY) {
                    for (int i = 0; i < nrow; i++) {
                        int k = cat.factor(getInt(i, col));
                        if (k > 0) matrix.set(i, j + k - 1, 1.0);
                    }
                    j += cat.size() - 1;
                } else if (encoder == CategoricalEncoder.ONE_HOT) {
                    for (int i = 0; i < nrow; i++) {
                        int k = cat.factor(getInt(i, col));
                        matrix.set(i, j + k, 1.0);
                    }
                    j += cat.size();
                }
            } else {
                for (int i = 0; i < nrow; i++) {
                    matrix.set(i, j, getDouble(i, col));
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
    default DataFrame summary() {
        int ncol = ncol();
        String[] names = names();
        DataType[] types = types();
        Measure[] measures = measures();
        String[] col = new String[ncol];
        double[] min = new double[ncol];
        double[] max = new double[ncol];
        double[] avg = new double[ncol];
        long[] count = new long[ncol];

        int k = 0;
        for (int j = 0; j < ncol; j++) {
            if (measures[j] instanceof CategoricalMeasure) continue;

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
     * @param numRows the number of rows to show
     * @return the string representation of top rows.
     */
    default String toString(int numRows) {
        return toString(numRows, true);
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     * @param truncate Whether truncate long strings and align cells right.
     * @return the string representation of top rows.
     */
    default String toString(final int numRows, final boolean truncate) {
        StringBuilder sb = new StringBuilder(schema().toString());
        sb.append('\n');

        boolean hasMoreData = size() > numRows;
        String[] names = names();
        int numCols = names.length;
        int maxColWidth;
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
        }).collect(java.util.stream.Collectors.toList());

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
     * @return the string representation of top rows.
     */
    default String[][] toStrings(int numRows) {
        return toStrings(numRows, true);
    }

    /**
     * Returns the string representation of top rows.
     * @param numRows Number of rows to show
     * @param truncate Whether truncate long strings.
     * @return the string representation of top rows.
     */
    default String[][] toStrings(final int numRows, final boolean truncate) {
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
     * Creates a DataFrame from a set of vectors.
     * @param vectors The column vectors.
     * @return the data frame.
     */
    static DataFrame of(BaseVector... vectors) {
        return new DataFrameImpl(vectors);
    }

    /**
     * Creates a DataFrame from a 2-dimensional array.
     * @param data The data array.
     * @param names the name of columns.
     * @return the data frame.
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
     * @return the data frame.
     */
    static DataFrame of(float[][] data, String... names) {
        int p = data[0].length;
        if (names == null || names.length == 0) {
            names = IntStream.range(1, p+1).mapToObj(i -> "V"+i).toArray(String[]::new);
        }

        FloatVector[] vectors = new FloatVector[p];
        for (int j = 0; j < p; j++) {
            float[] x = new float[data.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = data[i][j];
            }
            vectors[j] = FloatVector.of(names[j], x);
        }
        return DataFrame.of(vectors);
    }

    /**
     * Creates a DataFrame from a 2-dimensional array.
     * @param data The data array.
     * @param names the name of columns.
     * @return the data frame.
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
     * @param <T> The data type of elements.
     * @return the data frame.
     */
    static <T> DataFrame of(List<T> data, Class<T> clazz) {
        return new DataFrameImpl(data, clazz);
    }

    /**
     * Creates a DataFrame from a stream of tuples.
     * @param data The data stream.
     * @return the data frame.
     */
    static DataFrame of(Stream<? extends Tuple> data) {
        return new DataFrameImpl(data);
    }

    /**
     * Creates a DataFrame from a stream of tuples.
     * @param data The data stream.
     * @param schema The schema of tuple.
     * @return the data frame.
     */
    static DataFrame of(Stream<? extends Tuple> data, StructType schema) {
        return new DataFrameImpl(data, schema);
    }

    /**
     * Creates a DataFrame from a set of tuples.
     * @param data The data collection.
     * @return the data frame.
     */
    static DataFrame of(List<? extends Tuple> data) {
        return new DataFrameImpl(data);
    }

    /**
     * Creates a DataFrame from a set of tuples.
     * @param data The data collection.
     * @param schema The schema of tuple.
     * @return the data frame.
     */
    static DataFrame of(List<? extends Tuple> data, StructType schema) {
        return new DataFrameImpl(data, schema);
    }

    /**
     * Creates a DataFrame from a set of Maps.
     * @param data The data collection.
     * @param schema The schema of data.
     * @param <T> The data type of elements.
     * @return the data frame.
     */
    static <T> DataFrame of(Collection<Map<String, T>> data, StructType schema) {
        List<Tuple> rows = data.stream().map(map -> {
            Object[] row = new Object[schema.length()];
            for (int i = 0; i < row.length; i++) {
                row[i] = map.get(schema.name(i));
            }
            return Tuple.of(row, schema);
        }).collect(java.util.stream.Collectors.toList());
        return of(rows, schema);
    }

    /**
     * Creates a DataFrame from a JDBC ResultSet.
     * @param rs The JDBC result set.
     * @throws SQLException when JDBC operation fails.
     * @return the data frame.
     */
    static DataFrame of(ResultSet rs) throws SQLException {
        StructType schema = DataTypes.struct(rs);
        List<Tuple> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(Tuple.of(rs, schema));
        }

        return DataFrame.of(rows);
    }

    /** Stream collectors. */
    interface Collectors {
        /**
         * Returns a stream collector that accumulates objects into a DataFrame.
         *
         * @param <T>   the type of input elements to the reduction operation
         * @param clazz The class type of elements.
         * @return the stream collector.
         */
        static <T> Collector<T, List<T>, DataFrame> collect(Class<T> clazz) {
            return Collector.of(
                    // supplier
                    ArrayList::new,
                    // accumulator
                    List::add,
                    // combiner
                    (c1, c2) -> {
                        c1.addAll(c2);
                        return c1;
                    },
                    // finisher
                    (container) -> DataFrame.of(container, clazz)
            );
        }

        /**
         * Returns a stream collector that accumulates tuples into a DataFrame.
         *
         * @return the stream collector.
         */
        static Collector<Tuple, List<Tuple>, DataFrame> collect() {
            return Collector.of(
                    // supplier
                    ArrayList::new,
                    // accumulator
                    List::add,
                    // combiner
                    (c1, c2) -> {
                        c1.addAll(c2);
                        return c1;
                    },
                    // finisher
                    DataFrame::of
            );
        }

        /**
         * Returns a stream collector that accumulates tuples into a Matrix.
         *
         * @return the stream collector.
         */
        static Collector<Tuple, List<Tuple>, Matrix> matrix() {
            return Collector.of(
                    // supplier
                    ArrayList::new,
                    // accumulator
                    List::add,
                    // combiner
                    (c1, c2) -> {
                        c1.addAll(c2);
                        return c1;
                    },
                    // finisher
                    (container) -> {
                        if (container.isEmpty()) {
                            throw new IllegalArgumentException("Empty list of tuples");
                        }
                        int nrow = container.size();
                        int ncol = container.get(0).length();
                        Matrix m = new Matrix(nrow, ncol);
                        for (int i = 0; i < nrow; i++) {
                            for (int j = 0; j < ncol; j++) {
                                m.set(i, j, container.get(i).getDouble(j));
                            }
                        }
                        return m;
                    }
            );
        }
    }
}
