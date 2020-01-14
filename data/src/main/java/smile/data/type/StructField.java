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

import java.io.Serializable;
import smile.data.measure.ContinuousMeasure;
import smile.data.measure.DiscreteMeasure;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;

/**
 * A field in a Struct data type.
 *
 * @author Haifeng Li
 */
public class StructField implements Serializable {
    private static final long serialVersionUID = 2L;

    /** Field name. */
    public final String name;
    /** Field data type. */
    public final DataType type;
    /** Optional levels of measurements. */
    public final Measure measure;

    /**
     * Constructor.
     */
    public StructField(String name, DataType type) {
        this(name, type, null);
    }

    /**
     * Constructor.
     */
    public StructField(String name, DataType type, Measure measure) {
        if (measure instanceof ContinuousMeasure && !type.isFloating()) {
            throw new IllegalArgumentException(String.format("%s values cannot be of measure %s", type, measure));
        }

        if (measure instanceof DiscreteMeasure && !type.isIntegral()) {
            throw new IllegalArgumentException(String.format("%s values cannot be of measure %s", type, measure));
        }

        this.name = name;
        this.type = type;
        this.measure = measure;
    }

    @Override
    public String toString() {
        return measure != null ? String.format("%s: %s %s", name, type, measure) : String.format("%s: %s", name, type);
    }

    /** Returns the string representation of the field with given value. */
    public String toString(Object o) {
        if (o == null) return "null";
        return measure != null ? measure.toString(o) : type.toString(o);
    }

    /** Returns the string representation of the field with given value. */
    public Object valueOf(String s) {
        return measure != null ? measure.valueOf(s) : type.valueOf(s);
    }

    /** Returns true if the field is of integer or floating but not nominal scale. */
    public boolean isNumeric() {
        if (measure instanceof NominalScale) {
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
