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

package smile.interpolation;

import java.io.Serializable;

/**
 * In numerical analysis, interpolation is a method of constructing new data
 * points within the range of a discrete set of known data points.
 * In engineering and science one often has a number of data points, as
 * obtained by sampling or experimentation, and tries to construct a function
 * which closely fits those data points. This is called curve fitting or
 * regression analysis. Interpolation is a specific case of curve fitting,
 * in which the function must go exactly through the data points.
 *
 * @author Haifeng Li
 */
public interface Interpolation extends Serializable {

    /**
     * Given a value x, return an interpolated value.
     */
    double interpolate(double x);
}
