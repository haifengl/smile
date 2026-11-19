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
package smile.timeseries;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.tensor.DenseMatrix;
import smile.tensor.EVD;
import smile.tensor.SVD;
import static smile.tensor.ScalarType.*;

/**
 * Vector autoregressive model VAR(p).
 * <p>
 * A VAR models a <code>k</code>-dimensional series in which every component
 * is regressed on its own lags and on the lags of every other component:
 * <pre>
 * y_t = c + A_1 y_{t-1} + ... + A_p y_{t-p} + e_t,   e_t ~ N(0, Sigma)
 * </pre>
 * where <code>y_t</code> is a <code>k</code>-vector, <code>c</code> an
 * intercept vector, and each <code>A_i</code> is a <code>k x k</code>
 * coefficient matrix. All <code>k</code> equations share the same regressor
 * block, so they are estimated by a single least-squares solve; the fit is
 * equation-by-equation OLS, which is the maximum-likelihood estimator when
 * the same regressors appear in every equation.
 * <p>
 * The model is stable (stationary) when every eigenvalue of the companion
 * matrix lies inside the unit circle. See {@link #isStable()}.
 *
 * <h2>References</h2>
 * <ol>
 * <li>Helmut Lütkepohl. New Introduction to Multiple Time Series Analysis. Springer, 2005.</li>
 * <li>Christopher A. Sims. Macroeconomics and reality. Econometrica, 48(1):1–48, 1980.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class VAR implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(VAR.class);

    /** The number of series. */
    private final int k;
    /** The order of the model. */
    private final int p;
    /** Whether an intercept is included. */
    private final boolean intercept;
    /** The observed series. */
    private final double[][] x;
    /** The intercept vector; all zeros when {@link #intercept} is false. */
    private final double[] c;
    /** The coefficient matrices; {@code A[l-1][response][predictor]}. */
    private final double[][][] A;
    /** The residual covariance matrix. */
    private final double[][] covariance;
    /** The fitted values (n × k). */
    private final double[][] fittedValues;
    /** The residuals (n × k). */
    private final double[][] residuals;
    /** The log-likelihood. */
    private final double logLikelihood;
    /** The AIC. */
    private final double aic;
    /** The BIC. */
    private final double bic;

    private VAR(int k, int p, boolean intercept, double[][] x, double[] c, double[][][] A,
                double[][] covariance, double[][] fittedValues, double[][] residuals,
                double logLikelihood, double aic, double bic) {
        this.k = k;
        this.p = p;
        this.intercept = intercept;
        this.x = x;
        this.c = c;
        this.A = A;
        this.covariance = covariance;
        this.fittedValues = fittedValues;
        this.residuals = residuals;
        this.logLikelihood = logLikelihood;
        this.aic = aic;
        this.bic = bic;
    }

    /**
     * Fits a VAR(p) model with an intercept.
     *
     * @param x the series, an n-by-k array of observations in time order.
     * @param p the order of the model.
     * @return the fitted model.
     */
    public static VAR fit(double[][] x, int p) {
        return fit(x, p, true);
    }

    /**
     * Fits a VAR(p) model.
     *
     * @param x the series, an n-by-k array of observations in time order.
     * @param p the order of the model.
     * @param intercept whether to include an intercept.
     * @return the fitted model.
     */
    public static VAR fit(double[][] x, int p, boolean intercept) {
        validate(x, p);

        int n = x.length;
        int k = x[0].length;
        int rows = n - p;
        int columns = (intercept ? 1 : 0) + p * k;

        double[][] Y = new double[rows][k];
        DenseMatrix Z = DenseMatrix.zeros(Float64, rows, columns);

        int offset = intercept ? 1 : 0;
        for (int i = 0; i < rows; i++) {
            int t = p + i;
            Y[i] = Arrays.copyOf(x[t], k);
            int col = offset;
            for (int lag = 1; lag <= p; lag++) {
                for (int j = 0; j < k; j++) {
                    Z.set(i, col++, x[t - lag][j]);
                }
            }
            if (intercept) {
                Z.set(i, 0, 1.0);
            }
        }

        // Solve Z * B = Y for all k right-hand sides. The equations share the
        // regressors, so a single factorisation per column via SVD is enough and
        // is rank-deficiency safe.
        double[][] B = new double[columns][k];
        SVD svd = Z.copy().svd();
        for (int j = 0; j < k; j++) {
            double[] y = new double[rows];
            for (int i = 0; i < rows; i++) {
                y[i] = Y[i][j];
            }
            double[] b = svd.solve(y).toArray(new double[columns]);
            for (int i = 0; i < columns; i++) {
                B[i][j] = b[i];
            }
        }

        double[] c = new double[k];
        double[][][] A = new double[p][k][k];
        int col = offset;
        if (intercept) {
            for (int j = 0; j < k; j++) {
                c[j] = B[0][j];
            }
        }
        for (int lag = 0; lag < p; lag++) {
            for (int j = 0; j < k; j++) {
                for (int i = 0; i < k; i++) {
                    A[lag][j][i] = B[col + lag * k + i][j];
                }
            }
        }

        double[][] fitted = new double[rows][k];
        double[][] residuals = new double[rows][k];
        for (int r = 0; r < rows; r++) {
            for (int j = 0; j < k; j++) {
                double yhat = intercept ? c[j] : 0.0;
                for (int lag = 0; lag < p; lag++) {
                    double[] lagged = x[p + r - lag - 1];
                    for (int i = 0; i < k; i++) {
                        yhat += A[lag][j][i] * lagged[i];
                    }
                }
                fitted[r][j] = yhat;
                residuals[r][j] = Y[r][j] - yhat;
            }
        }

        double[][] covariance = new double[k][k];
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < k; i++) {
                double sum = 0.0;
                for (int r = 0; r < rows; r++) {
                    sum += residuals[r][i] * residuals[r][j];
                }
                covariance[i][j] = sum / rows;
            }
        }

        double logDet = logDeterminant(covariance);
        if (Double.isNaN(logDet)) {
            logger.warn("VAR residual covariance is singular; log-likelihood is reported as NaN");
        }
        double logLikelihood = -0.5 * rows * (k * (1.0 + Math.log(2 * Math.PI)) + logDet);
        int nParams = k * (k * p + (intercept ? 1 : 0));
        double aic = 2 * nParams - 2 * logLikelihood;
        double bic = nParams * Math.log(rows) - 2 * logLikelihood;

        return new VAR(k, p, intercept, x, c, A, covariance, fitted, residuals,
                logLikelihood, aic, bic);
    }

    private static void validate(double[][] x, int p) {
        if (x == null || x.length == 0) {
            throw new IllegalArgumentException("Empty data");
        }
        if (x[0] == null || x[0].length < 2) {
            throw new IllegalArgumentException("VAR requires at least two series");
        }
        if (p < 1) {
            throw new IllegalArgumentException("Invalid order p = " + p);
        }

        int k = x[0].length;
        for (int i = 0; i < x.length; i++) {
            if (x[i] == null || x[i].length != k) {
                throw new IllegalArgumentException("Jagged or null row at index " + i);
            }
            for (double v : x[i]) {
                if (!Double.isFinite(v)) {
                    throw new IllegalArgumentException("Non-finite value at row " + i);
                }
            }
        }

        if (x.length <= p * k + 1) {
            throw new IllegalArgumentException(String.format(
                    "Not enough observations for VAR(%d) with k=%d: length=%d, required>%d",
                    p, k, x.length, p * k + 1));
        }
    }

    /** Returns the log-determinant of a symmetric positive definite matrix. */
    private static double logDeterminant(double[][] sigma) {
        int k = sigma.length;
        DenseMatrix S = DenseMatrix.of(sigma);
        try {
            EVD evd = S.eigen();
            double logDet = 0.0;
            for (int i = 0; i < k; i++) {
                double w = evd.wr().get(i);
                if (w <= 0.0 || !Double.isFinite(w)) {
                    return Double.NaN;
                }
                logDet += Math.log(w);
            }
            return logDet;
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    /**
     * Returns the number of series.
     * @return k.
     */
    public int k() {
        return k;
    }

    /**
     * Returns the order of the model.
     * @return p.
     */
    public int p() {
        return p;
    }

    /**
     * Returns whether the model includes an intercept.
     * @return true if an intercept is fitted.
     */
    public boolean hasIntercept() {
        return intercept;
    }

    /**
     * Returns the intercept vector.
     * @return the intercept vector (zero when absent).
     */
    public double[] intercept() {
        return c.clone();
    }

    /**
     * Returns the coefficient matrices.
     * @return a copy of the coefficients, indexed {@code [lag][response][predictor]}.
     */
    public double[][][] coefficients() {
        double[][][] copy = new double[A.length][][];
        for (int lag = 0; lag < A.length; lag++) {
            copy[lag] = new double[A[lag].length][];
            for (int j = 0; j < A[lag].length; j++) {
                copy[lag][j] = A[lag][j].clone();
            }
        }
        return copy;
    }

    /**
     * Returns the residual covariance matrix.
     * @return a copy of the covariance matrix.
     */
    public double[][] covariance() {
        double[][] copy = new double[k][];
        for (int i = 0; i < k; i++) {
            copy[i] = covariance[i].clone();
        }
        return copy;
    }

    /**
     * Returns the fitted values.
     * @return a copy of the fitted values (n-p × k).
     */
    public double[][] fittedValues() {
        return deepCopy(fittedValues);
    }

    /**
     * Returns the residuals.
     * @return a copy of the residuals (n-p × k).
     */
    public double[][] residuals() {
        return deepCopy(residuals);
    }

    private static double[][] deepCopy(double[][] a) {
        double[][] copy = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            copy[i] = a[i].clone();
        }
        return copy;
    }

    /**
     * Returns the log-likelihood.
     * @return the log-likelihood.
     */
    public double logLikelihood() {
        return logLikelihood;
    }

    /**
     * Returns the Akaike information criterion.
     * @return the AIC.
     */
    public double aic() {
        return aic;
    }

    /**
     * Returns the Bayesian information criterion.
     * @return the BIC.
     */
    public double bic() {
        return bic;
    }

    /**
     * Tests the stability of the process: true when the spectral radius of the
     * companion matrix is strictly less than 1.
     *
     * @return true if the process is stable.
     */
    public boolean isStable() {
        return spectralRadius() < 1.0;
    }

    /**
     * Returns the spectral radius of the companion matrix, i.e. the largest
     * modulus of its eigenvalues. The process is stable when it is below 1.
     *
     * @return the spectral radius.
     */
    public double spectralRadius() {
        int dim = k * p;
        DenseMatrix companion = DenseMatrix.zeros(Float64, dim, dim);
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                companion.set(i, j, A[0][i][j]);
            }
        }
        for (int lag = 1; lag < p; lag++) {
            for (int j = 0; j < k; j++) {
                companion.set(lag * k + j, (lag - 1) * k + j, 1.0);
            }
        }

        EVD evd = companion.eigen();
        double radius = 0.0;
        for (int i = 0; i < dim; i++) {
            double re = evd.wr().get(i);
            double im = evd.wi() == null ? 0.0 : evd.wi().get(i);
            radius = Math.max(radius, Math.hypot(re, im));
        }
        return radius;
    }

    /**
     * Returns forecasts of the given number of steps ahead.
     *
     * @param steps the number of forecast steps; must be positive.
     * @return a steps-by-k array of forecasts.
     */
    public double[][] forecast(int steps) {
        if (steps <= 0) {
            throw new IllegalArgumentException("Invalid forecast horizon l = " + steps);
        }

        int n = x.length;
        double[][] forecasts = new double[steps][k];
        for (int h = 0; h < steps; h++) {
            double[] yhat = new double[k];
            for (int j = 0; j < k; j++) {
                double value = intercept ? c[j] : 0.0;
                for (int lag = 0; lag < p; lag++) {
                    // y_{n+h-lag-1}: observed history for the first h steps,
                    // then the forecasts already produced.
                    double[] row = h - lag - 1 >= 0 ? forecasts[h - lag - 1] : x[n + h - lag - 1];
                    for (int i = 0; i < k; i++) {
                        value += A[lag][j][i] * row[i];
                    }
                }
                yhat[j] = value;
            }
            forecasts[h] = yhat;
        }

        return forecasts;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "VAR(%d) with %d series:%n", p, k));
        sb.append(String.format(Locale.ROOT, "%nStability: spectral radius = %.4f (%s)%n",
                spectralRadius(), isStable() ? "stable" : "unstable"));

        for (int lag = 0; lag < p; lag++) {
            sb.append(String.format(Locale.ROOT, "%nCoefficients of lag %d:%n", lag + 1));
            for (int j = 0; j < k; j++) {
                sb.append("  ");
                for (int i = 0; i < k; i++) {
                    sb.append(String.format(Locale.ROOT, "%10.4f ", A[lag][j][i]));
                }
                sb.append(String.format(Locale.ROOT, "%n"));
            }
        }

        if (intercept) {
            sb.append(String.format(Locale.ROOT, "%nIntercept:%n  "));
            for (int j = 0; j < k; j++) {
                sb.append(String.format(Locale.ROOT, "%10.4f ", c[j]));
            }
            sb.append(String.format(Locale.ROOT, "%n"));
        }

        sb.append(String.format(Locale.ROOT, "%nResidual covariance:%n"));
        for (int j = 0; j < k; j++) {
            sb.append("  ");
            for (int i = 0; i < k; i++) {
                sb.append(String.format(Locale.ROOT, "%10.4f ", covariance[j][i]));
            }
            sb.append(String.format(Locale.ROOT, "%n"));
        }

        sb.append(String.format(Locale.ROOT, "%nAIC: %.4f  BIC: %.4f  Log-Likelihood: %.4f",
                aic, bic, logLikelihood));
        return sb.toString();
    }
}
