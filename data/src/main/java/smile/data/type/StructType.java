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
package smile.data.type;

import java.util.*;
import java.util.stream.Collectors;
import smile.data.Tuple;
import smile.data.measure.DiscreteMeasure;
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
    /** Optional scale of measurement of fields. */
    private final Map<String, Measure> measure;

    /**
     * Constructor.
     */
    StructType(List<StructField> fields) {
        this(fields.toArray(new StructField[fields.size()]));
    }

    /**
     * Constructor.
     */
    StructType(StructField... fields) {
        this.fields = fields;
        index = new HashMap<>(fields.length * 4 / 3);
        measure = new HashMap<>(fields.length * 4 / 3);
        for (int i = 0; i < fields.length; i++) {
            index.put(fields[i].name, i);
        }
    }

    /** Returns the number of fields. */
    public int length() {
        return fields.length;
    }

    /** Returns the fields. */
    public StructField[] fields() {
        return fields;
    }

    /** Return the field of given name. */
    public StructField field(String name) {
        return fields[fieldIndex(name)];
    }

    /** Return the field at position i. */
    public StructField field(int i) {
        return fields[i];
    }

    /** Returns the index of a field. */
    public int fieldIndex(String field) {
        return index.get(field);
    }

    /** Returns the map of field name to its (optional) scale of measure. */
    public Map<String, Measure> measure() {
        return measure;
    }

    /**
     * Updates the field type to the boxed one if the field has
     * null/missing values in the data.
     *
     * @param rows a set of tuples.
     */
    public void boxed(Collection<Tuple> rows) {
        for (int i = 0; i < fields.length; i++) {
            StructField field = fields[i];
            if (field.type.isPrimitive()) {
                final int idx = i;
                boolean missing = rows.stream().filter(t -> t.isNullAt(idx)).findAny().isPresent();
                if (missing) {
                    fields[i] = new StructField(field.name, field.type.boxed());
                }
            }
        }
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
                    Measure m = measure().get(field.name);
                    String value = v == null ? "null" :
                            ((m != null && m instanceof DiscreteMeasure) ?
                                    t.getScale(field.name) :
                                    field.type.toString(v));
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
            String[] field = element.split(":");
            DataType type = fields[index.get(field[0])].type;
            Object value = type.valueOf(field[1]);
            int i = index.get(field[0]);
            row[i] = value;
        }

        return Tuple.of(row, this);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StructType) {
            StructType t = (StructType) o;
            return Arrays.equals(fields, t.fields);
        }

        return false;
    }
}
