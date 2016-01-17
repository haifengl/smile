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

package smile.plot

import java.awt.{Dimension, Color}
import javax.swing.{JFrame, WindowConstants}
import smile.stat.distribution.{Distribution, DiscreteDistribution}
import smile.math.matrix.SparseMatrix

/** Data visualization operators.
  *
  * @author Haifeng Li
  */
trait Operators {

  private val windowCount = new java.util.concurrent.atomic.AtomicInteger

  /** Create a plot window. */
  def window(title: String = ""): JFrame = {
    val t = if (title.isEmpty) { "Smile Plot " + windowCount.addAndGet(1) } else title
    val frame = new JFrame(t)
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    frame.setSize(new Dimension(1000, 1000))
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)
    frame
  }

  /** Scatter plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param color the color used to draw points.
    * @param legend the legend used to draw points.
    *               - . : dot
    *               - + : +
    *               - - : -
    *               - | : |
    *               - * : star
    *               - x : x
    *               - o : circle
    *               - O : large circle
    *               - @ : solid circle
    *               - # : large solid circle
    *               - s : square
    *               - S : large square
    *               - q : solid square
    *               - Q : large solid square
    *               - others : dot
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], legend: Char = '*', color: Color = Color.BLACK): (JFrame, PlotCanvas) = {
    val canvas = ScatterPlot.plot(data, legend, color)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Scatter plot.
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

  /** Scatter plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param label the class labels of data.
    * @param legend the legend for all classes.
    * @param palette the colors for each class.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], label: Array[Int], legend: Char, palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = ScatterPlot.plot(data, label, legend, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Scatter plot.
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

  /** Line plot.
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

  /** Create a plot canvas with the staircase line plot.
    * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
    */
  def staircase(data: Array[Double]*): (JFrame, PlotCanvas) = {
    val canvas = StaircasePlot.plot(data: _*)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** A boxplot is a convenient way of graphically depicting groups of numerical
    * data through their five-number summaries (the smallest observation
    * (sample minimum), lower quartile (Q1), median (Q2), upper quartile (Q3),
    * and largest observation (sample maximum). A boxplot may also indicate
    * which observations, if any, might be considered outliers.
    *
    * Boxplots can be useful to display differences between populations without
    * making any assumptions of the underlying statistical distribution: they are
    * non-parametric. The spacings between the different parts of the box help
    * indicate the degree of dispersion (spread) and skewness in the data, and
    * identify outliers.
    *
    * For a data set, we construct a boxplot in the following manner:
    *
    *  - Calculate the first q<sub>1</sub>, the median q<sub>2</sub> and third
    * quartile q<sub>3</sub>.
    * - Calculate the interquartile range (IQR) by subtracting the first
    * quartile from the third quartile. (q<sub>3</sub> ? q<sub>1</sub>)
    *  - Construct a box above the number line bounded on the bottom by the first
    * quartile (q<sub>1</sub>) and on the top by the third quartile (q<sub>3</sub>).
    *  - Indicate where the median lies inside of the box with the presence of
    * a line dividing the box at the median value.
    *  - Any data observation which lies more than 1.5*IQR lower than the first
    * quartile or 1.5IQR higher than the third quartile is considered an outlier.
    * Indicate where the smallest value that is not an outlier is by connecting it
    * to the box with a horizontal line or "whisker". Optionally, also mark the
    * position of this value more clearly using a small vertical line. Likewise,
    * connect the largest value that is not an outlier to the box by a "whisker"
    * (and optionally mark it with another small vertical line).
    *  - Indicate outliers by dots.
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

  /** Box plot.
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

  /** Contour plot. A contour plot is a graphical technique for representing a 3-dimensional
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

  /** Contour plot. A contour plot is a graphical technique for representing a 3-dimensional
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

  /** Contour plot. A contour plot is a graphical technique for representing a 3-dimensional
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

  /** Contour plot. A contour plot is a graphical technique for representing a 3-dimensional
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

  /** 3D surface plot.
    *
    * @param z the z-axis values of surface.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def surface(z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Surface.plot(z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** 3D surface plot.
    *
    * @param z the z-axis values of surface.
    * @param palette the color palette.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def surface(z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Surface.plot(z, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** 3D surface plot.
    *
    * @param x the x-axis values of surface.
    * @param y the y-axis values of surface.
    * @param z the z-axis values of surface.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def surface(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Surface.plot(x, y, z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** 3D surface plot.
    *
    * @param x the x-axis values of surface.
    * @param y the y-axis values of surface.
    * @param z the z-axis values of surface.
    * @param palette the color palette.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def surface(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Surface.plot(x, y, z, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Wire frame plot.
    * A wire frame model specifies each edge of the physical object where two
    * mathematically continuous smooth surfaces meet, or by connecting an
    * object's constituent vertices using straight lines or curves.
    *
    * @param vertices an m x n x 2 or m x n x 3 array which are coordinates of m x n grid.
    * @param edges an m-by-2 array of which each row is the vertex indices of two
    *              end points of each edge.
    */
  def wireframe(vertices: Array[Array[Double]], edges: Array[Array[Int]]): (JFrame, PlotCanvas) = {
    val canvas = Wireframe.plot(vertices, edges)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** 2D grid plot.
    *
    * @param data an m x n x 2 array which are coordinates of m x n grid.
    */
  def grid(data: Array[Array[Array[Double]]]): (JFrame, PlotCanvas) = {
    val canvas = Grid.plot(data)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def heatmap(z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Heatmap.plot(z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def heatmap(z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Heatmap.plot(z, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Pseudo heat map plot.
    * @param x x coordinate of data matrix cells. Must be in ascending order.
    * @param y y coordinate of data matrix cells. Must be in ascending order.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Heatmap.plot(x, y, z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Pseudo heat map plot.
    * @param x x coordinate of data matrix cells. Must be in ascending order.
    * @param y y coordinate of data matrix cells. Must be in ascending order.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Heatmap.plot(x, y, z, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param rowLabels the labels for rows of data matrix.
    * @param columnLabels the labels for columns of data matrix.
    */
  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Heatmap.plot(rowLabels, columnLabels, z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param rowLabels the labels for rows of data matrix.
    * @param columnLabels the labels for columns of data matrix.
    * @param palette the color palette.
    */
  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Heatmap.plot(rowLabels, columnLabels, z, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Heat map of sparse matrix.
    * @param matrix a sparse matrix.
    */
  def heatmap(matrix: SparseMatrix): (JFrame, PlotCanvas) = {
    val canvas = SparseMatrixPlot.plot(matrix)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Heat map with hex shape.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def hexmap(z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Heatmap.plot(z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Heat map with hex shape.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def hexmap(z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Hexmap.plot(z, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Heat map with hex shape.
    * @param labels the descriptions of each cell in the data matrix.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def hexmap(labels: Array[Array[String]], z: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Hexmap.plot(labels, z)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Heat map with hex shape.
    * @param labels the descriptions of each cell in the data matrix.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def hexmap(labels: Array[Array[String]], z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas) = {
    val canvas = Hexmap.plot(labels, z, palette)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Histogram plot.
    * @param data a sample set.
    */
  def histogram(data: Array[Double]): (JFrame, PlotCanvas) = {
    val canvas = Histogram.plot(data)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Histogram plot.
    * @param data a sample set.
    * @param k the number of bins.
    */
  def histogram(data: Array[Double], k: Int): (JFrame, PlotCanvas) = {
    val canvas = Histogram.plot(data, k)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Histogram plot.
    * @param data a sample set.
    * @param breaks an array of size k+1 giving the breakpoints between
    *               histogram cells. Must be in ascending order.
    */
  def histogram(data: Array[Double], breaks: Array[Double]): (JFrame, PlotCanvas) = {
    val canvas = Histogram.plot(data, breaks)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    */
  def histogram(data: Array[Array[Double]]): (JFrame, PlotCanvas) = {
    val canvas = Histogram3D.plot(data)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    * @param k the number of bins.
    */
  def histogram(data: Array[Array[Double]], k: Int): (JFrame, PlotCanvas) = {
    val canvas = Histogram3D.plot(data, k)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    * @param xbins the number of bins on x-axis.
    * @param ybins the number of bins on y-axis.
    */
  def histogram(data: Array[Array[Double]], xbins: Int, ybins: Int): (JFrame, PlotCanvas) = {
    val canvas = Histogram3D.plot(data, xbins, ybins)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** QQ plot of samples to standard normal distribution.
    * The x-axis is the quantiles of x and the y-axis is the
    * quantiles of normal distribution.
    * @param x a sample set.
    */
  def qqplot(x: Array[Double]): (JFrame, PlotCanvas) = {
    val canvas = QQPlot.plot(x)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** QQ plot of samples to given distribution.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of
    * given distribution.
    * @param x a sample set.
    * @param d a distribution.
    */
  def qqplot(x: Array[Double], d: Distribution): (JFrame, PlotCanvas) = {
    val canvas = QQPlot.plot(x, d)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** QQ plot of two sample sets.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
    * @param x a sample set.
    * @param y a sample set.
    */
  def qqplot(x: Array[Double], y: Array[Double]): (JFrame, PlotCanvas) = {
    val canvas = QQPlot.plot(x, y)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** QQ plot of samples to given distribution.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of
    * given distribution.
    * @param x a sample set.
    * @param d a distribution.
    */
  def qqplot(x: Array[Int], d: DiscreteDistribution): (JFrame, PlotCanvas) = {
    val canvas = QQPlot.plot(x, d)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** QQ plot of two sample sets.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
    * @param x a sample set.
    * @param y a sample set.
    */
  def qqplot(x: Array[Int], y: Array[Int]): (JFrame, PlotCanvas) = {
    val canvas = QQPlot.plot(x, y)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }

  /** Dendrogram plot.
    *
    * @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
    *              step i of the clustering. If an element j in the row is less than n, then
    *              observation j was merged at this stage. If j &ge; n then the merge
    *              was with the cluster formed at the (earlier) stage j-n of the algorithm.
    * @param height a set of n-1 non-decreasing real values, which are the clustering height,
    *               i.e., the value of the criterion associated with the clustering method
    *               for the particular agglomeration.
    */
  def dendrogram(merge: Array[Array[Int]], height: Array[Double]): (JFrame, PlotCanvas) = {
    val canvas = Dendrogram.plot(merge, height)

    val win = window()
    win.add(canvas)

    (win, canvas)
  }
}
