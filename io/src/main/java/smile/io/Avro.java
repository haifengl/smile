/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.util.Utf8;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
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
        schema = new Schema.Parser().parse(Files.newInputStream(schemaFile));
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
        return read(Files.newInputStream(path), Integer.MAX_VALUE);
    }

    /**
     * Reads an avro file.
     *
     * @param path an Apache Avro file path or URI.
     */
    public DataFrame read(String path) throws IOException, URISyntaxException {
        return read(Input.stream(path), Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from an avro file.
     *
     * @param input  an Apache Avro file input stream.
     * @param limit reads a limited number of records.
     */
    public DataFrame read(InputStream input, int limit) throws IOException {
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
        try (DataFileStream<GenericRecord> dataFileReader = new DataFileStream<>(input, datumReader)) {
            StructType struct = toSmileSchema(schema);

            List<Tuple> rows = new ArrayList<>();
            GenericRecord record = null;
            while (dataFileReader.hasNext() && rows.size() < limit) {
                // Reuse the record to save memory
                record = dataFileReader.next(record);
                Object[] row = new Object[struct.length()];
                for (int i = 0; i < row.length; i++) {
                    row[i] = record.get(struct.field(i).name);
                    if (row[i] instanceof Utf8) {
                        String str = row[i].toString();
                        Measure measure = struct.field(i).measure;
                        row[i] = measure != null ? measure.valueOf(str) : str;
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
            NominalScale scale = null;
            if (field.schema().getType() == Schema.Type.ENUM) {
                scale = new NominalScale(field.schema().getEnumSymbols());
            }

            fields.add(new StructField(field.name(), typeOf(field.schema()), scale));
        }

        return DataTypes.struct(fields);
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
                return new NominalScale(schema.getEnumSymbols()).type();
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
            return typeOf(b).boxed();
        }

        if (a.getType() != Schema.Type.NULL && b.getType() == Schema.Type.NULL) {
            return typeOf(a).boxed();
        }

        return DataTypes.object(Object.class);
    }
}