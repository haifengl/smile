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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.ipc.message.ArrowBlock;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import static org.apache.arrow.vector.types.FloatingPointPrecision.DOUBLE;
import static org.apache.arrow.vector.types.FloatingPointPrecision.SINGLE;

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
     * The memory limit for allocator.
     */
    private long limit;
    /**
     * The number of rows in a record batch.
     * An Apache Arrow record batch is conceptually similar
     * to the Parquet row group. Parquet recommends a
     * disk/block/row group/file size of 512 to 1024 MB on HDFS.
     * 1 million rows x 100 columns of double will be about
     * 800 MB and also cover many use cases in machine learning.
     */
    private int batch;

    /** Constructor. */
    public Arrow() {
        this(Long.MAX_VALUE);
    }

    /**
     * Constructor.
     * @param limit the maximum amount of memory in bytes that can be allocated.
     */
    public Arrow(long limit) {
        this(limit, 1000000);
    }

    /**
     * Constructor.
     * @param limit the maximum amount of memory in bytes that can be allocated.
     * @param batch the number of rows in each record batch.
     */
    public Arrow(long limit, int batch) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Invalid limit: " + limit);
        }

        if (batch <= 0) {
            throw new IllegalArgumentException("Invalid batch size: " + batch);
        }

        this.limit = limit;
        this.batch = batch;
    }

    /**
     * Reads an arrow file.
     * @param path an Apache Arrow file path.
     */
    public DataFrame read(Path path) throws IOException {
        try (RootAllocator allocator = new RootAllocator(limit);
             FileInputStream input = new FileInputStream(path.toFile());
             ArrowFileReader reader = new ArrowFileReader(input.getChannel(), allocator)) {

            // The holder for a set of vectors to be loaded/unloaded.
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            List<ArrowBlock> blocks = reader.getRecordBlocks();
            DataFrame[] frames = new DataFrame[blocks.size()];
            for (int i = 0; i < frames.length; i++) {
                ArrowBlock block = blocks.get(i);
                if (!reader.loadRecordBatch(block)) {
                    throw new IOException("Failed to load record batch: " + block);
                }

                List<FieldVector> fieldVectors = root.getFieldVectors();
                logger.info("read {} rows and {} columns from block {}", root.getRowCount(), fieldVectors.size(), block);

                smile.data.vector.BaseVector[] vectors = new smile.data.vector.BaseVector[fieldVectors.size()];
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
                                    readDoubleField(fieldVector);
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

                frames[i] = DataFrame.of(vectors);
            }

            return frames[0];
        }
    }

    /** Writes the DataFrame to a file. */
    public void write(DataFrame df, Path path) throws IOException {
        Schema schema = toArrowSchema(df.schema());
        /**
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
        try (RootAllocator allocator = new RootAllocator(limit);
             VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
             FileOutputStream output = new FileOutputStream(path.toFile());
             ArrowFileWriter writer = new ArrowFileWriter(root, provider, output.getChannel())) {

            writer.start();
            final int size = df.size();
            for (int from = 0, entries = size; from < size; from += batch) {
                int count = Math.min(batch, entries - from);
                // set the batch row count
                root.setRowCount(count);

                for (Field field : root.getSchema().getFields()) {
                    FieldVector vector = root.getVector(field.getName());
                    DataType type = df.schema().field(field.getName()).type;
                    switch (type.id()) {
                        case Integer:
                            writeIntField(df, vector, from, count);
                            break;
                        case Long:
                            writeLongField(df, vector, from, count);
                            break;
                        case Double:
                            writeDoubleField(df, vector, from, count);
                            break;
                        case Float:
                            writeFloatField(df, vector, from, count);
                            break;
                        case Boolean:
                            writeBooleanField(df, vector, from, count);
                            break;
                        case Byte:
                            writeByteField(df, vector, from, count);
                            break;
                        case Short:
                            writeShortField(df, vector, from, count);
                            break;
                        case Char:
                            writeCharField(df, vector, from, count);
                            break;
                        case Object: {
                            Class clazz = ((ObjectType) type).getObjectClass();
                            if (clazz == Integer.class) {
                                writeIntObjectField(df, vector, from, count);
                            } else if (clazz == Long.class) {
                                writeLongObjectField(df, vector, from, count);
                            } else if (clazz == Double.class) {
                                writeDoubleObjectField(df, vector, from, count);
                            } else if (clazz == Float.class) {
                                writeFloatObjectField(df, vector, from, count);
                            } else if (clazz == Boolean.class) {
                                writeBooleanObjectField(df, vector, from, count);
                            } else if (clazz == Byte.class) {
                                writeByteObjectField(df, vector, from, count);
                            } else if (clazz == Short.class) {
                                writeShortObjectField(df, vector, from, count);
                            } else if (clazz == Character.class) {
                                writeCharObjectField(df, vector, from, count);
                            } else if (clazz == BigDecimal.class) {
                                writeDecimalField(df, vector, from, count);
                            } else if (clazz == String.class) {
                                writeStringField(df, vector, from, count);
                            } else if (clazz == LocalDate.class) {
                                writeDateField(df, vector, from, count);
                            } else if (clazz == LocalTime.class) {
                                writeTimeField(df, vector, from, count);
                            } else if (clazz == LocalDateTime.class) {
                                writeDateTimeField(df, vector, from, count);
                            } else {
                                throw new UnsupportedOperationException("Unsupported type: " + type);
                            }
                            break;
                        }
                        case Array: {
                            DataType etype = ((ArrayType) type).getComponentType();
                            switch (etype.id()) {
                                case Byte:
                                    writeByteArrayField(df, vector, from, count);
                                    break;
                                default:
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

            writer.end();
            writer.close();
            output.flush();
            output.close();
        }
    }

    /** Reads a boolean column. */
    private smile.data.vector.BaseVector readBitField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        BitVector vector = (BitVector) fieldVector;

        if (fieldVector.getField().isNullable()) {
            boolean[] a = new boolean[count];
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i) != 0;
            }

            return smile.data.vector.BooleanVector.of(fieldVector.getField().getName(), a);
        } else {
            Boolean[] a = new Boolean[count];
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i) != 0;
            }

            return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
        }
    }

    /** Reads a byte column. */
    private smile.data.vector.BaseVector readByteField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        TinyIntVector vector = (TinyIntVector) fieldVector;

        if (fieldVector.getField().isNullable()) {
            byte[] a = new byte[count];
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }

            return smile.data.vector.ByteVector.of(fieldVector.getField().getName(), a);
        } else {
            Byte[] a = new Byte[count];
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }

            return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
        }
    }

    /** Reads a char column. */
    private smile.data.vector.BaseVector readCharField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        SmallIntVector vector = (SmallIntVector) fieldVector;

        if (fieldVector.getField().isNullable()) {
            char[] a = new char[count];
            for (int i = 0; i < count; i++) {
                a[i] = (char) vector.get(i);
            }

            return smile.data.vector.CharVector.of(fieldVector.getField().getName(), a);
        } else {
            Character[] a = new Character[count];
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = (char) vector.get(i);
            }

            return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
        }
    }

    /** Reads a short column. */
    private smile.data.vector.BaseVector readShortField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        SmallIntVector vector = (SmallIntVector) fieldVector;

        if (fieldVector.getField().isNullable()) {
            short[] a = new short[count];
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }

            return smile.data.vector.ShortVector.of(fieldVector.getField().getName(), a);
        } else {
            Short[] a = new Short[count];
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }

            return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
        }
    }

    /** Reads an int column. */
    private smile.data.vector.BaseVector readIntField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        IntVector vector = (IntVector) fieldVector;

        if (fieldVector.getField().isNullable()) {
            int[] a = new int[count];
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }

            return smile.data.vector.IntVector.of(fieldVector.getField().getName(), a);
        } else {
            Integer[] a = new Integer[count];
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }

            return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
        }
    }

    /** Reads a long column. */
    private smile.data.vector.BaseVector readLongField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        BigIntVector vector = (BigIntVector) fieldVector;

        if (fieldVector.getField().isNullable()) {
            long[] a = new long[count];
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }

            return smile.data.vector.LongVector.of(fieldVector.getField().getName(), a);
        } else {
            Long[] a = new Long[count];
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }

            return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
        }
    }

    /** Reads a float column. */
    private smile.data.vector.BaseVector readFloatField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        Float4Vector vector = (Float4Vector) fieldVector;

        if (fieldVector.getField().isNullable()) {
            float[] a = new float[count];
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }

            return smile.data.vector.FloatVector.of(fieldVector.getField().getName(), a);
        } else {
            Float[] a = new Float[count];
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }

            return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
        }
    }

    /** Reads a double column. */
    private smile.data.vector.BaseVector readDoubleField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        Float8Vector vector = (Float8Vector) fieldVector;

        if (fieldVector.getField().isNullable()) {
            double[] a = new double[count];
            for (int i = 0; i < count; i++) {
                a[i] = vector.get(i);
            }

            return smile.data.vector.DoubleVector.of(fieldVector.getField().getName(), a);
        } else {
            Double[] a = new Double[count];
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }

            return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
        }
    }

    /** Reads a decmal column. */
    private smile.data.vector.BaseVector readDecimalField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        BigDecimal[] a = new BigDecimal[count];
        DecimalVector vector = (DecimalVector) fieldVector;
        for (int i = 0; i < count; i++) {
            a[i] = vector.isNull(i) ? null : vector.getObject(i);
        }

        return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
    }

    /** Reads a date column. */
    private smile.data.vector.BaseVector readDateField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        LocalDate[] a = new LocalDate[count];
        ZoneOffset zone = OffsetDateTime.now().getOffset();
        if (fieldVector instanceof DateDayVector) {
            DateDayVector vector = (DateDayVector) fieldVector;
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalDate.ofEpochDay(vector.get(i));
            }
        } else if (fieldVector instanceof DateMilliVector) {
            DateMilliVector vector = (DateMilliVector) fieldVector;
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i)), zone).toLocalDate();
            }
        }

        return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
    }

    /** Reads a time column. */
    private smile.data.vector.BaseVector readTimeField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        LocalTime[] a = new LocalTime[count];
        if (fieldVector instanceof TimeNanoVector) {
            TimeNanoVector vector = (TimeNanoVector) fieldVector;
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i));
            }
        } else if (fieldVector instanceof TimeMilliVector) {
            TimeMilliVector vector = (TimeMilliVector) fieldVector;
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i) * 1000000);
            }
        } else if (fieldVector instanceof TimeMicroVector) {
            TimeMicroVector vector = (TimeMicroVector) fieldVector;
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalTime.ofNanoOfDay(vector.get(i) * 1000);
            }
        } else if (fieldVector instanceof TimeSecVector) {
            TimeSecVector vector = (TimeSecVector) fieldVector;
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalTime.ofSecondOfDay(vector.get(i));
            }
        }
        return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
    }

    /** Reads a DateTime column. */
    private smile.data.vector.BaseVector readDateTimeField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        LocalDateTime[] a = new LocalDateTime[count];
        TimeStampVector vector = (TimeStampVector) fieldVector;
        String timezone = ((ArrowType.Timestamp) fieldVector.getField().getType()).getTimezone();
        ZoneOffset zone = timezone == null ? OffsetDateTime.now().getOffset() : ZoneOffset.of(timezone);
        if (fieldVector instanceof TimeStampMilliVector) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i)), zone);
            }
        } else if (fieldVector instanceof TimeStampNanoVector) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i)/1000000), zone);
            }
        } else if (fieldVector instanceof TimeStampMicroVector) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(vector.get(i)/1000), zone);
            }
        } else if (fieldVector instanceof TimeStampSecVector) {
            for (int i = 0; i < count; i++) {
                a[i] = vector.isNull(i) ? null : LocalDateTime.ofEpochSecond(vector.get(i), 0, zone);
            }
        }

        return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
    }

    /** Reads a byte[] column. */
    private smile.data.vector.BaseVector readByteArrayField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        byte[][] a = new byte[count][];
        if (fieldVector instanceof VarBinaryVector) {
            VarBinaryVector vector = (VarBinaryVector) fieldVector;
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }
        } else if (fieldVector instanceof FixedSizeBinaryVector){
            FixedSizeBinaryVector vector = (FixedSizeBinaryVector) fieldVector;
            for (int i = 0; i < count; i++) {
                if (vector.isNull(i))
                    a[i] = null;
                else
                    a[i] = vector.get(i);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported binary vector: " + fieldVector);
        }

        return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
    }

    /** Reads a String column. */
    private smile.data.vector.BaseVector readStringField(FieldVector fieldVector) {
        int count = fieldVector.getValueCount();
        VarCharVector vector = (VarCharVector) fieldVector;
        String[] a = new String[count];
        for (int i = 0; i < count; i++) {
            if (vector.isNull(i))
                a[i] = null;
            else
                a[i] = new String(vector.get(i));
        }

        return smile.data.vector.Vector.of(fieldVector.getField().getName(), a);
    }

    /** Writes an int column. */
    private void writeIntField(DataFrame df, FieldVector fieldVector, int from, int count) {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        IntVector vector = (IntVector) fieldVector;
        smile.data.vector.IntVector column = df.intVector(fieldVector.getField().getName());
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
        smile.data.vector.Vector<Integer> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Integer x = column.get(i);
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
        smile.data.vector.BooleanVector column = df.booleanVector(fieldVector.getField().getName());
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
        smile.data.vector.Vector<Boolean> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Boolean x = column.get(i);
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
        smile.data.vector.CharVector column = df.charVector(fieldVector.getField().getName());
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
        smile.data.vector.Vector<Character> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Character x = column.get(i);
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
        smile.data.vector.ByteVector column = df.byteVector(fieldVector.getField().getName());
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
        smile.data.vector.Vector<Byte> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Byte x = column.get(i);
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
        smile.data.vector.ShortVector column = df.shortVector(fieldVector.getField().getName());
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
        smile.data.vector.Vector<Short> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Short x = column.get(i);
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
        smile.data.vector.LongVector column = df.longVector(fieldVector.getField().getName());
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
        smile.data.vector.Vector<Long> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Long x = column.get(i);
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
        smile.data.vector.FloatVector column = df.floatVector(fieldVector.getField().getName());
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
        smile.data.vector.Vector<Float> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Float x = column.get(i);
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
        smile.data.vector.DoubleVector column = df.doubleVector(fieldVector.getField().getName());
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
        smile.data.vector.Vector<Double> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            Double x = column.get(i);
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
    private void writeStringField(DataFrame df, FieldVector fieldVector, int from, int count) throws UnsupportedEncodingException {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        VarCharVector vector = (VarCharVector) fieldVector;
        smile.data.vector.Vector<String> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            String x = column.get(j);
            if (x == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, x.getBytes("UTF-8"));
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Writes a decimal column. */
    private void writeDecimalField(DataFrame df, FieldVector fieldVector, int from, int count) throws UnsupportedEncodingException {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        DecimalVector vector = (DecimalVector) fieldVector;
        smile.data.vector.Vector<BigDecimal> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            BigDecimal x = column.get(j);
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
    private void writeDateField(DataFrame df, FieldVector fieldVector, int from, int count) throws UnsupportedEncodingException {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        DateDayVector vector = (DateDayVector) fieldVector;
        smile.data.vector.Vector<LocalDate> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            LocalDate x = column.get(j);
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
    private void writeTimeField(DataFrame df, FieldVector fieldVector, int from, int count) throws UnsupportedEncodingException {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        TimeNanoVector vector = (TimeNanoVector) fieldVector;
        smile.data.vector.Vector<LocalTime> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            LocalTime x = column.get(j);
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
    private void writeDateTimeField(DataFrame df, FieldVector fieldVector, int from, int count) throws UnsupportedEncodingException {
        fieldVector.setInitialCapacity(count);
        fieldVector.allocateNew();

        TimeStampMilliVector vector = (TimeStampMilliVector) fieldVector;
        smile.data.vector.Vector<LocalDateTime> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            LocalDateTime x = column.get(j);
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
        smile.data.vector.Vector<byte[]> column = df.vector(fieldVector.getField().getName());
        for (int i = 0, j = from; i < count; i++, j++) {
            byte[] bytes = column.get(j);
            if (bytes == null) {
                vector.setNull(i);
            } else {
                vector.setIndexDefined(i);
                vector.setSafe(i, bytes);
            }
        }

        fieldVector.setValueCount(count);
    }

    /** Converts a smile schema to arrow schema. */
    public static Schema toArrowSchema(StructType schema) {
        List<Field> fields = new ArrayList<>();
        for (StructField field : schema.fields()) {
            fields.add(toArrowField(field));
        }

        return new Schema(fields, null);
    }

    /** Converts an arrow schema to smile schema. */
    public static StructType toSmileSchema(Schema schema) {
        List<StructField> fields = new ArrayList<>();
        for (Field field : schema.getFields()) {
            fields.add(toSmileField(field));
        }

        return DataTypes.struct(fields);
    }

    /** Converts a smile struct field to arrow field. */
    public static Field toArrowField(smile.data.type.StructField field) {
        switch (field.type.id()) {
            case Integer:
                return new Field(field.name, new FieldType(false, new ArrowType.Int(32, true), null), null);
            case Long:
                return new Field(field.name, new FieldType(false, new ArrowType.Int(64, true), null), null);
            case Double:
                return new Field(field.name, new FieldType(false, new ArrowType.FloatingPoint(DOUBLE), null), null);
            case Float:
                return new Field(field.name, new FieldType(false, new ArrowType.FloatingPoint(SINGLE), null), null);
            case Boolean:
                return new Field(field.name, new FieldType(false, new ArrowType.Bool(), null), null);
            case Byte:
                return new Field(field.name, new FieldType(false, new ArrowType.Int(8, true), null), null);
            case Short:
                return new Field(field.name, new FieldType(false, new ArrowType.Int(16, true), null), null);
            case Char:
                return new Field(field.name, new FieldType(false, new ArrowType.Int(16, false), null), null);
            case Decimal:
                return new Field(field.name, FieldType.nullable(new ArrowType.Decimal(28, 10)), null);
            case String:
                return new Field(field.name, FieldType.nullable(new ArrowType.Utf8()), null);
            case Date:
                return new Field(field.name, FieldType.nullable(new ArrowType.Date(DateUnit.DAY)), null);
            case Time:
                return new Field(field.name, FieldType.nullable(new ArrowType.Time(TimeUnit.MILLISECOND, 32)), null);
            case DateTime:
                return new Field(field.name, FieldType.nullable(new ArrowType.Timestamp(TimeUnit.MILLISECOND, java.time.ZoneOffset.UTC.getId())), null);
            case Object: {
                Class clazz = ((ObjectType) field.type).getObjectClass();
                if (clazz == Integer.class) {
                    return new Field(field.name, FieldType.nullable(new ArrowType.Int(32, true)), null);
                } else if (clazz == Long.class) {
                    return new Field(field.name, FieldType.nullable(new ArrowType.Int(64, true)), null);
                } else if (clazz == Double.class) {
                    return new Field(field.name, FieldType.nullable(new ArrowType.FloatingPoint(DOUBLE)), null);
                } else if (clazz == Float.class) {
                    return new Field(field.name, FieldType.nullable(new ArrowType.FloatingPoint(SINGLE)), null);
                } else if (clazz == Boolean.class) {
                    return new Field(field.name, FieldType.nullable(new ArrowType.Bool()), null);
                } else if (clazz == Byte.class) {
                    return new Field(field.name, FieldType.nullable(new ArrowType.Int(8, true)), null);
                } else if (clazz == Short.class) {
                    return new Field(field.name, FieldType.nullable(new ArrowType.Int(16, true)), null);
                } else if (clazz == Character.class) {
                    return new Field(field.name, FieldType.nullable(new ArrowType.Int(16, false)), null);
                } else {
                    throw new UnsupportedOperationException("Unsupported smile to arrow type conversion: " + field.type);
                }
            }
            case Array: {
                DataType etype = ((ArrayType) field.type).getComponentType();
                switch (etype.id()) {
                    case Integer:
                        return new Field(field.name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Arrays.asList(new Field(null, new FieldType(false, new ArrowType.Int(32, true), null), null))
                        );
                    case Long:
                        return new Field(field.name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Arrays.asList(new Field(null, new FieldType(false, new ArrowType.Int(64, true), null), null))
                        );
                    case Double:
                        return new Field(field.name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Arrays.asList(new Field(null, new FieldType(false, new ArrowType.FloatingPoint(DOUBLE), null), null))
                        );
                    case Float:
                        return new Field(field.name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Arrays.asList(new Field(null, new FieldType(false, new ArrowType.FloatingPoint(SINGLE), null), null))
                        );
                    case Boolean:
                        return new Field(field.name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Arrays.asList(new Field(null, new FieldType(false, new ArrowType.Bool(), null), null))
                        );
                    case Byte:
                        return new Field(field.name, FieldType.nullable(new ArrowType.Binary()), null);
                    case Short:
                        return new Field(field.name,
                                new FieldType(false, new ArrowType.List(), null),
                                // children type
                                Arrays.asList(new Field(null, new FieldType(false, new ArrowType.Int(16, true), null), null))
                        );
                    case Char:
                        return new Field(field.name, FieldType.nullable(new ArrowType.Utf8()), null);
                    default:
                        throw new UnsupportedOperationException("Unsupported smile to arrow type conversion: " + field.type);
                }
            }
            case Struct: {
                smile.data.type.StructType children = (smile.data.type.StructType) field.type;
                return new Field(field.name,
                        new FieldType(false, new ArrowType.Struct(), null),
                        // children type
                        Arrays.stream(children.fields()).map(Arrow::toArrowField).collect(Collectors.toList())
                );
            }
            default:
                throw new UnsupportedOperationException("Unsupported smile to arrow type conversion: " + field.type);
        }
    }

    /** Converts an arrow field to smile struct field. */
    public static StructField toSmileField(Field field) {
        String name = field.getName();
        ArrowType type = field.getType();
        boolean nullable = field.isNullable();
        switch (type.getTypeID()) {
            case Int:
                ArrowType.Int itype = (ArrowType.Int) type;
                int bitWidth = itype.getBitWidth();
                switch (bitWidth) {
                    case  8: return new StructField(name, nullable ? DataTypes.ByteObjectType : DataTypes.ByteType);
                    case 16:
                        if (itype.getIsSigned())
                            return new StructField(name, nullable ? DataTypes.ShortObjectType : DataTypes.ShortType);
                        else
                            return new StructField(name, nullable ? DataTypes.CharObjectType : DataTypes.CharType);
                    case 32: return new StructField(name, nullable ? DataTypes.IntegerObjectType : DataTypes.IntegerType);
                    case 64: return new StructField(name, nullable ? DataTypes.LongObjectType : DataTypes.LongType);
                    default: throw new UnsupportedOperationException("Unsupported integer bit width: " + bitWidth);
                }

            case FloatingPoint:
                FloatingPointPrecision precision = ((ArrowType.FloatingPoint) type).getPrecision();
                switch (precision) {
                    case DOUBLE: return new StructField(name, nullable ? DataTypes.DoubleObjectType : DataTypes.DoubleType);
                    case SINGLE: return new StructField(name, nullable ? DataTypes.FloatObjectType : DataTypes.FloatType);
                    case HALF: throw new UnsupportedOperationException("Unsupported float precision: " + precision);
                }

            case Bool:
                return new StructField(name, nullable ? DataTypes.BooleanObjectType : DataTypes.BooleanType);

            case Decimal:
                return new StructField(name, DataTypes.DecimalType);

            case Utf8:
                return new StructField(name, DataTypes.StringType);

            case Date:
                return new StructField(name, DataTypes.DateType);

            case Time:
                return new StructField(name, DataTypes.TimeType);

            case Timestamp:
                return new StructField(name, DataTypes.DateTimeType);

            case Binary:
            case FixedSizeBinary:
                return new StructField(name, DataTypes.array(DataTypes.ByteType));

            case List:
            case FixedSizeList:
                List<Field> child = field.getChildren();
                if (child.size() != 1) {
                    throw new IllegalStateException(String.format("List type has %d child fields.", child.size()));
                }

                return new StructField(name, DataTypes.array(toSmileField(child.get(0)).type));

            case Struct:
                List<StructField> children = field.getChildren().stream().map(Arrow::toSmileField).collect(Collectors.toList());
                return new StructField(name, DataTypes.struct(children));

            default:
                throw new UnsupportedOperationException("Unsupported arrow to smile type conversion: " + type);
        }
    }
}
