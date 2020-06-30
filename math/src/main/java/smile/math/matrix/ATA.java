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
 * The matrix of A' * A. For SVD, we compute eigen decomposition of A' * A
 * when m >= n, or that of A * A' when m < n.
 *
 * @author Haifeng Li
 */
class ATA extends DMatrix {

    private DMatrix A;
    private DMatrix AtA;
    double[] buf;

    /** Constructor. */
    public ATA(DMatrix A) {
        this.A = A;

        if (A.nrows() >= A.ncols()) {
            buf = new double[A.nrows()];

            if ((A.ncols() < 10000) && (A instanceof Matrix)) {
                //AtA = A.ata();
            }
        } else {
            buf = new double[A.ncols()];

            if ((A.nrows() < 10000) && (A instanceof Matrix)) {
                //AtA = A.aat();
            }
        }
    }

    @Override
    public int nrows() {
        if (A.nrows() >= A.ncols()) {
            return A.ncols();
        } else {
            return A.nrows();
        }
    }

    @Override
    public int ncols() {
        return nrows();
    }

    @Override
    public long size() {
        return A.size();
    }

    @Override
    public void mv(Transpose trans, double alpha, double[] x, double beta, double[] y) {
        if (AtA != null) {
            AtA.mv(trans, alpha, x, beta, y);
        } else {
            if (A.nrows() >= A.ncols()) {
                A.mv(x, buf);
                A.tv(buf, y);
            } else {
                A.tv(x, buf);
                A.mv(buf, y);
            }
        }
    }

    @Override
    public void mv(double[] work, int inputOffset, int outputOffset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void tv(double[] work, int inputOffset, int outputOffset) {
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
