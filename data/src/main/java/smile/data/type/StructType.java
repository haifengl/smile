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

package smile.data.type;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import smile.data.Tuple;
import smile.data.measure.*;

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
    private final Map<String, Measure> measures;

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
        measures = new HashMap<>(fields.length * 4 / 3);
        for (int i = 0; i < fields.length; i++) {
            index.put(fields[i].name, i);
        }
/*
        // set default scale of measure based field type.
        for (StructField field : fields) {
            switch (field.type.id()) {
                case Byte:
                case Short:
                case Integer:
                case Long:
                    measures.put(field.name, new IntervalScale(NumberFormat.getIntegerInstance()));
                    break;

                case Float:
                case Double:
                case Decimal:
                    measures.put(field.name, new RatioScale(NumberFormat.getNumberInstance()));
                    break;

                case Boolean:
                    measures.put(field.name, new NominalScale("false", "true"));
                    break;
            }
        }
 */
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

    /** Returns the name of a field. */
    public String fieldName(int i) {
        return fields[i].name;
    }

    /** Returns the map of field name to its (optional) scale of measure. */
    public Map<String, Measure> measures() {
        return measures;
    }

    /** Returns the optional measure of a field. */
    public Measure measure(String field) {
        return measures.get(field);
    }

    /** Returns the lambda functions that parse field values. */
    public List<Function<String, Object>> parser() {
        List<Function<String, Object>> parser = new ArrayList<>();
        for (int i = 0; i < fields.length; i++) {
            StructField field = fields[i];
            Measure scale = measure(field.name);
            if (scale != null) {
                parser.add(s -> scale.valueOf(s));
            } else {
                final DataType type = field.type;
                parser.add(s -> type.valueOf(s));
            }
        }
        return parser;
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
                    Measure m = measure(field.name);
                    String value = v == null ? "null" : ((m != null) ? m.toString(v) : field.type.toString(v));
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
            StructField field = fields[i];
            Measure scale = measure(field.name);
            if (scale != null) {
                row[i] = scale.valueOf(pair[1]);
            } else {
                row[i] = field.type.valueOf(pair[1]);
            }
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
