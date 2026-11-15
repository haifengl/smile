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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import smile.math.MathEx
import smile.timeseries.*

class TimeSeriesTest {

    private val series = DoubleArray(50) { i -> 2.0 * i + MathEx.random() * 0.1 }

    @Test
    fun testAR() {
        val model = ar(series, 2)
        assertEquals(2, model.p())
        assertEquals(3, model.forecast(3).size)
    }

    @Test
    fun testARMA() {
        val model = arma(series, 1, 1)
        assertEquals(1, model.p())
        assertEquals(1, model.q())
    }

    @Test
    fun testARIMA() {
        val model = arima(series, 1, 1, 1)
        assertEquals(1, model.p())
        assertEquals(1, model.d())
        assertEquals(1, model.q())
        assertEquals(5, model.forecast(5).size)
    }

    @Test
    fun testDifferencingAndDiagnostics() {
        val d1 = series.diff(1)
        assertEquals(49, d1.size)

        val stages = series.diffStages(1, 2)
        assertEquals(2, stages.size)
        assertEquals(49, stages[0].size)
        assertEquals(48, stages[1].size)

        val a = series.acf(1)
        assertTrue(a <= 1.0)

        val p = series.pacf(1)
        assertTrue(p <= 1.0)

        val c = series.cov(1)
        assertTrue(c > 0.0)

        val bp = series.boxPierce(5)
        assertEquals(5, bp.df)

        val lb = series.ljungBox(5)
        assertEquals(5, lb.df)
    }

    @Test
    fun testETSAndSARIMAAndVAR() {
        val seasonal = DoubleArray(120) { i -> 20.0 + 3.0 * Math.cos(2 * Math.PI * i / 12) }
        val etsModel = ets(seasonal, period = 12, seasonal = ETS.Seasonal.ADDITIVE)
        assertEquals(4, etsModel.forecast(4).size)
        assertEquals(12, etsAuto(seasonal, 12).period())

        val sarmaModel = sarma(series, 1, 1, 0, 0, 12)
        assertEquals(1, sarmaModel.p())
        assertEquals(2, sarmaModel.forecast(2).size)

        val sarimaModel = sarima(seasonal, 0, 0, 0, 0, 1, 0, 12)
        assertEquals(1, sarimaModel.D())
        assertEquals(3, sarimaModel.forecast(3).size)

        val varFit = varModel(Array(100) { i -> doubleArrayOf(Math.sin(0.1 * i), Math.cos(0.1 * i)) }, p = 1)
        assertEquals(2, varFit.k())
        assertEquals(3, varFit.forecast(3).size)
    }

    @Test
    fun testForecastMetricsAndBacktest() {
        val truth = doubleArrayOf(1.0, 2.0, 3.0)
        val forecast = doubleArrayOf(1.5, 1.5, 3.5)
        val metrics = forecastMetrics(truth, forecast)
        assertTrue(metrics.mae() > 0.0)

        val bt = backtest(DoubleArray(60) { i -> 10.0 + Math.sin(0.2 * i) },
            { h -> AR.fit(h, 2) }, horizon = 3, folds = 3)
        assertEquals(3, bt.folds())
        assertEquals(9, bt.actual().size)
    }
}
