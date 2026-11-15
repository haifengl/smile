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

import java.io.Serializable;

/**
 * A fitted univariate time series model.
 * <p>
 * The interface gives the backtesting utilities and the residual-based
 * anomaly detector a polymorphic handle on the models of this package
 * ({@link AR}, {@link ARMA}, {@link ARIMA}, {@link ETS}, {@link SARIMA})
 * without changing their existing signatures.
 * <p>
 * All quantities are reported on the scale of the original series, i.e.
 * forecasts are already integrated back over any differencing applied by
 * the model.
 *
 * @author Haifeng Li
 */
public interface TimeSeriesModel extends Serializable {
    /**
     * Returns forecasts of the given number of steps ahead.
     *
     * @param steps the number of forecast steps; must be positive.
     * @return the forecasts, on the original scale of the series.
     */
    double[] forecast(int steps);

    /**
     * Returns the in-sample fitted values.
     *
     * @return the fitted values.
     */
    double[] fittedValues();

    /**
     * Returns the in-sample residuals, i.e. {@code x[t] - fittedValues()[t]}.
     *
     * @return the residuals.
     */
    double[] residuals();

    /**
     * Returns the Gaussian log-likelihood of the fitted model.
     *
     * @return the log-likelihood.
     */
    double logLikelihood();

    /**
     * Returns the Akaike information criterion.
     *
     * @return the AIC.
     */
    double aic();

    /**
     * Returns the Bayesian information criterion.
     *
     * @return the BIC.
     */
    double bic();

    /**
     * Returns the innovation variance of the fitted model.
     *
     * @return the innovation variance.
     */
    double variance();
}
