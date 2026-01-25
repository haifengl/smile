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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.type;

import java.beans.PropertyDescriptor;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Objects;
import smile.data.measure.CategoricalMeasure;
import smile.data.measure.NumericalMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;

/**
 * A field in a Struct data type.
 *
 * @param name the field name.
 * @param dtype the field data type.
 * @param measure the level of measurement.
 * @author Haifeng Li
 */
public record StructField(String name, DataType dtype, Measure measure) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    /** Constructor. */
    public StructField {
        if (measure instanceof NumericalMeasure && (dtype.isBoolean() || dtype.isChar() || dtype.isString())) {
            throw new IllegalArgumentException(String.format("%s values cannot be of measure %s", dtype, measure));
        }

        if (measure instanceof CategoricalMeasure && !dtype.isIntegral()) {
            throw new IllegalArgumentException(String.format("%s values cannot be of measure %s", dtype, measure));
        }
    }

    /**
     * Constructor.
     * @param name the field name.
     * @param dtype the field data type.
     */
    public StructField(String name, DataType dtype) {
        this(name, dtype, null);
    }

    @Override
    public String toString() {
        return measure != null ? String.format("%s: %s %s", name, dtype, measure) : String.format("%s: %s", name, dtype);
    }

    /**
     * Returns the string representation of the field object.
     * @param o the object.
     * @return the string representation.
     */
    public String toString(Object o) {
        if (o == null) return "null";
        return measure != null ? measure.toString(o) : dtype.toString(o);
    }

    /**
     * Returns the object value of string.
     * @param s the string.
     * @return the object value.
     */
    public Object valueOf(String s) {
        return measure != null ? measure.valueOf(s) : dtype.valueOf(s);
    }

    /**
     * Returns the StructField with the new name.
     * @param name the new name.
     * @return the StructField with the new name.
     */
    public StructField withName(String name) {
        return new StructField(name, dtype, measure);
    }

    /**
     * Returns true if the field is of integer or floating but not nominal scale.
     * @return true if the field is of integer or floating but not nominal scale.
     */
    public boolean isNumeric() {
        if (measure instanceof NominalScale) {
            return false;
        }

        return dtype.isFloating() || dtype.isIntegral();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StructField f) {
            return name.equals(f.name) && dtype.equals(f.dtype) && Objects.equals(measure, f.measure);
        }

        return false;
    }

    /**
     * Returns the struct field of a class property.
     * @param prop the property descriptor.
     * @return the struct field.
     */
    public static StructField of(PropertyDescriptor prop) {
        Class<?> clazz = prop.getPropertyType();
        DataType dtype = DataType.of(clazz);
        NominalScale scale = getScale(clazz);
        return new StructField(prop.getName(), dtype, scale);
    }

    /**
     * Returns the struct field of a record component.
     * @param comp the record component.
     * @return the struct field.
     */
    public static StructField of(RecordComponent comp) {
        Class<?> clazz = comp.getType();
        DataType dtype = DataType.of(clazz);
        NominalScale scale = getScale(clazz);
        return new StructField(comp.getName(), dtype, scale);
    }

    /**
     * Returns the nominal scale of an enum class.
     * @param clazz an enum class.
     * @return the nominal scale or null if clazz is not an enum.
     */
    private static NominalScale getScale(Class<?> clazz) {
        if (clazz.isEnum()) {
            Object[] levels = clazz.getEnumConstants();
            return new NominalScale(Arrays.stream(levels).map(Object::toString).toArray(String[]::new));
        }
        return null;
    }
}
