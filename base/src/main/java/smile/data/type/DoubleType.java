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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.type;

import java.text.DecimalFormat;

/**
 * Double data type.
 *
 * @author Haifeng Li
 */
public class DoubleType extends PrimitiveType {
    /**
     * Thread-local formatter for double values.
     * {@link DecimalFormat} is not thread-safe, so each thread gets its own instance.
     */
    private static final ThreadLocal<DecimalFormat> format =
            ThreadLocal.withInitial(() -> new DecimalFormat("#.######"));

    /**
     * Constructor.
     * @param nullable True if the data may be null.
     */
    DoubleType(boolean nullable) {
        super(ID.Double, nullable);
    }

    @Override
    public boolean isDouble() {
        return true;
    }

    @Override
    public String toString(Object o) {
        return format.get().format(o);
    }

    @Override
    public Double valueOf(String s) {
        return Double.valueOf(s);
    }
}
