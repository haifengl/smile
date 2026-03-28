/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * <p>
 * Avro provides rich data structures, a compact, fast, binary data format,
 * a container file, to store persistent data, and remote procedure call (RPC).
 * <p>
 * Avro relies on schemas. When Avro data is stored in a file, its schema
 * is stored with it. Avro schemas are defined with JSON.
 *
 * @author Haifeng Li
 */
public class Avro {
    /**
     * Avro schema.
     */
    private final Schema schema;

    /**
     * Constructor.
     *
     * @param schema the data schema.
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
     * @param schema the input stream of schema.
     * @throws IOException when fails to read the file.
     */
    public Avro(InputStream schema) throws IOException {
        this(new Schema.Parser().parse(schema));
    }

    /**
     * Constructor.
     *
     * @param schema the path to Avro schema file.
     * @throws IOException when fails to read the file.
     */
    public Avro(Path schema) throws IOException {
        this(Files.newInputStream(schema));
    }

    /**
     * Reads an avro file.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public DataFrame read(Path path) throws IOException {
        return read(Files.newInputStream(path), Integer.MAX_VALUE);
    }

    /**
     * Reads an avro file.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    public DataFrame read(String path) throws IOException, URISyntaxException {
        return read(Files.newInputStream(Path.of(path)), Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from an avro file.
     *
     * @param input the input stream of data file.
     * @param limit the number of records to read.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public DataFrame read(InputStream input, int limit) throws IOException {
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
        try (DataFileStream<GenericRecord> dataFileReader = new DataFileStream<>(input, datumReader)) {
            StructType struct = toStructType(schema);

            List<Tuple> rows = new ArrayList<>();
            GenericRecord record = null;
            while (dataFileReader.hasNext() && rows.size() < limit) {
                // Reuse the record to save memory
                record = dataFileReader.next(record);
                Object[] row = new Object[struct.length()];
                for (int i = 0; i < row.length; i++) {
                    row[i] = record.get(struct.field(i).name());
                    if (row[i] instanceof Utf8) {
                        String str = row[i].toString();
                        Measure measure = struct.field(i).measure();
                        row[i] = measure != null ? measure.valueOf(str) : str;
                    }
                }
                rows.add(Tuple.of(struct, row));
            }
            return DataFrame.of(struct, rows);
        }
    }

    /**
     * Converts an avro type to smile data type.
     * @param schema an avro schema.
     * @return smile data type.
     */
    public static DataType toDataType(Schema schema) {
        return toDataType(schema, false);
    }

    /**
     * Converts an avro type to smile data type.
     * @param schema an avro schema.
     * @param nullable true if the data may be null.
     * @return smile data type.
     */
    private static DataType toDataType(Schema schema, boolean nullable) {
        return switch (schema.getType()) {
            case BOOLEAN -> nullable ? DataTypes.NullableBooleanType : DataTypes.BooleanType;
            case INT -> nullable ? DataTypes.NullableIntType : DataTypes.IntType;
            case LONG -> nullable ? DataTypes.NullableLongType : DataTypes.LongType;
            case FLOAT -> nullable ? DataTypes.NullableFloatType : DataTypes.FloatType;
            case DOUBLE -> nullable ? DataTypes.NullableDoubleType : DataTypes.DoubleType;
            case STRING -> DataTypes.StringType;
            case FIXED, BYTES -> DataTypes.ByteArrayType;
            case ENUM -> new NominalScale(schema.getEnumSymbols()).type();
            case ARRAY -> DataTypes.array(toDataType(schema.getElementType(), nullable));
            case MAP -> DataTypes.object(java.util.Map.class);
            case UNION -> avroUnion(schema.getTypes());
            default -> throw new UnsupportedOperationException("Unsupported Avro type: " + schema);
        };
    }

    /** Converts an avro union type. */
    private static DataType avroUnion(List<Schema> union) {
        if (union.isEmpty()) {
            throw new IllegalArgumentException("Empty type list of Union");
        }

        if (union.size() > 2) {
            String s = union.stream().map(Schema::getType).map(Object::toString).collect(Collectors.joining(", "));
            throw new UnsupportedOperationException(String.format("Unsupported type Union(%s)", s));
        }

        if (union.size() == 1) {
            return toDataType(union.getFirst(), false);
        }

        Schema a = union.get(0);
        Schema b = union.get(1);

        if (a.getType() == Schema.Type.NULL && b.getType() != Schema.Type.NULL) {
            return toDataType(b, true);
        }

        if (a.getType() != Schema.Type.NULL && b.getType() == Schema.Type.NULL) {
            return toDataType(a, true);
        }

        return DataTypes.object(Object.class);
    }

    /**
     * Converts an avro schema field to smile field.
     * @param field an avro schema field.
     * @return the struct field.
     */
    public static StructField toStructField(Schema.Field field) {
        NominalScale scale = null;
        if (field.schema().getType() == Schema.Type.ENUM) {
            scale = new NominalScale(field.schema().getEnumSymbols());
        }

        return new StructField(field.name(), toDataType(field.schema()), scale);
    }

    /**
     * Converts an avro schema to smile schema.
     * @param schema an avro schema.
     * @return the struct type.
     */
    public static StructType toStructType(Schema schema) {
        List<StructField> fields = new ArrayList<>();
        for (Schema.Field field : schema.getFields()) {
            fields.add(toStructField(field));
        }

        return new StructType(fields);
    }
}
