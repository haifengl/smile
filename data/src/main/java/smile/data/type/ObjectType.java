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

    /**
     * Constructor.
     * @param clazz the class of objects.
     */
    ObjectType(Class clazz) {
        this.clazz = clazz;
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
        return String.format("object[%s]", clazz.getName());
    }

    @Override
    public String toString() {
        return clazz.getSimpleName();
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
