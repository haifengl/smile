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

package smile.math.matrix;

import smile.math.blas.Transpose;

/**
 * The matrix of A' * A. For SVD, we compute eigenvalue decomposition
 * of A' * A when m >= n, or that of A * A' when m < n.
 *
 * @author Haifeng Li
 */
class ATA extends DMatrix {

    /** A' * A */
    private DMatrix A;
    /** Workspace for A * x */
    private double[] Ax;
    /** The larger dimension of A. */
    private int m;
    /** The smaller dimension of A. */
    private int n;

    /** Constructor. */
    public ATA(DMatrix A) {
        this.A = A;
        this.m = Math.max(A.nrows(), A.ncols());
        this.n = Math.min(A.nrows(), A.ncols());
        this.Ax = new double[m + n];
    }

    @Override
    public int nrows() {
        return n;
    }

    @Override
    public int ncols() {
        return n;
    }

    @Override
    public long size() {
        return A.size();
    }

    @Override
    public void mv(double[] work, int inputOffset, int outputOffset) {
        System.arraycopy(work, inputOffset, Ax, 0, n);

        if (A.nrows() >= A.ncols()) {
            A.mv(Ax, 0, n);
            A.tv(Ax, n, 0);
        } else {
            A.tv(Ax, 0, n);
            A.mv(Ax, n, 0);
        }

        System.arraycopy(Ax, 0, work, outputOffset, n);
    }

    @Override
    public void tv(double[] work, int inputOffset, int outputOffset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void mv(Transpose trans, double alpha, double[] x, double beta, double[] y) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double get(int i, int j) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DMatrix set(int i, int j, double x) {
        throw new UnsupportedOperationException();
    }
}
