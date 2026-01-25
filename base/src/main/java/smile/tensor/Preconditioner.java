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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

/**
 * The preconditioner matrix. A preconditioner P of a matrix A is a matrix
 * such that P<sup>-1</sup>A has a smaller condition number than A.
 * Preconditioners are useful in iterative methods to solve a linear
 * system A * x = b since the rate of convergence for most iterative
 * linear solvers increases because the condition number of a matrix
 * decreases as a result of preconditioning. Preconditioned iterative
 * solvers typically outperform direct solvers for large, especially
 * for sparse matrices.
 * <p>
 * The preconditioner matrix P is close to A and should be easy to
 * solve for linear systems. The preconditioner matrix could be as
 * simple as the trivial diagonal part of A in some cases.
 *
 * @author Haifeng Li
 */
public interface Preconditioner {
    /**
     * Solve P * x = b for the preconditioner matrix P.
     *
     * @param b the right hand side of linear system.
     * @param x the output solution vector.
     */
    void solve(Vector b, Vector x);

    /**
     * Returns a simple Jacobi preconditioner matrix that is the
     * trivial diagonal part of A in some cases.
     * @param A the matrix of linear system.
     * @return the preconditioner matrix.
     */
    static Preconditioner Jacobi(Matrix A) {
        if (A.nrow() != A.ncol()) {
            throw new IllegalArgumentException("Matrix A must be square");
        }

        int n = A.ncol();
        double[] diag = A.diagonal().toArray(new double[n]);
        return (b, x) -> {
            for (int i = 0; i < n; i++) {
                double d = diag[i] != 0.0 ? b.get(i) / diag[i] : b.get(i);
                x.set(i, d);
            }
        };
    }
}
