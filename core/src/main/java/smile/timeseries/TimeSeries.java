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
package smile.timeseries;

import smile.math.MathEx;
import smile.tensor.Cholesky;
import smile.tensor.DenseMatrix;
import smile.tensor.Vector;

/**
 * Time series utility functions.
 *
 * @author Haifeng Li
 */
public interface TimeSeries {
    /**
     * Returns the first-differencing of time series. First-differencing a time
     * series will remove a linear trend (i.e., differences=1).
     * In addition, first-differencing a time series at a lag equal to the
     * period will remove a seasonal trend (e.g., set lag=12 for monthly data).
     *
     * @param x time series
     * @param lag the lag at which to difference
     * @return the first-differencing of time series.
     */
    static double[] diff(double[] x, int lag) {
        return diff(x, lag, 1)[0];
    }

    /**
     * Returns the differencing of time series. First-differencing a time
     * series will remove a linear trend (i.e., differences=1);
     * twice-differencing will remove a quadratic trend (i.e., differences=2).
     * In addition, first-differencing a time series at a lag equal to the
     * period will remove a seasonal trend (e.g., set lag=12 for monthly data).
     *
     * @param x time series
     * @param lag the lag at which to difference
     * @param differences the order of differencing
     * @return the differencing of time series.
     */
    static double[][] diff(double[] x, int lag, int differences) {
        double[][] diff = new double[differences][];

        for (int d = 0; d < differences; d++) {
            int n = x.length - lag;
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                y[i] = x[i + lag] - x[i];
            }
            diff[d] = y;
            x = diff[d];
        }

        return diff;
    }

    /**
     * Auto-covariance function.
     *
     * @param x time series.
     * @param lag the lag.
     * @return auto-covariance.
     */
    static double cov(double[] x, int lag) {
        if (lag < 0) {
            lag = -lag;
        }

        int T = x.length;
        double mu = MathEx.mean(x);

        double cov = 0.0;
        for (int i = lag; i < T; i++) {
            cov += (x[i] - mu) * (x[i-lag] - mu);
        }

        return cov;
    }

    /**
     * Auto-correlation function.
     *
     * @param x time series.
     * @param lag the lag.
     * @return auto-correlation.
     */
    static double acf(double[] x, int lag) {
        if (lag == 0) {
            return 1.0;
        }

        if (lag < 0) {
            lag = -lag;
        }

        int T = x.length;
        double mu = MathEx.mean(x);

        double variance = 0.0;
        for (int i = 0; i < lag; i++) {
            variance += MathEx.pow2(x[i] - mu);
        }

        double cov = 0.0;
        for (int i = lag; i < T; i++) {
            cov += (x[i] - mu) * (x[i-lag] - mu);
            variance += MathEx.pow2(x[i] - mu);
        }

        return cov / variance;
    }

    /**
     * Partial auto-correlation function. The partial auto-correlation function
     * (PACF) gives the partial correlation of a stationary time series with
     * its own lagged values, regressed the values of the time series at all
     * shorter lags.
     *
     * @param x time series.
     * @param lag the lag.
     * @return partial auto-correlation.
     */
    static double pacf(double[] x, int lag) {
        if (lag < 0) {
            lag = -lag;
        }

        if (lag <= 1) {
            return acf(x, lag);
        }

        double[] r = new double[lag];
        for (int i = 0; i < lag; i++) {
            r[i] = acf(x, i+1);
        }

        double[] r1 = new double[lag];
        r1[0] = 1.0;
        System.arraycopy(r, 0, r1, 1, lag - 1);
        DenseMatrix toeplitz = DenseMatrix.toeplitz(r1);

        Cholesky cholesky = toeplitz.cholesky();
        Vector pacf = cholesky.solve(r);

        return pacf.get(lag - 1);
    }
}
