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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.util.Strings;

/**
 * Reads and writes files in variations of the Comma Separated Value
 * (CSV) format.
 *
 * @author Haifeng Li
 */
public class CSV {
    /** Regex for boolean. */
    private static Pattern booleanPattern = Pattern.compile("(true|false)", Pattern.CASE_INSENSITIVE);
    /** Regex for integer. */
    private static Pattern intPattern = Pattern.compile("[-+]?\\d+");
    /** Regex for double. */
    private static Pattern doublePattern = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?");
    /** Regex for date. */
    private static Pattern datePattern = Pattern.compile("\\d{4}(-|\\/)((0[1-9])|(1[0-2]))(-|\\/)((0[1-9])|([1-2][0-9])|(3[0-1]))");
    /** Regex for time. */
    private static Pattern timePattern = Pattern.compile("(([0-1][0-9])|(2[0-3])):([0-5][0-9])(:([0-5][0-9]))?");
    /** Regex for datetime. */
    private static Pattern datetimePattern = Pattern.compile("\\d{4}(-|\\/)((0[1-9])|(1[0-2]))(-|\\/)((0[1-9])|([1-2][0-9])|(3[0-1]))(T|\\s)(([0-1][0-9])|(2[0-3])):([0-5][0-9]):([0-5][0-9])(Z)?");
    /** The schema of data structure. */
    private StructType schema;
    /** The CSV file format. */
    private CSVFormat format;

    /**
     * Constructor.
     * Standard Comma Separated Value format,
     * as for RFC4180 but allowing empty lines.
     */
    public CSV() {
        this(CSVFormat.DEFAULT);
    }

    /**
     * Constructor.
     * @param format The format of a CSV file.
     */
    public CSV(CSVFormat format) {
        this.format = format;
    }

    /**
     * Sets the schema.
     * @param schema the schema of file.
     */
    public CSV withSchema(StructType schema) {
        this.schema = schema;
        return this;
    }
    /**
     * Reads a CSV file.
     * @param path a CSV file path.
     */
    public DataFrame read(Path path) throws IOException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from a CSV file.
     * @param path a CSV file path.
     * @param limit reads a limited number of records.
     */
    public DataFrame read(Path path, int limit) throws IOException {
        if (schema == null) {
            // infer the schema from top 100 rows.
            schema = inferSchema(path, Math.min(100, limit));
        }

        StructField[] fields = schema.fields();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            CSVParser parser = format.parse(reader);
            List<Tuple> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Object[] row = new Object[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    row[i] = fields[i].type.valueOf(record.get(i).trim());
                }
                rows.add(Tuple.of(row, schema));
                if (rows.size() >= limit) break;
            }

