/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
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
package smile.data.type;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.arrow.vector.types.pojo.Field;
import org.apache.avro.Schema;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.schema.MessageType;
import smile.data.Tuple;
import smile.data.measure.Measure;
import smile.data.vector.ValueVector;

/**
 * Struct data type is determined by the fixed order of the fields
 * of primitive data types in the struct. An instance of a struct type
 * will be a tuple.
 *
 * @param fields The struct fields.
 * @param index The map of field name to index.
 *
 * @author Haifeng Li
 */
public record StructType(StructField[] fields, Map<String, Integer> index) implements DataType {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StructType.class);

    /**
     * Constructor.
     * @param fields the struct fields.
     */
    public StructType(List<StructField> fields) {
        this(fields.toArray(new StructField[0]));
    }

    /**
     * Constructor.
     * @param fields the struct fields.
     */
    public StructType(StructField... fields) {
        this(fields, name2Index(fields));
    }

    /**
     * Returns a map of field name to ordinal index.
     * @param fields the struct fields.
     * @return a map of field name to ordinal index.
     */
    private static Map<String, Integer> name2Index(StructField[] fields) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < fields.length; i++) {
            map.put(fields[i].name(), i);
        }
        return map;
    }

    /**
     * Returns the schema of a set of columns.
     * @param columns the columns to form a data frame.
     * @return the schema.
     */
    public static StructType of(ValueVector... columns) {
        StructField[] fields = new StructField[columns.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = columns[i].field();
        }
        return new StructType(fields);
    }

    /**
     * Returns the number of fields.
     * @return the number of fields.
     */
    public int length() {
        return fields.length;
    }

    /**
     * Return the i-th field.
     * @param i the field index.
     * @return the field.
     */
    public StructField field(int i) {
        return fields[i];
    }

    /**
     * Return the field of given name.
     * @param name the field name.
     * @return the field.
     */
    public StructField field(String name) {
        return fields[indexOf(name)];
    }

    /**
     * Return the i-th field.
     * This is an alias to {@link #field(int) field} for Scala's convenience.
     * @param i the field index.
     * @return the field.
     */
    public StructField apply(int i) {
        return fields[i];
    }

    /**
     * Return the field of given name.
     * This is an alias to {@link #field(String) field} for Scala's convenience.
     * @param name the field name.
     * @return the field.
     */
    public StructField apply(String name) {
        return fields[indexOf(name)];
    }

    /**
     * Returns the ordinal index of a field.
     * @param field the field name.
     * @return the index of field.
     */
    public int indexOf(String field) {
        return index.get(field);
    }

    /**
     * Returns the field names.
     * @return the field names.
     */
    public String[] names() {
        return Arrays.stream(fields)
                .map(StructField::name)
                .toArray(String[]::new);
    }

    /**
     * Returns the field data types.
     * @return the field data types.
     */
    public DataType[] dtypes() {
        return Arrays.stream(fields)
                .map(StructField::dtype)
                .toArray(DataType[]::new);
    }

    /**
     * Returns the field's level of measurements.
     * @return the field's level of measurements.
     */
    public Measure[] measures() {
        return Arrays.stream(fields)
                .map(StructField::measure)
                .toArray(Measure[]::new);
    }

    /**
     * Returns the lambda functions that parse field values.
     * @return the lambda functions that parse field values.
     */
    public List<Function<String, Object>> parser() {
        List<Function<String, Object>> parser = new ArrayList<>();
        for (StructField field : fields) {
            parser.add(field::valueOf);
        }
        return parser;
    }

    @Override
    public String name() {
        return Arrays.stream(fields)
                .map(field -> String.format("%s: %s", field.name(), field.dtype().name()))
                .collect(Collectors.joining(", ", "Struct[", "]"));
    }

    @Override
    public ID id() {
        return ID.Struct;
    }

    @Override
    public String toString() {
        return Arrays.toString(fields);
    }

    @Override
    public String toString(Object o) {
        if (o instanceof Tuple t) {
            return IntStream.range(0, length())
                    .mapToObj(i -> {
                        var field = fields[i];
                        String value = field.toString(t.get(i));
                        return String.format("  %s: %s", field.name(), value);
                    })
                    .collect(Collectors.joining(",\n", "{\n", "\n}"));
        } else {
            return o.toString();
        }
    }

    @Override
    public Tuple valueOf(String s) {
        // strip surrounding []
        String[] elements = s.substring(1, s.length() - 1).split(",");
        final Object[] row = new Object[fields.length];
        for (String element : elements) {
            String[] pair = element.split(":");
            int i = index.get(pair[0]);
            row[i] = fields[i].valueOf(pair[1]);
        }

        return Tuple.of(this, row);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StructType t) {
            return Arrays.equals(fields, t.fields);
        }

        return false;
    }

    /**
     * Returns the struct type of record or bean class.
     * @param clazz The class type of elements.
     * @return the struct type.
     */
    public static StructType of(Class<?> clazz) {
        if (clazz.isRecord()) {
            StructField[] fields = Arrays.stream(clazz.getRecordComponents())
                    .map(StructField::of)
                    .toArray(StructField[]::new);
            return new StructType(fields);
        } else {
            try {
                BeanInfo bean = Introspector.getBeanInfo(clazz);
                PropertyDescriptor[] props = bean.getPropertyDescriptors();
                StructField[] fields = Arrays.stream(props)
                        .filter(prop -> !prop.getName().equals("class"))
                        .map(StructField::of)
                        .toArray(StructField[]::new);

                return new StructType(fields);
            } catch (java.beans.IntrospectionException ex) {
                logger.error("Failed to introspect a bean: ", ex);
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Converts an avro schema to smile schema.
     * @param schema an avro schema.
     * @return the struct type.
     */
    public static StructType of(Schema schema) {
        List<StructField> fields = new ArrayList<>();
        for (Schema.Field field : schema.getFields()) {
            fields.add(StructField.of(field));
        }

        return new StructType(fields);
    }

    /**
     * Converts a parquet schema to smile schema.
     * @param schema a parquet schema.
     * @return the struct type.
     */
    public static StructType of(MessageType schema) {
        List<StructField> fields = new ArrayList<>();
        for (ColumnDescriptor column : schema.getColumns()) {
            fields.add(StructField.of(column));
        }

        return new StructType(fields);
    }

    /**
     * Converts an arrow schema to smile schema.
     * @param schema an arrow schema.
     * @return the struct type.
     */
    public static StructType of(org.apache.arrow.vector.types.pojo.Schema schema) {
        List<StructField> fields = new ArrayList<>();
        for (Field field : schema.getFields()) {
            fields.add(StructField.of(field));
        }

        return new StructType(fields);
    }

    /**
     * Converts smile schema to an arrow schema.
     * @return the arrow schema.
     */
    public org.apache.arrow.vector.types.pojo.Schema toArrow() {
        List<Field> fields = new ArrayList<>();
        for (StructField field : this.fields) {
            fields.add(field.toArrow());
        }

        return new org.apache.arrow.vector.types.pojo.Schema(fields, null);
    }
}
