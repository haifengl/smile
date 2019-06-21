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
 * Char data type.
 *
 * @author Haifeng Li
 */
public class CharType implements DataType {

    /** Singleton instance. */
    static CharType instance = new CharType();

    /**
     * Private constructor for singleton design pattern.
     */
    private CharType() {
    }

    @Override
    public boolean isChar() {
        return true;
    }

    @Override
    public String name() {
        return "char";
    }

    @Override
    public ID id() {
        return ID.Char;
    }

    @Override
    public String toString() {
        return "char";
    }

    @Override
    public Character valueOf(String s) {
        if (s == null || s.length() == 0) return null;
        return s.charAt(0);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CharType;
    }
}
