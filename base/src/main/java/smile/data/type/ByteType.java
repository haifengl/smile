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
 * Byte data type.
 *
 * @author Haifeng Li
 */
public class ByteType extends PrimitiveType {
    /**
     * Constructor.
     * @param nullable True if the data may be null.
     */
    ByteType(boolean nullable) {
        super(ID.Byte, nullable);
    }

    @Override
    public boolean isByte() {
        return true;
    }

    @Override
    public Byte valueOf(String s) {
        return Byte.valueOf(s);
    }
}
