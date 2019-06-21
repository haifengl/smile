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

package smile.util;

/**
 * Represents a function that produces an float-valued result.
 * This is the float-producing primitive specialization for Function.
 *
 * Java 8 doesn't have ToFloatFunction interface in java.util.function.
 *
 * @author Haifeng Li
 */
/**  */
public interface ToFloatFunction<T> {
    /** Applies this function to the given argument. */
    float applyAsFloat(T o);
}

