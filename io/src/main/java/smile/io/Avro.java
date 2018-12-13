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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.util.Utf8;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;

/**
 * Apache Avro is a data serialization system.
 *
 * Avro provides rich data structures, a compact, fast, binary data format,
 * a container file, to store persistent data, and remote procedure call (RPC).
 *
 * Avro relies on schemas. When Avro data is stored in a file, its schema
 * is stored with it. Avro schemas are defined with JSON.
 *
 * @author Haifeng Li
 */
public class Avro {
    /**
     * Avro schema.
     */
    private Schema schema;

    /**
     * Constructor.
     */
    public Avro(Schema schema) {
        if (schema.getType() != Schema.Type.RECORD) {
            throw new IllegalArgumentException("The type of schema is not Record");
        }
        this.schema = schema;
    }

    /**
     * Constructor.
     *
     * @param schemaFile Avro schema file path.
     */
    public Avro(Path schemaFile) throws IOException {
        schema = new Schema.Parser().parse(schemaFile.toFile());
        if (schema.getType() != Schema.Type.RECORD) {
            throw new IllegalArgumentException("The type of schema is not Record");
        }
    }

    /**
     * Reads an avro file.
     *
     * @param path an Apache Avro file path.
     */
    public DataFrame read(Path path) throws IOException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from an avro file.
     *
     * @param path  an Apache Avro file path.
     * @param limit reads a limited number of records.
     */
    public DataFrame read(Path path, int limit) throws IOException {
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>(schema);
        try (DataFileReader<GenericRecord> dataFileReader = new DataFileReader<GenericRecord>(path.toFile(), datumReader)) {
            GenericRecord record = null;
            StructType struct = toSmileSchema(schema);
            List<Tuple> rows = new ArrayList<>();
            while (dataFileReader.hasNext()) {
                // Reuse the record to save memory
                record = dataFileReader.next(record);
                Object[] row = new Object[struct.length()];
                for (int i = 0; i < row.length; i++) {
                    row[i] = record.get(struct.field(i).name);
                    if (row[i] instanceof Utf8) {
                        row[i] = row[i].toString();
                    }
                }
                rows.add(Tuple.of(row, struct));
            }
            return DataFrame.of(rows);
        }
    }

    /** Converts an arrow schema to smile schema. */
    private StructType toSmileSchema(Schema schema) {
        List<StructField> fields = new ArrayList<>();
        for (Schema.Field field : schema.getFields()) {
            fields.add(toSmileField(field));
        }

        return DataTypes.struct(fields);
    }

    /** Converts an avro field to a smile struct field. */
    private StructField toSmileField(Schema.Field field) {
        return new StructField(field.name(), typeOf(field.schema()));
    }

    /** Converts an avro type to smile type. */
    private DataType typeOf(Schema schema) {
        switch (schema.getType()) {
            case BOOLEAN:
                return DataTypes.BooleanType;
            case INT:
                return DataTypes.IntegerType;
            case LONG:
                return DataTypes.LongType;
            case FLOAT:
                return DataTypes.FloatType;
            case DOUBLE:
                return DataTypes.DoubleType;
            case STRING:
                return DataTypes.StringType;
            case FIXED:
            case BYTES:
                return DataTypes.ByteArrayType;
            case ENUM:
                return DataTypes.StringType;
            case ARRAY:
                return DataTypes.array(typeOf(schema.getElementType()));
            case MAP:
                return DataTypes.object(java.util.Map.class);
            case UNION:
                return unionType(schema);
            default:
                throw new UnsupportedOperationException("Unsupported Avro type: " + schema);
        }
    }

    /** Converts a Union type. */
    private DataType unionType(Schema schema) {
        List<Schema> union = schema.getTypes();
        if (union.isEmpty()) {
            throw new IllegalArgumentException("Empty type list of Union");
        }

        if (union.size() > 2) {
            String s = union.stream().map(t -> t.getType()).map(Object::toString).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException(String.format("Unsupported type Union(%s)", s));
        }

        if (union.size() == 1) {
            return typeOf(union.get(0));
        }

        Schema a = union.get(0);
        Schema b = union.get(1);

        if (a.getType() == Schema.Type.NULL && b.getType() != Schema.Type.NULL) {
            return prompt(typeOf(b));
        }

        if (a.getType() != Schema.Type.NULL && b.getType() == Schema.Type.NULL) {
            return prompt(typeOf(a));
        }

        return DataTypes.object(Object.class);
    }

    /** Prompts a primitive type to boxed type. */
    private DataType prompt(DataType type) {
        switch (type.id()) {
            case Boolean: return DataTypes.BooleanObjectType;
            case Char: return DataTypes.CharObjectType;
            case Byte: return DataTypes.ByteObjectType;
            case Short: return DataTypes.ByteObjectType;
            case Integer: return DataTypes.ShortObjectType;
            case Long: return DataTypes.IntegerObjectType;
            case Float: return DataTypes.LongObjectType;
            case Double: return DataTypes.DoubleObjectType;
            default: return type;
        }
    }

    /**
     * Writes the DataFrame to a file.
     */
    public void write(DataFrame df, Path path) throws IOException {
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<GenericRecord>(schema);
        try (DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<GenericRecord>(datumWriter)) {
            dataFileWriter.create(schema, path.toFile());
            for (int i = 0; i < df.nrows(); i++) {
                GenericRecord record = new GenericData.Record(schema);
                dataFileWriter.append(record);
            }
        }
    }
}