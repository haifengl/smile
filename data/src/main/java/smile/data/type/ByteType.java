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
 * Byte data type.
 *
 * @author Haifeng Li
 */
public class ByteType implements DataType {

    /** Singleton instance. */
    static ByteType instance = new ByteType();

    /**
     * Private constructor for singleton design pattern.
     */
    private ByteType() {
    }

    @Override
    public boolean isByte() {
        return true;
    }

    @Override
    public ID id() {
        return ID.Byte;
    }

    @Override
    public String name() {
        return "byte";
    }

    @Override
    public String toString() {
        return "byte";
    }

    @Override
    public Byte valueOf(String s) {
        return Byte.valueOf(s);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteType;
    }
}
