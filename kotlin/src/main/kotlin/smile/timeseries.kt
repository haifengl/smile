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
package smile.timeseries

/**
 * Fits an autoregressive model AR(p).
 *
 * @param x the time series.
 * @param p the order of AR.
 * @return the fitted model.
 */
fun ar(x: DoubleArray, p: Int): AR = AR.fit(x, p)

/**
 * Fits an autoregressive moving average model ARMA(p, q).
 *
 * @param x the time series.
 * @param p the order of AR.
 * @param q the order of MA.
 * @return the fitted model.
 */
fun arma(x: DoubleArray, p: Int, q: Int): ARMA = ARMA.fit(x, p, q)

/**
 * Fits an autoregressive integrated moving average model ARIMA(p, d, q).
 *
 * @param x the time series.
 * @param p the order of AR.
 * @param d the degree of differencing.
 * @param q the order of MA.
 * @return the fitted model.
 */
fun arima(x: DoubleArray, p: Int, d: Int, q: Int): ARIMA = ARIMA.fit(x, p, d, q)

/**
 * Computes the differencing of a time series.
 *
 * @param x time series.
 * @param lag the lag at which to difference.
 * @param differences the order of differencing.
 * @return the differenced series.
 */
fun diff(x: DoubleArray, lag: Int = 1, differences: Int = 1): DoubleArray =
    if (differences == 1) TimeSeries.diff(x, lag) else TimeSeries.diff(x, lag, differences)[differences - 1]

/**
 * Computes the intermediate differencing stages of a time series.
 *
 * @param x time series.
 * @param lag the lag at which to difference.
 * @param differences the order of differencing.
 * @return the array of differencing stages.
 */
fun diffStages(x: DoubleArray, lag: Int = 1, differences: Int = 1): Array<DoubleArray> =
    TimeSeries.diff(x, lag, differences)

/**
 * Auto-correlation function.
 *
 * @param x time series.
 * @param lag the lag.
 * @return the auto-correlation.
 */
fun acf(x: DoubleArray, lag: Int = 1): Double = TimeSeries.acf(x, lag)

/**
 * Partial auto-correlation function.
 *
 * @param x time series.
 * @param lag the lag.
 * @return the partial auto-correlation.
 */
fun pacf(x: DoubleArray, lag: Int = 1): Double = TimeSeries.pacf(x, lag)

/**
 * Auto-covariance function.
 *
 * @param x time series.
 * @param lag the lag.
 * @return the auto-covariance.
 */
fun cov(x: DoubleArray, lag: Int = 1): Double = TimeSeries.cov(x, lag)

/**
 * Box-Pierce test for serial correlation.
 *
 * @param x time series.
 * @param lag the maximum lag for the test.
 * @return the test result.
 */
fun boxPierce(x: DoubleArray, lag: Int = 1): BoxTest = BoxTest.pierce(x, lag)

/**
 * Ljung-Box test for serial correlation.
 *
 * @param x time series.
 * @param lag the maximum lag for the test.
 * @return the test result.
 */
fun ljungBox(x: DoubleArray, lag: Int = 1): BoxTest = BoxTest.ljung(x, lag)

// ── Extension Functions on DoubleArray ────────────────────────────────────────

/** Differencing of this time series. */
@JvmName("diffArray")
fun DoubleArray.diff(lag: Int = 1, differences: Int = 1): DoubleArray = diff(this, lag, differences)

/** Intermediate differencing stages of this time series. */
@JvmName("diffStagesArray")
fun DoubleArray.diffStages(lag: Int = 1, differences: Int = 1): Array<DoubleArray> = diffStages(this, lag, differences)

/** Auto-correlation of this time series. */
@JvmName("acfArray")
fun DoubleArray.acf(lag: Int = 1): Double = acf(this, lag)

/** Partial auto-correlation of this time series. */
@JvmName("pacfArray")
fun DoubleArray.pacf(lag: Int = 1): Double = pacf(this, lag)

/** Auto-covariance of this time series. */
@JvmName("covArray")
fun DoubleArray.cov(lag: Int = 1): Double = cov(this, lag)

/** Box-Pierce test of this time series. */
@JvmName("boxPierceArray")
fun DoubleArray.boxPierce(lag: Int = 1): BoxTest = boxPierce(this, lag)

/** Ljung-Box test of this time series. */
@JvmName("ljungBoxArray")
fun DoubleArray.ljungBox(lag: Int = 1): BoxTest = ljungBox(this, lag)
