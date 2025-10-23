/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import smile.data.DataFrame;
import smile.data.type.*;

import static org.apache.arrow.vector.types.FloatingPointPrecision.DOUBLE;
import static org.apache.arrow.vector.types.FloatingPointPrecision.SINGLE;

/**
 * Apache Arrow is a cross-language development platform for in-memory data.
 * It specifies a standardized language-independent columnar memory format
 * for flat and hierarchical data, organized for efficient analytic
 * operations on modern hardware.
 *
 * @author Haifeng Li
 */
public class Arrow {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Arrow.class);

    /**
     * The number of records in a record batch.
     * An Apache Arrow record batch is conceptually similar
     * to the Parquet row group. Parquet recommends a
     * disk/block/row group/file size of 512 to 1024 MB on HDFS.
     * 1 million rows x 100 columns of double will be about
     * 800 MB and also cover many use cases in machine learning.
     */
    private final int batch;

    /** Constructor. */
    public Arrow() {
        this(1000000);
    }

    /**
     * Constructor.
     * @param batch the number of records in a record batch.
     */
    public Arrow(int batch) {
        if (batch <= 0) {
            throw new IllegalArgumentException("Invalid batch size: " + batch);
        }

        this.batch = batch;
    }

    /**
     * Reads an arrow file.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public DataFrame read(Path path) throws IOException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads an arrow file.
     *
     * @param path the input file path.
     * @param limit the number of records to read.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public DataFrame read(Path path, int limit) throws IOException {
        return read(Files.newInputStream(path), limit);
    }

    /**
     * Reads a limited number of records from an arrow file.
     *
     * @param path the input file path.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    public DataFrame read(String path) throws IOException, URISyntaxException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from an arrow file.
     *
     * @param path the input file path.
     * @param limit the number of records to read.
     * @throws IOException when fails to read the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    public DataFrame read(String path, int limit) throws IOException, URISyntaxException {
        return read(Input.stream(path), limit);
    }

    /**
     * Reads a limited number of records from an arrow file.
     *
     * @param input the input stream.
     * @param limit the number of records to read.
     * @throws IOException when fails to read the file.
     * @return the data frame.
     */
    public DataFrame read(InputStream input, int limit) throws IOException {
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);
             ArrowStreamReader reader = new ArrowStreamReader(input, allocator)) {
            List<DataFrame> frames = new ArrayList<>();
            int rowCount = 0;
            while (reader.loadNextBatch() && rowCount < limit) {
                try (VectorSchemaRoot root = reader.getVectorSchemaRoot()) {
                    DataFrame frame = read(root);
                    frames.add(frame);
                    rowCount += frames.size();
                }
            }

            if (frames.isEmpty()) {
                throw new IllegalStateException("No record batch");
            } else if (frames.size() == 1) {
                return frames.getFirst();
            } else {
                DataFrame df = frames.getFirst();
                return df.concat(frames.subList(1, frames.size()).toArray(new DataFrame[frames.size() - 1]));
            }
        }
    }

    static DataFrame read(VectorSchemaRoot root) {
        List<FieldVector> fieldVectors = root.getFieldVectors();
        logger.info("read {} rows and {} columns", root.getRowCount(), fieldVectors.size());

        smile.data.vector.ValueVector[] vectors = new smile.data.vector.ValueVector[fieldVectors.size()];
        for (int j = 0; j < fieldVectors.size(); j++) {
            FieldVector fieldVector = fieldVectors.get(j);
            ArrowType type = fieldVector.getField().getType();
            switch (type.getTypeID()) {
                case Int:
                    ArrowType.Int itype = (ArrowType.Int) type;
                    int bitWidth = itype.getBitWidth();
                    switch (bitWidth) {
                        case 8:
                            vectors[j] = readByteField(fieldVector);
                            break;
                        case 16:
                            if (itype.getIsSigned())
                                vectors[j] = readShortField(fieldVector);
                            else
                                vectors[j] = readCharField(fieldVector);
                            break;
                        case 32:
                            vectors[j] = readIntField(fieldVector);
                            break;
                        case 64:
                            vectors[j] = readLongField(fieldVector);
                            break;
                        default:
                            throw new UnsupportedOperationException("Unsupported integer bit width: " + bitWidth);
                    }
                    break;
                case FloatingPoint:
                    FloatingPointPrecision precision = ((ArrowType.FloatingPoint) type).getPrecision();
                    switch (precision) {
                        case DOUBLE:
                            vectors[j] = readDoubleField(fieldVector);
                            break;
                        case SINGLE:
                            vectors[j] = readFloatField(fieldVector);
                            break;
                        case HALF:
                            throw new UnsupportedOperationException("Unsupported float precision: " + precision);
                    }
                    break;
                case Decimal:
                    vectors[j] = readDecimalField(fieldVector);
                    break;
                case Bool:
                    vectors[j] = readBitField(fieldVector);
                    break;
                case Date:
                    vectors[j] = readDateField(fieldVector);
                    break;
                case Time:
                    vectors[j] = readTimeField(fieldVector);
                    break;
                case Timestamp:
                    vectors[j] = readDateTimeField(fieldVector);
                    break;
                case Binary:
                case FixedSizeBinary:
                    vectors[j] = readByteArrayField(fieldVector);
                    break;
                case Utf8:
                    vectors[j] = readStringField(fieldVector);
                    break;
                default: throw new UnsupportedOperationException("Unsupported column type: " + fieldVector.getMinorType());
            }
        }

        return new DataFrame(vectors);
    }

    /**
     * Writes the data frame to an arrow file.
     *
     * @param data the data frame.
     * @param path the output file path.
     * @throws IOException when fails to write the file.
     */
    public void write(DataFrame data, Path path) throws IOException {
        Schema schema = toArrow(data.schema());
        /*
         * When a field is dictionary encoded, the values are represented
         * by an array of Int32 representing the index of the value in the
         * dictionary. The Dictionary is received as one or more
         * DictionaryBatches with the id referenced by a dictionary attribute
         * defined in the metadata (Message.fbs) in the Field table.
         * The dictionary has the same layout as the type of the field would
         * dictate. Each entry in the dictionary can be accessed by its index
         * in the DictionaryBatches. When a Schema references a Dictionary id,
         * it must send at least one DictionaryBatch for this id.
         */
        DictionaryProvider provider = new DictionaryProvider.MapDictionaryProvider();
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);
             VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
             OutputStream output = Files.newOutputStream(path);
             ArrowStreamWriter writer = new ArrowStreamWriter(root, provider, output)) {

            writer.start();
            final int size = data.size();
            for (int from = 0; from < size; from += batch) {
                int count = Math.min(batch, size - from);
                // set the batch row count
                root.setRowCount(count);

                for (Field field : root.getSchema().getFields()) {
                    FieldVector vector = root.getVector(field.getName());
                    DataType type = data.schema().field(field.getName()).dtype();
                    switch (type.id()) {
                        case Int:
                            writeIntField(data, vector, from, count);
                            break;
                        case Long:
                            writeLongField(data, vector, from, count);
                            break;
                        case Double:
                            writeDoubleField(data, vector, from, count);
                            break;
                        case Float:
                            writeFloatField(data, vector, from, count);
                            break;
                        case Boolean:
                            writeBooleanField(data, vector, from, count);
                            break;
                        case Byte:
                            writeByteField(data, vector, from, count);
                            break;
                        case Short:
                            writeShortField(data, vector, from, count);
                            break;
                        case Char:
                            writeCharField(data, vector, from, count);
                            break;
                        case String:
                            writeStringField(data, vector, from, count);
                            break;
                        case Date:
                            writeDateField(data, vector, from, count);
                            break;
                        case Time:
                            writeTimeField(data, vector, from, count);
                            break;
                        case DateTime:
                            writeDateTimeField(data, vector, from, count);
                            break;
                        case Object: {
                            Class<?> clazz = ((ObjectType) type).getObjectClass();
                            if (clazz == Integer.class) {
                                writeNullableIntField(data, vector, from, count);
                            } else if (clazz == Long.class) {
                                writeNullableLongField(data, vector, from, count);
                            } else if (clazz == Double.class) {
                                writeNullableDoubleField(data, vector, from, count);
                            } else if (clazz == Float.class) {
                                writeNullableFloatField(data, vector, from, count);
                            } else if (clazz == Boolean.class) {
                                writeNullableBooleanField(data, vector, from, count);
                            } else if (clazz == Byte.class) {
                                writeNullableByteField(data, vector, from, count);
                            } else if (clazz == Short.class) {
                                writeNullableShortField(data, vector, from, count);
                            } else if (clazz == Character.class) {
                                writeNullableCharField(data, vector, from, count);
                            } else if (clazz == BigDecimal.class) {
                                writeDecimalField(data, vector, from, count);
                            } else if (clazz == String.class) {
                                writeStringField(data, vector, from, count);
                            } else if (clazz == LocalDate.class) {
                                writeDateField(data, vector, from, count);
                            } else if (clazz == LocalTime.class) {
                                writeTimeField(data, vector, from, count);
                            } else if (clazz == LocalDateTime.class) {
                                writeDateTimeField(data, vector, from, count);
                            } else {
                                throw new UnsupportedOperationException("Unsupported type: " + type);
                            }
                            break;
                        }
                        case Array: {
                            DataType etype = ((ArrayType) type).getComponentType();
                            if (etype.id() == DataType.ID.Byte) {
                                writeByteArrayField(data, vector, from, count);
                            } else {
                                throw new UnsupportedOperationException("Unsupported type: " + type);
                            }
                            break;
                        }

                        default:
                            throw new UnsupportedOperationException("Unsupported type: " + type);
                    }
                }

                writer.writeBatch();
                logger.info("write {} rows", count);
            }
        }
    }

    /** Reads a boolean column. */
    static smile.data.vector.ValueVector readBitField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        BitVector vector = (BitVector) fieldVector;

        BitSet data = new BitSet(count);
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                data.set(i, vector.get(i) != 0);
            }
            return new smile.data.vector.BooleanVector(name, count, data);
        } else {
            BitSet mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    data.set(i, vector.get(i) != 0);
            }
            return new smile.data.vector.NullableBooleanVector(name, count, data, mask);
        }
    }

    /** Reads a byte column. */
    static smile.data.vector.ValueVector readByteField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        TinyIntVector vector = (TinyIntVector) fieldVector;

        byte[] data = new byte[count];
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                data[i] = vector.get(i);
            }
            return new smile.data.vector.ByteVector(name, data);
        } else {
            BitSet mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    data[i] = vector.get(i);
            }
            return new smile.data.vector.NullableByteVector(name, data, mask);
        }
    }

    /** Reads a char column. */
    static smile.data.vector.ValueVector readCharField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        SmallIntVector vector = (SmallIntVector) fieldVector;

        char[] data = new char[count];
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                data[i] = (char) vector.get(i);
            }
            return new smile.data.vector.CharVector(name, data);
        } else {
            BitSet mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    data[i] = (char) vector.get(i);
            }
            return new smile.data.vector.NullableCharVector(name, data, mask);
        }
    }

    /** Reads a short column. */
    static smile.data.vector.ValueVector readShortField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        SmallIntVector vector = (SmallIntVector) fieldVector;

        short[] data = new short[count];
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                data[i] = vector.get(i);
            }
            return new smile.data.vector.ShortVector(name, data);
        } else {
            BitSet mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    data[i] = vector.get(i);
            }
            return new smile.data.vector.NullableShortVector(name, data, mask);
        }
    }

    /** Reads an int column. */
    static smile.data.vector.ValueVector readIntField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        IntVector vector = (IntVector) fieldVector;

        int[] data = new int[count];
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                data[i] = vector.get(i);
            }
            return new smile.data.vector.IntVector(name, data);
        } else {
            BitSet mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    data[i] = vector.get(i);
            }
            return new smile.data.vector.NullableIntVector(name, data, mask);
        }
    }

    /** Reads a long column. */
    static smile.data.vector.ValueVector readLongField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        BigIntVector vector = (BigIntVector) fieldVector;

        long[] data = new long[count];
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                data[i] = vector.get(i);
            }
            return new smile.data.vector.LongVector(name, data);
        } else {
            BitSet mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    data[i] = vector.get(i);
            }
            return new smile.data.vector.NullableLongVector(name, data, mask);
        }
    }

    /** Reads a float column. */
    static smile.data.vector.ValueVector readFloatField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        Float4Vector vector = (Float4Vector) fieldVector;

        float[] data = new float[count];
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                data[i] = vector.get(i);
            }
            return new smile.data.vector.FloatVector(name, data);
        } else {
            BitSet mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    data[i] = vector.get(i);
            }
            return new smile.data.vector.NullableFloatVector(name, data, mask);
        }
    }

    /** Reads a double column. */
    static smile.data.vector.ValueVector readDoubleField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        Float8Vector vector = (Float8Vector) fieldVector;

        double[] data = new double[count];
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                data[i] = vector.get(i);
            }
            return new smile.data.vector.DoubleVector(name, data);
        } else {
            BitSet mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    data[i] = vector.get(i);
            }
            return new smile.data.vector.NullableDoubleVector(name, data, mask);
        }
    }

    /** Reads a decimal column. */
    static smile.data.vector.ValueVector readDecimalField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        BigDecimal[] data = new BigDecimal[count];
        DecimalVector vector = (DecimalVector) fieldVector;
        for (int i = 0; i < count; i++) {
            data[i] = vector.isNull(i) ? null : vector.getObject(i);
        }

        var field = new StructField(name, DataTypes.DecimalType);
        return new smile.data.vector.NumberVector<>(field, data);
    }

    /** Reads a date column. */
    static smile.data.vector.ValueVector readDateField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        LocalDate[] data = new LocalDate[count];
        ZoneOffset zone = OffsetDateTime.now().getOffset();
        if (fieldVector instanceof DateDayVector vector) {
            for (int i = 0; i < count; i++) {
                data[i] = vector.isNull(i) ? null : LocalDate.ofEpochDay(vector.get(i));
            }
        } else if (fieldVector instanceof DateMilliVector vector) {
            for (int i = 0; i < count; i++) {
                data[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i)), zone).toLocalDate();
            }
        }

        var field = new StructField(name, DataTypes.DateType);
        return new smile.data.vector.ObjectVector<>(field, data);
    }

    /** Reads a time column. */
    static smile.data.vector.ValueVector readTimeField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        LocalTime[] data = new LocalTime[count];
        switch (fieldVector) {
            case TimeNanoVector vector -> {
                for (int i = 0; i < count; i++) {
                    data[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i));
                }
            }
            case TimeMilliVector vector -> {
                for (int i = 0; i < count; i++) {
                    data[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i) * 1000000L);
                }
            }
            case TimeMicroVector vector -> {
                for (int i = 0; i < count; i++) {
                    data[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i) * 1000);
                }
            }
            case TimeSecVector vector -> {
                for (int i = 0; i < count; i++) {
                    data[i] = vector.isNull(i) ? null : LocalTime.ofSecondOfDay(vector.get(i));
                }
            }
            default -> throw new IllegalArgumentException("Invalid field vector type: " + fieldVector.getMinorType());
        }

        var field = new StructField(name, DataTypes.TimeType);
        return new smile.data.vector.ObjectVector<>(field, data);
    }

    /** Reads a DateTime column. */
    static smile.data.vector.ValueVector readDateTimeField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        LocalDateTime[] data = new LocalDateTime[count];
        String timezone = ((ArrowType.Timestamp) fieldVector.getField().getType()).getTimezone();
        ZoneOffset zone = timezone == null ? ZoneOffset.UTC : ZoneOffset.of(timezone);
        switch (fieldVector) {
            case TimeStampMilliVector vector -> {
                for (int i = 0; i < count; i++) {
                    data[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i)), zone);
                }
            }
            case TimeStampNanoVector vector -> {
                for (int i = 0; i < count; i++) {
                    data[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i) / 1000000), zone);
                }
            }
            case TimeStampMicroVector vector -> {
                for (int i = 0; i < count; i++) {
                    data[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i) / 1000), zone);
                }
            }
            case TimeStampSecVector vector -> {
                for (int i = 0; i < count; i++) {
                    data[i] = vector.isNull(i) ? null : LocalDateTime.ofEpochSecond(vector.get(i), 0, zone);
                }
            }
            default -> throw new IllegalArgumentException("Invalid field vector type: " + fieldVector.getMinorType());
        }

        var field = new StructField(name, DataTypes.DateTimeType);
        return new smile.data.vector.ObjectVector<>(field, data);
    }

    /** Reads a byte[] column. */
    static smile.data.vector.ValueVector readByteArrayField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        byte[][] data = new byte[count][];
        if (fieldVector instanceof VarBinaryVector vector) {
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    data[i] = null;
                else
                    data[i] = vector.get(i);
            }
        } else if (fieldVector instanceof FixedSizeBinaryVector vector){
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    data[i] = null;
                else
                    data[i] = vector.get(i);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported binary vector: " + fieldVector);
        }

        var field = new StructField(name, DataTypes.ByteArrayType);
        return new smile.data.vector.ObjectVector<>(field, data);
    }

    /** Reads a String column. */
    static smile.data.vector.ValueVector readStringField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        var name = fieldVector.getField().getName();
        VarCharVector vector = (VarCharVector) fieldVector;
        String[] data = new String[count];
        for (int i = 0; i < count; i++) {
            if (vector.isNull(i))
                data[i] = null;
            else
                data[i] = new String(vector.get(i));
        }

        return new smile.data.vector.StringVector(name, data);
    }

    /** Writes an int column. */
    private void writeIntField(DataFrame df, FieldVector fieldVector, int from, int count) {
        var column = df.column(fieldVector.getField().getName());
        if (column.isNullable()) {
            writeNullableIntField(df, fieldVector, from, count);
            return;
        }

        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        IntVector vector = (IntVector) fieldVector;
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getInt(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable int column. */
    private void writeNullableIntField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        IntVector vector = (IntVector) fieldVector;
        smile.data.vector.ValueVector column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Integer x = (Integer) column.get(i);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a boolean column. */
    private void writeBooleanField(DataFrame df, FieldVector fieldVector, int from, int count) {
        var column = df.column(fieldVector.getField().getName());
        if (column.isNullable()) {
            writeNullableBooleanField(df, fieldVector, from, count);
            return;
        }

        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        BitVector vector = (BitVector) fieldVector;
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getInt(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable boolean column. */
    private void writeNullableBooleanField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        BitVector vector = (BitVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Boolean x = (Boolean) column.get(i);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x ? 1 : 0);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a byte column. */
    private void writeCharField(DataFrame df, FieldVector fieldVector, int from, int count) {
        var column = df.column(fieldVector.getField().getName());
        if (column.isNullable()) {
            writeNullableCharField(df, fieldVector, from, count);
            return;
        }

        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        UInt2Vector vector = (UInt2Vector) fieldVector;
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getChar(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable char column. */
    private void writeNullableCharField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        UInt2Vector vector = (UInt2Vector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Character x = (Character) column.get(i);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a byte column. */
    private void writeByteField(DataFrame df, FieldVector fieldVector, int from, int count) {
        var column = df.column(fieldVector.getField().getName());
        if (column.isNullable()) {
            writeNullableByteField(df, fieldVector, from, count);
            return;
        }

        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        TinyIntVector vector = (TinyIntVector) fieldVector;
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getByte(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable byte column. */
    private void writeNullableByteField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        TinyIntVector vector = (TinyIntVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Byte x = (Byte) column.get(i);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a short column. */
    private void writeShortField(DataFrame df, FieldVector fieldVector, int from, int count) {
        var column = df.column(fieldVector.getField().getName());
        if (column.isNullable()) {
            writeNullableShortField(df, fieldVector, from, count);
            return;
        }

        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        SmallIntVector vector = (SmallIntVector) fieldVector;
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getShort(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable short column. */
    private void writeNullableShortField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        SmallIntVector vector = (SmallIntVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Short x = (Short) column.get(i);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a long column. */
    private void writeLongField(DataFrame df, FieldVector fieldVector, int from, int count) {
        var column = df.column(fieldVector.getField().getName());
        if (column.isNullable()) {
            writeNullableLongField(df, fieldVector, from, count);
            return;
        }

        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        BigIntVector vector = (BigIntVector) fieldVector;
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getLong(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable long column. */
    private void writeNullableLongField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        BigIntVector vector = (BigIntVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Long x = (Long) column.get(i);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a float column. */
    private void writeFloatField(DataFrame df, FieldVector fieldVector, int from, int count) {
        var column = df.column(fieldVector.getField().getName());
        if (column.isNullable()) {
            writeNullableFloatField(df, fieldVector, from, count);
            return;
        }

        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        Float4Vector vector  = (Float4Vector) fieldVector;
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getFloat(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable float column. */
    private void writeNullableFloatField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        Float4Vector vector  = (Float4Vector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Float x = (Float) column.get(i);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a double column. */
    private void writeDoubleField(DataFrame df, FieldVector fieldVector, int from, int count) {
        var column = df.column(fieldVector.getField().getName());
        if (column.isNullable()) {
            writeNullableDoubleField(df, fieldVector, from, count);
            return;
        }

        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        Float8Vector vector  = (Float8Vector) fieldVector;
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getDouble(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable double column. */
    private void writeNullableDoubleField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        Float8Vector vector  = (Float8Vector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Double x = (Double) column.get(i);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a string column. */
    private void writeStringField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        VarCharVector vector = (VarCharVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            String x = (String) column.get(j);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x.getBytes(StandardCharsets.UTF_8));
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a decimal column. */
    private void writeDecimalField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        DecimalVector vector = (DecimalVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            BigDecimal x = (BigDecimal) column.get(j);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a date column. */
    private void writeDateField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        DateDayVector vector = (DateDayVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            LocalDate x = (LocalDate) column.get(j);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, (int) x.toEpochDay());
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a time column. */
    private void writeTimeField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        TimeNanoVector vector = (TimeNanoVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            LocalTime x = (LocalTime) column.get(j);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x.toNanoOfDay());
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a datetime column. */
    private void writeDateTimeField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        TimeStampMilliTZVector vector = (TimeStampMilliTZVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            LocalDateTime x = (LocalDateTime) column.get(j);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x.toInstant(OffsetDateTime.now().getOffset()).toEpochMilli());
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a byte array column. */
    private void writeByteArrayField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        VarBinaryVector vector = (VarBinaryVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            byte[] bytes = (byte[]) column.get(j);
            if (bytes == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, bytes);
            }
        }

        fieldVector.setValueCount(count);
    }

    /**
     * Converts an arrow field to smile data type.
     * @param field an arrow field.
     * @return the data type.
     */
    public static DataType toDataType(Field field) {
        ArrowType type = field.getType();
        boolean nullable = field.isNullable();
        return switch (type.getTypeID()) {
            case Int -> {
                ArrowType.Int itype = (ArrowType.Int) type;
                int bitWidth = itype.getBitWidth();
                yield switch (bitWidth) {
                    case  8 -> nullable ? DataTypes.NullableByteType : DataTypes.ByteType;
                    case 16 -> nullable ? DataTypes.NullableShortType : DataTypes.ShortType;
                    case 32 -> nullable ? DataTypes.NullableIntType : DataTypes.IntType;
                    case 64 -> nullable ? DataTypes.NullableLongType : DataTypes.LongType;
                    default -> throw new UnsupportedOperationException("Unsupported integer bit width: " + bitWidth);
                };
            }

            case FloatingPoint -> {
                FloatingPointPrecision precision = ((ArrowType.FloatingPoint) type).getPrecision();
                yield switch (precision) {
                    case DOUBLE -> nullable ? DataTypes.NullableDoubleType : DataTypes.DoubleType;
                    case SINGLE -> nullable ? DataTypes.NullableFloatType : DataTypes.FloatType;
                    case HALF -> throw new UnsupportedOperationException("Unsupported float precision: " + precision);
                };
            }

            case Bool -> DataTypes.BooleanType;
            case Decimal -> DataTypes.DecimalType;
            case Utf8 -> DataTypes.StringType;
            case Date -> DataTypes.DateType;
            case Time -> DataTypes.TimeType;
            case Timestamp -> DataTypes.DateTimeType;
            case Binary, FixedSizeBinary -> DataTypes.ByteArrayType;
            case List, FixedSizeList -> {
                List<Field> child = field.getChildren();
                if (child.size() != 1) {
                    throw new IllegalStateException(String.format("List type has %d child fields.", child.size()));
                }

                yield DataTypes.array(toStructField(child.getFirst()).dtype());
            }

            case Struct -> {
                List<StructField> children = field.getChildren().stream().map(Arrow::toStructField).toList();
                yield new StructType(children);
            }

            default -> throw new UnsupportedOperationException("Unsupported arrow to smile type conversion: " + type);
        };
    }

    /**
     * Converts an arrow field to smile field.
     * @param field an arrow field.
     * @return the struct field.
     */
    public static StructField toStructField(Field field) {
        String name = field.getName();
        var dtype = toDataType(field);
        return new StructField(name, dtype);
    }

    /**
     * Converts a smile struct field to arrow field.
     * @param field smile struct field.
     * @return the arrow field.
     */
    public static Field toArrow(StructField field) {
        var name = field.name();
        var dtype = field.dtype();
        return switch (dtype.id()) {
            case Int -> new Field(name, new FieldType(dtype.isNullable(), new ArrowType.Int(32, true), null), null);
            case Long -> new Field(name, new FieldType(dtype.isNullable(), new ArrowType.Int(64, true), null), null);
            case Double -> new Field(name, new FieldType(dtype.isNullable(), new ArrowType.FloatingPoint(DOUBLE), null), null);
            case Float -> new Field(name, new FieldType(dtype.isNullable(), new ArrowType.FloatingPoint(SINGLE), null), null);
            case Boolean -> new Field(name, new FieldType(dtype.isNullable(), new ArrowType.Bool(), null), null);
            case Byte -> new Field(name, new FieldType(dtype.isNullable(), new ArrowType.Int(8, true), null), null);
            case Short -> new Field(name, new FieldType(dtype.isNullable(), new ArrowType.Int(16, true), null), null);
            case Char -> new Field(name, new FieldType(dtype.isNullable(), new ArrowType.Int(16, false), null), null);
            case Decimal -> new Field(name, FieldType.nullable(new ArrowType.Decimal(28, 10, 128)), null);
            case String -> new Field(name, FieldType.nullable(new ArrowType.Utf8()), null);
            case Date -> new Field(name, FieldType.nullable(new ArrowType.Date(DateUnit.DAY)), null);
            case Time -> new Field(name, FieldType.nullable(new ArrowType.Time(TimeUnit.MILLISECOND, 32)), null);
            case DateTime -> new Field(name, FieldType.nullable(new ArrowType.Timestamp(TimeUnit.MILLISECOND, java.time.ZoneOffset.UTC.getId())), null);
            case Object -> {
                Class<?> clazz = ((ObjectType) dtype).getObjectClass();
                if (clazz == Integer.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(32, true)), null);
                } else if (clazz == Long.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(64, true)), null);
                } else if (clazz == Double.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.FloatingPoint(DOUBLE)), null);
                } else if (clazz == Float.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.FloatingPoint(SINGLE)), null);
                } else if (clazz == Boolean.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Bool()), null);
                } else if (clazz == Byte.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(8, true)), null);
                } else if (clazz == Short.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(16, true)), null);
                } else if (clazz == Character.class) {
                    yield new Field(name, FieldType.nullable(new ArrowType.Int(16, false)), null);
                } else {
                    throw new UnsupportedOperationException("Unsupported arrow type conversion: " + clazz.getName());
                }
            }
            case Array -> {
                DataType etype = ((ArrayType) dtype).getComponentType();
                yield switch (etype.id()) {
                    case Int -> new Field(name,
                            new FieldType(false, new ArrowType.List(), null),
                            // children type
                            Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.Int(32, true), null), null))
                    );
                    case Long -> new Field(name,
                            new FieldType(false, new ArrowType.List(), null),
                            // children type
                            Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.Int(64, true), null), null))
                    );
                    case Double -> new Field(name,
                            new FieldType(false, new ArrowType.List(), null),
                            // children type
                            Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.FloatingPoint(DOUBLE), null), null))
                    );
                    case Float -> new Field(name,
                            new FieldType(false, new ArrowType.List(), null),
                            // children type
                            Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.FloatingPoint(SINGLE), null), null))
                    );
                    case Boolean -> new Field(name,
                            new FieldType(false, new ArrowType.List(), null),
                            // children type
                            Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.Bool(), null), null))
                    );
                    case Byte -> new Field(name, FieldType.nullable(new ArrowType.Binary()), null);
                    case Short -> new Field(name,
                            new FieldType(false, new ArrowType.List(), null),
                            // children type
                            Collections.singletonList(new Field(null, new FieldType(false, new ArrowType.Int(16, true), null), null))
                    );
                    case Char -> new Field(name, FieldType.nullable(new ArrowType.Utf8()), null);
                    default -> throw new UnsupportedOperationException("Unsupported array type conversion: " + etype);
                };
            }
            case Struct -> {
                StructType children = (StructType) dtype;
                yield new Field(name,
                        new FieldType(false, new ArrowType.Struct(), null),
                        // children type
                        children.fields().stream().map(Arrow::toArrow).toList()
                );
            }
        };
    }

    /**
     * Converts an arrow schema to smile schema.
     * @param schema an arrow schema.
     * @return the struct type.
     */
    public static StructType toStructType(org.apache.arrow.vector.types.pojo.Schema schema) {
        List<StructField> fields = new ArrayList<>();
        for (Field field : schema.getFields()) {
            fields.add(toStructField(field));
        }

        return new StructType(fields);
    }

    /**
     * Converts smile schema to an arrow schema.
     * @param schema smile schema.
     * @return the arrow schema.
     */
    public static org.apache.arrow.vector.types.pojo.Schema toArrow(StructType schema) {
        List<Field> fields = new ArrayList<>();
        for (StructField field : schema.fields()) {
            fields.add(toArrow(field));
        }

        return new org.apache.arrow.vector.types.pojo.Schema(fields, null);
    }
}
