/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data.type;

import java.text.ParseException;
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
    public String toString() {
        return String.format("%s[]", type);
    }

    @Override
    public String toString(Object o) {
        return Arrays.toString((Object[]) o);
    }

    @Override
    public Object[] valueOf(String s) throws ParseException {
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
