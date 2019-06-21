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
 * The preconditioner matrix in the biconjugate gradient method.
 *
 * @author Haifeng Li
 */
public interface Preconditioner {
    /**
     * Solve A<sub>d</sub> * x = b for the preconditioner matrix A<sub>d</sub>.
     * The preconditioner matrix A<sub>d</sub> is close to A and should be
     * easy to solve for linear systems. This method is useful for preconditioned
     * conjugate gradient method. The preconditioner matrix could be as simple
     * as the trivial diagonal part of A in some cases.
     */
    void solve(double[] b, double[] x);
}
