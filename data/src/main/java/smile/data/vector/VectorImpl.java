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

package smile.data.vector;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import smile.data.measure.NominalScale;
import smile.data.measure.OrdinalScale;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.ObjectType;

/**
 * An immutable vector.
 *
 * @author Haifeng Li
 */
class VectorImpl<T> implements Vector<T> {
    /** The name of vector. */
    private String name;
    /** The data type of vector. */
    private DataType type;
    /** The vector data. */
    private T[] vector;

    /** Constructor. */
    public VectorImpl(String name, Class clazz, T[] vector) {
        this.name = name;
        this.type = DataTypes.object(clazz);
        this.vector = vector;
    }

    /** Constructor. */
    public VectorImpl(String name, DataType type, T[] vector) {
        this.name = name;
        this.type = type;
        this.vector = vector;
    }

    @Override
    public DataType type() {
        return type;
    }

    @Override
    public Object array() {
        return vector;
    }

    @Override
    public T get(int i) {
        return vector[i];
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int size() {
        return vector.length;
    }

    @Override
    public Stream<T> stream() {
        return Arrays.stream(vector);
    }

    @Override
    public String toString() {
        return toString(10);
    }

    @Override
    public Vector<LocalDate> toDate() {
        if (type.id() == DataType.ID.String) {
            return toDate(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        LocalDate[] dates = null;
        if (type.id() == DataType.ID.DateTime) {
            dates = stream().map(d -> ((LocalDateTime) d).toLocalDate()).toArray(LocalDate[]::new);
        } else if (type.id() == DataType.ID.Object) {
            Class clazz = ((ObjectType) type).getObjectClass();

            if (clazz == Date.class) {
                dates = stream().map(d -> ((Date) d).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()).toArray(LocalDate[]::new);
            } else if (clazz == Instant.class) {
                dates = stream().map(d -> ((Instant) d).atZone(ZoneId.systemDefault()).toLocalDate()).toArray(LocalDate[]::new);
            }
        }

        if (dates != null) {
            return new VectorImpl<>(name, DataTypes.DateType, dates);
        }

        throw new UnsupportedOperationException("Unsupported data type for toDate(): " + type);
    }

    @Override
    public Vector<LocalDate> toDate(DateTimeFormatter format) {
        if (type.id() != DataType.ID.String) {
            throw new UnsupportedOperationException("The vector is not of Strings.");
        }

        LocalDate[] dates = stream().map(s -> format.parse((String) s)).toArray(LocalDate[]::new);
        return new VectorImpl<>(name, DataTypes.DateType, dates);
    }

    @Override
    public Vector<LocalTime> toTime() {
        if (type.id() == DataType.ID.String) {
            return toTime(DateTimeFormatter.ISO_LOCAL_TIME);
        }

        LocalTime[] dates = null;
        if (type.id() == DataType.ID.DateTime) {
            dates = stream().map(d -> ((LocalDateTime) d).toLocalTime()).toArray(LocalTime[]::new);
        } else if (type.id() == DataType.ID.Object) {
            Class clazz = ((ObjectType) type).getObjectClass();

            if (clazz == Date.class) {
                dates = stream().map(d -> ((Date) d).toInstant().atZone(ZoneId.systemDefault()).toLocalTime()).toArray(LocalTime[]::new);
            } else if (clazz == Instant.class) {
                dates = stream().map(d -> ((Instant) d).atZone(ZoneId.systemDefault()).toLocalTime()).toArray(LocalTime[]::new);
            }
        }

        if (dates != null) {
            return new VectorImpl<>(name, DataTypes.TimeType, dates);
        }

        throw new UnsupportedOperationException("Unsupported data type for toTime(): " + type);
    }

    @Override
    public Vector<LocalTime> toTime(DateTimeFormatter format) {
        if (type.id() != DataType.ID.String) {
            throw new UnsupportedOperationException("The vector is not of Strings.");
        }

        LocalTime[] dates = stream().map(s -> format.parse((String) s)).toArray(LocalTime[]::new);
        return new VectorImpl<>(name, DataTypes.TimeType, dates);
    }

    @Override
    public Vector<LocalDateTime> toDateTime() {
        if (type.id() == DataType.ID.String) {
            return toDateTime(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        LocalDateTime[] dates = null;
        if (type.id() == DataType.ID.Object) {
            Class clazz = ((ObjectType) type).getObjectClass();

            if (clazz == Date.class) {
                dates = stream().map(d -> ((Date) d).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()).toArray(LocalDateTime[]::new);
            } else if (clazz == Instant.class) {
                dates = stream().map(d -> ((Instant) d).atZone(ZoneId.systemDefault()).toLocalDateTime()).toArray(LocalDateTime[]::new);
            }
        }

        if (dates != null) {
            return new VectorImpl<>(name, DataTypes.DateTimeType, dates);
        }

        throw new UnsupportedOperationException("Unsupported data type for toDateTime(): " + type);
    }

    @Override
    public Vector<LocalDateTime> toDateTime(DateTimeFormatter format) {
        if (type.id() != DataType.ID.String) {
            throw new UnsupportedOperationException("The vector is not of Strings.");
        }

        LocalDateTime[] dates = stream().map(s -> format.parse((String) s)).toArray(LocalDateTime[]::new);
        return new VectorImpl<>(name, DataTypes.DateTimeType, dates);
    }

    @Override
    public BaseVector toNominal(NominalScale scale) {
        if (type.id() != DataType.ID.String) {
            throw new UnsupportedOperationException("The vector is not of Strings.");
        }

        int[] data = stream().mapToInt(s -> s == null ? -1 : (int) scale.valueOf((String) s)).toArray();

        switch (scale.type().id()) {
            case Byte:
                byte[] bytes = new byte[data.length];
                System.arraycopy(data, 0, bytes, 0, data.length);
                return new ByteVectorImpl(name, bytes);
            case Short:
                short[] shorts = new short[data.length];
                System.arraycopy(data, 0, shorts, 0, data.length);
                return new ShortVectorImpl(name, shorts);
            case Integer:
                return new IntVectorImpl(name, data);
            default:
                // we should never reach here.
                throw new UnsupportedOperationException("Unsupported data type for nominal measure: " + scale.type());
        }
    }

    @Override
    public BaseVector toOrdinal(OrdinalScale scale) {
        if (type.id() != DataType.ID.String) {
            throw new UnsupportedOperationException("The vector is not of Strings.");
        }

        int[] data = stream().mapToInt(s -> s == null ? -1 : (int) scale.valueOf((String) s)).toArray();

        switch (scale.type().id()) {
            case Byte:
                byte[] bytes = new byte[data.length];
                System.arraycopy(data, 0, bytes, 0, data.length);
                return new ByteVectorImpl(name, bytes);
            case Short:
                short[] shorts = new short[data.length];
                System.arraycopy(data, 0, shorts, 0, data.length);
                return new ShortVectorImpl(name, shorts);
            case Integer:
                return new IntVectorImpl(name, data);
            default:
                // we should never reach here.
                throw new UnsupportedOperationException("Unsupported data type for ordinal measure: " + scale.type());
        }
    }
}