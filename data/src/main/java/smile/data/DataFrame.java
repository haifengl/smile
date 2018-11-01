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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An immutable collection of data organized into named columns.
 *
 * @author Haifeng Li
 */
public interface DataFrame extends Dataset<Row> {
    /** Returns the number of columns. */
    int numColumns();

    /** Returns all column names as an array. */
    String[] names();

    /** Returns all column types as an array. */
    Class[] types();

    /**
     * Returns the index of a given column name.
     * @throws IllegalArgumentException when a field `name` does not exist.
     */
    int columnIndex(String name);

    /** Selects column based on the column name and return it as a Column. */
    default <T> Vector<T> apply(String colName) {
        return column(colName);
    }

    /** Selects column based on the column index. */
    <T> Vector<T> column(int i);

    /** Selects column based on the column name. */
    default <T> Vector<T> column(String colName) {
        return column(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default <T> Vector<T> column(Enum<?> e) {
        return column(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    IntVector intColumn(int i);

    /** Selects column based on the column name. */
    default IntVector intColumn(String colName) {
        return intColumn(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default IntVector intColumn(Enum<?> e) {
        return intColumn(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    LongVector longColumn(int i);

    /** Selects column based on the column name. */
    default LongVector longColumn(String colName) {
        return longColumn(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default LongVector longColumn(Enum<?> e) {
        return longColumn(columnIndex(e.toString()));
    }

    /** Selects column based on the column index. */
    DoubleVector doubleColumn(int i);

    /** Selects column based on the column name. */
    default DoubleVector doubleColumn(String colName) {
        return doubleColumn(columnIndex(colName));
    }

    /** Selects column using an enum value. */
    default DoubleVector doubleColumn(Enum<?> e) {
        return doubleColumn(columnIndex(e.toString()));
    }

    /** Selects a new DataFrame with given column indices. */
    DataFrame select(int... cols);

    /** Selects a new DataFrame with given column names. */
    default DataFrame select(String... cols) {
        int[] indices = Arrays.asList(cols).stream().mapToInt(col -> columnIndex(col)).toArray();
        return select(indices);
    }

    /** Returns a new DataFrame without given column indices. */
    DataFrame drop(int... cols);

    /** Returns a new DataFrame without given column names. */
    default DataFrame drop(String... cols) {
        int[] indices = Arrays.asList(cols).stream().mapToInt(col -> columnIndex(col)).toArray();
        return drop(indices);
    }

    /**
     * Return the matrix obtained by converting all the variables
     * in a data frame to numeric mode and then binding them together
     * as the columns of a matrix. Factors and ordered factors are
     * replaced by their internal codes.
     */
    smile.math.matrix.Matrix toMatrix();

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
                String str = row.get(i).toString();
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
        String sep = IntStream.of(colWidths).mapToObj(w -> string('-', w)).collect(Collectors.joining("+"));
        sep = "+" + sep + "+\n";
        sb.append(sep);

        // column names
        StringBuilder header = new StringBuilder();
        header.append('|');
        for (int i = 0; i < numCols; i++) {
            if (truncate) {
                header.append(leftPad(names[i], colWidths[i], ' '));
            } else {
                header.append(rightPad(names[i], colWidths[i], ' '));
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
                    line.append(leftPad(row[i], colWidths[i], ' '));
                } else {
                    line.append(rightPad(row[i], colWidths[i], ' '));
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

    /** Creates a local DataFrame. */
    static <T> DataFrame of(Collection<T> data, Class<T> clazz) throws java.beans.IntrospectionException {
        return new DataFrameImpl(data, clazz);
    }

    /** Left pad a String with a specified character.
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *         null if null String input
     */
    static String leftPad(String str, int size, char padChar) {
        if (str == null)
            return null;

        int pads = size - str.length();
        if (pads <= 0)
            return str; // returns original String when possible

        return string(padChar, pads).concat(str);
    }

    /** Right pad a String with a specified character.
     *
     * @param str  the String to pad out, may be null
     * @param size  the size to pad to
     * @param padChar  the character to pad with
     * @return left padded String or original String if no padding is necessary,
     *         null if null String input
     */
    static String rightPad(String str, int size, char padChar) {
        if (str == null)
            return null;

        int pads = size - str.length();
        if (pads <= 0)
            return str; // returns original String when possible

        return str.concat(string(padChar, pads));
    }

    /** Returns a string with a single repeated character to a specific length. */
    static String string(char ch, int len) {
        char[] chars = new char[len];
        Arrays.fill(chars, ch);
        return new String(chars);
    }
}
