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

package smile.data.type;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import smile.data.Tuple;
import smile.data.measure.Measure;

/**
 * Struct data type is determined by the fixed order of the fields
 * of primitive data types in the struct. An instance of a struct type
 * will be a tuple.
 *
 * @author Haifeng Li
 */
public class StructType implements DataType {

    /** Struct fields. */
    private final StructField[] fields;
    /** Field name to index map. */
    private final Map<String, Integer> index;

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
        this.fields = fields;
        index = new HashMap<>(fields.length * 4 / 3);
        for (int i = 0; i < fields.length; i++) {
            index.put(fields[i].name, i);
        }
    }

    /**
     * Returns the number of fields.
     * @return the number of fields.
     */
    public int length() {
        return fields.length;
    }

    /**
     * Returns the fields.
     * @return the fields.
     */
    public StructField[] fields() {
        return fields;
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
     * Return the field at position i.
     * @param i the field index.
     * @return the field.
     */
    public StructField field(int i) {
        return fields[i];
    }

    /**
     * Returns the index of a field.
     * @param field the field name.
     * @return the index of field.
     */
    public int indexOf(String field) {
        if (!index.containsKey(field)) {
            throw new IllegalArgumentException(String.format("Field %s doesn't exist", field));
        }
        return index.get(field);
    }

    /**
     * Returns the field name.
     * @param i the index of field.
     * @return the field name.
     */
    public String name(int i) {
        return fields[i].name;
    }

    /**
     * Returns the field names.
     * @return the field names.
     */
    public String[] names() {
        return Arrays.stream(fields)
                .map(field -> field.name)
                .toArray(String[]::new);
    }

    /**
     * Returns the field data type.
     * @param i the index of field.
     * @return the field data type.
     */
    public DataType type(int i) {
        return fields[i].type;
    }

    /**
     * Returns the field data types.
     * @return the field data types.
     */
    public DataType[] types() {
        return Arrays.stream(fields)
                .map(field -> field.type)
                .collect(java.util.stream.Collectors.toList())
                .toArray(new DataType[fields.length]);
    }

    /**
     * Returns the field's level of measurements.
     * @param i the index of field.
     * @return the field's level of measurements.
     */
    public Measure measure(int i) {
        return fields[i].measure;
    }

    /**
     * Returns the field's level of measurements.
     * @return the field's level of measurements.
     */
    public Measure[] measures() {
        return Arrays.stream(fields)
                .map(field -> field.measure)
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

    /**
     * Updates the field type to the boxed one if the field has
     * null/missing values in the data.
     *
     * @param rows a set of tuples.
     * @return the new struct with boxed field types.
     */
    public StructType boxed(Collection<Tuple> rows) {
        return new StructType(IntStream.range(0, length()).mapToObj(i -> {
            StructField field = fields[i];
            if (field.type.isPrimitive()) {
                final int idx = i;
                boolean missing = rows.stream().anyMatch(t -> t.isNullAt(idx));
                if (missing) {
                    field = new StructField(field.name, field.type.boxed(), field.measure);
                }
            }
            return field;
        }).toArray(StructField[]::new));
    }

    /**
     * Updates the field type to the primitive one.
     * @return the new struct with primitive field types.
     */
    public StructType unboxed() {
        return new StructType(IntStream.range(0, length()).mapToObj(i -> {
            StructField field = fields[i];
            if (field.type.isObject()) {
                field = new StructField(field.name, field.type.unboxed(), field.measure);
            }
            return field;
        }).toArray(StructField[]::new));
    }

    @Override
    public String name() {
        return Arrays.stream(fields)
                .map(field -> String.format("%s: %s", field.name, field.type.name()))
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
        Tuple t = (Tuple) o;
        return Arrays.stream(fields)
                .map(field -> {
                    Object v = t.get(field.name);
                    String value = v == null ? "null" : field.toString(v);
                    return String.format("  %s: %s", field.name, value);
                })
                .collect(Collectors.joining(",\n", "{\n", "\n}"));
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

        return Tuple.of(row, this);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StructType t) {
            return Arrays.equals(fields, t.fields);
        }

        return false;
    }
}
