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
package smile

import smile.timeseries.{AR, ARMA, ARIMA, BackTest, BoxTest, ETS, ForecastMetrics, SARIMA, SARMA, TimeSeries, VAR}
import smile.util.time

/** Time series analysis and forecasting.
  *
  * @author Haifeng Li
  */
package object timeseries {

  /** Fits an autoregressive model AR(p).
    *
    * @param x the time series.
    * @param p the order of AR.
    * @return the fitted model.
    */
  def ar(x: Array[Double], p: Int): AR = time("AR") {
    AR.fit(x, p)
  }

  /** Fits an autoregressive moving average model ARMA(p, q).
    *
    * @param x the time series.
    * @param p the order of AR.
    * @param q the order of MA.
    * @return the fitted model.
    */
  def arma(x: Array[Double], p: Int, q: Int): ARMA = time("ARMA") {
    ARMA.fit(x, p, q)
  }

  /** Fits an autoregressive integrated moving average model ARIMA(p, d, q).
    *
    * @param x the time series.
    * @param p the order of AR.
    * @param d the degree of differencing.
    * @param q the order of MA.
    * @return the fitted model.
    */
  def arima(x: Array[Double], p: Int, d: Int, q: Int): ARIMA = time("ARIMA") {
    ARIMA.fit(x, p, d, q)
  }

  /** Computes the differencing of a time series.
    *
    * @param x time series.
    * @param lag the lag at which to difference.
    * @param differences the order of differencing.
    * @return the differenced series.
    */
  def diff(x: Array[Double], lag: Int = 1, differences: Int = 1): Array[Double] = {
    if (differences == 1) TimeSeries.diff(x, lag)
    else TimeSeries.diff(x, lag, differences)(differences - 1)
  }

  /** Computes the intermediate differencing stages of a time series.
    *
    * @param x time series.
    * @param lag the lag at which to difference.
    * @param differences the order of differencing.
    * @return the array of differencing stages.
    */
  def diffStages(x: Array[Double], lag: Int = 1, differences: Int = 1): Array[Array[Double]] = {
    TimeSeries.diff(x, lag, differences)
  }

  /** Auto-correlation function.
    *
    * @param x time series.
    * @param lag the lag.
    * @return the auto-correlation.
    */
  def acf(x: Array[Double], lag: Int = 1): Double = {
    TimeSeries.acf(x, lag)
  }

  /** Partial auto-correlation function.
    *
    * @param x time series.
    * @param lag the lag.
    * @return the partial auto-correlation.
    */
  def pacf(x: Array[Double], lag: Int = 1): Double = {
    TimeSeries.pacf(x, lag)
  }

  /** Auto-covariance function.
    *
    * @param x time series.
    * @param lag the lag.
    * @return the auto-covariance.
    */
  def cov(x: Array[Double], lag: Int = 1): Double = {
    TimeSeries.cov(x, lag)
  }

  /** Box-Pierce test for serial correlation.
    *
    * @param x time series.
    * @param lag the maximum lag for the test.
    * @return the test result.
    */
  def boxPierce(x: Array[Double], lag: Int = 1): BoxTest = {
    BoxTest.pierce(x, lag)
  }

  /** Ljung-Box test for serial correlation.
    *
    * @param x time series.
    * @param lag the maximum lag for the test.
    * @return the test result.
    */
  def ljungBox(x: Array[Double], lag: Int = 1): BoxTest = {
    BoxTest.ljung(x, lag)
  }

  /** Fits an exponential smoothing state space model (ETS).
    *
    * @param x the time series.
    * @param period the seasonal period; 1 for non-seasonal data.
    * @param trend the trend component.
    * @param seasonal the seasonal component.
    * @return the fitted model.
    */
  def ets(
    x: Array[Double],
    period: Int = 1,
    trend: ETS.Trend = ETS.Trend.NONE,
    seasonal: ETS.Seasonal = ETS.Seasonal.NONE
  ): ETS = time("ETS") {
    ETS.fit(x, new ETS.Options(period, trend, seasonal))
  }

  /** Fits an ETS model, selecting the trend and seasonal components by AICc.
    *
    * @param x the time series.
    * @param period the seasonal period; 1 for non-seasonal data.
    * @return the best model by AICc.
    */
  def etsAuto(x: Array[Double], period: Int = 1): ETS = time("ETS") {
    ETS.auto(x, period)
  }

  /** Fits a seasonal ARMA model SARMA(p, q)(P, Q)[s].
    *
    * @param x the (stationary) time series.
    * @param p the non-seasonal AR order.
    * @param q the non-seasonal MA order.
    * @param P the seasonal AR order.
    * @param Q the seasonal MA order.
    * @param s the seasonal period.
    * @return the fitted model.
    */
  def sarma(x: Array[Double], p: Int, q: Int, P: Int, Q: Int, s: Int): SARMA = time("SARMA") {
    SARMA.fit(x, p, q, P, Q, s)
  }

  /** Fits a seasonal ARIMA model SARIMA(p, d, q)(P, D, Q)[s].
    *
    * @param x the time series.
    * @param p the non-seasonal AR order.
    * @param d the non-seasonal differencing order.
    * @param q the non-seasonal MA order.
    * @param P the seasonal AR order.
    * @param D the seasonal differencing order.
    * @param Q the seasonal MA order.
    * @param s the seasonal period.
    * @return the fitted model.
    */
  def sarima(x: Array[Double], p: Int, d: Int, q: Int,
             P: Int, D: Int, Q: Int, s: Int): SARIMA = time("SARIMA") {
    SARIMA.fit(x, p, d, q, P, D, Q, s)
  }

  /** Fits a vector autoregressive model VAR(p).
    *
    * @param x the series, an n-by-k array of observations in time order.
    * @param p the order of the model.
    * @param intercept whether to include an intercept.
    * @return the fitted model.
    */
  def varModel(x: Array[Array[Double]], p: Int, intercept: Boolean = true): VAR = time("VAR") {
    VAR.fit(x, p, intercept)
  }

  /** Computes forecast accuracy metrics.
    *
    * @param truth the actual values.
    * @param forecast the forecast values.
    * @return the metrics.
    */
  def forecastMetrics(truth: Array[Double], forecast: Array[Double]): ForecastMetrics = {
    ForecastMetrics.of(truth, forecast)
  }

  /** Runs a rolling-origin backtest.
    *
    * @param x the series, in chronological order.
    * @param fit the model factory.
    * @param horizon the number of points in each test block.
    * @param folds the number of test blocks.
    * @param expanding whether the training window expands (true) or rolls (false).
    * @return the backtest results.
    */
  def backtest(x: Array[Double], fit: BackTest.Fit, horizon: Int, folds: Int,
               expanding: Boolean = true): BackTest = {
    BackTest.rollingOrigin(x, fit, horizon, folds, expanding)
  }
}
