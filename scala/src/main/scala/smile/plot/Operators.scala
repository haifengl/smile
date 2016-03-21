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

import java.awt.{GridLayout, Color}
import javax.swing.{JFrame, JPanel}
import smile.data.AttributeDataset
import smile.classification.Classifier
import smile.regression.Regression
import smile.clustering.HierarchicalClustering
import smile.math.Math
import smile.math.matrix.SparseMatrix
import smile.stat.distribution.{Distribution, DiscreteDistribution}
import smile.projection.PCA

/** Data visualization operators.
  *
  * @author Haifeng Li
  */
trait Operators {
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
  def plot(data: Array[Array[Double]], legend: Char = '*', color: Color = Color.BLACK): Window = {
    val canvas = ScatterPlot.plot(data, legend, color)
    Window(canvas)
  }

  /** Scatter plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param labels labels of points.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], labels: Array[String]): Window = {
    val canvas = ScatterPlot.plot(data, labels)
    Window(canvas)
  }

  /** Scatter plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param label the class labels of data.
    * @param legend the legend for all classes.
    * @param palette the color for each class.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], label: Array[Int], legend: Char, palette: Array[Color]): Window = {
    val canvas = ScatterPlot.plot(data, label, legend, palette)
    Window(canvas)
  }

  /** Scatter plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param label the class labels of data.
    * @param legend the legend for each class.
    * @param palette the color for each class.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], label: Array[Int], legend: Array[Char], palette: Array[Color]): Window = {
    val canvas = ScatterPlot.plot(data, label, legend, palette)
    Window(canvas)
  }

  /** Plot a grid of scatter plots of for all attribute pairs in the attribute data.
    *
    * @param data an attribute frame.
    * @param legend the legend for all classes.
    * @return the window frame.
    */
  def plot(data: AttributeDataset, legend: Char): JFrame = {
    val x = data.unzip
    val p = data.attributes.length
    val names = data.attributes.map(_.getName)

    val panel = new JPanel(new GridLayout(p, p))
    panel.setBackground(Color.white)

    for (i <- 0 until p) {
      for (j <- 0 until p) {
        val x2 = x.map { row => Array(row(i), row(j)) }
        val canvas = ScatterPlot.plot(x2, legend)
        canvas.setAxisLabels(names(i), names(j))
        panel.add(canvas)
      }
    }

    val frame = Window.frame(data.getName)
    frame.add(panel)
    frame
  }

  /** Plot a grid of scatter plots of for all attribute pairs in the attribute data of which the response variable is
    * integer.
    *
    * @param data an attribute frame.
    * @param legend the legend for all classes.
    * @param palette the color for each class.
    * @return the window frame.
    */
  def plot(data: AttributeDataset, legend: Char, palette: Array[Color]): JFrame = {
    val (x, y) = data.unzipInt
    val p = data.attributes.length
    val names = data.attributes.map(_.getName)

    val panel = new JPanel(new GridLayout(p, p))
    panel.setBackground(Color.white)

    for (i <- 0 until p) {
      for (j <- 0 until p) {
        val x2 = x.map { row => Array(row(i), row(j)) }
        val canvas = ScatterPlot.plot(x2, y, legend, palette)
        canvas.setAxisLabels(names(i), names(j))
        panel.add(canvas)
      }
    }

    val frame = Window.frame(data.getName)
    frame.add(panel)
    frame
  }

