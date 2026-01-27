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

import java.util.function.Function;

/**
 * Object data type.
 *
 * @author Haifeng Li
 */
public class ObjectType implements DataType {
    /** Object type. */
    static final ObjectType instance = new ObjectType(Object.class);
    /** Boolean object type. */
    static final ObjectType BooleanObjectType = new ObjectType(Boolean.class);
    /** Char object type. */
    static final ObjectType CharObjectType = new ObjectType(Character.class);
    /** Byte object type. */
    static final ObjectType ByteObjectType = new ObjectType(Byte.class);
    /** Short object type. */
    static final ObjectType ShortObjectType = new ObjectType(Short.class);
    /** Integer object type. */
    static final ObjectType IntegerObjectType = new ObjectType(Integer.class);
    /** Long object type. */
    static final ObjectType LongObjectType = new ObjectType(Long.class);
    /** Float object type. */
    static final ObjectType FloatObjectType = new ObjectType(Float.class);
    /** Double object type. */
    static final ObjectType DoubleObjectType = new ObjectType(Double.class);

    /** Object Class. */
    private final Class<?> clazz;
    /** toString lambda. */
    private final transient Function<Object, String> format;

    /**
     * Constructor.
     * @param clazz the class of objects.
     */
    ObjectType(Class<?> clazz) {
        this.clazz = clazz;
        if (clazz == Float.class) {
            format = DataTypes.FloatType::toString;
        } else if (clazz == Double.class) {
            format = DataTypes.DoubleType::toString;
        } else if (clazz == Integer.class) {
            format = DataTypes.IntType::toString;
        } else if (clazz == Long.class) {
            format = DataTypes.IntType::toString;
        } else {
            format = Object::toString;
        }
    }

    /**
     * Returns the class of objects.
     * This is different from Object.getClass(), which returns
     * ObjectType.class.
     * @return the class of objects.
     */
    public Class<?> getObjectClass() {
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
        return String.format("Class<%s>", clazz.getName());
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
        if (o instanceof ObjectType t) {
            return clazz == t.getObjectClass();
        }

        return false;
    }
}
