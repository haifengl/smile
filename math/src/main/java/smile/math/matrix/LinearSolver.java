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

package smile.math.matrix;

/**
 * The interface of the solver of linear system.
 *
 * @author Haifeng Li
 */
public interface LinearSolver {
    /**
     * Solve A*x = b.
     * @param b   a vector with as many rows as A.
     * @param x   is output vector so that A*x = b
     * @return the solution vector x
     * @throws RuntimeException if matrix is singular.
     */
    double[] solve(double[] b, double[] x);
}