            for (StructField field : schema.fields()) {
                if (field.type.isPrimitive()) {
                    int i = schema.fieldIndex(field.name);
                    boolean missing = false;
                    for (Tuple row : rows) {
                        if (row.isNullAt(i)) {
                            missing = true;
                            break;
                        }
                    }

                    if (missing) {
                        switch (field.type.id()) {
                            case Boolean:
                                schema.fields()[i] = new StructField(field.name, DataTypes.BooleanObjectType);
                                break;
                            case Byte:
                                schema.fields()[i] = new StructField(field.name, DataTypes.ByteObjectType);
                                break;
                            case Char:
                                schema.fields()[i] = new StructField(field.name, DataTypes.CharObjectType);
                                break;
                            case Short:
                                schema.fields()[i] = new StructField(field.name, DataTypes.ShortObjectType);
                                break;
                            case Integer:
                                schema.fields()[i] = new StructField(field.name, DataTypes.IntegerObjectType);
                                break;
                            case Long:
                                schema.fields()[i] = new StructField(field.name, DataTypes.LongObjectType);
                                break;
                            case Float:
                                schema.fields()[i] = new StructField(field.name, DataTypes.FloatObjectType);
                                break;
                            case Double:
                                schema.fields()[i] = new StructField(field.name, DataTypes.DoubleObjectType);
                                break;
                        }
                    }
                }
            }
            return DataFrame.of(rows);
        }
    }

    /**
     * Infer the schema from the top n rows.
     *  - Infer type of each row.
     *  - Merge row types to find common type
     *  - String type by default.
     */
    private StructType inferSchema(Path path, int nrows) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String[] names;
            DataType[] types;

            CSVParser parser = format.parse(reader);
            Map<String, Integer> header = parser.getHeaderMap();
            if (header != null) {
                names = new String[header.size()];
                types = new DataType[header.size()];
                for (Map.Entry<String, Integer> column : header.entrySet()) {
                    names[column.getValue()] = column.getKey();
                }
            } else {
                Iterator<CSVRecord> iter = parser.iterator();
                if (!iter.hasNext()) {
                    throw new IOException("Empty file");
                }

                CSVRecord record = iter.next();
                names = new String[record.size()];
                types = new DataType[record.size()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = String.format("V%d", i+1);
                    types[i] = typeOf(record.get(i).trim());
                }
            }

            int k = 0;
            for (CSVRecord record : parser) {
                for (int i = 0; i < names.length; i++) {
                    types[i] = typeCoercion(types[i], typeOf(record.get(i).trim()));
                }

                if (++k >= nrows) break;
            }

            StructField[] fields = new StructField[names.length];
            for (int i = 0; i < fields.length; i++) {
                fields[i] = new StructField(names[i], types[i] == null ? DataTypes.StringType : types[i]);
            }
            return DataTypes.struct(fields);
        }
    }

    /** Guess the type of a string. */
    private DataType typeOf(String s) {
        if (Strings.isNullOrEmpty(s)) return null;
        if (match(datetimePattern, s)) return DataTypes.DateTimeType;
        if (match(datePattern, s)) return DataTypes.DateType;
        if (match(timePattern, s)) return DataTypes.TimeType;
        if (match(intPattern, s)) return DataTypes.IntegerType;
        if (match(doublePattern, s)) return DataTypes.DoubleType;
        if (match(booleanPattern, s)) return DataTypes.BooleanType;
        return DataTypes.StringType;
    }

    /** Returns true if the whole string matches the regex pattern. */
    private boolean match(Pattern pattern, String s) {
        Matcher m = pattern.matcher(s);
        return m.matches();
    }

    /** Returns the common type. */
    private DataType typeCoercion(DataType a, DataType b) {
        if (a == null) return b;
        if (b == null) return a;

        if (a.id() == b.id()) return a;

        if (a.id() == DataType.ID.String || b.id() == DataType.ID.String)
            return DataTypes.StringType;

        if ((a.id() == DataType.ID.Integer && b.id() == DataType.ID.Double) ||
            (b.id() == DataType.ID.Integer && a.id() == DataType.ID.Double))
            return DataTypes.DoubleType;

        if ((a.id() == DataType.ID.Date && b.id() == DataType.ID.DateTime) ||
            (b.id() == DataType.ID.Date && a.id() == DataType.ID.DateTime))
            return DataTypes.DateTimeType;

        return DataTypes.StringType;
    }

    /** Writes a data frame to a file with UTF-8. */
    public void write(DataFrame df, Path path) throws IOException {
        write(df, path, StandardCharsets.UTF_8);
    }

    /** Writes a data frame to a file with given charset. */
    public void write(DataFrame df, Path path, Charset charset) throws IOException {
        int p = df.schema().length();
        List<String> record = new ArrayList<>(p);
        try (CSVPrinter printer = format.print(path, charset)) {
            for (int i = 0; i < df.size(); i++) {
                Tuple row = df.get(i);
                for (int j = 0; j < p; j++) record.add(row.getString(j));
                printer.printRecord(record);
                record.clear();
            }
        }
    }
}
