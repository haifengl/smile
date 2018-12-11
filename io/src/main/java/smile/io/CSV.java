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
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * Reads and writes files in variations of the Comma Separated Value
 * (CSV) format. The file may contain comment lines (starting with '%')
 * and missing values (indicated by placeholder '?'), which both can be
 * parameterized.
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
        StructField[] fields = schema.fields();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            CSVParser parser = format.parse(reader);
            List<Tuple> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Object[] row = new Object[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    row[i] = fields[i].type.valueOf(record.get(i));
                }
                rows.add(Tuple.of(row, schema));
                if (rows.size() >= limit) break;
            }
            return DataFrame.of(rows);
        }
    }

    public void write(DataFrame df, Path path) throws IOException {
        write(df, path, StandardCharsets.UTF_8);
    }

    public void write(DataFrame df, Path path, Charset charset) throws IOException {
        int p = schema.length();
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
