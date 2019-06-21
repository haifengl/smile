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

import java.text.DecimalFormat;

/**
 * Float data type.
 *
 * @author Haifeng Li
 */
public class FloatType implements DataType {
    /** Format for toString. */
    private static DecimalFormat format = new DecimalFormat("#.####");

    /** Singleton instance. */
    static FloatType instance = new FloatType();

    /**
     * Private constructor for singleton design pattern.
     */
    private FloatType() {
    }

    @Override
    public boolean isFloat() {
        return true;
    }

    @Override
    public String name() {
        return "float";
    }

    @Override
    public ID id() {
        return ID.Float;
    }

    @Override
    public String toString() {
        return "float";
    }

    @Override
    public String toString(Object o) {
        return format.format(o);
    }

    @Override
    public Float valueOf(String s) {
        return Float.valueOf(s);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FloatType;
    }
}
