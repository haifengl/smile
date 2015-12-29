/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile

import java.awt.{Dimension, Color}
import javax.swing.{JFrame, WindowConstants}

/**
 * Graphics & plotting shell commands.
 *
 * @author Haifeng Li
 */
package object plot {

  val windowCount = new java.util.concurrent.atomic.AtomicInteger

  /** Create a window/JFrame */
  def window(title: String = ""): JFrame = {
    val t = if (title.isEmpty) { "SMILE Plot " + windowCount.addAndGet(1) } else title
    val frame = new JFrame(t)
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    frame.setSize(new Dimension(1000, 1000))
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)
    frame
  }

  /**
   * Scatter plot.
   *
   * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
   * @param color the color used to draw points.
   * @param legend the legend used to draw points.
   *               <ul>
   *               <li> . : dot
   *               <li> + : +
   *               <li> - : -
   *               <li> | : |
   *               <li> * : star
   *               <li> x : x
   *               <li> o : circle
   *               <li> O : large circle
   *               <li> @ : solid circle
   *               <li> # : large solid circle
   *               <li> s : square
   *               <li> S : large square
   *               <li> q : solid square
   *               <li> Q : large solid square
   *               <li> others : dot
   *               </ul>
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def plot(data: Array[Array[Double]], legend: Char = '*', color: Color = Color.BLACK): (JFrame, PlotCanvas) = {
    val canvas = ScatterPlot.plot(data, legend, color)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * Scatter plot.
   *
   * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
   * @param labels labels of points.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def plot(data: Array[Array[Double]], labels: Array[String]): (JFrame, PlotCanvas) = {
    val canvas = ScatterPlot.plot(data, labels)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * Scatter plot.
   *
   * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
   * @param label the class labels of data.
   * @param legend the legend for each class.
   * @param palette the colors for each class.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def plot(data: Array[Array[Double]], label: Array[Int], legend: Array[Char], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = ScatterPlot.plot(data, label, legend, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * Scatter plot which connects points by straight lines.
   *
   * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
   * @param style the stroke style of line.
   * @param legend the legend used to draw data points. The default value ' ' makes the point indistinguishable
   *               from the line on purpose.
   * @param color the color of line.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def line(data: Array[Array[Double]], style: Line.Style = Line.Style.SOLID, color: Color = Color.BLACK, legend: Char = ' '): (JFrame, PlotCanvas) = {
    val canvas = LinePlot.plot(data, style, color)

    if (legend != ' ') {
      val scatter = new ScatterPlot(data, legend)
      scatter.setColor(color)
      canvas.add(scatter)
    }

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * A boxplot is a convenient way of graphically depicting groups of numerical
   * data through their five-number summaries (the smallest observation
   * (sample minimum), lower quartile (Q1), median (Q2), upper quartile (Q3),
   * and largest observation (sample maximum). A boxplot may also indicate
   * which observations, if any, might be considered outliers.
   * <p>
   * Boxplots can be useful to display differences between populations without
   * making any assumptions of the underlying statistical distribution: they are
   * non-parametric. The spacings between the different parts of the box help
   * indicate the degree of dispersion (spread) and skewness in the data, and
   * identify outliers.
   * <p>
   * For a data set, we construct a boxplot in the following manner:
   * <ul>
   * <li> Calculate the first q<sub>1</sub>, the median q<sub>2</sub> and third
   * quartile q<sub>3</sub>.
   * <li> Calculate the interquartile range (IQR) by subtracting the first
   * quartile from the third quartile. (q<sub>3</sub> ? q<sub>1</sub>)
   * <li> Construct a box above the number line bounded on the bottom by the first
   * quartile (q<sub>1</sub>) and on the top by the third quartile (q<sub>3</sub>).
   * <li> Indicate where the median lies inside of the box with the presence of
   * a line dividing the box at the median value.
   * <li> Any data observation which lies more than 1.5*IQR lower than the first
   * quartile or 1.5IQR higher than the third quartile is considered an outlier.
   * Indicate where the smallest value that is not an outlier is by connecting it
   * to the box with a horizontal line or "whisker". Optionally, also mark the
   * position of this value more clearly using a small vertical line. Likewise,
   * connect the largest value that is not an outlier to the box by a "whisker"
   * (and optionally mark it with another small vertical line).
   * <li> Indicate outliers by dots.
   * </ul>
   *
   * @param data a data matrix of which each row will create a box plot.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def boxplot(data: Array[Double]*): (JFrame, PlotCanvas) = {
    val canvas = BoxPlot.plot(data: _*)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * Box plot.
   *
   * @param data a data matrix of which each row will create a box plot.
   * @param labels the labels for each box plot.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def boxplot(data: Array[Array[Double]], labels: Array[String]): (JFrame, PlotCanvas) = {
    val canvas = BoxPlot.plot(data, labels)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * A contour plot is a graphical technique for representing a 3-dimensional
   * surface by plotting constant z slices, called contours, on a 2-dimensional
   * format. That is, given a value for z, lines are drawn for connecting the
   * (x, y) coordinates where that z value occurs. The contour plot is an
   * alternative to a 3-D surface plot.
   *
   * @param z the data matrix to create contour plot.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def contour(z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Contour.plot(z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * A contour plot is a graphical technique for representing a 3-dimensional
   * surface by plotting constant z slices, called contours, on a 2-dimensional
   * format. That is, given a value for z, lines are drawn for connecting the
   * (x, y) coordinates where that z value occurs. The contour plot is an
   * alternative to a 3-D surface plot.
   *
   * @param z the data matrix to create contour plot.
   * @param levels the level values of contours.
   * @param palette the color for each contour level.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def contour(z: Array[Array[Double]], levels: Array[Double], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Contour.plot(z, levels, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * A contour plot is a graphical technique for representing a 3-dimensional
   * surface by plotting constant z slices, called contours, on a 2-dimensional
   * format. That is, given a value for z, lines are drawn for connecting the
   * (x, y) coordinates where that z value occurs. The contour plot is an
   * alternative to a 3-D surface plot.
   *
   * @param x the x coordinates of the data grid of z. Must be in ascending order.
   * @param y the y coordinates of the data grid of z. Must be in ascending order.
   * @param z the data matrix to create contour plot.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Contour.plot(x, y, z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /**
   * A contour plot is a graphical technique for representing a 3-dimensional
   * surface by plotting constant z slices, called contours, on a 2-dimensional
   * format. That is, given a value for z, lines are drawn for connecting the
   * (x, y) coordinates where that z value occurs. The contour plot is an
   * alternative to a 3-D surface plot.
   *
   * @param x the x coordinates of the data grid of z. Must be in ascending order.
   * @param y the y coordinates of the data grid of z. Must be in ascending order.
   * @param z the data matrix to create contour plot.
   * @param levels the level values of contours.
   * @param palette the color for each contour level.
   *
   * @return a tuple of window frame and plot canvas which can be added other shapes.
   */
  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]], levels: Array[Double], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Contour.plot(x, y, z, levels, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }
}
