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

package smile.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import smile.data.AttributeDataset;

/**
 * The delimited text file parser. By default, the parser expects a
 * white-space-separated-values file. Each line in the file corresponds
 * to a row in the table. Within a line, fields are separated by white spaces,
 * each field belonging to one table column. This class can also be
 * used to read other text tabular files by setting delimiter character
 * such ash ','. The file may contain comment lines (starting with '%')
 * and missing values (indicated by placeholder '?'), which both can be
 * parameterized.
 *
 * @author Haifeng Li
 */
public class DelimitedTextParser {

    /**
     * The delimiter character to separate columns.
     */
    private String delimiter = "\\s+";
    /**
     * The start of comments.
     */
    private String comment = "%";
    /**
     * The placeholder of missing values in the data.
     */
    private String missing = "?";
    /**
     * The dataset has column names at first row.
     */
    private boolean hasColumnNames = false;
    /**
     * The dataset has row names at first column.
     */
    private boolean hasRowNames = false;
    /**
     * The column index of dependent/response variable.
     */
    private int responseIndex = -1;
    /**
     * Indices of columns to ignore
     */
    private List<Integer> ignoredColumns = new ArrayList<>();

    /**
     * Constructor with default delimiter of white space, comment line
     * starting with '%', missing value placeholder "?", no column names,
     * no row names.
     */
    public DelimitedTextParser() {
    }

    /**
     * Returns the delimiter character/string.
     */
    public String getDelimiter() {
        return delimiter;
    }

    /**
     * Set the delimiter character/string.
     */
    public DelimitedTextParser setDelimiter(String delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    /**
     * Returns the character/string that starts a comment line.
     */
    public String getCommentStartWith() {
        return comment;
    }

    /**
     * Set the character/string that starts a comment line.
     */
    public DelimitedTextParser setCommentStartWith(String comment) {
        this.comment = comment;
        return this;
    }

    /**
     * Returns the missing value placeholder.
     */
    public String getMissingValuePlaceholder() {
        return missing;
    }

    /**
     * Set the missing value placeholder.
     */
    public DelimitedTextParser setMissingValuePlaceholder(String missing) {
        this.missing = missing;
        return this;
    }

    /**
     * Sets the list of column indices to ignore (starting at 0)
     */
    public DelimitedTextParser setIgnoredColumns(List<Integer> ignoredColumns) {
        this.ignoredColumns = ignoredColumns;
        return this;
    }

    /**
     * Adds one columns index to ignore
     */
    public DelimitedTextParser addIgnoredColumn(int index) {
        this.ignoredColumns.add(index);
        return this;
    }

    /**
     * Adds several column indices to ignore
     */
    public DelimitedTextParser addIgnoredColumns(List<Integer> ignoredColumns) {
        this.ignoredColumns.addAll(ignoredColumns);
        return this;
    }


    /**
     * Returns if the dataset has row names (at column 0).
     */
    public boolean hasRowNames() {
        return hasRowNames;
    }

    /**
     * Set if the dataset has row names (at column 0).
     */
    public DelimitedTextParser setRowNames(boolean hasRowNames) {
        this.hasRowNames = hasRowNames;
        return this;
    }

    /**
     * Returns if the dataset has column namesS (at row 0).
     */
    public boolean hasColumnNames() {
        return hasColumnNames;
    }

    /**
     * Set if the dataset has column names (at row 0).
     */
    public DelimitedTextParser setColumnNames(boolean hasColNames) {
        this.hasColumnNames = hasColNames;
        return this;
    }

    /**
     * Parse a dataset from a buffered reader.
     * @param name the name of dataset.
     * @param reader the buffered reader for data.
     * @throws java.io.IOException
     */
    private AttributeDataset parse(String name, BufferedReader reader) throws IOException, ParseException {
        String line = reader.readLine();
        while (line != null) {
            if (line.isEmpty() || line.startsWith(comment)) {
                line = reader.readLine();
            } else {
                break;
            }
        }

        if (line == null) {
            throw new IOException("Empty data source.");
        }

        if (hasRowNames) {
            addIgnoredColumn(0);
        }

        String[] s = line.split(delimiter, 0);

        int p = s.length - ignoredColumns.size();

        if (p <= 0) {
          throw new IllegalArgumentException("There are more ignored columns (" + ignoredColumns.size() + ") than columns in the file (" + s.length + ").");
        }

        if (responseIndex >= s.length) {
          throw new ParseException("Invalid response variable index: " + responseIndex, responseIndex);
        }

        if (ignoredColumns.contains(responseIndex)) {
          throw new IllegalArgumentException("The response variable is present in the list of ignored columns.");
        }

        if (p == 1) {
          throw new IllegalArgumentException("All columns are ignored, except the response variable.");
        }

        if (responseIndex >= 0) {
          p--;
        }
/*
        if (attributes == null) {
            attributes = new Attribute[p];
            for (int i = 0, k = 0; i < s.length; i++) {
                if (!ignoredColumns.contains(i) && i != responseIndex) {
                    attributes[k++] = new NumericAttribute("V" + (i + 1));
                }
            }
        }

        int ncols = attributes.length;

        if (responseIndex >= 0) {
            ncols++;
        }

        ncols += ignoredColumns.size();

        if (ncols != s.length)
            throw new ParseException(String.format("%d columns, expected %d", s.length, ncols), s.length);

        AttributeDataset data = new AttributeDataset(name, attributes, response);

        if (hasColumnNames) {
            for (int i = 0, k = 0; i < s.length; i++) {
                if (!ignoredColumns.contains(i)) {
                    if (i != responseIndex) {
                        attributes[k++].setName(s[i]);
                    } else {
                        response.setName(s[i]);
                    }
                }
            }
        } else {
            String rowName = hasRowNames ? s[0] : null;
            double[] x = new double[attributes.length];
            double y = Double.NaN;

            for (int i = 0, k = 0; i < s.length; i++) {
                if (!ignoredColumns.contains(i)) {
                    if (i == responseIndex) {
                        y = response.valueOf(s[i]);
                    } else if (missing != null && missing.equalsIgnoreCase(s[i])) {
                        x[k++] = Double.NaN;
                    } else {
                        x[k] = attributes[k].valueOf(s[i]);
                        k++;
                    }
                }
            }

            AttributeDataset.Row datum = Double.isNaN(y) ? data.add(x) : data.add(x, y);
            datum.name = rowName;
        }

        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith(comment)) {
                continue;
            }

            s = line.split(delimiter, 0);
            if (s.length != ncols) {
                throw new ParseException(String.format("%d columns, expected %d", s.length, ncols), s.length);
            }

            String rowName = hasRowNames ? s[0] : null;
            double[] x = new double[attributes.length];
            double y = Double.NaN;

            for (int i = 0, k = 0; i < s.length; i++) {
                if (!ignoredColumns.contains(i)) {
                    if (i == responseIndex) {
                        y = response.valueOf(s[i]);
                    } else if (missing != null && missing.equalsIgnoreCase(s[i])) {
                        x[k++] = Double.NaN;
                    } else {
                        x[k] = attributes[k].valueOf(s[i]);
                        k++;
                    }
                }
            }

            AttributeDataset.Row datum = Double.isNaN(y) ? data.add(x) : data.add(x, y);
            datum.name = rowName;
        }
*/
        return null;
    }
}
