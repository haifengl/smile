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
import java.util.Arrays;

/**
 * B-spline basis matrix for a single predictor. A B-spline (basis spline)
 * of degree {@code d} is a piecewise polynomial defined on a knot sequence.
 * This class builds the basis matrix and the second-difference penalty matrix
 * used in penalized regression splines (P-splines).
 *
 * <p>The resulting basis has {@code k + d} columns, where {@code k} is the
 * number of inner knots. With a second-difference penalty on the B-spline
 * coefficients, this achieves smoothing similar to a cubic smoothing spline
 * but with the computational advantage of a banded penalty.
 *
 * @param degree the degree of the B-spline polynomial (default: 3 for cubic splines).
 * @param knots the full (extended) knot vector, including repeated boundary knots.
 * @param xmin the minimum value of the predictor (used for clamping at prediction time).
 * @param xmax the maximum value of the predictor (used for clamping at prediction time).
 * @param numBasis the number of basis functions (columns of the basis matrix).
 * @author Haifeng Li
 */
public record BSpline(int degree, double[] knots, double xmin, double xmax, int numBasis) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     *
     * @param x       the predictor values (used to determine the knot placement).
     * @param df      the desired degrees of freedom (number of basis functions).
     *                Must be {@code >= degree + 1}.
     * @param degree  the polynomial degree (3 = cubic).
     */
    public BSpline(double[] x, int df, int degree) {
        if (df < degree + 1) {
            throw new IllegalArgumentException(String.format(
                "df (%d) must be >= degree + 1 (%d)", df, degree + 1));
        }
        this(degree,
                new double[(df - degree - 1) + 2 * (degree + 1)],  // Will be overwritten below
                Arrays.stream(x).min().orElseThrow(() -> new IllegalArgumentException("Empty predictor array")),
                Arrays.stream(x).max().orElseThrow(() -> new IllegalArgumentException("Empty predictor array")),
                df);

        // Number of interior knots
        int numInner = df - degree - 1;
        // Build extended knot vector: (degree+1) boundary knots on each side + inner knots
        int numKnots = numInner + 2 * (degree + 1);

        // Left boundary knots
        for (int i = 0; i <= degree; i++) {
            knots[i] = xmin;
        }
        // Inner knots at quantiles of x
        if (numInner > 0) {
            double[] sorted = x.clone();
            Arrays.sort(sorted);
            for (int i = 1; i <= numInner; i++) {
                double p = (double) i / (numInner + 1);
                knots[degree + i] = quantile(sorted, p);
            }
        }
        // Right boundary knots
        for (int i = 0; i <= degree; i++) {
            knots[numKnots - 1 - i] = xmax;
        }
    }

    /**
     * Constructor with default cubic degree.
     *
     * @param x  the predictor values.
     * @param df the desired degrees of freedom (number of basis functions).
     */
    public BSpline(double[] x, int df) {
        this(x, df, 3);
    }

    /**
     * Evaluates a quantile of a sorted array.
     */
    private static double quantile(double[] sorted, double p) {
        int n = sorted.length;
        double h = (n - 1) * p;
        int lo = (int) h;
        int hi = Math.min(lo + 1, n - 1);
        return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]);
    }

    /**
     * Computes the B-spline basis matrix for a set of predictor values.
     * Returns an {@code n x numBasis} matrix where each row contains the
     * basis function evaluations for one observation.
     *
     * @param x the predictor values.
     * @return the basis matrix (n rows × numBasis columns).
     */
    public double[][] basis(double[] x) {
        int n = x.length;
        double[][] B = new double[n][numBasis];
        for (int i = 0; i < n; i++) {
            double xi = Math.max(xmin, Math.min(xmax, x[i]));
            double[] b = bsplineRow(xi);
            System.arraycopy(b, 0, B[i], 0, numBasis);
        }
        return B;
    }

    /**
     * Evaluates all B-spline basis functions at a single point {@code x}.
     *
     * @param x the point at which to evaluate the basis.
     * @return an array of length {@code numBasis} of basis values.
     */
    public double[] basis(double x) {
        double xi = Math.max(xmin, Math.min(xmax, x));
        return bsplineRow(xi);
    }

    /**
     * Computes the second-difference penalty matrix {@code D'D} of size
     * {@code numBasis x numBasis}. This penalizes the roughness of the
     * fitted smooth function by penalizing differences in adjacent B-spline
     * coefficients (a discrete approximation to the integrated squared
     * second derivative).
     *
     * @return the penalty matrix.
     */
    public double[][] penalty() {
        int p = numBasis;
        // Second-difference matrix D of size (p-2) x p
        double[][] D = new double[p - 2][p];
        for (int i = 0; i < p - 2; i++) {
            D[i][i]     =  1.0;
            D[i][i + 1] = -2.0;
            D[i][i + 2] =  1.0;
        }
        // Compute D'D
        double[][] DtD = new double[p][p];
        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                double s = 0.0;
                for (int i = 0; i < p - 2; i++) {
                    s += D[i][j] * D[i][k];
                }
                DtD[j][k] = s;
            }
        }
        return DtD;
    }

    /**
     * Evaluates the B-spline basis functions of degree {@code d} at point
     * {@code x} using the de Boor recursion.
     */
    private double[] bsplineRow(double x) {
        double[] t = knots;
        int m = t.length;

        // Initialize degree-0 basis: find the knot span containing x.
        // For x == t[m-1] (right boundary), clamp to the last non-degenerate span.
        double[] N = new double[m - 1];
        if (x == t[m - 1]) {
            // Find the last non-degenerate knot span [t[j], t[j+1]) with t[j] < t[j+1]
            int lastSpan = m - 2;
            while (lastSpan > 0 && t[lastSpan] == t[lastSpan + 1]) lastSpan--;
            N[lastSpan] = 1.0;
        } else {
            for (int j = 0; j < m - 1; j++) {
                N[j] = (x >= t[j] && x < t[j + 1]) ? 1.0 : 0.0;
            }
        }

        // Recur up to degree using de Boor recursion
        for (int r = 1; r <= degree; r++) {
            double[] Nnew = new double[m - 1 - r];
            for (int j = 0; j < m - 1 - r; j++) {
                double left = 0.0, right = 0.0;
                double denom1 = t[j + r] - t[j];
                double denom2 = t[j + r + 1] - t[j + 1];
                if (denom1 != 0) left  = ((x - t[j])         / denom1) * N[j];
                if (denom2 != 0) right = ((t[j + r + 1] - x) / denom2) * N[j + 1];
                Nnew[j] = left + right;
            }
            N = Nnew;
        }

        // N now has length (m - 1 - degree) = numBasis
        assert N.length == numBasis : "B-spline basis length mismatch: " + N.length + " vs " + numBasis;
        return N;
    }
}
