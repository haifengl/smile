/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

package smile.math.distance;

import java.io.Serializable;
import java.util.function.ToDoubleBiFunction;

/**
 * An interface to calculate a distance measure between two objects. A distance
 * function maps pairs of points into the nonnegative reals and has to satisfy
 * <ul>
 * <li> non-negativity: <code>d(x, y) &ge; 0</code>
 * <li> isolation: <code>d(x, y) = 0</code> if and only if <code>x = y</code>
 * <li> symmetry: <code>d(x, y) = d(x, y)</code>
 * </ul>
 * Note that a distance function is not required to satisfy triangular inequality
 * <code>|x - y| + |y - z| &ge; |x - z|</code>, which is necessary for a metric.
 *
 * @author Haifeng Li
 */
public interface Distance<T> extends ToDoubleBiFunction<T,T>, Serializable {
    /**
     * Returns the distance measure between two objects.
     */
    double d(T x, T y);

    /**
     * Returns the distance measure between two objects.
     * This is simply for Scala convenience.
     */
    default double apply(T x, T y) {
        return d(x, y);
    }

    @Override
    default double applyAsDouble(T x, T y) {
        return d(x, y);
    }
}
