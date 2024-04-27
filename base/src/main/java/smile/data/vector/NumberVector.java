/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.data.vector;

import smile.data.type.DataType;
import smile.data.type.StructField;

/**
 * An immutable number object vector.
 *
 * @author Haifeng Li
 */
public interface NumberVector extends Vector<Number> {
    /**
     * Fill null/NaN/Inf values using the specified value.
     * @param value the value to replace NAs.
     */
    void fillna(double value);

    /**
     * Creates a named number vector.
     *
     * @param name the name of vector.
     * @param clazz the class of data type.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NumberVector of(String name, Class<?> clazz, Number[] vector) {
        return new NumberVectorImpl(name, clazz, vector);
    }

    /**
     * Creates a named number vector.
     *
     * @param name the name of vector.
     * @param type the data type of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NumberVector of(String name, DataType type, Number[] vector) {
        return new NumberVectorImpl(name, type, vector);
    }

    /** Creates a named number vector.
     *
     * @param field the struct field of vector.
     * @param vector the data of vector.
     * @return the vector.
     */
    static NumberVector of(StructField field, Number[] vector) {
        return new NumberVectorImpl(field, vector);
    }
}