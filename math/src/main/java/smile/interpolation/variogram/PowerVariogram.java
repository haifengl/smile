/*
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
 */

package smile.interpolation.variogram;

import smile.math.MathEx;

/**
 * Power variogram.
 * <p>
 *     v(r) = c + &alpha; r<sup>&beta;</sup>
 * <p>
 * where &beta; is fixed and &alpha; is fitted by unweighted least squares
 * over all pairs of data points. The value of &beta; should be in the range
 * {@code 1 <=} &beta; {@code < 2}. A good general choice is 1.5, but for
 * functions with a strong linear trend, we may experiment with values as
 * large as 1.99.
 * <p>
 * The parameter c is the so-called nugget effect. Though the value of the
 * variogram for <code>h = 0</code> is strictly 0, several factors, such as
 * sampling error and short scale variability, may cause sample values separated
 * by extremely small distances to be quite dissimilar. This causes a discontinuity
 * at the origin of the variogram. The vertical jump from the value of 0 at the
 * origin to the value of the variogram at extremely small separation distances is
 * called the nugget effect.
 *
 * @author Haifeng Li
 */
public class PowerVariogram implements Variogram {

    /** The parameter fitted by unweighted least squares on data points. */
    private final double alpha;
    /** The fixed parameter {@code 1 <= beta < 2}. */
    private final double beta;
    /** The nugget effect parameter. */
    private final double nugget;

    /**
     * Constructor. No nugget effect and &beta; = 1.5 and &alpha; will be estimated from x and y.
     * @param x the training data.
     * @param y the responsible variable.
     */
    public PowerVariogram(double[][] x, double[] y) {
        this(x, y, 1.5);
    }

    /**
     * Constructor. No nugget effect and &alpha; will be estimated from x and y.
     * @param x the training data.
     * @param y the responsible variable.
     * @param beta the beta parameter {@code 1 <=} &beta; {@code < 2}.
     */
    public PowerVariogram(double[][] x, double[] y, double beta) {
        this(x, y, beta, 0.0);
    }

    /**
     * Constructor. &alpha; will be estimated from x and y.
     * @param x the training data.
     * @param y the responsible variable.
     * @param beta the beta parameter {@code 1 <=} &beta; {@code < 2}.
     * @param nugget the nugget effect parameter. The height of the jump of
     * the variogram at the discontinuity at the origin.
     */
    public PowerVariogram(double[][] x, double[] y, double beta, double nugget) {
        if (beta < 1 || beta >= 2) {
            throw new IllegalArgumentException("Invalid beta = " + beta);
        }

        if (nugget < 0) {
            throw new IllegalArgumentException("Invalid nugget effect = " + nugget);
        }

        this.beta = beta;
        this.nugget = nugget;

        int n = x.length;
        int dim = x[0].length;

        double num = 0.0, denom = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double rb = 0.0;
                for (int k = 0; k < dim; k++) {
                    rb += MathEx.pow2(x[i][k] - x[j][k]);
                }

                rb = Math.pow(rb, 0.5 * beta);
                num += rb * 0.5 * MathEx.pow2(y[i] - y[j] - nugget);
                denom += rb * rb;
            }
        }

        alpha = num / denom;
    }

    @Override
    public double f(double r) {
        return nugget + alpha * Math.pow(r, beta);
    }

    @Override
    public String toString() {
        return String.format("Power Variogram(range = %.4f, sill = %.4f, nugget effect = %.4f)", alpha, beta, nugget);
    }
}
