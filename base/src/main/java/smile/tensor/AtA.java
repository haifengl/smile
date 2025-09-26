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

import smile.linalg.Transpose;
import static smile.linalg.Transpose.*;

/**
 * The square matrix of {@code A' * A} or {@code A * A'}, whichever is smaller.
 * For SVD, we compute eigenvalue decomposition of {@code A' * A}
 * when {@code m >= n}, or that of {@code A * A'} when {@code m < n}.
 */
public class AtA implements Matrix {
    /**
     * The base matrix.
     */
    private final Matrix A;
    /**
     * The larger dimension of A.
     */
    private final int m;
    /**
     * The smaller dimension of A.
     */
    private final int n;
    /**
     * Workspace for A * x
     */
    private final Vector Ax;

    /**
     * Constructor.
     * @param A the base matrix.
     */
    public AtA(Matrix A) {
        this.A = A;
        this.m = Math.max(A.nrow(), A.ncol());
        this.n = Math.min(A.nrow(), A.ncol());
        this.Ax = A.vector(m + n);
    }

    @Override
    public ScalarType scalarType() {
        return A.scalarType();
    }

    @Override
    public int nrow() {
        return n;
    }

    @Override
    public int ncol() {
        return n;
    }

    @Override
    public long length() {
        return A.length();
    }

    @Override
    public double get(int i, int j) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void set(int i, int j, double x) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int i, int j, double x) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void mul(int i, int j, double x) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Matrix copy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Matrix transpose() {
        return this;
    }

    @Override
    public void mv(Transpose trans, double alpha, Vector x, double beta, Vector y) {
        if (A.nrow() >= A.ncol()) {
            A.mv(x, Ax);
            A.mv(TRANSPOSE, alpha, Ax, beta, y);
        } else {
            A.tv(x, Ax);
            A.mv(NO_TRANSPOSE, alpha, Ax, beta, y);
        }
    }

    @Override
    public void mv(Vector work, int inputOffset, int outputOffset) {
        work.copy(inputOffset, Ax, 0, n);

        if (A.nrow() >= A.ncol()) {
            A.mv(Ax, 0, n);
            A.tv(Ax, n, 0);
        } else {
            A.tv(Ax, 0, n);
            A.mv(Ax, n, 0);
        }

        Ax.copy(0, work, outputOffset, n);
    }

    @Override
    public void tv(Vector work, int inputOffset, int outputOffset) {
        // The square matrix (AA' or A'A) is symmetric.
        mv(work, inputOffset, outputOffset);
    }

    @Override
    public Matrix mm(Matrix B) {
        if (A.nrow() >= A.ncol()) {
            return A.tm(A.mm(B));
        } else {
            return A.mm(A.tm(B));
        }

    }

    @Override
    public Matrix tm(Matrix B) {
        // The square matrix (AA' or A'A) is symmetric.
        return mm(B);
    }
}
