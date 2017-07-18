/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.netlib;

import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import com.github.fommil.netlib.LAPACK;
import org.netlib.util.intW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cholesky decomposition is a decomposition of a symmetric, positive-definite
 * matrix into a lower triangular matrix L and the transpose of the lower
 * triangular matrix such that A = L*L'. The lower triangular matrix is the
 * Cholesky triangle of the original, positive-definite matrix. When it is
 * applicable, the Cholesky decomposition is roughly twice as efficient as the LU
 * decomposition for solving systems of linear equations.
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
 * <p>
 * If the matrix is not positive definite, an exception will be thrown out from
 * the method decompose().
 *
 * @author Haifeng Li
 */
public class Cholesky extends smile.math.matrix.Cholesky {
    private static final Logger logger = LoggerFactory.getLogger(Cholesky.class);

    /**
     * Constructor.
     * @param  L the lower triangular part of matrix contains the Cholesky
     *          factorization.
     */
    public Cholesky(DenseMatrix L) {
        super(L);
    }

    /**
     * Returns the matrix inverse.
     */
    public DenseMatrix inverse() {
        int n = L.nrows();
        DenseMatrix inv = Matrix.eye(n);
        solve(inv);
        return inv;
    }

    /**
     * Solve the linear system A * x = b. On output, b will be overwritten with
     * the solution vector.
     * @param  b   the right hand side of linear systems. On output, b will be
     * overwritten with solution vector.
     */
    public void solve(double[] b) {
        // B use b as the internal storage. Therefore b will contains the results.
        DenseMatrix B = Matrix.newInstance(b);
        solve(B);
    }

    /**
     * Solve the linear system A * X = B. On output, B will be overwritten with
     * the solution matrix.
     * @param  B   the right hand side of linear systems.
     */
    public void solve(DenseMatrix B) {
        if (B.nrows() != L.nrows()) {
            throw new IllegalArgumentException(String.format("Row dimensions do not agree: A is %d x %d, but B is %d x %d", L.nrows(), L.ncols(), B.nrows(), B.ncols()));
        }

        intW info = new intW(0);
        LAPACK.getInstance().dpotrs(NLMatrix.Lower, L.nrows(), B.ncols(), L.data(), L.ld(), B.data(), B.ld(), info);

        if (info.val < 0) {
            logger.error("LAPACK DPOTRS error code: {}", info.val);
            throw new IllegalArgumentException("LAPACK DPOTRS error code: " + info.val);
        }
    }
}

