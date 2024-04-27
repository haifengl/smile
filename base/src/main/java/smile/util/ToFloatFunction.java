/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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

package smile.util;

/**
 * Represents a function that produces a float-valued result.
 * This is the float-producing primitive specialization for Function.
 * <p>
 * There is no ToFloatFunction interface in java.util.function.
 *
 * @param <T> the type of the input to the function.
 *
 * @author Haifeng Li
 */
public interface ToFloatFunction<T> {
    /**
     * Applies this function to the given argument.
     * @param o the input object.
     * @return the function value.
     */
    float applyAsFloat(T o);
}

