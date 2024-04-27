/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.distance;

import smile.math.blas.UPLO;
import smile.math.matrix.Matrix;

import java.io.Serial;

/**
 * In statistics, Mahalanobis distance is based on correlations between
 * variables by which different patterns can be identified and analyzed.
 * It is a useful way of determining similarity of an unknown sample set
 * to a known one. It differs from Euclidean distance in that it takes
 * into account the correlations of the data set and is scale-invariant,
 * i.e. not dependent on the scale of measurements.
 *
 * @author Haifeng Li
 */
public class MahalanobisDistance implements Metric<double[]> {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The covariance matrix. */
    private final Matrix sigma;
    /** The inverse of covariance matrix. */
    private final Matrix sigmaInv;

    /**
     * Constructor.
     * @param cov the covariance matrix.
     */
    public MahalanobisDistance(double[][] cov) {
        sigma = Matrix.of(cov);
        sigma.uplo(UPLO.LOWER);
        sigmaInv = sigma.inverse();
    }

    @Override
    public String toString() {
        return String.format("Mahalanobis Distance(%s)", sigma);
    }

    @Override
    public double d(double[] x, double[] y) {
        if (x.length != sigma.nrow()) {
            throw new IllegalArgumentException(String.format("Array x[%d] has different dimension with Sigma[%d][%d].", x.length, sigma.nrow(), sigma.ncol()));
        }

        if (y.length != sigma.nrow()) {
            throw new IllegalArgumentException(String.format("Array y[%d] has different dimension with Sigma[%d][%d].", y.length, sigma.nrow(), sigma.ncol()));
        }

        int n = x.length;
        double[] z = new double[n];
        for (int i = 0; i < n; i++)
            z[i] = x[i] - y[i];

        double dist = sigmaInv.xAx(z);
        return Math.sqrt(dist);
    }
}
