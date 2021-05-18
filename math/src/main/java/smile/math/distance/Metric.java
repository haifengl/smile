/*
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
 */

package smile.math.distance;

/**
 * A metric function defines a distance between elements of a set. Besides
 * non-negativity, isolation, and symmetry, it also has to satisfy triangular
 * inequality.
 * <ul>
 * <li> non-negativity: {@code d(x, y) >= 0}
 * <li> isolation: {@code d(x, y) = 0} if and only if {@code x = y}
 * <li> symmetry: {@code d(x, y) = d(x, y)}
 * <li> triangular inequality: {@code |x - y| + |y - z| >= |x - z|}.
 * </ul>
 *
 * @author Haifeng Li
 */
public interface Metric<T> extends Distance<T> {

}
