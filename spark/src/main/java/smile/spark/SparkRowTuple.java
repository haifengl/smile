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
package smile.spark;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import org.apache.spark.sql.Row;
import smile.data.Tuple;
import smile.data.type.StructType;

/**
 * An adapter wrapping an Apache Spark {@link Row} as a SMILE {@link Tuple}.
 *
 * @author Haifeng Li
 */
public class SparkRowTuple implements Tuple {
    private final StructType schema;
    private final Row row;

    /**
     * Constructor.
     *
     * @param schema the SMILE schema.
     * @param row    the Spark row.
     */
    public SparkRowTuple(StructType schema, Row row) {
        this.schema = Objects.requireNonNull(schema, "schema cannot be null");
        this.row = Objects.requireNonNull(row, "row cannot be null");
    }

    @Override
    public StructType schema() {
        return schema;
    }

    @Override
    public int length() {
        return row.size();
    }

    @Override
    public int indexOf(String name) {
        return row.fieldIndex(name);
    }

    @Override
    public boolean isNullAt(int i) {
        return row.isNullAt(i);
    }

    @Override
    public Object get(int i) {
        if (row.isNullAt(i)) return null;
        Object val = row.get(i);
        if (val instanceof org.apache.spark.ml.linalg.Vector vec) {
            return vec.toArray();
        }
        if (val instanceof org.apache.spark.mllib.linalg.Vector vec) {
            return vec.toArray();
        }
        return val;
    }

    @Override
    public boolean getBoolean(int i) {
        return row.getBoolean(i);
    }

    @Override
    public byte getByte(int i) {
        return row.getByte(i);
    }

    @Override
    public short getShort(int i) {
        return row.getShort(i);
    }

    @Override
    public int getInt(int i) {
        return row.getInt(i);
    }

    @Override
    public long getLong(int i) {
        return row.getLong(i);
    }

    @Override
    public float getFloat(int i) {
        return row.getFloat(i);
    }

    @Override
    public double getDouble(int i) {
        return row.getDouble(i);
    }

    @Override
    public BigDecimal getDecimal(int i) {
        return row.getDecimal(i);
    }

    @Override
    public String getString(int i) {
        return row.getString(i);
    }

    @Override
    public LocalDate getDate(int i) {
        if (isNullAt(i)) return null;
        Object val = row.get(i);
        if (val instanceof LocalDate d) return d;
        if (val instanceof java.sql.Date d) return d.toLocalDate();
        java.sql.Date d = row.getDate(i);
        return d != null ? d.toLocalDate() : null;
    }

    @Override
    public LocalDateTime getDateTime(int i) {
        if (isNullAt(i)) return null;
        Object val = row.get(i);
        if (val instanceof LocalDateTime dt) return dt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof java.time.Instant inst) return LocalDateTime.ofInstant(inst, ZoneId.systemDefault());
        java.sql.Timestamp ts = row.getTimestamp(i);
        return ts != null ? ts.toLocalDateTime() : null;
    }

    @Override
    public LocalTime getTime(int i) {
        if (isNullAt(i)) return null;
        Object val = row.get(i);
        if (val instanceof LocalTime t) return t;
        if (val instanceof java.sql.Time t) return t.toLocalTime();
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalTime();
        if (val instanceof LocalDateTime dt) return dt.toLocalTime();
        return null;
    }

    @Override
    public Tuple getStruct(int i) {
        if (isNullAt(i)) return null;
        Row nested = row.getStruct(i);
        if (nested == null) return null;
        StructType nestedSchema = DataTypeUtils.toSmileSchema(nested.schema());
        return new SparkRowTuple(nestedSchema, nested);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getArray(int i) {
        if (isNullAt(i)) return null;
        Object val = row.get(i);
        if (val instanceof Object[] arr) {
            return (T[]) arr;
        }
        if (val instanceof java.util.List<?> list) {
            return (T[]) list.toArray();
        }
        if (val instanceof org.apache.spark.ml.linalg.Vector vec) {
            double[] d = vec.toArray();
            Double[] boxed = new Double[d.length];
            for (int k = 0; k < d.length; k++) boxed[k] = d[k];
            return (T[]) boxed;
        }
        return Tuple.super.getArray(i);
    }

    /**
     * Returns the underlying Spark Row.
     *
     * @return the Spark row.
     */
    public Row row() {
        return row;
    }
}
