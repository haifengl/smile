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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.util.List;
import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.DecimalMetadata;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.OriginalType;
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
     * @param path an Apache Parquet file path.
     */
    public static DataFrame read(Path path) throws IOException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a local parquet file.
     * @param path an Apache Parquet file path.
     * @param limit reads a limited number of records.
     */
    public static DataFrame read(Path path, int limit) throws IOException {
        return read(new LocalInputFile(path), limit);
    }

    /**
     * Reads a HDFS parquet file.
     * @param path an Apache Parquet file path.
     */
    public static DataFrame read(String path) throws IOException, URISyntaxException {
        return read(path, Integer.MAX_VALUE);
    }

    /**
     * Reads a HDFS parquet file.
     * @param path an Apache Parquet file path.
     * @param limit reads a limited number of records.
     */
    public static DataFrame read(String path, int limit) throws IOException, URISyntaxException {
        return read(input(path), limit);
    }

    /**
     * Reads a parquet file.
     * @param file an interface with the methods needed by Parquet
     *             to read data files. See HadoopInputFile for example.
     */
    public static DataFrame read(InputFile file) throws IOException {
        return read(file, Integer.MAX_VALUE);
    }

    /**
     * Reads a limited number of records from a parquet file.
     * @param file an interface with the methods needed by Parquet
     *             to read data files. See HadoopInputFile for example.
     * @param limit reads a limited number of records.
     */
    public static DataFrame read(InputFile file, int limit) throws IOException {
        try (ParquetFileReader reader = ParquetFileReader.open(file)) {
            ParquetMetadata footer = reader.getFooter();
            MessageType schema = footer.getFileMetaData().getSchema();
            StructType struct = toSmileSchema(schema);
            logger.debug("The meta data of parquet file {}: {}", file.toString(), ParquetMetadata.toPrettyJSON(footer));

            int nrows = (int) Math.min(reader.getRecordCount(), limit);
            List<Tuple> rows = new ArrayList<>(nrows);

            PageReadStore store;
            while ((store = reader.readNextRowGroup()) != null) {
                final long rowCount = store.getRowCount();
                final MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
                final RecordReader<Group> recordReader = columnIO.getRecordReader(store, new GroupRecordConverter(schema));
                for (int i = 0; i < rowCount && rows.size() < nrows; i++) {
                    rows.add(Tuple.of(group2object(recordReader.read(), schema.getColumns(), struct), struct));
                }
            }

            return DataFrame.of(rows);
        }
    }

    /** Returns the Parquet's InputFile instance of a file path or URI. */
    private static InputFile input(String path) throws IOException, URISyntaxException {
        URI uri = new URI(path);
        if (uri.getScheme() == null) return new LocalInputFile(Paths.get(path));

        switch (uri.getScheme().toLowerCase()) {
            case "file":
                return new LocalInputFile(Paths.get(path));

            case "s3":
            case "s3a":
            case "s3n":
            case "hdfs":
                Configuration conf = new Configuration();
                FileSystem fs = FileSystem.get(conf);
                return HadoopInputFile.fromPath(new org.apache.hadoop.fs.Path(path), new org.apache.hadoop.conf.Configuration());

            default: // http, ftp, ...
                throw new IllegalArgumentException("Unsupported URI schema for Parquet files: " + path);
        }
    }

    private static Object[] group2object(Group g, List<ColumnDescriptor> columns, StructType schema) {
        int length = schema.length();
        Object[] o = new Object[length];
        for (int i = 0; i < length; i++) {
            int rep = g.getFieldRepetitionCount(i);
            StructField field = schema.field(i);
            ColumnDescriptor column = columns.get(i);
            PrimitiveType primitiveType = column.getPrimitiveType();
            OriginalType originalType = primitiveType.getOriginalType();

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
                    if (originalType == null) {
                        if (rep == 1) {
                            o[i] = g.getInteger(i, 0);
                        } else if (rep > 1) {
                            int[] a = new int[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = g.getInteger(i, j);
                            o[i] = a;
                        }
                        break;
                    }

                    switch (originalType) {
                        case INT_8:
                            if (rep == 1) {
                                o[i] = (byte) g.getInteger(i, 0);
                            } else if (rep > 1) {
                                byte[] a = new byte[rep];
                                for (int j = 0; j < rep; j++)
                                    a[j] = (byte) g.getInteger(i, j);
                                o[i] = a;
                            }
                            break;
                        case UINT_8:
                        case INT_16:
                            if (rep == 1) {
                                o[i] = (short) g.getInteger(i, 0);
                            } else if (rep > 1) {
                                short[] a = new short[rep];
                                for (int j = 0; j < rep; j++)
                                    a[j] = (short) g.getInteger(i, j);
                                o[i] = a;
                            }
                            break;
                        case UINT_16:
                        case INT_32:
                            if (rep == 1) {
                                o[i] = g.getInteger(i, 0);
                            } else if (rep > 1) {
                                int[] a = new int[rep];
                                for (int j = 0; j < rep; j++)
                                    a[j] = g.getInteger(i, j);
                                o[i] = a;
                            }
                            break;
                        case DECIMAL:
                            if (rep == 1) {
                                int unscaledValue = g.getInteger(i, 0);
                                DecimalMetadata decimalMetadata = primitiveType.getDecimalMetadata();
                                int scale = decimalMetadata.getScale();
                                o[i] = BigDecimal.valueOf(unscaledValue, scale);
                            }
                            break;
                        case DATE:
                            if (rep == 1) {
                                int days = g.getInteger(i, 0);
                                o[i] = LocalDate.ofEpochDay(days);
                            }
                            break;
                        case TIME_MILLIS:
                            if (rep == 1) {
                                int millis = g.getInteger(i, 0);
                                o[i] = LocalTime.ofNanoOfDay(millis * 1000000);
                            }
                            break;
                    }
                    break;

                case INT64:
                    if (originalType == null) {
                        if (rep == 1) {
                            o[i] = g.getLong(i, 0);
                        } else if (rep > 1) {
                            long[] a = new long[rep];
                            for (int j = 0; j < rep; j++)
                                a[j] = g.getLong(i, j);
                            o[i] = a;
                        }
                        break;
                    }

                    switch (originalType) {
                        case INT_64:
                            if (rep == 1) {
                                o[i] = g.getLong(i, 0);
                            } else if (rep > 1) {
                                long[] a = new long[rep];
                                for (int j = 0; j < rep; j++)
                                    a[j] = g.getLong(i, j);
                                o[i] = a;
                            }
                            break;
                        case DECIMAL:
                            if (rep == 1) {
                                long unscaledValue = g.getLong(i, 0);
                                DecimalMetadata decimalMetadata = primitiveType.getDecimalMetadata();
                                int scale = decimalMetadata.getScale();
                                o[i] = BigDecimal.valueOf(unscaledValue, scale);
                            }
                            break;
                        case TIME_MICROS:
                            if (rep == 1) {
                                long micros = g.getLong(i, 0);
                                o[i] = LocalTime.ofNanoOfDay(micros * 1000);
                            }
                            break;
                        case TIMESTAMP_MILLIS:
                            if (rep == 1) {
                                long millis = g.getLong(i, 0);
                                o[i] = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
                            }
                            break;
                        case TIMESTAMP_MICROS:
                            if (rep == 1) {
                                long micros = g.getLong(i, 0);
                                long second = micros / 1000000;
                                int nano = (int) (micros % 1000000) * 1000;
                                o[i] = LocalDateTime.ofEpochSecond(second, nano, ZoneOffset.UTC);
                            }
                            break;
                    }
                    break;

                case INT96:
                    if (rep == 1) {
                        ByteBuffer buf = g.getInt96(i, 0).toByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
                        long nanoOfDay = buf.getLong();
                        int julianDay = buf.getInt();
                        // see http://stackoverflow.com/questions/466321/convert-unix-timestamp-to-julian
                        // it's 2440587.5, rounding up to compatible with Hive
                        LocalDate date = LocalDate.ofEpochDay(julianDay - 2440588);
                        LocalTime time = LocalTime.ofNanoOfDay(nanoOfDay);
                        o[i] = LocalDateTime.of(date, time);
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

                case BINARY:
                case FIXED_LEN_BYTE_ARRAY:
                    switch (field.type.id()) {
                        case String:
                            if (rep == 1) {
                                o[i] = g.getString(i, 0);
                            } else if (rep > 1) {
                                String[] a = new String[rep];
                                for (int j = 0; j < rep; j++)
                                    a[j] = g.getString(i, j);
                                o[i] = a;
                            }
                            break;
                        case Decimal:
                            if (rep == 1) {
                                byte[] value = g.getBinary(i, 0).getBytes();
                                DecimalMetadata decimalMetadata = primitiveType.getDecimalMetadata();
                                int scale = decimalMetadata.getScale();
                                o[i] = new BigDecimal(new BigInteger(value), scale);
                            }
                            break;
                        default:
                            if (rep >= 1) {
                                o[i] = g.getBinary(i, 0).getBytes();
                            }
                            break;
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
        OriginalType originalType = primitiveType.getOriginalType();
        Type.Repetition repetition = primitiveType.getRepetition();

        switch (primitiveType.getPrimitiveTypeName()) {
            case BOOLEAN:
                switch (repetition) {
                    case REQUIRED:
                        return new StructField(name, DataTypes.BooleanType);
                    case OPTIONAL:
                        return new StructField(name, DataTypes.BooleanObjectType);
                    case REPEATED:
                        return new StructField(name, DataTypes.BooleanArrayType);
                }

            case INT32:
                if (originalType == null) {
                    switch (repetition) {
                        case REQUIRED:
                            return new StructField(name, DataTypes.IntegerType);
                        case OPTIONAL:
                            return new StructField(name, DataTypes.IntegerObjectType);
                        case REPEATED:
                            return new StructField(name, DataTypes.IntegerArrayType);
                    }
                }

                switch (originalType) {
                    case INT_8:
                        switch (repetition) {
                            case REQUIRED:
                                return new StructField(name, DataTypes.ByteType);
                            case OPTIONAL:
                                return new StructField(name, DataTypes.ByteObjectType);
                            case REPEATED:
                                return new StructField(name, DataTypes.ByteArrayType);
                        }
                    case UINT_8:
                    case INT_16:
                        switch (repetition) {
                            case REQUIRED:
                                return new StructField(name, DataTypes.ShortType);
                            case OPTIONAL:
                                return new StructField(name, DataTypes.ShortObjectType);
                            case REPEATED:
                                return new StructField(name, DataTypes.ShortArrayType);
                        }
                    case UINT_16:
                    case INT_32:
                        switch (repetition) {
                            case REQUIRED:
                                return new StructField(name, DataTypes.IntegerType);
                            case OPTIONAL:
                                return new StructField(name, DataTypes.IntegerObjectType);
                            case REPEATED:
                                return new StructField(name, DataTypes.IntegerArrayType);
                        }
                    case DECIMAL:
                        return new StructField(name, DataTypes.DecimalType);
                    case DATE:
                        return new StructField(name, DataTypes.DateType);
                    case TIME_MILLIS:
                        return new StructField(name, DataTypes.TimeType);
                }
                break;

            case INT64:
                if (originalType == null) {
                    switch (repetition) {
                        case REQUIRED:
                            return new StructField(name, DataTypes.LongType);
                        case OPTIONAL:
                            return new StructField(name, DataTypes.LongObjectType);
                        case REPEATED:
                            return new StructField(name, DataTypes.LongArrayType);
                    }
                }

                switch (originalType) {
                    case INT_64:
                        switch (repetition) {
                            case REQUIRED:
                                return new StructField(name, DataTypes.LongType);
                            case OPTIONAL:
                                return new StructField(name, DataTypes.LongObjectType);
                            case REPEATED:
                                return new StructField(name, DataTypes.LongArrayType);
                        }
                    case DECIMAL:
                        return new StructField(name, DataTypes.DecimalType);
                    case TIME_MICROS:
                        return new StructField(name, DataTypes.TimeType);
                    case TIMESTAMP_MILLIS:
                    case TIMESTAMP_MICROS:
                        return new StructField(name, DataTypes.DateTimeType);
                }
                break;

            case INT96:
                return new StructField(name, DataTypes.DateTimeType);

            case FLOAT:
                switch (repetition) {
                    case REQUIRED:
                        return new StructField(name, DataTypes.FloatType);
                    case OPTIONAL:
                        return new StructField(name, DataTypes.FloatObjectType);
                    case REPEATED:
                        return new StructField(name, DataTypes.FloatArrayType);
                }

            case DOUBLE:
                switch (repetition) {
                    case REQUIRED:
                        return new StructField(name, DataTypes.DoubleType);
                    case OPTIONAL:
                        return new StructField(name, DataTypes.DoubleObjectType);
                    case REPEATED:
                        return new StructField(name, DataTypes.DoubleArrayType);
                }

            case BINARY:
            case FIXED_LEN_BYTE_ARRAY :
                if (originalType == null) {
                    return new StructField(name, DataTypes.ByteArrayType);
                }

                switch (originalType) {
                    case UTF8:
                    case JSON:
                        return new StructField(name, DataTypes.StringType);
                    case DECIMAL:
                        return new StructField(name, DataTypes.DecimalType);
                    default:
                        return new StructField(name, DataTypes.ByteArrayType);
                }
        }

        throw new UnsupportedOperationException("Unsupported OriginalType " + originalType + " for primitive type " + primitiveType);
    }
}
