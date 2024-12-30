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
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import smile.data.DataFrame;
import smile.data.type.*;

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
     * The root allocator. Typically only one created for a JVM.
     * Arrow provides a tree-based model for memory allocation.
     * The RootAllocator is created first, then all allocators are
     * created as children of that allocator. The RootAllocator is
     * responsible for being the master bookeeper for memory
     * allocations. All other allocators are created as children
     * of this tree. Each allocator can first determine whether
     * it has enough local memory to satisfy a particular request.
     * If not, the allocator can ask its parent for an additional
     * memory allocation.
     */
    private static RootAllocator allocator;
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
     * Creates the root allocator.
     * The RootAllocator is responsible for being the master
     * bookeeper for memory allocations.
     *
     * @param limit the memory allocation limit in bytes.
     */
    public static void allocate(long limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Invalid RootAllocator limit: " + limit);
        }

        allocator = new RootAllocator(limit);
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
        if (allocator == null) {
            allocate(Long.MAX_VALUE);
        }

        try (ArrowStreamReader reader = new ArrowStreamReader(input, allocator)) {

            // The holder for a set of vectors to be loaded/unloaded.
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            List<DataFrame> frames = new ArrayList<>();
            int size = 0;
            while (reader.loadNextBatch() && size < limit) {
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

                DataFrame frame = new DataFrame(vectors);
                frames.add(frame);
                size += frames.size();
            }

            if (frames.isEmpty()) {
                throw new IllegalStateException("No record batch");
            } else if (frames.size() == 1) {
                return frames.getFirst();
            } else {
                DataFrame df = frames.getFirst();
                return df.union(frames.subList(1, frames.size()).toArray(new DataFrame[frames.size() - 1]));
            }
        }
    }

    /**
     * Writes the data frame to an arrow file.
     *
     * @param data the data frame.
     * @param path the output file path.
     * @throws IOException when fails to write the file.
     */
    public void write(DataFrame data, Path path) throws IOException {
        if (allocator == null) {
            allocate(Long.MAX_VALUE);
        }

        Schema schema = data.schema().toArrow();
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
        try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
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
                                writeIntObjectField(data, vector, from, count);
                            } else if (clazz == Long.class) {
                                writeLongObjectField(data, vector, from, count);
                            } else if (clazz == Double.class) {
                                writeDoubleObjectField(data, vector, from, count);
                            } else if (clazz == Float.class) {
                                writeFloatObjectField(data, vector, from, count);
                            } else if (clazz == Boolean.class) {
                                writeBooleanObjectField(data, vector, from, count);
                            } else if (clazz == Byte.class) {
                                writeByteObjectField(data, vector, from, count);
                            } else if (clazz == Short.class) {
                                writeShortObjectField(data, vector, from, count);
                            } else if (clazz == Character.class) {
                                writeCharObjectField(data, vector, from, count);
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
    private smile.data.vector.ValueVector readBitField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        BitVector vector = (BitVector) fieldVector;

        BitSet a = new BitSet(count);
        BitSet mask = null;
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                if (vector.get(i) != 0) a.set(i);
            }
        } else {
            mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    if (vector.get(i) != 0) a.set(i);
            }
        }

        var field = new StructField(fieldVector.getField().getName(), DataTypes.ByteType);
        var valueVector = new smile.data.vector.BooleanVector(field, a);
        valueVector.setNullMask(mask);
        return valueVector;
    }

    /** Reads a byte column. */
    private smile.data.vector.ValueVector readByteField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        TinyIntVector vector = (TinyIntVector) fieldVector;

        byte[] a = new byte[count];
        BitSet mask = null;
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }
        } else {
            mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    a[i] = vector.get(i);
            }
        }

        var valueVector = new smile.data.vector.ByteVector(fieldVector.getField().getName(), a);
        valueVector.setNullMask(mask);
        return valueVector;
    }

    /** Reads a char column. */
    private smile.data.vector.ValueVector readCharField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        SmallIntVector vector = (SmallIntVector) fieldVector;

        char[] a = new char[count];
        BitSet mask = null;
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                a[i] = (char) vector.get(i);
            }
        } else {
            mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    a[i] = (char) vector.get(i);
            }
        }

        var valueVector = new smile.data.vector.CharVector(fieldVector.getField().getName(), a);
        valueVector.setNullMask(mask);
        return valueVector;
    }

    /** Reads a short column. */
    private smile.data.vector.ValueVector readShortField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        SmallIntVector vector = (SmallIntVector) fieldVector;

        short[] a = new short[count];
        BitSet mask = null;
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }
        } else {
            mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    a[i] = vector.get(i);
            }
        }

        var valueVector = new smile.data.vector.ShortVector(fieldVector.getField().getName(), a);
        valueVector.setNullMask(mask);
        return valueVector;
    }

    /** Reads an int column. */
    private smile.data.vector.ValueVector readIntField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        IntVector vector = (IntVector) fieldVector;

        int[] a = new int[count];
        BitSet mask = null;
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }
        } else {
            mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    a[i] = vector.get(i);
            }
        }

        var valueVector = new smile.data.vector.IntVector(fieldVector.getField().getName(), a);
        valueVector.setNullMask(mask);
        return valueVector;
    }

    /** Reads a long column. */
    private smile.data.vector.ValueVector readLongField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        BigIntVector vector = (BigIntVector) fieldVector;

        long[] a = new long[count];
        BitSet mask = null;
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }
        } else {
            mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    a[i] = vector.get(i);
            }
        }

        var valueVector = new smile.data.vector.LongVector(fieldVector.getField().getName(), a);
        valueVector.setNullMask(mask);
        return valueVector;
    }

    /** Reads a float column. */
    private smile.data.vector.ValueVector readFloatField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        Float4Vector vector = (Float4Vector) fieldVector;

        float[] a = new float[count];
        BitSet mask = null;
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }
        } else {
            mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    a[i] = vector.get(i);
            }
        }

        var valueVector = new smile.data.vector.FloatVector(fieldVector.getField().getName(), a);
        valueVector.setNullMask(mask);
        return valueVector;
    }

    /** Reads a double column. */
    private smile.data.vector.ValueVector readDoubleField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        Float8Vector vector = (Float8Vector) fieldVector;

        double[] a = new double[count];
        BitSet mask = null;
        if (!fieldVector.getField().isNullable()) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }
        } else {
            mask = new BitSet(count);
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    mask.set(i);
                else
                    a[i] = vector.get(i);
            }
        }

        var valueVector = new smile.data.vector.DoubleVector(fieldVector.getField().getName(), a);
        valueVector.setNullMask(mask);
        return valueVector;
    }

    /** Reads a decimal column. */
    private smile.data.vector.ValueVector readDecimalField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        BigDecimal[] a = new BigDecimal[count];
        DecimalVector vector = (DecimalVector) fieldVector;
        for (int i = 0; i < count; i++) {
            a[i] = vector.isNull(i) ? null : vector.getObject(i);
        }

        var field = new StructField(fieldVector.getField().getName(), DataTypes.DecimalType);
        return new smile.data.vector.ObjectVector<>(field, a);
    }

    /** Reads a date column. */
    private smile.data.vector.ValueVector readDateField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        LocalDate[] a = new LocalDate[count];
        ZoneOffset zone = OffsetDateTime.now().getOffset();
        if (fieldVector instanceof DateDayVector vector) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalDate.ofEpochDay(vector.get(i));
            }
        } else if (fieldVector instanceof DateMilliVector vector) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i)), zone).toLocalDate();
            }
        }

        var field = new StructField(fieldVector.getField().getName(), DataTypes.DateType);
        return new smile.data.vector.ObjectVector<>(field, a);
    }

    /** Reads a time column. */
    private smile.data.vector.ValueVector readTimeField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        LocalTime[] a = new LocalTime[count];
        switch (fieldVector) {
            case TimeNanoVector vector -> {
                for (int i = 0; i < count; i++) {
                    a[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i));
                }
            }
            case TimeMilliVector vector -> {
                for (int i = 0; i < count; i++) {
                    a[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i) * 1000000L);
                }
            }
            case TimeMicroVector vector -> {
                for (int i = 0; i < count; i++) {
                    a[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i) * 1000);
                }
            }
            case TimeSecVector vector -> {
                for (int i = 0; i < count; i++) {
                    a[i] = vector.isNull(i) ? null : LocalTime.ofSecondOfDay(vector.get(i));
                }
            }
            default -> throw new IllegalArgumentException("Invalid field vector type: " + fieldVector.getMinorType());
        }

        var field = new StructField(fieldVector.getField().getName(), DataTypes.TimeType);
        return new smile.data.vector.ObjectVector<>(field, a);
    }

    /** Reads a DateTime column. */
    private smile.data.vector.ValueVector readDateTimeField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        LocalDateTime[] a = new LocalDateTime[count];
        String timezone = ((ArrowType.Timestamp) fieldVector.getField().getType()).getTimezone();
        ZoneOffset zone = timezone == null ? OffsetDateTime.now().getOffset() : ZoneOffset.of(timezone);
        switch (fieldVector) {
            case TimeStampMilliVector vector -> {
                for (int i = 0; i < count; i++) {
                    a[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i)), zone);
                }
            }
            case TimeStampNanoVector vector -> {
                for (int i = 0; i < count; i++) {
                    a[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i) / 1000000), zone);
                }
            }
            case TimeStampMicroVector vector -> {
                for (int i = 0; i < count; i++) {
                    a[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i) / 1000), zone);
                }
            }
            case TimeStampSecVector vector -> {
                for (int i = 0; i < count; i++) {
                    a[i] = vector.isNull(i) ? null : LocalDateTime.ofEpochSecond(vector.get(i), 0, zone);
                }
            }
            default -> throw new IllegalArgumentException("Invalid field vector type: " + fieldVector.getMinorType());
        }

        var field = new StructField(fieldVector.getField().getName(), DataTypes.DateTimeType);
        return new smile.data.vector.ObjectVector<>(field, a);
    }

    /** Reads a byte[] column. */
    private smile.data.vector.ValueVector readByteArrayField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        byte[][] a = new byte[count][];
        if (fieldVector instanceof VarBinaryVector vector) {
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }
        } else if (fieldVector instanceof FixedSizeBinaryVector vector){
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported binary vector: " + fieldVector);
        }

        var field = new StructField(fieldVector.getField().getName(), DataTypes.ByteArrayType);
        return new smile.data.vector.ObjectVector<>(field, a);
    }

    /** Reads a String column. */
    private smile.data.vector.ValueVector readStringField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        VarCharVector vector = (VarCharVector) fieldVector;
        String[] a = new String[count];
        for (int i = 0; i < count; i++) {
            if (vector.isNull(i))
                a[i] = null;
            else
                a[i] = new String(vector.get(i));
        }

        return new smile.data.vector.StringVector(fieldVector.getField().getName(), a);
    }

    /** Writes an int column. */
    private void writeIntField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        IntVector vector = (IntVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getInt(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable int column. */
    private void writeIntObjectField(DataFrame df, FieldVector fieldVector, int from, int count) {
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
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        BitVector vector = (BitVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getInt(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable boolean column. */
    private void writeBooleanObjectField(DataFrame df, FieldVector fieldVector, int from, int count) {
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
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        UInt2Vector vector = (UInt2Vector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getChar(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable char column. */
    private void writeCharObjectField(DataFrame df, FieldVector fieldVector, int from, int count) {
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
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        TinyIntVector vector = (TinyIntVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getByte(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable byte column. */
    private void writeByteObjectField(DataFrame df, FieldVector fieldVector, int from, int count) {
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
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        SmallIntVector vector = (SmallIntVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getShort(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable short column. */
    private void writeShortObjectField(DataFrame df, FieldVector fieldVector, int from, int count) {
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
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        BigIntVector vector = (BigIntVector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getLong(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable long column. */
    private void writeLongObjectField(DataFrame df, FieldVector fieldVector, int from, int count) {
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
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        Float4Vector vector  = (Float4Vector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getFloat(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable float column. */
    private void writeFloatObjectField(DataFrame df, FieldVector fieldVector, int from, int count) {
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
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        Float8Vector vector  = (Float8Vector) fieldVector;
        var column = df.column(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            vector.set(i, column.getDouble(j));
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a nullable double column. */
    private void writeDoubleObjectField(DataFrame df, FieldVector fieldVector, int from, int count) {
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
}
