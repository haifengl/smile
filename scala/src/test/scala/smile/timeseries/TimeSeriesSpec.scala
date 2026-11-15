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

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import smile.math.MathEx

class TimeSeriesSpec extends AnyWordSpec with Matchers {

  "timeseries shims" should {
    val series = Array.tabulate(50)(i => 2.0 * i + MathEx.random() * 0.1)

    "fit AR" in {
      val model = ar(series, 2)
      model.p() must be (2)
      model.forecast(3).length must be (3)
    }

    "fit ARMA" in {
      val model = arma(series, 1, 1)
      model.p() must be (1)
      model.q() must be (1)
    }

    "fit ARIMA" in {
      val model = arima(series, 1, 1, 1)
      model.p() must be (1)
      model.d() must be (1)
      model.q() must be (1)
      val forecast = model.forecast(5)
      forecast.length must be (5)
    }

    "compute differencing, acf, pacf, cov, and box tests" in {
      val d1 = diff(series, 1)
      d1.length must be (49)

      val stages = diffStages(series, 1, 2)
      stages.length must be (2)
      stages(0).length must be (49)
      stages(1).length must be (48)

      val a = acf(series, 1)
      a must be <= 1.0

      val p = pacf(series, 1)
      p must be <= 1.0

      val c = cov(series, 1)
      c must be > 0.0

      val bp = boxPierce(series, 5)
      bp.df must be (5)

      val lb = ljungBox(series, 5)
      lb.df must be (5)
    }

    "fit ETS and SARIMA and VAR" in {
      val seasonal = Array.tabulate(120)(i => 20.0 + 3.0 * Math.cos(2 * Math.PI * i / 12))
      val etsModel = ets(seasonal, period = 12, seasonal = ETS.Seasonal.ADDITIVE)
      etsModel.forecast(4).length must be (4)
      etsAuto(seasonal, 12).period() must be (12)

      val sarmaModel = sarma(series, 1, 1, 0, 0, 12)
      sarmaModel.p() must be (1)
      sarmaModel.forecast(2).length must be (2)

      val sarimaModel = sarima(seasonal, 0, 0, 0, 0, 1, 0, 12)
      sarimaModel.D() must be (1)
      sarimaModel.forecast(3).length must be (3)

      val varFit = varModel(Array.tabulate(100)(i => Array(Math.sin(0.1 * i), Math.cos(0.1 * i))), 1)
      varFit.k() must be (2)
      varFit.forecast(3).length must be (3)
    }

    "compute forecast metrics and backtest" in {
      val truth = Array(1.0, 2.0, 3.0)
      val forecast = Array(1.5, 1.5, 3.5)
      val metrics = forecastMetrics(truth, forecast)
      metrics.mae() must be > (0.0)

      val bt = backtest(Array.tabulate(60)(i => 10.0 + Math.sin(0.2 * i)),
        (h: Array[Double]) => AR.fit(h, 2), horizon = 3, folds = 3)
      bt.folds() must be (3)
      bt.actual().length must be (9)
    }
  }
}
