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

import smile.data.measure.ContinuousMeasure;
import smile.data.measure.DiscreteMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.vector.DoubleVector;

import java.util.Optional;
import java.util.stream.DoubleStream;

/**
 * A field in a Struct data type.
 *
 * @author Haifeng Li
 */
public class StructField {
    /** Field name. */
    public final String name;
    /** Field data type. */
    public final DataType type;
    /** Optional levels of measurements. */
    public final Optional<Measure> measure;

    /**
     * Constructor.
     */
    public StructField(String name, DataType type) {
        this(name, type, Optional.empty());
    }

    /**
     * Constructor.
     */
    public StructField(String name, DataType type, Measure measure) {
        this(name, type, Optional.ofNullable(measure));
    }

    /**
     * Constructor.
     */
    public StructField(String name, DataType type, Optional<Measure> measure) {
        if (measure.isPresent() && measure.get() instanceof ContinuousMeasure && !type.isFloating()) {
            throw new IllegalArgumentException(String.format("%s values cannot be of measure %s", type, measure));
        }

        if (measure.isPresent() && measure.get() instanceof DiscreteMeasure && !type.isIntegral()) {
            throw new IllegalArgumentException(String.format("%s values cannot be of measure %s", type, measure));
        }

        this.name = name;
        this.type = type;
        this.measure = measure;
    }

    @Override
    public String toString() {
        return measure.isPresent() ? String.format("%s: %s %s", name, type, measure.get()) : String.format("%s: %s", name, type);
    }

    /** Returns the string representation of the field with given value. */
    public String toString(Object o) {
        return o == null ? "null" : measure.map(m -> m.toString(o)).orElseGet(() -> type.toString(o));
    }

    /** Returns the string representation of the field with given value. */
    public Object valueOf(String s) {
        return measure.map(m -> (Object) m.valueOf(s)).orElseGet(() -> type.valueOf(s));
    }

    /** Returns true if the field is of integer or floating but not nominal scale. */
    public boolean isNumeric() {
        if (measure.isPresent() && measure.get() instanceof NominalScale) {
            return false;
        }

        return type.isFloating() || type.isIntegral();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StructField) {
            StructField f = (StructField) o;
            return name.equals(f.name) && type.equals(f.type);
        }

        return false;
    }
}
