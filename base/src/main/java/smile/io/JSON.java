/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.util.Strings;

/**
 * Reads JSON datasets. No nested objects are currently allowed.
 *
 * @author Haifeng Li
 */
public class JSON {
    /** The schema of data structure. */
    private StructType schema;
    /** Charset of file. */
    private Charset charset = StandardCharsets.UTF_8;
    /** Reads JSON files in single-line or multi-line mode. */
    private Mode mode = Mode.SINGLE_LINE;

    /** JSON files in single-line or multi-line mode. */
    public enum Mode {
        /** One JSON object per line. */
        SINGLE_LINE,

        /**
         * A JSON object may occupy multiple lines.
         * The file contains a list of objects.
         * Files will be loaded as a whole entity and cannot be split.
         */
        MULTI_LINE
    }

    /**
     * Constructor.
     */
    public JSON() {

    }

    /**
     * Sets the schema.
     * @param schema the data schema.
     * @return this object.
     */
    public JSON schema(StructType schema) {
        this.schema = schema;
        return this;
    }

    /**
     * Sets the charset.
     * @param charset the charset of file.
     * @return this object.
     */
    public JSON charset(Charset charset) {
        this.charset = charset;
        return this;
    }

    /**
     * Sets the file mode (single-line or multi-line).
     * @param mode the file mode.
     * @return this object.
     */
    public JSON mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    /**
     * Reads a JSON file.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public DataFrame read(Path path) throws IOException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a JSON file.
     * @param path the input file path.
     * @param limit the number of records to read.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public DataFrame read(Path path, int limit) throws IOException {
        if (schema == null) {
            // infer the schema from top 1000 objects.
            schema = inferSchema(Files.newBufferedReader(path, charset), Math.min(1000, limit));
        }

        return read(Files.newBufferedReader(path, charset), Integer.MAX_VALUE);
    }

    /**
     * Reads a JSON file.
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    public DataFrame read(String path) throws IOException, URISyntaxException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a JSON file.
     * @param path the input file path.
     * @param limit the number of records to read.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    public DataFrame read(String path, int limit) throws IOException, URISyntaxException {
        if (schema == null) {
            // infer the schema from top 1000 objects.
            schema = inferSchema(Input.reader(path, charset), Math.min(1000, limit));
        }

        return read(Input.reader(path, charset), Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from a JSON file.
     * @param reader the file reader.
     * @param limit the number of records to read.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public DataFrame read(BufferedReader reader, int limit) throws IOException {
        if (schema == null) {
            // infer the schema from top 1000 rows.
            throw new IllegalStateException("The schema is not set or inferred.");
        }

        List<Function<String, Object>> parser = schema.parser();
        List<Tuple> rows = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        if (mode == Mode.MULTI_LINE) {
            List<Map<String, String>> maps = objectMapper.readValue(reader, new TypeReference<>() {
            });
            for (Map<String, String> map : maps) {
                rows.add(toTuple(map, parser));
                if (rows.size() >= limit) break;
            }
        } else {
            String line = reader.readLine();
            while (rows.size() < limit && line != null) {
                try {
                    Map<String, String> map = objectMapper.readValue(line, new TypeReference<>() {
                    });
                    rows.add(toTuple(map, parser));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                line = reader.readLine();
            }
        }

        schema = schema.boxed(rows);
        return DataFrame.of(rows, schema);
    }

    /** Converts a map to tuple. */
    private Tuple toTuple(Map<String, String> map, List<Function<String, Object>> parser) {
        Object[] row = new Object[schema.length()];
        for (int i = 0; i < row.length; i++) {
            String s = map.get(schema.field(i).name);
            if (!Strings.isNullOrEmpty(s)) {
                row[i] = parser.get(i).apply(s);
            }
        }

        return Tuple.of(row, schema);
    }

    /**
     * Infer the schema from the top n rows.
     * <ol>
     *  <li>Infer type of each row.</li>
     *  <li>Merge row types to find common type</li>
     *  <li>String type by default.</li>
     * </ol>
     *
     * @param reader the file reader.
     * @param limit the number of records to read.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public StructType inferSchema(BufferedReader reader, int limit) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        if (mode == Mode.MULTI_LINE) {
            List<Map<String, String>> maps = objectMapper.readValue(reader, new TypeReference<>() {
            });
            for (Map<String, String> map : maps) {
                rows.add(map);
                if (rows.size() >= limit) break;
            }
        } else {
            String line = reader.readLine();
            while (rows.size() < limit && line != null) {
                try {
                    Map<String, String> map = objectMapper.readValue(line, new TypeReference<>() {
                    });
                    rows.add(map);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                line = reader.readLine();
            }
        }

        if (rows.isEmpty()) {
            throw new IOException("Empty file");
        }

        Map<String, DataType> types = new HashMap<>();
        for (Map<String, String> row : rows) {
            for (Map.Entry<String, String> e : row.entrySet()) {
                String name = e.getKey();
                String value = e.getValue();
                types.put(name, DataType.coerce(types.get(name), DataType.infer(value)));
            }
        }

        int i = 0;
        StructField[] fields = new StructField[types.size()];
        for (Map.Entry<String, DataType> type : types.entrySet()) {
            fields[i++] = new StructField(type.getKey(), type.getValue());
        }
        return DataTypes.struct(fields);
    }
}
