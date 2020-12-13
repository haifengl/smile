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

/**
 * Time series analysis. A time series is a series of data points indexed
 * in time order. Most commonly, a time series is a sequence taken at
 * successive equally spaced points in time. Thus it is a sequence of
 * discrete-time data.
 * <p>
 * Methods for time series analysis may be divided into two classes:
 * frequency-domain methods and time-domain methods. The former include
 * spectral analysis and wavelet analysis; the latter include
 * auto-correlation and cross-correlation analysis. In the time domain,
 * correlation and analysis can be made in a filter-like manner using
 * scaled correlation, thereby mitigating the need to operate in the
 * frequency domain.
 * <p>
 * The foundation of time series analysis is stationarity. A time series
 * <code>{r_t}</code> is said to be strictly stationary if the joint
 * distribution of <code>(r_t1,...,r_tk)</code> is identical to that of
 * <code>(r_t1+t,...,r_tk+t)</code> for all t, where k is an arbitrary
 * positive integer and <code>(t1,...,tk)</code> is a collection of
 * k positive integers. In other word, strict stationarity requires
 * that the joint distribution of <code>(r_t1,...,r_tk)</code> is
 * invariant under time shift. This is a very strong condition that
 * is hard to verify empirically. A time series <code>{r_t}</code>
 * is weakly stationary if both the mean of r_t and the covariance
 * between r_t and r_t-l are tim invariant, where l is an arbitrary
 * integer.
 * <p>
 * Intuitively, a stationary time series is one whose properties
 * do not depend on the time at which the series is observed.
 * Thus, time series with trends, or with seasonality, are not
 * stationary — the trend and seasonality will affect the value
 * of the time series at different times. On the other hand,
 * a white noise series is stationary — it does not matter when
 * you observe it, it should look much the same at any point in time.
 * Note that a time series with cyclic behaviour (but with no trend
 * or seasonality) is stationary.
 * <p>
 * Differencing is a widely used data transform for making time series
 * stationary. Differencing can help stabilize the mean of the time
 * series by removing changes in the level of a time series, and so
 * eliminating (or reducing) trend and seasonality. In addition,
 * transformations such as logarithms can help to stabilise the
 * variance of a time series.
 *
 * @author Haifeng Li
 */
package smile.timeseries;