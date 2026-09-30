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
}
