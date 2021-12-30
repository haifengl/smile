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

package smile.data.vector;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Stream;
import smile.data.measure.CategoricalMeasure;
import smile.data.measure.NumericalMeasure;
import smile.data.measure.Measure;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import smile.data.type.ObjectType;
import smile.data.type.StructField;

/**
 * An immutable vector.
 *
 * @author Haifeng Li
 */
class VectorImpl<T> implements Vector<T> {
    /** The name of vector. */
    private final String name;
    /** The data type of vector. */
    private final DataType type;
    /** Optional measure. */
    private final Measure measure;
    /** The vector data. */
    private final T[] vector;

    /** Constructor. */
    public VectorImpl(String name, Class<?> clazz, T[] vector) {
        this.name = name;
        this.type = DataTypes.object(clazz);
        this.measure = null;
        this.vector = vector;
    }

    /** Constructor. */
    public VectorImpl(String name, DataType type, T[] vector) {
        this.name = name;
        this.type = type;
        this.measure = null;
        this.vector = vector;
    }

    /** Constructor. */
    public VectorImpl(StructField field, T[] vector) {
        if (field.measure != null) {
            if ((field.type.isIntegral() && field.measure instanceof NumericalMeasure) ||
                (field.type.isFloating() && field.measure instanceof CategoricalMeasure) ||
                (!field.type.isIntegral() && !field.type.isFloating())) {
                throw new IllegalArgumentException(String.format("Invalid measure %s for %s", field.measure, type()));
            }
        }

        this.name = field.name;
        this.type = field.type;
        this.measure = field.measure;
        this.vector = vector;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DataType type() {
        return type;
    }

    @Override
    public Measure measure() {
        return measure;
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
    public Vector<T> get(int... index) {
        @SuppressWarnings("unchecked")
        T[] v = (T[]) java.lang.reflect.Array.newInstance(vector.getClass().getComponentType(), index.length);
        for (int i = 0; i < index.length; i++) v[i] = vector[index[i]];
        return new VectorImpl<>(field(), v);
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
    public T[] toArray() {
        return vector;
    }

    @Override
    public double[] toDoubleArray() {
        if (type.isBoolean()) {
            return stream().mapToDouble(d -> d == null ? Double.NaN : ((Boolean) d ? 1.0 : 0.0)).toArray();
        } else if (type.isChar()) {
            return stream().mapToDouble(d -> d == null ? Double.NaN : (Character) d).toArray();
        } else if (type.isNumeric()) {
            return stream().mapToDouble(d -> d == null ? Double.NaN : ((Number) d).doubleValue()).toArray();
        } else {
            throw new UnsupportedOperationException(name() + ":" + type());
        }
    }

    @Override
    public double[] toDoubleArray(double[] a) {
        if (type.isBoolean()) {
            for (int i = 0; i < vector.length; i++) {
                Boolean b = (Boolean) vector[i];
                a[i] = b == null ? Double.NaN : (b ? 1.0 : 0.0);
            }
        } else if (type.isChar()) {
            for (int i = 0; i < vector.length; i++) {
                Character n = (Character) vector[i];
                a[i] = n == null ? Double.NaN : n;
            }
        } else if (type.isNumeric()) {
            for (int i = 0; i < vector.length; i++) {
                Number n = (Number) vector[i];
                a[i] = n == null ? Double.NaN : n.doubleValue();
            }
        } else {
            throw new UnsupportedOperationException(name() + ":" + type());
        }

        return a;
    }

    @Override
    public int[] toIntArray() {
        if (type.isBoolean()) {
            return stream().mapToInt(d -> d == null ? Integer.MIN_VALUE : ((Boolean) d ? 1 : 0)).toArray();
        } else if (type.isChar()) {
            return stream().mapToInt(d -> d == null ? Integer.MIN_VALUE : (Character) d).toArray();
        } else if (type.isIntegral()) {
            return stream().mapToInt(d -> d == null ? Integer.MIN_VALUE : ((Number) d).intValue()).toArray();
        } else {
            throw new UnsupportedOperationException(name() + ":" + type());
        }
    }

    @Override
    public int[] toIntArray(int[] a) {
        if (type.isBoolean()) {
            for (int i = 0; i < vector.length; i++) {
                Boolean b = (Boolean) vector[i];
                a[i] = b == null ? Integer.MIN_VALUE : (b ? 1 : 0);
            }
        } else if (type.isChar()) {
            for (int i = 0; i < vector.length; i++) {
                Character n = (Character) vector[i];
                a[i] = n == null ? Integer.MIN_VALUE : n;
            }
        } else if (type.isIntegral()) {
            for (int i = 0; i < vector.length; i++) {
                Number n = (Number) vector[i];
                a[i] = n == null ? Integer.MIN_VALUE : n.intValue();
            }
        } else {
            throw new UnsupportedOperationException(name() + ":" + type());
        }

        return a;
    }

    @Override
    public Vector<LocalDate> toDate() {
        LocalDate[] dates = null;
        if (type.id() == DataType.ID.DateTime) {
            dates = stream().map(d -> ((LocalDateTime) d).toLocalDate()).toArray(LocalDate[]::new);
        } else if (type.id() == DataType.ID.Object) {
            Class<?> clazz = ((ObjectType) type).getObjectClass();

            if (clazz == Date.class) {
                dates = stream().map(d -> ((Date) d).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()).toArray(LocalDate[]::new);
            } else if (clazz == Instant.class) {
                dates = stream().map(d -> ((Instant) d).atZone(ZoneId.systemDefault()).toLocalDate()).toArray(LocalDate[]::new);
            }
        }

        if (dates == null) {
            throw new UnsupportedOperationException("Unsupported data type for toDate(): " + type);
        }

        return new VectorImpl<>(name, DataTypes.DateType, dates);
    }

    @Override
    public Vector<LocalTime> toTime() {
        LocalTime[] dates = null;
        if (type.id() == DataType.ID.DateTime) {
            dates = stream().map(d -> ((LocalDateTime) d).toLocalTime()).toArray(LocalTime[]::new);
        } else if (type.id() == DataType.ID.Object) {
            Class<?> clazz = ((ObjectType) type).getObjectClass();

            if (clazz == Date.class) {
                dates = stream().map(d -> ((Date) d).toInstant().atZone(ZoneId.systemDefault()).toLocalTime()).toArray(LocalTime[]::new);
            } else if (clazz == Instant.class) {
                dates = stream().map(d -> ((Instant) d).atZone(ZoneId.systemDefault()).toLocalTime()).toArray(LocalTime[]::new);
            }
        }

        if (dates == null) {
            throw new UnsupportedOperationException("Unsupported data type for toTime(): " + type);
        }

        return new VectorImpl<>(name, DataTypes.TimeType, dates);
    }

    @Override
    public Vector<LocalDateTime> toDateTime() {
        LocalDateTime[] dates = null;
        if (type.id() == DataType.ID.Object) {
            Class<?> clazz = ((ObjectType) type).getObjectClass();

            if (clazz == Date.class) {
                dates = stream().map(d -> ((Date) d).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()).toArray(LocalDateTime[]::new);
            } else if (clazz == Instant.class) {
                dates = stream().map(d -> ((Instant) d).atZone(ZoneId.systemDefault()).toLocalDateTime()).toArray(LocalDateTime[]::new);
            }
        }

        if (dates == null) {
            throw new UnsupportedOperationException("Unsupported data type for toDateTime(): " + type);
        }

        return new VectorImpl<>(name, DataTypes.DateTimeType, dates);
    }
}