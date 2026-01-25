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

/**
 * Integer data type.
 *
 * @author Haifeng Li
 */
public class IntType extends PrimitiveType {
    /**
     * Constructor.
     * @param nullable True if the data may be null.
     */
    IntType(boolean nullable) {
        super(ID.Int, nullable);
    }

    @Override
    public boolean isInt() {
        return true;
    }

    @Override
    public Integer valueOf(String s) {
        return Integer.valueOf(s);
    }
}
