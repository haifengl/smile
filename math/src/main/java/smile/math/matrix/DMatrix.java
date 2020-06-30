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
 * Double precision matrix base class.
 *
 * @author Haifeng Li
 */
public abstract class DMatrix extends IMatrix<double[]> {
    /**
     * Sets A[i,j] = x.
     */
    public abstract DMatrix set(int i, int j, double x);

    /**
     * Returns A[i, j].
     */
    public abstract double get(int i, int j);

    /**
     * Returns A[i, j]. For Scala users.
     */
    public double apply(int i, int j) {
        return get(i, j);
    }

    @Override
    String str(int i, int j) {
        return String.format("%.4f", get(i, j));
    }

    /**
     * Returns the diagonal elements.
     */
    public double[] diag() {
        int n = Math.min(nrows(), ncols());

        double[] d = new double[n];
        for (int i = 0; i < n; i++) {
            d[i] = get(i, i);
        }

        return d;
    }

    /**
     * Returns the matrix trace. The sum of the diagonal elements.
     */
    public double trace() {
        int n = Math.min(nrows(), ncols());

        double t = 0.0;
        for (int i = 0; i < n; i++) {
            t += get(i, i);
        }

        return t;
    }

    /**
     * Matrix-vector multiplication.
     * <pre><code>
     *     y = alpha * A * x + beta * y
     * </code></pre>
     */
    public abstract void mv(Transpose trans, double alpha, double[] x, double beta, double[] y);

    @Override
    public double[] mv(double[] x) {
        double[] y = new double[nrows()];
        mv(Transpose.NO_TRANSPOSE, 1.0, x, 0.0, y);
        return y;
    }

    @Override
    public void mv(double[] x, double[] y) {
        mv(Transpose.NO_TRANSPOSE, 1.0, x, 0.0, y);
    }

    @Override
    public double[] tv(double[] x) {
        double[] y = new double[nrows()];
        mv(Transpose.TRANSPOSE, 1.0, x, 0.0, y);
        return y;
    }

    @Override
    public void tv(double[] x, double[] y) {
        mv(Transpose.TRANSPOSE, 1.0, x, 0.0, y);
    }

    /**
     * Find k largest approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param k the number of eigenvalues we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     */
    public Matrix.EVD eigen(int k) {
        return eigen(k, 1.0E-6, 10 * nrows());
    }

    /**
     * Find k largest approximate eigen pairs of a symmetric matrix by the
     * Lanczos algorithm.
     *
     * @param k the number of eigenvalues we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     * @param kappa relative accuracy of ritz values acceptable as eigenvalues.
     * @param maxIter Maximum number of iterations.
     */
    public Matrix.EVD eigen(int k, double kappa, int maxIter) {
        try {
            Class<?> clazz = Class.forName("smile.netlib.ARPACK");
            java.lang.reflect.Method method = clazz.getMethod("eigen", Matrix.class, Integer.TYPE, String.class, Double.TYPE, Integer.TYPE);
            return (Matrix.EVD) method.invoke(null, this, k, "LA", kappa, maxIter);
        } catch (Exception e) {
            if (!(e instanceof ClassNotFoundException)) {
                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Matrix.class);
                logger.info("Matrix.eigen({}, {}, {}):", k, kappa, maxIter, e);
            }
            return Lanczos.eigen(this, k, kappa, maxIter);
        }
    }

    /**
     * Find k largest approximate singular triples of a matrix by the
     * Lanczos algorithm.
     *
     * @param k the number of singular triples we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     */
    public Matrix.SVD svd(int k) {
        return svd(k, 1.0E-6, 10 * nrows());
    }

    /**
     * Find k largest approximate singular triples of a matrix by the
     * Lanczos algorithm.
     *
     * @param k the number of singular triples we wish to compute for the input matrix.
     * This number cannot exceed the size of A.
     * @param kappa relative accuracy of ritz values acceptable as singular values.
     * @param maxIter Maximum number of iterations.
     */
    public Matrix.SVD svd(int k, double kappa, int maxIter) {
        ATA B = new ATA(this);
        Matrix.EVD eigen = Lanczos.eigen(B, k, kappa, maxIter);

        double[] s = eigen.wr;
        for (int i = 0; i < s.length; i++) {
            s[i] = Math.sqrt(s[i]);
        }

        int m = nrows();
        int n = ncols();

        if (m >= n) {
            Matrix V = eigen.Vr;

            double[] tmp = new double[m];
            double[] vi = new double[n];
            Matrix U = new Matrix(m, s.length);
            for (int i = 0; i < s.length; i++) {
                for (int j = 0; j < n; j++) {
                    vi[j] = V.get(j, i);
                }

                mv(vi, tmp);

                for (int j = 0; j < m; j++) {
                    U.set(j, i, tmp[j] / s[i]);
                }
            }

            return new Matrix.SVD(s, U, V);
        } else {
            Matrix U = eigen.Vr;

            double[] tmp = new double[n];
            double[] ui = new double[m];
            Matrix V = new Matrix(n, s.length);
            for (int i = 0; i < s.length; i++) {
                for (int j = 0; j < m; j++) {
                    ui[j] = U.get(j, i);
                }

                tv(ui, tmp);

                for (int j = 0; j < n; j++) {
                    V.set(j, i, tmp[j] / s[i]);
                }
            }

            return new Matrix.SVD(s, U, V);
        }
    }
}
