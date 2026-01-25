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

import java.text.DecimalFormat;

/**
 * Float data type.
 *
 * @author Haifeng Li
 */
public class FloatType extends PrimitiveType {
    /** Format for toString. */
    private static final DecimalFormat format = new DecimalFormat("#.####");

    /**
     * Constructor.
     * @param nullable True if the data may be null.
     */
    FloatType(boolean nullable) {
        super(ID.Float, nullable);
    }

    @Override
    public boolean isFloat() {
        return true;
    }

    @Override
    public String toString(Object o) {
        return format.format(o);
    }

    @Override
    public Float valueOf(String s) {
        return Float.valueOf(s);
    }
}
