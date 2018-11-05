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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * An immutable collection of data organized into named columns.
 *
 * @author Haifeng Li
 */
public interface DataFrame extends Dataset<Tuple> {
    /** Returns all column names as an array. */
    String[] names();

    /** Returns all column types as an array. */
    Class[] types();
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
                new VectorImpl<>("Column", names()),
                new VectorImpl<>("Type", types())
        );

        return new DataFrameImpl(vectors);
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

    /**
     * Returns a new DataFrame that combines this DataFrame
     * with one more more other DataFrames by columns.
     */
    DataFrame bind(DataFrame... dataframes);

    /**
     * Returns a new DataFrame that combines this DataFrame
     * with one more more additional vectors.
     */
    DataFrame bind(BaseVector... vectors);

    /** Returns a new DataFrame without given column names. */
    default DataFrame drop(String... cols) {
        int[] indices = Arrays.asList(cols).stream().mapToInt(col -> columnIndex(col)).toArray();
        return drop(indices);
    }

    /**
     * Returns a stream collector that accumulates elements into a Dataset.
     *
     * @param <T> the type of input elements to the reduction operation
     * @param clazz The class type of elements.
     */
    static <T> Collector<T, List<T>, DataFrame> toDataFrame(Class<T> clazz) {
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
        String sep = IntStream.of(colWidths).mapToObj(w -> Utils.string('-', w)).collect(Collectors.joining("+"));
        sep = "+" + sep + "+\n";
        sb.append(sep);

        // column names
        StringBuilder header = new StringBuilder();
        header.append('|');
        for (int i = 0; i < numCols; i++) {
            if (truncate) {
                header.append(Utils.leftPad(names[i], colWidths[i], ' '));
            } else {
                header.append(Utils.rightPad(names[i], colWidths[i], ' '));
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
                    line.append(Utils.leftPad(row[i], colWidths[i], ' '));
                } else {
                    line.append(Utils.rightPad(row[i], colWidths[i], ' '));
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
     * Creates a default columnar implementation of DataFrame from a collection.
     * @param data The data collection.
     * @param clazz The class type of elements.
     * @param <T> The type of elements.
     */
    static <T> DataFrame of(Collection<T> data, Class<T> clazz) {
        return new DataFrameImpl(data, clazz);
    }

    /**
     * Creates a default columnar implementation of DataFrame from a set of vectors.
     * @param vectors The column vectors.
     */
    static DataFrame of(BaseVector... vectors) {
        return new DataFrameImpl(Arrays.asList(vectors));
    }
}
