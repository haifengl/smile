/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.plot.swing

import java.awt.Color.{BLUE, RED}
import java.lang.Math.*
import org.specs2.mutable.*
import smile.read
import smile.interpolation.*
import smile.math.matrix.*
import smile.stat.distribution.*
import smile.plot.show
import smile.plot.Render.*
import smile.io.Paths

class PlotSpec extends Specification {
  val iris = read.arff(Paths.getTestData("weka/iris.arff"))
  // the matrix to display
  val z = Array(
    Array(1.0, 2.0, 4.0, 1.0),
    Array(6.0, 3.0, 5.0, 2.0),
    Array(4.0, 2.0, 1.0, 5.0),
    Array(5.0, 4.0, 2.0, 3.0)
  )
  // make the matrix larger with bicubic interpolation
  val x = Array(0.0, 1.0, 2.0, 3.0)
  val y = Array(0.0, 1.0, 2.0, 3.0)
  val bicubic = new BicubicInterpolation(x, y, z)
  val Z = Array.ofDim[Double](101, 101)
  for (i <- 0 to 100) {
    for (j <- 0 to 100)
      Z(i)(j) = bicubic.interpolate(i * 0.03, j * 0.03)
  }

  "Plot" should {
    "Heart" in {
      val heart = -314 to 314 map { i =>
        val t = i / 100.0
        val x = 16 * pow(sin(t), 3)
        val y = 13 * cos(t) - 5 * cos(2*t) - 2 * cos(3*t) - cos(4*t)
        Array(x, y)
      }

      show(line(heart.toArray, color = RED))
      1 mustEqual 1
    }
    "Scatter" in {
      val canvas = plot(iris, "sepallength", "sepalwidth", "class", '*')
      canvas.setAxisLabels("sepallength", "sepalwidth")
      show(canvas)
      1 mustEqual 1
    }
    "Iris" in {
      val canvas = plot(iris, "sepallength", "sepalwidth", "petallength", "class", '*')
      canvas.setAxisLabels("sepallength", "sepalwidth", "petallength")
      show(canvas)
      1 mustEqual 1
    }
    "SPLOM" in {
      show(splom(iris, '*', "class"))
      1 mustEqual 1
    }
    "Box" in {
      val groups = (iris("sepallength").toDoubleArray zip iris("class").toStringArray).groupBy(_._2)
      val labels = groups.keys.toArray
      val data = groups.values.map { a => a.map(_._1) }.toArray
      val canvas = boxplot(data, labels)
      canvas.setAxisLabels("", "sepallength")
      show(canvas)
      1 mustEqual 1
    }
    "Histogram" in {
      val cow = read.csv(Paths.getTestData("stat/cow.txt").toString, header = false)("V1").toDoubleArray
      val canvas = hist(cow.filter(_ <= 3500), 50)
      canvas.setAxisLabels("Weight", "Probability")
      show(canvas)
      1 mustEqual 1
    }
    "Histogram 3D" in {
      val gauss = new MultivariateGaussianDistribution(Array(0.0, 0.0), Matrix.of(Array(Array(1.0, 0.6), Array(0.6, 2.0))))
      val data = (0 until 10000) map { _ => gauss.rand }
      show(hist3(data.toArray, 50, 50))
      1 mustEqual 1
    }
    "QQ" in {
      val gauss = new GaussianDistribution(0.0, 1.0)
      val data = (0 until 1000) map { _ => gauss.rand }
      show(qqplot(data.toArray))
      1 mustEqual 1
    }
    "Heatmap" in {
      show(heatmap(Z, Palette.jet(256)))
      1 mustEqual 1
    }
    "Sparse Matrix" in {
      val sparse = SparseMatrix.text(Paths.getTestData("matrix/mesh2em5.txt"))
      val canvas = spy(sparse)
      canvas.setTitle("mesh2em5")
      show(canvas)
      1 mustEqual 1
    }
    "Contour" in {
      val canvas = heatmap(Z, Palette.jet(256))
      canvas.add(Contour.of(Z))
      show(canvas)
      1 mustEqual 1
    }
    "Surface" in {
      show(surface(Z, Palette.jet(256, 1.0f)))
      1 mustEqual 1
    }
    "Wireframe" in {
      val (vertices, edges) = read.wavefront(Paths.getTestData("wavefront/teapot.obj"))
      show(wireframe(vertices, edges))
      1 mustEqual 1
    }
    "Interpolation" in {
      val x = Array(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
      val y = Array(0.0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794)

      val controls = Array.ofDim[Double](x.length, 2)
      for (i <- x.indices) {
        controls(i)(0) = x(i)
        controls(i)(1) = y(i)
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
