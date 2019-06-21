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
 * Double data type.
 *
 * @author Haifeng Li
 */
public class DoubleType implements DataType {
    /** Format for toString. */
    private static DecimalFormat format = new DecimalFormat("#.######");

    /** Singleton instance. */
    static DoubleType instance = new DoubleType();

    /**
     * Private constructor for singleton design pattern.
     */
    private DoubleType() {
    }

    @Override
    public boolean isDouble() {
        return true;
    }

    @Override
    public ID id() {
        return ID.Double;
    }

    @Override
    public String name() {
        return "double";
    }

    @Override
    public String toString() {
        return "double";
    }

    @Override
    public String toString(Object o) {
        return format.format(o);
    }

    @Override
    public Double valueOf(String s) {
        return Double.valueOf(s);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DoubleType;
    }
}
