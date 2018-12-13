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
import java.util.function.Function;
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

/**
 * Reads and writes files in variations of the Comma Separated Value
 * (CSV) format.
 *
 * @author Haifeng Li
 */
public class CSV {
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
        List<Function<String, Object>> parser = schema.parser();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            List<Tuple> rows = new ArrayList<>();
            for (CSVRecord record : format.parse(reader)) {
                Object[] row = new Object[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    String s = record.get(i).trim();
                    if (!s.isEmpty()) {
                        row[i] = parser.get(i).apply(s);
                    }
                }
                rows.add(Tuple.of(row, schema));
                if (rows.size() >= limit) break;
            }

            schema.boxed(rows);
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
                    types[i] = DataType.infer(record.get(i).trim());
                }
            }

            int k = 0;
            for (CSVRecord record : parser) {
                for (int i = 0; i < names.length; i++) {
                    types[i] = DataType.coerce(types[i], DataType.infer(record.get(i).trim()));
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

    /** Writes a data frame to a file with UTF-8. */
    public void write(DataFrame df, Path path) throws IOException {
        write(df, path, StandardCharsets.UTF_8);
    }

    /** Writes a data frame to a file with given charset. */
    public void write(DataFrame df, Path path, Charset charset) throws IOException {
        int p = df.schema().length();
        String[] header = new String[p];
        for (int i = 0; i < p; i++) {
            header[i] = df.schema().field(i).name;
        }

        List<String> record = new ArrayList<>(p);
        try (CSVPrinter printer = format.withHeader(header).print(path, charset)) {
            for (int i = 0; i < df.size(); i++) {
                Tuple row = df.get(i);
                for (int j = 0; j < p; j++) record.add(row.getString(j));
                printer.printRecord(record);
                record.clear();
            }
        }
    }
}
