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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression.gam;

import java.io.Serial;
import java.io.Serializable;

/**
 * A penalized regression spline (P-spline) for a single predictor variable.
 * This is the building block of a Generalized Additive Model: each predictor
 * in the GAM is associated with one {@code SmoothingSpline} that models the
 * non-parametric smooth contribution {@code f_j(x_j)}.
 *
 * <p>The smooth function is represented as a linear combination of B-spline
 * basis functions:
 * <pre>
 *     f(x) = B(x) * beta
 * </pre>
 * where {@code B(x)} is a row vector of B-spline basis evaluations and
 * {@code beta} is the vector of coefficients estimated during model fitting.
 *
 * <p>To ensure identifiability within the additive model, the smooth is
 * centered: its mean over the training data is subtracted so that
 * {@code sum_i f(x_i) = 0}. This centering constant is stored and added
 * back on prediction.
 *
 * @param name the name of the predictor variable.
 * @param spline the B-spline basis specification for this predictor.
 * @param lambda the smoothing parameter. Larger values produce smoother fits.
 *        The penalty term added to the log-likelihood is {@code lambda * beta' P beta},
 *        where P is the second-difference penalty matrix.
 * @param coefficients the fitted B-spline coefficients (after centering).
 * @param center the centering constant (mean of f(x_i) over training data), subtracted
 *        to make the smooth identifiable in the additive model.
 * @param edf the effective degrees of freedom consumed by this smooth.
 *        Computed as {@code trace(S)}, where S is the smoother/hat matrix.
 * @author Haifeng Li
 */
public record SmoothingSpline(String name,
                              BSpline spline,
                              double lambda,
                              double[] coefficients,
                              double center,
                              double edf) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Evaluates the smooth function at a single point.
     * Returns {@code f(x) = B(x) * coefficients - center}.
     * The centering is applied so that the smooth has zero mean over training data.
     *
     * @param x the predictor value.
     * @return the smooth function value (centered).
     */
    public double predict(double x) {
        double[] b = spline.basis(x);
        double val = 0.0;
        for (int j = 0; j < b.length; j++) {
            val += b[j] * coefficients[j];
        }
        return val - center;
    }

    /**
     * Evaluates the smooth function at multiple points.
     *
     * @param x the predictor values.
     * @return the smooth function values (centered).
     */
    public double[] predict(double[] x) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            result[i] = predict(x[i]);
        }
        return result;
    }

    /**
     * Returns the fitted B-spline coefficients.
     * @return the coefficients.
     */
    public double[] coefficients() {
        return coefficients;
    }

    /**
     * Returns the effective degrees of freedom for this smooth.
     * @return the effective degrees of freedom.
     */
    public double edf() {
        return edf;
    }

    /**
     * Returns the name of the predictor variable.
     * @return the predictor name.
     */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("s(%s, lambda=%.4g, edf=%.2f)", name, lambda, edf);
    }
}

