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

package smile.data;

import java.io.Serializable;

/**
 * A tuple is an immutable finite ordered list (sequence) of elements.
 * Allows both generic access by ordinal, which will incur boxing overhead
 * for primitives, as well as native primitive access.
 *
 * It is invalid to use the native primitive interface to retrieve a value
 * that is null, instead a user must check `isNullAt` before attempting
 * to retrieve a value that might be null.
 *
 * @author Haifeng Li
 */
public interface Tuple extends Serializable {
    /** Number of elements in the Tuple. */
    int size();

    /**
     * Returns the value at position i. The value may be null.
     */
    default Object apply(int i) {
        return get(i);
    }

    /**
     * Returns the value by field name. The value may be null.
     */
    default Object apply(String field) {
        return get(field);
    }

    /**
     * Returns the value at position i. The value may be null.
     */
    Object get(int i);

    /**
     * Returns the value by field name. The value may be null.
     */
    default Object get(String field) {
        return get(fieldIndex(field));
    }

    /** Checks whether the value at position i is null. */
    default boolean isNullAt(int i) {
        return get(i) == null;
    }

    /**
     * Returns the value at position i as a primitive boolean.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default boolean getBoolean(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default char getChar(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i as a primitive byte.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default byte getByte(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i as a primitive short.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default short getShort(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i as a primitive int.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default int getInt(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i as a primitive long.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default long getLong(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i as a primitive float.
     * Throws an exception if the type mismatches or if the value is null.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default float getFloat(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i as a primitive double.
     *
     * @throws ClassCastException when data type does not match.
     * @throws NullPointerException when value is null.
     */
    default double getDouble(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i as a String object.
     *
     * @throws ClassCastException when data type does not match.
     */
    default String getString(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i of decimal type as java.math.BigDecimal.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.math.BigDecimal getDecimal(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i of date type as java.sql.Date.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.sql.Date getDate(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i of date type as java.sql.Timestamp.
     *
     * @throws ClassCastException when data type does not match.
     */
    default java.sql.Timestamp getTimestamp(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i of array type as `java.util.List`.
     *
     * @throws ClassCastException when data type does not match.
     */
    default <T> java.util.List<T> getList(int i) {
        return getAs(i);
    }

    /**
     * Returns the value at position i.
     * For primitive types if value is null it returns 'zero value' specific for primitive
     * ie. 0 for Int - use isNullAt to ensure that value is not null
     *
     * @throws ClassCastException when data type does not match.
     */
    @SuppressWarnings("unchecked")
    default <T> T getAs(int i) {
        return (T) get(i);
    }

    /**
     * Returns the value of a given fieldName.
     * For primitive types if value is null it returns 'zero value' specific for primitive
     * ie. 0 for Int - use isNullAt to ensure that value is not null
     *
     * @throws UnsupportedOperationException when schema is not defined.
     * @throws IllegalArgumentException when fieldName do not exist.
     * @throws ClassCastException when data type does not match.
     */
    default <T> T getAs(String fieldName) {
        return getAs(fieldIndex(fieldName));
    }

    /**
     * Returns the index of a given field name.
     *
     * @throws UnsupportedOperationException when schema is not defined.
     * @throws IllegalArgumentException when a field `name` does not exist.
     */
    default int fieldIndex(String name) {
        throw new UnsupportedOperationException("fieldIndex on a Tuple without schema is undefined.");
    }

    /** Returns true if there are any NULL values in this tuple. */
    default boolean anyNull() {
        for (int i = 0; i < size(); i++) {
            if (isNullAt(i)) return true;
        }
        return false;
    }

    /**
     * Returns the string representation of the tuple.
     * @param delimiter field delimiter.
     */
    default String toString(String delimiter) {
        StringBuilder sb = new StringBuilder();

        String[] fields = new String[size()];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = get(i).toString();
        }

        sb.append("(");
        sb.append(String.join(delimiter, fields));
        sb.append(")");

        return sb.toString();
    }
}