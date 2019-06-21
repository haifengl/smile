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
 * The matrix of A' * A. For SVD, we compute eigen decomposition of A' * A
 * when m >= n, or that of A * A' when m < n.
 */
class ATA implements Matrix {

    private Matrix A;
    private Matrix AtA;
    double[] buf;

    public ATA(Matrix A) {
        this.A = A;

        if (A.nrows() >= A.ncols()) {
            buf = new double[A.nrows()];

            if ((A.ncols() < 10000) && (A instanceof DenseMatrix)) {
                AtA = A.ata();
            }
        } else {
            buf = new double[A.ncols()];

            if ((A.nrows() < 10000) && (A instanceof DenseMatrix)) {
                AtA = A.aat();
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
    public ATA transpose() {
        return this;
    }

    @Override
    public ATA ata() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ATA aat() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double[] ax(double[] x, double[] y) {
        if (AtA != null) {
            AtA.ax(x, y);
        } else {
            if (A.nrows() >= A.ncols()) {
                A.ax(x, buf);
                A.atx(buf, y);
            } else {
                A.atx(x, buf);
                A.ax(buf, y);
            }
        }

        return y;
    }

    @Override
    public boolean isSymmetric() {
        return true;
    }

    @Override
    public double[] atx(double[] x, double[] y) {
        return ax(x, y);
    }

    @Override
    public double[] axpy(double[] x, double[] y) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double[] axpy(double[] x, double[] y, double b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double get(int i, int j) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double apply(int i, int j) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double[] atxpy(double[] x, double[] y) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double[] atxpy(double[] x, double[] y, double b) {
        throw new UnsupportedOperationException();
    }
}
