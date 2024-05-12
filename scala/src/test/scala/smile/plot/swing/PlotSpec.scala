/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing

import java.awt.Color.{BLUE, RED}
import org.specs2.mutable._
import smile.interpolation._
import smile.plot.swing._
import smile.plot.show
import smile.plot.Render._

class PlotSpec extends Specification {
  "Plot" should {
    "Interpolation" in {
      val x = Array(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
      val y = Array(0.0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794)

      val controls = Array.ofDim[Double](x.length, 2)
      for (i <- 0 until x.length) {
        controls(i)(0) = x(i);
        controls(i)(1) = y(i);
      }

      val linear = new LinearInterpolation(x, y)

      val linearData = (0 to 60).map { i =>
        val x = i * 0.1
        val y = linear.interpolate(x)
        Array(x, y)
      }.toArray

      val canvas = plot(controls, '*')
      canvas.add(LinePlot.of(linearData, Line.Style.SOLID, BLUE, "Linear"))

      val cubic = new CubicSplineInterpolation1D(x, y)
      val cubicData = (0 to 60).map { i =>
        val x = i * 0.1
        val y = cubic.interpolate(x)
        Array(x, y)
      }.toArray

      canvas.add(LinePlot.of(cubicData, Line.Style.DASH, RED, "Cubic"))

      show(canvas)
      1 mustEqual 1
    }
  }
}
