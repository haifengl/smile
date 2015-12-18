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

package smile.data.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.ParseException;
import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.Datum;
import smile.data.NominalAttribute;
import smile.data.NumericAttribute;
import smile.data.StringAttribute;

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
     * The attribute of dependent/response variable.
     */
    private Attribute response = null;
    /**
     * The column index of dependent/response variable.
     */
    private int responseIndex = -1;

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
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
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
    public void setCommentStartWith(String comment) {
        this.comment = comment;
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
    public void setMissingValuePlaceholder(String missing) {
        this.missing = missing;
    }

    /**
     * Sets the attribute and column index (starting at 0) of dependent/response variable.
     */
    public void setResponseIndex(Attribute response, int index) {
        if (response.type != Attribute.Type.NOMINAL && response.type != Attribute.Type.NUMERIC) {
            throw new IllegalArgumentException("The response variable is not numeric or nominal.");
        }
        
        this.response = response;
        this.responseIndex = index;
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
    public void setRowNames(boolean hasRowNames) {
        this.hasRowNames = hasRowNames;
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
    public void setColumnNames(boolean hasColNames) {
        this.hasColumnNames = hasColNames;
    }

    /**
     * Parse a dataset from given URI.
     * @throws java.io.FileNotFoundException
     */
    public AttributeDataset parse(URI uri) throws FileNotFoundException, IOException, ParseException {
        return parse(new File(uri));
    }

    /**
     * Parse a dataset from given URI.
     * @param uri the URI of data source.
     * @param attributes the list attributes of data in proper order.
     * @throws java.io.FileNotFoundException
     */
    public AttributeDataset parse(String name, Attribute[] attributes, URI uri) throws FileNotFoundException, IOException, ParseException {
        return parse(name, attributes, new File(uri));
    }

    /**
     * Parse a dataset from given file.
     * @param path the file path of data source.
     * @throws java.io.FileNotFoundException
     */
    public AttributeDataset parse(String path) throws FileNotFoundException, IOException, ParseException {
        return parse(new File(path));
    }

    /**
     * Parse a dataset from given file.
     * @param path the file path of data source.
     * @param attributes the list attributes of data in proper order.
     * @throws java.io.FileNotFoundException
     */
    public AttributeDataset parse(String name, Attribute[] attributes, String path) throws FileNotFoundException, IOException, ParseException {
        return parse(name, attributes, new File(path));
    }

    /**
     * Parse a dataset from given file.
     * @param file the file of data source.
     * @throws java.io.FileNotFoundException
     */
    public AttributeDataset parse(File file) throws FileNotFoundException, IOException, ParseException {
        String name = file.getPath();
        return parse(name, new FileInputStream(file));
    }

    /**
     * Parse a dataset from given file.
     * @param file the file of data source.
     * @param attributes the list attributes of data in proper order.
     * @throws java.io.FileNotFoundException
     */
    public AttributeDataset parse(String name, Attribute[] attributes, File file) throws FileNotFoundException, IOException, ParseException {
        AttributeDataset data = new AttributeDataset(name, attributes);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
           parse(data, reader);
        }
        return data;
    }

    /**
     * Parse a dataset from an input stream.
     * @param name the name of dataset.
     * @param stream the input stream of data.
     * @throws java.io.FileNotFoundException
     */
    public AttributeDataset parse(String name, InputStream stream) throws IOException, ParseException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            // process header
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

            String[] s = line.split(delimiter, 0);
            int start = 0;
            int dimension = s.length;
            if (hasRowNames) {
                dimension--;
                start = 1;
            }

            if (responseIndex >= s.length) {
                throw new ParseException("Invalid response variable index: " + responseIndex, responseIndex);
            }

            if (responseIndex >= 0) {
                dimension--;
            }

            Attribute[] attributes = new Attribute[dimension];

            if (hasColumnNames) {
                for (int i = 0, j = start; i < dimension; j++) {
                    if (j != responseIndex) {
                        attributes[i++] = new NumericAttribute(s[j]);
                    } else {
                        switch (response.type) {
                        case NOMINAL:
                            response = new NominalAttribute(s[j]);
                            break;
                        case NUMERIC:
                            response = new NumericAttribute(s[j]);
                            break;
                        default:
                            throw new IllegalStateException("Invalid response variable type.");
                        }
                    }
                }
            } else {
                for (int i = 0; i < dimension; i++) {
                    attributes[i] = new NumericAttribute("V" + (i + 1));
                }
            }

            AttributeDataset data = new AttributeDataset(name, attributes, response);

            if (!hasColumnNames) {
                String rowName = hasRowNames ? s[0] : null;
                double[] x = new double[attributes.length];
                double y = Double.NaN;

                for (int i = hasRowNames ? 1 : 0, k = 0; i < s.length; i++) {
                    if (i == responseIndex) {
                        y = response.valueOf(s[i]);
                    } else if (missing != null && missing.equalsIgnoreCase(s[i])) {
                        x[k++] = Double.NaN;
                    } else {
                        x[k] = attributes[k].valueOf(s[i]);
                        k++;
                    }
                }

                Datum<double[]> datum = new Datum<double[]>(x, y);
                datum.name = rowName;
                data.add(datum);
            }

            parse(data, reader);

            for (Attribute attribute : attributes) {
                if (attribute instanceof NominalAttribute) {
                    NominalAttribute a = (NominalAttribute) attribute;
                    a.setOpen(false);
                }

                if (attribute instanceof StringAttribute) {
                    StringAttribute a = (StringAttribute) attribute;
                    a.setOpen(false);
                }
            }

            return data;
        }
    }
    
    /**
     * Parse a dataset from a buffered reader.
     * @param data the dataset.
     * @param description the detailed description of dataset.
     * @param reader the buffered reader for data.
     * @param attributes the list attributes of data in proper order.
     * @throws java.io.IOException
     */
    private void parse(AttributeDataset data, BufferedReader reader) throws IOException, ParseException {
        Attribute[] attributes = data.attributes();
        
        int n = attributes.length;
        
        if (hasRowNames) {
            n = n + 1;
        }

        if (responseIndex >= 0) {
            n = n + 1;
        } 
        
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith(comment)) {
                continue;
            }

            String[] s = line.split(delimiter, 0);
            if (s.length != n) {
                throw new ParseException(String.format("%d columns, expected %d", s.length, n), s.length);
            }

            String rowName = hasRowNames ? s[0] : null;
            double[] x = new double[attributes.length];
            double y = Double.NaN;

            for (int i = hasRowNames ? 1 : 0, k = 0; i < s.length; i++) {
                if (i == responseIndex) {
                    y = response.valueOf(s[i]);
                } else if (missing != null && missing.equalsIgnoreCase(s[i])) {
                    x[k++] = Double.NaN;
                } else {
                    x[k] = attributes[k].valueOf(s[i]);
                    k++;
                }
            }

            Datum<double[]> datum = new Datum<double[]>(x, y);
            datum.name = rowName;
            data.add(datum);
        }
    }
}
