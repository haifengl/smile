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

import java.util.Arrays;

/**
 * Array of primitive data type.
 *
 * @author Haifeng Li
 */
public class ArrayType implements DataType {
    /** Boolean array type. */
    static ArrayType BooleanArrayType = new ArrayType(DataTypes.BooleanType);
    /** Char array type. */
    static ArrayType CharArrayType = new ArrayType(DataTypes.CharType);
    /** Byte array type. */
    static ArrayType ByteArrayType = new ArrayType(DataTypes.ByteType);
    /** Short array type. */
    static ArrayType ShortArrayType = new ArrayType(DataTypes.ShortType);
    /** Integer array type. */
    static ArrayType IntegerArrayType = new ArrayType(DataTypes.IntegerType);
    /** Long array type. */
    static ArrayType LongArrayType = new ArrayType(DataTypes.LongType);
    /** Float array type. */
    static ArrayType FloatArrayType = new ArrayType(DataTypes.FloatType);
    /** Double array type. */
    static ArrayType DoubleArrayType = new ArrayType(DataTypes.DoubleType);

    /** Element data type. */
    private DataType type;

    /**
     * Constructor.
     * @param type element data type.
     */
    ArrayType(DataType type) {
        this.type = type;
    }

    /**
     * Returns the type of array elements.
     */
    public DataType getComponentType() {
        return type;
    }

    @Override
    public String name() {
        return String.format("Array%s", type.name());
    }

    @Override
    public ID id() {
        return ID.Array;
    }

    @Override
    public String toString() {
        return String.format("%s[]", type);
    }

    @Override
    public String toString(Object o) {
        return Arrays.toString((Object[]) o);
    }

    @Override
    public Object[] valueOf(String s) {
        // strip surrounding []
        String[] elements = s.substring(1, s.length() - 1).split(",");
        Object[] array = new Object[elements.length];
        for (int i = 0; i < elements.length; i++) {
            array[i] = type.valueOf(elements[i]);
        }
        return array;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ArrayType) {
            return type == ((ArrayType) o).getComponentType();
        }

        return false;
    }}