  /** Plot a grid of scatter plots of for all attribute pairs in the attribute data of which the response variable is
    * integer.
    *
    * @param data an attribute frame.
    * @param legend the legend for each class.
    * @param palette the color for each class.
    * @return the window frame.
    */
  def plot(data: AttributeDataset, legend: Array[Char], palette: Array[Color]): JFrame = {
    val (x, y) = data.unzipInt
    val p = data.attributes.length
    val names = data.attributes.map(_.getName)

    val panel = new JPanel(new GridLayout(p, p))
    panel.setBackground(Color.white)

    for (i <- 0 until p) {
      for (j <- 0 until p) {
        val x2 = x.map { row => Array(row(i), row(j)) }
        val canvas = ScatterPlot.plot(x2, y, legend, palette)
        canvas.setAxisLabels(names(i), names(j))
        panel.add(canvas)
      }
    }

    val frame = Window.frame(data.getName)
    frame.add(panel)
    frame
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
  def line(data: Array[Array[Double]], style: Line.Style = Line.Style.SOLID, color: Color = Color.BLACK, legend: Char = ' '): Window = {
    val canvas = LinePlot.plot(data, style, color)

    if (legend != ' ') {
      val scatter = new ScatterPlot(data, legend)
      scatter.setColor(color)
      canvas.add(scatter)
    }

    Window(canvas)
  }

  /** Create a plot canvas with the staircase line plot.
    * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
    */
  def staircase(data: Array[Double]*): Window = {
    val canvas = StaircasePlot.plot(data: _*)
    Window(canvas)
  }

  /** A box plot is a convenient way of graphically depicting groups of numerical
    * data through their five-number summaries (the smallest observation
    * (sample minimum), lower quartile (Q1), median (Q2), upper quartile (Q3),
    * and largest observation (sample maximum). A box plot may also indicate
    * which observations, if any, might be considered outliers.
    *
    * Box plots can be useful to display differences between populations without
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
  def boxplot(data: Array[Double]*): Window = {
    val canvas = BoxPlot.plot(data: _*)
    Window(canvas)
  }

  /** Box plot.
    *
    * @param data a data matrix of which each row will create a box plot.
    * @param labels the labels for each box plot.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def boxplot(data: Array[Array[Double]], labels: Array[String]): Window = {
    val canvas = BoxPlot.plot(data, labels)
    Window(canvas)
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
  def contour(z: Array[Array[Double]]): Window = {
    val canvas = Contour.plot(z)
    Window(canvas)
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
  def contour(z: Array[Array[Double]], levels: Array[Double], palette: Array[Color]): Window = {
    val canvas = Contour.plot(z, levels, palette)
    Window(canvas)
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
  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): Window = {
    val canvas = Contour.plot(x, y, z)
    Window(canvas)
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
  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]], levels: Array[Double], palette: Array[Color]): Window = {
    val canvas = Contour.plot(x, y, z, levels, palette)
    Window(canvas)
  }

  /** 3D surface plot.
    *
    * @param z the z-axis values of surface.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def surface(z: Array[Array[Double]]): Window = {
    val canvas = Surface.plot(z)
    Window(canvas)
  }

  /** 3D surface plot.
    *
    * @param z the z-axis values of surface.
    * @param palette the color palette.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def surface(z: Array[Array[Double]], palette: Array[Color]): Window = {
    val canvas = Surface.plot(z, palette)
    Window(canvas)
  }

  /** 3D surface plot.
    *
    * @param x the x-axis values of surface.
    * @param y the y-axis values of surface.
    * @param z the z-axis values of surface.
    *
    * @return a tuple of window frame and plot canvas which can be added other shapes.
    */
  def surface(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): Window = {
    val canvas = Surface.plot(x, y, z)
    Window(canvas)
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
  def surface(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): Window = {
    val canvas = Surface.plot(x, y, z, palette)
    Window(canvas)
  }

  /** Wire frame plot.
    * A wire frame model specifies each edge of the physical object where two
    * mathematically continuous smooth surfaces meet, or by connecting an
    * object's constituent vertices using straight lines or curves.
    *
    * @param vertices a n-by-2 or n-by-3 array which are coordinates of n vertices.
    * @param edges an m-by-2 array of which each row is the vertex indices of two
    *              end points of each edge.
    */
  def wireframe(vertices: Array[Array[Double]], edges: Array[Array[Int]]): Window = {
    val canvas = Wireframe.plot(vertices, edges)
    Window(canvas)
  }

  /** 2D grid plot.
    *
    * @param data an m x n x 2 array which are coordinates of m x n grid.
    */
  def grid(data: Array[Array[Array[Double]]]): Window = {
    val canvas = Grid.plot(data)
    Window(canvas)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def heatmap(z: Array[Array[Double]]): Window = {
    val canvas = Heatmap.plot(z)
    Window(canvas)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def heatmap(z: Array[Array[Double]], palette: Array[Color]): Window = {
    val canvas = Heatmap.plot(z, palette)
    Window(canvas)
  }

  /** Pseudo heat map plot.
    * @param x x coordinate of data matrix cells. Must be in ascending order.
    * @param y y coordinate of data matrix cells. Must be in ascending order.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): Window = {
    val canvas = Heatmap.plot(x, y, z)
    Window(canvas)
  }

  /** Pseudo heat map plot.
    * @param x x coordinate of data matrix cells. Must be in ascending order.
    * @param y y coordinate of data matrix cells. Must be in ascending order.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): Window = {
    val canvas = Heatmap.plot(x, y, z, palette)
    Window(canvas)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param rowLabels the labels for rows of data matrix.
    * @param columnLabels the labels for columns of data matrix.
    */
  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]]): Window = {
    val canvas = Heatmap.plot(rowLabels, columnLabels, z)
    Window(canvas)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param rowLabels the labels for rows of data matrix.
    * @param columnLabels the labels for columns of data matrix.
    * @param palette the color palette.
    */
  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]], palette: Array[Color]): Window = {
    val canvas = Heatmap.plot(rowLabels, columnLabels, z, palette)
    Window(canvas)
  }

  /** Visualize sparsity pattern.
    * @param matrix a sparse matrix.
    */
  def spy(matrix: SparseMatrix): Window = {
    val canvas = SparseMatrixPlot.plot(matrix)
    Window(canvas)
  }

  /** Heat map with hex shape.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def hexmap(z: Array[Array[Double]]): Window = {
    val canvas = Heatmap.plot(z)
    Window(canvas)
  }

  /** Heat map with hex shape.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def hexmap(z: Array[Array[Double]], palette: Array[Color]): Window = {
    val canvas = Hexmap.plot(z, palette)
    Window(canvas)
  }

  /** Heat map with hex shape.
    * @param labels the descriptions of each cell in the data matrix.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def hexmap(labels: Array[Array[String]], z: Array[Array[Double]]): Window = {
    val canvas = Hexmap.plot(labels, z)
    Window(canvas)
  }

  /** Heat map with hex shape.
    * @param labels the descriptions of each cell in the data matrix.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def hexmap(labels: Array[Array[String]], z: Array[Array[Double]], palette: Array[Color]): Window = {
    val canvas = Hexmap.plot(labels, z, palette)
    Window(canvas)
  }

  /** Histogram plot.
    * @param data a sample set.
    */
  def hist(data: Array[Double]): Window = {
    val canvas = Histogram.plot(data)
    Window(canvas)
  }

  /** Histogram plot.
    * @param data a sample set.
    * @param k the number of bins.
    */
  def hist(data: Array[Double], k: Int): Window = {
    val canvas = Histogram.plot(data, k)
    Window(canvas)
  }

  /** Histogram plot.
    * @param data a sample set.
    * @param breaks an array of size k+1 giving the breakpoints between
    *               histogram cells. Must be in ascending order.
    */
  def hist(data: Array[Double], breaks: Array[Double]): Window = {
    val canvas = Histogram.plot(data, breaks)
    Window(canvas)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    */
  def hist(data: Array[Array[Double]]): Window = {
    val canvas = Histogram3D.plot(data)
    Window(canvas)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    * @param k the number of bins.
    */
  def hist(data: Array[Array[Double]], k: Int): Window = {
    val canvas = Histogram3D.plot(data, k)
    Window(canvas)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    * @param xbins the number of bins on x-axis.
    * @param ybins the number of bins on y-axis.
    */
  def hist(data: Array[Array[Double]], xbins: Int, ybins: Int): Window = {
    val canvas = Histogram3D.plot(data, xbins, ybins)
    Window(canvas)
  }

  /** QQ plot of samples to standard normal distribution.
    * The x-axis is the quantiles of x and the y-axis is the
    * quantiles of normal distribution.
    * @param x a sample set.
    */
  def qqplot(x: Array[Double]): Window = {
    val canvas = QQPlot.plot(x)
    Window(canvas)
  }

  /** QQ plot of samples to given distribution.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of
    * given distribution.
    * @param x a sample set.
    * @param d a distribution.
    */
  def qqplot(x: Array[Double], d: Distribution): Window = {
    val canvas = QQPlot.plot(x, d)
    Window(canvas)
  }

  /** QQ plot of two sample sets.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
    * @param x a sample set.
    * @param y a sample set.
    */
  def qqplot(x: Array[Double], y: Array[Double]): Window = {
    val canvas = QQPlot.plot(x, y)
    Window(canvas)
  }

  /** QQ plot of samples to given distribution.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of
    * given distribution.
    * @param x a sample set.
    * @param d a distribution.
    */
  def qqplot(x: Array[Int], d: DiscreteDistribution): Window = {
    val canvas = QQPlot.plot(x, d)
    Window(canvas)
  }

  /** QQ plot of two sample sets.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
    * @param x a sample set.
    * @param y a sample set.
    */
  def qqplot(x: Array[Int], y: Array[Int]): Window = {
    val canvas = QQPlot.plot(x, y)
    Window(canvas)
  }

  /** Plots the classification boundary.
   *
   * @param x training data.
   * @param y training label.
   * @param model classification model.
   */
  def plot(x: Array[Array[Double]], y: Array[Int], model: Classifier[Array[Double]]): Window = {
    require(x(0).size == 2, "plot of classification model supports only 2-dimensional data")

    val win = plot(x, y, 'o', Palette.COLORS)

    val lower = win.canvas.getLowerBounds
    val upper = win.canvas.getUpperBounds

    val steps = 50
    val step1 = (upper(0) - lower(0)) / steps
    val v1 = (0 to steps).map(lower(0) + step1 * _).toArray

    val step2 = (upper(1) - lower(1)) / steps
    val v2 = (0 to steps).map(lower(1) + step2 * _).toArray

    val z = Array.ofDim[Double](v1.length, v2.length)
    for (i <- 0 to steps) {
      for (j <- 0 to steps) {
        val p = Array(v1(i), v2(j))
        val c = model.predict(p)
        z(j)(i) = c
        win.canvas.point('.', Palette.COLORS(c), p: _*)
      }
    }

    val levels = (0 until Math.max(y)).map(_ + 0.5).toArray
    val contour = new Contour(v1, v2, z, levels)
    contour.showLevelValue(false)
    win.canvas.add(contour)

    win
  }

  /** Plots the regression surface.
    *
    * @param x training data.
    * @param y response variable.
    * @param model regression model.
    */
  def plot(x: Array[Array[Double]], y: Array[Double], model: Regression[Array[Double]]): Window = {
    require(x(0).size == 2, "plot of regression model supports only 2-dimensional data")

    val points = x.zip(y).map { case (x, y) => Array(x(0), x(1), y) }.toArray
    val win = plot(points, 'o')

    val lower = win.canvas.getLowerBounds
    val upper = win.canvas.getUpperBounds

    val steps = 50
    val step1 = (upper(0) - lower(0)) / steps
    val v1 = (0 to steps).map(lower(0) + step1 * _).toArray

    val step2 = (upper(1) - lower(1)) / steps
    val v2 = (0 to steps).map(lower(1) + step2 * _).toArray

    val z = Array.ofDim[Double](v1.length, v2.length)
    for (i <- 0 to steps) {
      for (j <- 0 to steps) {
        val p = Array(v1(i), v2(j))
        z(j)(i) = model.predict(p)
      }
    }

    val surface = new Surface(v1, v2, z, Palette.jet(256))
    win.canvas.add(surface)

    win
  }

  /** The scree plot is a useful visual aid for determining an appropriate number of principal components.
    * The scree plot graphs the eigenvalue against the component number. To determine the appropriate
    * number of components, we look for an "elbow" in the scree plot. The component number is taken to
    * be the point at which the remaining eigenvalues are relatively small and all about the same size.
    *
    * @param pca principal component analysis object.
    */
  def screeplot(pca: PCA): Window = {
    val canvas = PlotCanvas.screeplot(pca)
    Window(canvas)
  }

  /** A dendrogram is a tree diagram to illustrate the arrangement
    * of the clusters produced by hierarchical clustering.
    *
    * @param hc hierarchical clustering object.
    */
  def dendrogram(hc: HierarchicalClustering): Window = {
    val canvas = Dendrogram.plot("Dendrogram", hc.getTree, hc.getHeight)
    Window(canvas)
  }

  /** A dendrogram is a tree diagram to illustrate the arrangement
    * of the clusters produced by hierarchical clustering.
    *
    * @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
    *              step i of the clustering. If an element j in the row is less than n, then
    *              observation j was merged at this stage. If j &ge; n then the merge
    *              was with the cluster formed at the (earlier) stage j-n of the algorithm.
    * @param height a set of n-1 non-decreasing real values, which are the clustering height,
    *               i.e., the value of the criterion associated with the clustering method
    *               for the particular agglomeration.
    */
  def dendrogram(merge: Array[Array[Int]], height: Array[Double]): Window = {
    val canvas = Dendrogram.plot(merge, height)
    Window(canvas)
  }
}
