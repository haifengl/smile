/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.tensor;

import java.io.Serializable;
import java.lang.foreign.MemorySegment;
import static smile.linalg.lapack.clapack_h.*;

/**
 * The Cholesky decomposition of a symmetric, positive-definite matrix.
 * When it is applicable, the Cholesky decomposition is roughly twice as
 * efficient as the LU decomposition for solving systems of linear equations.
 * <p>
 * The Cholesky decomposition is mainly used for the numerical solution of
 * linear equations. The Cholesky decomposition is also commonly used in
 * the Monte Carlo method for simulating systems with multiple correlated
 * variables: The matrix of inter-variable correlations is decomposed,
 * to give the lower-triangular L. Applying this to a vector of uncorrelated
 * simulated shocks, u, produces a shock vector Lu with the covariance
 * properties of the system being modeled.
 * <p>
 * Unscented Kalman filters commonly use the Cholesky decomposition to choose
 * a set of so-called sigma points. The Kalman filter tracks the average
 * state of a system as a vector x of length n and covariance as an n-by-n
 * matrix P. The matrix P is always positive semi-definite, and can be
 * decomposed into L*L'. The columns of L can be added and subtracted from
 * the mean x to form a set of 2n vectors called sigma points. These sigma
 * points completely capture the mean and covariance of the system state.
 *
 * @param lu the Cholesky factorization.
 * @author Haifeng Li
 */
public record Cholesky(DenseMatrix lu) implements Serializable {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Cholesky.class);

    /**
     * Constructor.
     * @param lu the lower/upper triangular part of matrix contains the Cholesky
     *           factorization.
     */
    public Cholesky {
        if (lu.nrow() != lu.ncol()) {
            throw new UnsupportedOperationException("Cholesky constructor on a non-square matrix");
        }
    }

    /**
     * Returns the matrix determinant.
     * @return the matrix determinant.
     */
    public double det() {
        int n = lu.n;
        double d = 1.0;
        for (int i = 0; i < n; i++) {
            d *= lu.get(i, i);
        }

        return d * d;
    }

    /**
     * Returns the log of matrix determinant.
     * @return the log of matrix determinant.
     */
    public double logdet() {
        int n = lu.n;
        double d = 0.0;
        for (int i = 0; i < n; i++) {
            d += Math.log(lu.get(i, i));
        }

        return 2.0 * d;
    }

    /**
     * Returns the inverse of matrix.
     * @return the inverse of matrix.
     */
    public DenseMatrix inverse() {
        DenseMatrix inv = lu.eye(lu.n);
        solve(inv);
        return inv;
    }

    /**
     * Solve A * x = b.
     * @param b the right hand side of linear systems.
     * @throws RuntimeException when the matrix is singular.
     * @return the solution vector.
     */
    public Vector solve(double[] b) {
        Vector x = lu.vector(b);
        solve(x);
        return x;
    }

    /**
     * Solve A * x = b.
     * @param b the right hand side of linear systems.
     * @throws RuntimeException when the matrix is singular.
     * @return the solution vector.
     */
    public Vector solve(float[] b) {
        Vector x = lu.vector(b);
        solve(x);
        return x;
    }

    /**
     * Solves the linear system A * X = B.
     * @param B the right hand side of linear systems. On output, B will
     *          be overwritten with the solution matrix.
     */
    public void solve(DenseMatrix B) {
        if (lu.scalarType() != B.scalarType()) {
            throw new IllegalArgumentException("Incompatible ScalarType: " + B.scalarType() + " != " + lu.scalarType());
        }
        if (lu.m != B.m) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", lu.m, lu.n, B.m, B.n));
        }

        byte[] uplo = { lu.uplo.lapack() };
        int[] n = { lu.n };
        int[] nrhs = { B.n };
        int[] lda = { lu.ld };
        int[] ldb = { B.ld };
        int[] info = { 0 };
        MemorySegment uplo_ = MemorySegment.ofArray(uplo);
        MemorySegment n_ = MemorySegment.ofArray(n);
        MemorySegment nrhs_ = MemorySegment.ofArray(nrhs);
        MemorySegment lda_ = MemorySegment.ofArray(lda);
        MemorySegment ldb_ = MemorySegment.ofArray(ldb);
        MemorySegment info_ = MemorySegment.ofArray(info);
        switch(lu.scalarType()) {
            case Float64 -> dpotrs_(uplo_, n_, nrhs_, lu.memory, lda_, B.memory, ldb_, info_);
            case Float32 -> spotrs_(uplo_, n_, nrhs_, lu.memory, lda_, B.memory, ldb_, info_);
            default -> throw new UnsupportedOperationException("Unsupported scala type: " + lu.scalarType());
        }

        if (info[0] != 0) {
            logger.error("LAPACK POTRS error code: {}", info[0]);
            throw new ArithmeticException("LAPACK POTRS error code: " + info[0]);
        }
    }
}
