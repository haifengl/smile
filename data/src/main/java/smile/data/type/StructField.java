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

    /**
     * Constructor with the ISO date formatter that formats
     * or parses a date without an offset, such as '2011-12-03'.
     */
    public StructField(String name, DataType type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", name, type);
    }

    /** Returns the string representation of the field with given value. */
    public String toString(Object o) {
        return String.format("%s: %s", name, type.toString(o));
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
