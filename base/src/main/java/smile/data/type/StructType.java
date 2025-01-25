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
package smile.data.type;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
public record StructType(List<StructField> fields, Map<String, Integer> index) implements DataType {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StructType.class);

    /**
     * Constructor.
     * @param fields the struct fields.
     */
    public StructType(List<StructField> fields) {
        this(new ArrayList<>(fields), name2Index(fields));
    }

    /**
     * Constructor.
     * @param fields the struct fields.
     */
    public StructType(StructField... fields) {
        this(Arrays.asList(fields));
    }

    /**
     * Returns a map of field name to ordinal index.
     * @param fields the struct fields.
     * @return a map of field name to ordinal index.
     */
    private static Map<String, Integer> name2Index(List<StructField> fields) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            if (map.putIfAbsent(fields.get(i).name(), i) != null) {
                throw new IllegalArgumentException("Duplicate field name: " + fields.get(i).name());
            }
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
        return fields.size();
    }

    /**
     * Return the i-th field.
     * @param i the field index.
     * @return the field.
     */
    public StructField field(int i) {
        return fields.get(i);
    }

    /**
     * Return the field of given name.
     * @param name the field name.
     * @return the field.
     */
    public StructField field(String name) {
        return fields.get(indexOf(name));
    }

    /**
     * Return the i-th field.
     * This is an alias to {@link #field(int) field} for Scala's convenience.
     * @param i the field index.
     * @return the field.
     */
    public StructField apply(int i) {
        return fields.get(i);
    }

    /**
     * Return the field of given name.
     * This is an alias to {@link #field(String) field} for Scala's convenience.
     * @param name the field name.
     * @return the field.
     */
    public StructField apply(String name) {
        return fields.get(indexOf(name));
    }

    /**
     * Sets a field.
     * @param i the field index.
     * @param field the new field.
     */
    public void set(int i, StructField field) {
        index.remove(fields.get(i).name());
        fields.set(i, field);
        index.put(field.name(), i);
    }

    /**
     * Sets a field.
     * This is an alias to {@link #set(int, StructField) set} for Scala's convenience.
     * @param i the field index.
     * @param field the new field.
     */
    public void update(int i, StructField field) {
        index.remove(fields.get(i).name());
        fields.set(i, field);
        index.put(field.name(), i);
    }

    /**
     * Renames a field.
     * @param name the field name.
     * @param newName the new name.
     */
    public void rename(String name, String newName) {
        Integer idx = index.get(name);
        if (idx == null) {
            throw new IllegalArgumentException("Field " + name + " not found");
        }

        fields.set(idx, fields.get(idx).withName(newName));
        index.remove(name);
        index.put(newName, idx);
    }

    /**
     * Adds a field.
     * @param field a struct field.
     */
    public void add(StructField field) {
        if (index.putIfAbsent(field.name(), fields.size()) != null) {
            throw new IllegalArgumentException("Duplicate field name: " + field.name());
        }
        fields.add(field);
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
        return fields.stream()
                .map(StructField::name)
                .toArray(String[]::new);
    }

    /**
     * Returns the field data types.
     * @return the field data types.
     */
    public DataType[] dtypes() {
        return fields.stream()
                .map(StructField::dtype)
                .toArray(DataType[]::new);
    }

    /**
     * Returns the field's level of measurements.
     * @return the field's level of measurements.
     */
    public Measure[] measures() {
        return fields.stream()
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
        return fields.stream()
                .map(field -> String.format("%s: %s", field.name(), field.dtype().name()))
                .collect(Collectors.joining(", ", "Struct(", ")"));
    }

    @Override
    public ID id() {
        return ID.Struct;
    }

    @Override
    public String toString() {
        return fields.stream()
                .map(field -> {
                    String s = String.format("  %s", field.toString());
                    var dtype = field.dtype();
                    if (dtype.isPrimitive() && !dtype.isNullable()) {
                        s += " NOT NULL";
                    }
                    return s;
                }).collect(Collectors.joining(",\n", "{\n", "\n}"));
    }

    @Override
    public String toString(Object o) {
        if (o instanceof Tuple t) {
            return IntStream.range(0, fields.size())
                    .mapToObj(i -> {
                        var field = fields.get(i);
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
        final Object[] row = new Object[fields.size()];
        for (String element : elements) {
            String[] pair = element.split(":");
            int i = index.get(pair[0]);
            row[i] = fields.get(i).valueOf(pair[1]);
        }

        return Tuple.of(this, row);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StructType t) {
            return fields.equals(t.fields);
        }

        return false;
    }

    /**
     * Returns the struct type of record or bean class.
     * @param clazz the class type of elements.
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
     * Returns the struct type of record or bean class properties.
     * @param props the record or bean class properties.
     * @return the struct type.
     */
    public static StructType of(Property[] props) {
        return new StructType(Arrays.stream(props).map(Property::field).toList());
    }

    /**
     * Returns a struct data type from JDBC result set meta data.
     * @param rs the JDBC result set.
     * @throws SQLException when JDBC operation fails.
     * @return the struct data type.
     */
    public static StructType of(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        String dbms = rs.getStatement().getConnection().getMetaData().getDatabaseProductName();
        return StructType.of(meta, dbms);
    }

    /**
     * Returns a struct data type from JDBC result set meta data.
     * @param meta the JDBC result set meta data.
     * @param dbms the name of database management system.
     * @throws SQLException when JDBC operation fails.
     * @return the struct data type.
     */
    public static StructType of(ResultSetMetaData meta, String dbms) throws SQLException {
        int ncol = meta.getColumnCount();
        StructField[] fields = new StructField[ncol];
        for (int i = 1; i <= ncol; i++) {
            String name = meta.getColumnName(i);
            DataType dtype = DataType.of(
                    JDBCType.valueOf(meta.getColumnType(i)),
                    meta.isNullable(i) == ResultSetMetaData.columnNullable,
                    dbms);
            fields[i-1] = new StructField(name, dtype);
        }

        return new StructType(fields);
    }
}
