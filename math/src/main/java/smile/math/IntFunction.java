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

package smile.math;

import java.io.Serializable;

/**
 * An interface representing a univariate int function.
 *
 * @author Haifeng Li
 */
public interface IntFunction extends Serializable {
    /**
     * Computes the value of the function at x.
     */
    int f(int x);

    /**
     * Computes the value of the function at x.
     * It delegates the computation to f().
     * This is simply for Scala convenience.
     */
    default int apply(int x) {
        return f(x);
    }
}
