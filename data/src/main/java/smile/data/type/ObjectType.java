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

import java.util.function.Function;

/**
 * Object data type.
 *
 * @author Haifeng Li
 */
public class ObjectType implements DataType {
    /** Object type. */
    static ObjectType instance = new ObjectType(Object.class);
    /** Boolean object type. */
    static ObjectType BooleanObjectType = new ObjectType(Boolean.class);
    /** Char object type. */
    static ObjectType CharObjectType = new ObjectType(Character.class);
    /** Byte object type. */
    static ObjectType ByteObjectType = new ObjectType(Byte.class);
    /** Short object type. */
    static ObjectType ShortObjectType = new ObjectType(Short.class);
    /** Integer object type. */
    static ObjectType IntegerObjectType = new ObjectType(Integer.class);
    /** Long object type. */
    static ObjectType LongObjectType = new ObjectType(Long.class);
    /** Float object type. */
    static ObjectType FloatObjectType = new ObjectType(Float.class);
    /** Double object type. */
    static ObjectType DoubleObjectType = new ObjectType(Double.class);

    /** Object Class. */
    private Class clazz;
    /** toString lambda. */
    private Function<Object, String> format;

    /**
     * Constructor.
     * @param clazz the class of objects.
     */
    ObjectType(Class clazz) {
        this.clazz = clazz;
        if (clazz == Float.class) {
            format = o -> DataTypes.FloatType.toString(o);
        } else if (clazz == Double.class) {
            format = o -> DataTypes.DoubleType.toString(o);
        } else if (clazz == Integer.class) {
            format = o -> DataTypes.IntegerType.toString(o);
        } else if (clazz == Long.class) {
            format = o -> DataTypes.IntegerType.toString(o);
        } else {
            format = o -> o.toString();
        }
    }

    /**
     * Returns the class of objects.
     * This is different from Object.getClass(), which returns
     * ObjectType.class.
     */
    public Class getObjectClass() {
        return clazz;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public boolean isBoolean() {
        return clazz == Boolean.class;
    }

    @Override
    public boolean isChar() {
        return clazz == Character.class;
    }

    @Override
    public boolean isByte() {
        return clazz == Byte.class;
    }

    @Override
    public boolean isShort() {
        return clazz == Short.class;
    }

    @Override
    public boolean isInt() {
        return clazz == Integer.class;
    }

    @Override
    public boolean isLong() {
        return clazz == Long.class;
    }

    @Override
    public boolean isFloat() {
        return clazz == Float.class;
    }

    @Override
    public boolean isDouble() {
        return clazz == Double.class;
    }

    @Override
    public String name() {
        return String.format("Object[%s]", clazz.getName());
    }

    @Override
    public ID id() {
        return ID.Object;
    }

    @Override
    public String toString() {
        return clazz.getSimpleName();
    }

    @Override
    public String toString(Object o) {
        return format.apply(o);
    }

    @Override
    public Object valueOf(String s) {
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ObjectType) {
            return clazz == ((ObjectType) o).getObjectClass();
        }

        return false;
    }
}
