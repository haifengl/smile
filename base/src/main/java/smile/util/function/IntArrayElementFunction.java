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

package smile.util.function;

/**
 * Represents a function that accepts an array element of integer value
 * and produces a result.
 *
 * @author Karl Li
 */
@FunctionalInterface
public interface IntArrayElementFunction {
    /**
     * Performs this operation on the given element.
     *
     * @param i the index of array element.
     * @param x the value of array element.
     * @return the function result.
     */
    int apply(int i, int x);
}
