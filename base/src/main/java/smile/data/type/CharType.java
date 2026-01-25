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
 * Char data type.
 *
 * @author Haifeng Li
 */
public class CharType extends PrimitiveType {
    /**
     * Constructor.
     * @param nullable True if the data may be null.
     */
    CharType(boolean nullable) {
        super(ID.Char, nullable);
    }

    @Override
    public boolean isChar() {
        return true;
    }

    @Override
    public Character valueOf(String s) {
        if (s == null || s.isEmpty()) return null;
        return s.charAt(0);
    }
}
