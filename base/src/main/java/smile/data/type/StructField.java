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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import smile.data.measure.CategoricalMeasure;
import smile.data.measure.NumericalMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;

/**
 * A field in a Struct data type.
 *
 * @author Haifeng Li
 */
public record StructField(String name, DataType dtype, Measure measure) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * Constructor.
     * @param name the field name.
     * @param dtype the field data type.
     * @param measure the level of measurement.
     */
    public StructField {
        if (measure instanceof NumericalMeasure && !dtype.isFloating()) {
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
}
