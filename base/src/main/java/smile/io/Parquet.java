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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.*;

/**
 * Apache Parquet is a columnar storage format that supports
 * nested data structures. It uses the record shredding and
 * assembly algorithm described in the Dremel paper.
 *
 * @author Haifeng Li
 */
public class Parquet {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Parquet.class);

    /** Private constructor to prevent object creation. */
    private Parquet() {

    }

    /**
     * Reads a local parquet file.
     * @param path the input file path.
     * @throws IOException when fails to write the file.
     * @return the data frame.
     */
    public static DataFrame read(Path path) throws IOException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a local parquet file.
     * @param path the input file path.
     * @param limit the number of records to read.
     * @throws IOException when fails to write the file.
     * @return the data frame.
     */
    public static DataFrame read(Path path, int limit) throws IOException {
        return read(new LocalInputFile(path), limit);
    }

    /**
     * Reads a HDFS parquet file.
     * @param path the input file path.
     * @throws IOException when fails to write the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    public static DataFrame read(String path) throws IOException, URISyntaxException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a HDFS parquet file.
     * @param path the input file path.
     * @param limit the number of records to read.
     * @throws IOException when fails to write the file.
     * @throws URISyntaxException when the file path syntax is wrong.
     * @return the data frame.
     */
    public static DataFrame read(String path, int limit) throws IOException, URISyntaxException {
        return read(HadoopInput.file(path), limit);
    }

    /**
     * Reads a parquet file.
     * @param file an interface with the methods needed by Parquet
     *             to read data files. See HadoopInputFile for example.
     * @throws IOException when fails to write the file.
     * @return the data frame.
     */
    public static DataFrame read(InputFile file) throws IOException {
        return read(file, Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from a parquet file.
     * @param file an interface with the methods needed by Parquet
     *             to read data files. See HadoopInputFile for example.
     * @param limit the number of records to read.
     * @throws IOException when fails to write the file.
     * @return the data frame.
     */
    public static DataFrame read(InputFile file, int limit) throws IOException {
        try (ParquetFileReader reader = ParquetFileReader.open(file)) {
            ParquetMetadata footer = reader.getFooter();
            MessageType schema = footer.getFileMetaData().getSchema();
            StructType struct = toSmileSchema(schema);
            logger.debug("The meta data of parquet file {}: {}", file, ParquetMetadata.toPrettyJSON(footer));

            int nrow = (int) Math.min(reader.getRecordCount(), limit);
            List<Tuple> rows = new ArrayList<>(nrow);

            PageReadStore store;
            while ((store = reader.readNextRowGroup()) != null) {
                final long rowCount = store.getRowCount();
                final MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
                final RecordReader<Group> recordReader = columnIO.getRecordReader(store, new GroupRecordConverter(schema));
                for (int i = 0; i < rowCount && rows.size() < nrow; i++) {
                    rows.add(Tuple.of(readRowGroup(recordReader.read(), schema.getColumns(), struct), struct));
                }
            }

            return DataFrame.of(rows);
        }
    }

    private static Object[] readRowGroup(Group g, List<ColumnDescriptor> columns, StructType schema) {
        int length = schema.length();
        Object[] o = new Object[length];
        for (int i = 0; i < length; i++) {
            int rep = g.getFieldRepetitionCount(i);
            ColumnDescriptor column = columns.get(i);
            PrimitiveType primitiveType = column.getPrimitiveType();
            LogicalTypeAnnotation logicalType = primitiveType.getLogicalTypeAnnotation();

            switch (primitiveType.getPrimitiveTypeName()) {
                case BOOLEAN:
                    if (rep == 1) {
                        o[i] = g.getBoolean(i, 0);
                    } else if (rep > 1) {
                        boolean[] a = new boolean[rep];
                        for (int j = 0; j < rep; j++)
                            a[j] = g.getBoolean(i, j);
                        o[i] = a;
                    }
                    break;

                case INT32:
                    if (logicalType == null || logicalType instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation) {
                        if (rep == 1) {
                            o[i] = g.getInteger(i, 0);
                        } else if (rep > 1) {
                            int[] a = new int[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = g.getInteger(i, j);
                            o[i] = a;
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
                        int scale = ((LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) logicalType).getScale();
                        if (rep == 1) {
                            o[i] = BigDecimal.valueOf(g.getInteger(i, 0), scale);
                        } else if (rep > 1) {
                            BigDecimal[] a = new BigDecimal[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = BigDecimal.valueOf(g.getInteger(i, j), scale);
                            o[i] = a;
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) {
                        if (rep == 1) {
                            o[i] = LocalDate.ofEpochDay(g.getInteger(i, 0));
                        } else if (rep > 1) {
                            LocalDate[] a = new LocalDate[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = LocalDate.ofEpochDay(g.getInteger(i, j));
                            o[i] = a;
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation) {
                        LogicalTypeAnnotation.TimeLogicalTypeAnnotation timeType = (LogicalTypeAnnotation.TimeLogicalTypeAnnotation) logicalType;
                        if (timeType.getUnit() != LogicalTypeAnnotation.TimeUnit.MILLIS) {
                            throw new IllegalStateException("Invalid TimeUnit for INT32: " + timeType.getUnit());
                        }

                        if (rep == 1) {
                            o[i] = LocalTime.ofNanoOfDay(g.getInteger(i, 0) * 1000000L);
                        } else if (rep > 1) {
                            LocalTime[] a = new LocalTime[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = LocalTime.ofNanoOfDay(g.getInteger(i, j) * 1000000L);
                            o[i] = a;
                        }
                    } else {
                        throw new IllegalStateException("Invalid logical type for INT32: " + logicalType);
                    }
                    break;

                case INT64:
                    if (logicalType == null || logicalType instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation) {
                        if (rep == 1) {
                            o[i] = g.getLong(i, 0);
                        } else if (rep > 1) {
                            long[] a = new long[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = g.getLong(i, j);
                            o[i] = a;
                        }
                        break;
                    } else if (logicalType instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
                        int scale = ((LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) logicalType).getScale();
                        if (rep == 1) {
                            o[i] = BigDecimal.valueOf(g.getLong(i, 0), scale);
                        } else if (rep > 1) {
                            BigDecimal[] a = new BigDecimal[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = BigDecimal.valueOf(g.getLong(i, j), scale);
                            o[i] = a;
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation) {
                        LogicalTypeAnnotation.TimeLogicalTypeAnnotation timeType = (LogicalTypeAnnotation.TimeLogicalTypeAnnotation) logicalType;
                        switch (timeType.getUnit()) {
                            case MILLIS:
                                throw new IllegalStateException("Invalid TimeUnit for INT64: " + timeType.getUnit());
                            case MICROS:
                                if (rep == 1) {
                                    o[i] = LocalTime.ofNanoOfDay(g.getLong(i, 0) * 1000);
                                } else if (rep > 1) {
                                    LocalTime[] a = new LocalTime[rep];
                                    for (int j = 0; j < rep; j++)
                                        a[j] = LocalTime.ofNanoOfDay(g.getLong(i, j) * 1000);
                                    o[i] = a;
                                }
                                break;
                            case NANOS:
                                if (rep == 1) {
                                    o[i] = LocalTime.ofNanoOfDay(g.getLong(i, 0));
                                } else if (rep > 1) {
                                    LocalTime[] a = new LocalTime[rep];
                                    for (int j = 0; j < rep; j++)
                                        a[j] = LocalTime.ofNanoOfDay(g.getLong(i, j));
                                    o[i] = a;
                                }
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation) {
                        LogicalTypeAnnotation.TimestampLogicalTypeAnnotation timeType = (LogicalTypeAnnotation.TimestampLogicalTypeAnnotation) logicalType;
                        ZoneId zone = timeType.isAdjustedToUTC() ? ZoneOffset.UTC : ZoneId.systemDefault();
                        switch (timeType.getUnit()) {
                            case MILLIS:
                                if (rep == 1) {
                                    o[i] = LocalDateTime.ofInstant(Instant.ofEpochMilli(g.getLong(i, 0)), zone);
                                } else if (rep > 1) {
                                    LocalDateTime[] a = new LocalDateTime[rep];
                                    for (int j = 0; j < rep; j++)
                                        a[j] = LocalDateTime.ofInstant(Instant.ofEpochMilli(g.getLong(i, j)), zone);
                                    o[i] = a;
                                }
                                break;
                            case MICROS:
                                if (rep == 1) {
                                    long micros = g.getLong(i, 0);
                                    o[i] = LocalDateTime.ofInstant(Instant.ofEpochSecond(micros / 1000000, (int) (micros % 100000) * 1000), zone);
                                } else if (rep > 1) {
                                    LocalDateTime[] a = new LocalDateTime[rep];
                                    for (int j = 0; j < rep; j++) {
                                        long micros = g.getLong(i, j);
                                        a[j] = LocalDateTime.ofInstant(Instant.ofEpochSecond(micros / 1000000, (int) (micros % 100000) * 1000), zone);
                                    }
                                    o[i] = a;
                                }
                                break;
                            case NANOS:
                                if (rep == 1) {
                                    long nanos = g.getLong(i, 0);
                                    o[i] = LocalDateTime.ofInstant(Instant.ofEpochSecond(nanos / 1000000000, (int) (nanos % 100000000)), zone);
                                } else if (rep > 1) {
                                    LocalDateTime[] a = new LocalDateTime[rep];
                                    for (int j = 0; j < rep; j++) {
                                        long nanos = g.getLong(i, j);
                                        a[j] = LocalDateTime.ofInstant(Instant.ofEpochSecond(nanos / 1000000000, (int) (nanos % 100000000)), zone);
                                    }
                                    o[i] = a;
                                }
                        }
                    } else {
                        throw new IllegalStateException("Invalid logical type for INT64: " + logicalType);
                    }
                    break;

                case INT96:
                    // see http://stackoverflow.com/questions/466321/convert-unix-timestamp-to-julian
                    // it's 2440587.5, rounding up to compatible with Hive
                    if (rep == 1) {
                        ByteBuffer buf = g.getInt96(i, 0).toByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
                        long nanoOfDay = buf.getLong();
                        int julianDay = buf.getInt();
                        LocalDate date = LocalDate.ofEpochDay(julianDay - 2440588);
                        LocalTime time = LocalTime.ofNanoOfDay(nanoOfDay);
                        o[i] = LocalDateTime.of(date, time);
                    } else if (rep > 1) {
                        LocalDateTime[] a = new LocalDateTime[rep];
                        for (int j = 0; j < rep; j++) {
                            ByteBuffer buf = g.getInt96(i, 0).toByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
                            long nanoOfDay = buf.getLong();
                            int julianDay = buf.getInt();
                            LocalDate date = LocalDate.ofEpochDay(julianDay - 2440588);
                            LocalTime time = LocalTime.ofNanoOfDay(nanoOfDay);
                            a[j] = LocalDateTime.of(date, time);
                        }
                        o[i] = a;
                    }
                    break;

                case FLOAT:
                    if (rep == 1) {
                        o[i] = g.getFloat(i, 0);
                    } else if (rep > 1) {
                        float[] a = new float[rep];
                        for (int j = 0; j < rep; j++)
                            a[j] = g.getFloat(i, j);
                        o[i] = a;
                    }
                    break;

                case DOUBLE:
                    if (rep == 1) {
                        o[i] = g.getDouble(i, 0);
                    } else if (rep > 1) {
                        double[] a = new double[rep];
                        for (int j = 0; j < rep; j++)
                            a[j] = g.getDouble(i, j);
                        o[i] = a;
                    }
                    break;

                case FIXED_LEN_BYTE_ARRAY:
                    if (logicalType instanceof LogicalTypeAnnotation.UUIDLogicalTypeAnnotation) {
                        if (rep == 1) {
                            byte[] bytes = g.getBinary(i, 0).getBytes();
                            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                            long high = byteBuffer.getLong();
                            long low = byteBuffer.getLong();
                            o[i] = new UUID(high, low);
                        } else if (rep > 1) {
                            UUID[] a = new UUID[rep];
                            for (int j = 0; j < rep; j++) {
                                byte[] bytes = g.getBinary(i, j).getBytes();
                                ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                                long high = byteBuffer.getLong();
                                long low = byteBuffer.getLong();
                                a[j] = new UUID(high, low);
                            }
                            o[i] = a;
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.IntervalLogicalTypeAnnotation) {
                        if (rep == 1) {
                            byte[] bytes = g.getBinary(i, 0).getBytes();
                            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                            int months = byteBuffer.getInt();
                            int days = byteBuffer.getInt();
                            int millis = byteBuffer.getInt();
                            Duration duration = Duration.ofDays(days);
                            o[i] = duration.plusDays(months * 30L).plusMillis(millis);
                        } else if (rep > 1) {
                            Duration[] a = new Duration[rep];
                            for (int j = 0; j < rep; j++) {
                                byte[] bytes = g.getBinary(i, j).getBytes();
                                ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
                                int months = byteBuffer.getInt();
                                int days = byteBuffer.getInt();
                                int millis = byteBuffer.getInt();
                                Duration duration = Duration.ofDays(days);
                                a[j] = duration.plusDays(months * 30L).plusMillis(millis);
                            }
                            o[i] = a;
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
                        int scale = ((LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) logicalType).getScale();
                        if (rep == 1) {
                            byte[] value = g.getBinary(i, 0).getBytes();
                            o[i] = new BigDecimal(new BigInteger(value), scale);
                        } else if (rep > 1) {
                            BigDecimal[] a = new BigDecimal[rep];
                            for (int j = 0; j < rep; j++) {
                                byte[] value = g.getBinary(i, j).getBytes();
                                a[j] = new BigDecimal(new BigInteger(value), scale);
                            }
                            o[i] = a;
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation) {
                        if (rep == 1) {
                            o[i] = g.getString(i, 0);
                        } else if (rep > 1) {
                            String[] a = new String[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = g.getString(i, j);
                            o[i] = a;
                        }
                    } else {
                        if (rep == 1) {
                            o[i] = g.getBinary(i, 0).getBytes();
                        } else if (rep > 1) {
                            byte[][] a = new byte[rep][];
                            for (int j = 0; j < rep; j++) {
                                a[j] = g.getBinary(i, j).getBytes();
                            }
                            o[i] = a;
                        }
                    }
                    break;

                case BINARY:
                    if (logicalType instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
                        int scale = ((LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) logicalType).getScale();
                        if (rep == 1) {
                            byte[] value = g.getBinary(i, 0).getBytes();
                            o[i] = new BigDecimal(new BigInteger(value), scale);
                        } else if (rep > 1) {
                            BigDecimal[] a = new BigDecimal[rep];
                            for (int j = 0; j < rep; j++) {
                                byte[] value = g.getBinary(i, j).getBytes();
                                a[j] = new BigDecimal(new BigInteger(value), scale);
                            }
                            o[i] = a;
                        }
                    } else if (logicalType instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation || logicalType instanceof LogicalTypeAnnotation.JsonLogicalTypeAnnotation) {
                        if (rep == 1) {
                            o[i] = g.getString(i, 0);
                        } else if (rep > 1) {
                            String[] a = new String[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = g.getString(i, j);
                            o[i] = a;
                        }
                    } else {
                        if (rep == 1) {
                            o[i] = g.getBinary(i, 0).getBytes();
                        } else if (rep > 1) {
                            byte[][] a = new byte[rep][];
                            for (int j = 0; j < rep; j++) {
                                a[j] = g.getBinary(i, j).getBytes();
                            }
                            o[i] = a;
                        }
                    }
                    break;
            }
        }
        return o;
    }

    /** Converts a parquet schema to smile schema. */
    private static StructType toSmileSchema(MessageType schema) {
        List<StructField> fields = new ArrayList<>();
        for (ColumnDescriptor column : schema.getColumns()) {
            fields.add(toSmileField(column));
        }

        return DataTypes.struct(fields);
    }

    /** Converts a parquet column to smile struct field. */
    private static StructField toSmileField(ColumnDescriptor column) {
        String name = String.join(".", column.getPath());
        PrimitiveType primitiveType = column.getPrimitiveType();
        LogicalTypeAnnotation logicalType = primitiveType.getLogicalTypeAnnotation();
        Type.Repetition repetition = primitiveType.getRepetition();

        switch (primitiveType.getPrimitiveTypeName()) {
            case BOOLEAN:
                return switch (repetition) {
                    case REQUIRED -> new StructField(name, DataTypes.BooleanType);
                    case OPTIONAL -> new StructField(name, DataTypes.BooleanObjectType);
                    case REPEATED -> new StructField(name, DataTypes.BooleanArrayType);
                };

            case INT32:
                if (logicalType == null || logicalType instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation) {
                    return switch (repetition) {
                        case REQUIRED -> new StructField(name, DataTypes.IntegerType);
                        case OPTIONAL -> new StructField(name, DataTypes.IntegerObjectType);
                        case REPEATED -> new StructField(name, DataTypes.IntegerArrayType);
                    };
                } else if (logicalType instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
                    switch (repetition) {
                        case REQUIRED:
                        case OPTIONAL:
                            return new StructField(name, DataTypes.DecimalType);
                    }
                } else if (logicalType instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) {
                    switch (repetition) {
                        case REQUIRED:
                        case OPTIONAL:
                            return new StructField(name, DataTypes.DateType);
                    }
                } else if (logicalType instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation) {
                    switch (repetition) {
                        case REQUIRED:
                        case OPTIONAL:
                            return new StructField(name, DataTypes.TimeType);
                    }
                }
                break;

            case INT64:
                if (logicalType == null || logicalType instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation) {
                    return switch (repetition) {
                        case REQUIRED -> new StructField(name, DataTypes.LongType);
                        case OPTIONAL -> new StructField(name, DataTypes.LongObjectType);
                        case REPEATED -> new StructField(name, DataTypes.LongArrayType);
                    };
                } else if (logicalType instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
                    switch (repetition) {
                        case REQUIRED:
                        case OPTIONAL:
                            return new StructField(name, DataTypes.DecimalType);
                    }
                } else if (logicalType instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation) {
                    switch (repetition) {
                        case REQUIRED:
                        case OPTIONAL:
                            return new StructField(name, DataTypes.TimeType);
                    }
                } else if (logicalType instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation) {
                    switch (repetition) {
                        case REQUIRED:
                        case OPTIONAL:
                            return new StructField(name, DataTypes.DateTimeType);
                    }
                }
                break;

            case INT96:
                return new StructField(name, DataTypes.DateTimeType);

            case FLOAT:
                return switch (repetition) {
                    case REQUIRED -> new StructField(name, DataTypes.FloatType);
                    case OPTIONAL -> new StructField(name, DataTypes.FloatObjectType);
                    case REPEATED -> new StructField(name, DataTypes.FloatArrayType);
                };

            case DOUBLE:
                return switch (repetition) {
                    case REQUIRED -> new StructField(name, DataTypes.DoubleType);
                    case OPTIONAL -> new StructField(name, DataTypes.DoubleObjectType);
                    case REPEATED -> new StructField(name, DataTypes.DoubleArrayType);
                };

            case FIXED_LEN_BYTE_ARRAY :
                if (logicalType instanceof LogicalTypeAnnotation.UUIDLogicalTypeAnnotation) {
                    return new StructField(name, DataTypes.ObjectType);
                } else if (logicalType instanceof LogicalTypeAnnotation.IntervalLogicalTypeAnnotation) {
                    return new StructField(name, DataTypes.ObjectType);
                } else if (logicalType instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
                    return new StructField(name, DataTypes.DecimalType);
                } else if (logicalType instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation) {
                    return new StructField(name, DataTypes.StringType);
                } else {
                    return new StructField(name, DataTypes.ByteArrayType);
                }

            case BINARY:
                if (logicalType instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
                    return new StructField(name, DataTypes.DecimalType);
                } else if (logicalType instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation) {
                    return new StructField(name, DataTypes.StringType);
                } else {
                    return new StructField(name, DataTypes.ByteArrayType);
                }
        }

        throw new UnsupportedOperationException("Unsupported LogicalType " + logicalType + " for primitive type " + primitiveType);
    }
}
