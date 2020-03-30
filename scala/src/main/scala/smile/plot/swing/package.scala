/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.plot

import java.awt.image.BufferedImage
import java.awt.{Color, GridLayout}
import java.io.ByteArrayOutputStream
import java.util.Base64

import javax.imageio.ImageIO
import javax.swing.{JComponent, JPanel, SwingUtilities}
import smile.data.DataFrame
import smile.classification.Classifier
import smile.regression.Regression
import smile.clustering.HierarchicalClustering
import smile.math.MathEx
import smile.math.matrix.SparseMatrix
import smile.stat.distribution.{DiscreteDistribution, Distribution}
import smile.projection.PCA

/** Swing based data visualization.
  *
  * @author Haifeng Li
  */
package object swing {
  /** Returns the HTML img tag with the canvas is encoded by BASE64. */
  def img(canvas: JComponent): String = {
    val headless = new Headless(canvas)
    headless.pack
    headless.setVisible(true)
    SwingUtilities.invokeAndWait(() => {})

    val bi = new BufferedImage(canvas.getWidth, canvas.getHeight, BufferedImage.TYPE_INT_ARGB)
    val g2d = bi.createGraphics
    canvas.print(g2d)

    val os = new ByteArrayOutputStream
    ImageIO.write(bi, "png", os)
    val base64 = Base64.getEncoder.encodeToString(os.toByteArray)

    s"""<img src="data:image/png;base64,${base64}">"""
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
    * @return the plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], legend: Char = '*', color: Color = Color.BLACK): PlotCanvas = {
    ScatterPlot.plot(data, legend, color)
  }

  /** Scatter plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param labels labels of points.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], labels: Array[String]): PlotCanvas = {
    ScatterPlot.plot(data, labels)
  }

  /** Scatter plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param label the class labels of data.
    * @param legend the legend for all classes.
    * @param palette the color for each class.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], label: Array[Int], legend: Char, palette: Array[Color]): PlotCanvas = {
    ScatterPlot.plot(data, label, legend, palette)
  }

  /** Scatter plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param label the class labels of data.
    * @param legend the legend for each class.
    * @param palette the color for each class.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def plot(data: Array[Array[Double]], label: Array[Int], legend: Array[Char], palette: Array[Color]): PlotCanvas = {
    ScatterPlot.plot(data, label, legend, palette)
  }

  /** Plot a grid of scatter plots of for all attribute pairs in the data frame.
    *
    * @param data a data frame.
    * @param legend the legend for all classes.
    * @return the plot panel.
    */
  def plot(data: DataFrame, legend: Char): PlotGroup = {
    val x = data.toArray
    val p = data.ncols
    val names = data.names

    val plots = new PlotGroup()

    for (i <- 0 until p) {
      for (j <- 0 until p) {
        val x2 = x.map { row => Array(row(i), row(j)) }
        val canvas = ScatterPlot.plot(x2, legend)
        canvas.setAxisLabels(names(i), names(j))
        plots.add(canvas)
      }
    }

    plots
  }

  /** Plot a grid of scatter plots of for all attribute pairs in the data frame
    * of which the response variable is integer.
    *
    * @param data an attribute frame.
    * @param legend the legend for all classes.
    * @param palette the color for each class.
    * @return the plot panel.
    */
  def plot(data: DataFrame, category: String, legend: Char, palette: Array[Color]): PlotGroup = {
    val dat = data.drop(category)
    val x = dat.toArray
    val y = data.column(category).toIntArray
    val p = x(0).length
    val names = dat.names()

    val plots = new PlotGroup()

    for (i <- 0 until p) {
      for (j <- 0 until p) {
        val x2 = x.map { row => Array(row(i), row(j)) }
        val canvas = ScatterPlot.plot(x2, y, legend, palette)
        canvas.setAxisLabels(names(i), names(j))
        plots.add(canvas)
      }
    }

    plots
  }

  /** Plot a grid of scatter plots of for all attribute pairs in the data frame
    * of which the response variable is integer.
    *
    * @param data an attribute frame.
    * @param legend the legend for each class.
    * @param palette the color for each class.
    * @return the plot panel.
    */
  def plot(data: DataFrame, category: String, legend: Array[Char], palette: Array[Color]): JPanel = {
    val dat = data.drop(category)
    val x = dat.toArray
    val y = data.column(category).toIntArray
    val p = x(0).length
    val names = dat.names()

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

    panel
  }

  /** Line plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param style the stroke style of line.
    * @param legend the legend used to draw data points. The default value ' ' makes the point indistinguishable
    *               from the line on purpose.
    * @param color the color of line.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def line(data: Array[Array[Double]], style: Line.Style = Line.Style.SOLID, color: Color = Color.BLACK, legend: Char = ' '): PlotCanvas = {
    val canvas = LinePlot.plot(data, style, color)

    if (legend != ' ') {
      val scatter = new ScatterPlot(data, legend)
      scatter.setColor(color)
      canvas.add(scatter)
    }

    canvas
  }

  /** Create a plot canvas with the staircase line plot.
    * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
    */
  def staircase(data: Array[Double]*): PlotCanvas = {
    StaircasePlot.plot(data: _*)
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
    * @return the plot canvas which can be added other shapes.
    */
  def boxplot(data: Array[Double]*): PlotCanvas = {
    BoxPlot.plot(data: _*)
  }

  /** Box plot.
    *
    * @param data a data matrix of which each row will create a box plot.
    * @param labels the labels for each box plot.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def boxplot(data: Array[Array[Double]], labels: Array[String]): PlotCanvas = {
    BoxPlot.plot(data, labels)
  }

  /** Contour plot. A contour plot is a graphical technique for representing a 3-dimensional
    * surface by plotting constant z slices, called contours, on a 2-dimensional
    * format. That is, given a value for z, lines are drawn for connecting the
    * (x, y) coordinates where that z value occurs. The contour plot is an
    * alternative to a 3-D surface plot.
    *
    * @param z the data matrix to create contour plot.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def contour(z: Array[Array[Double]]): PlotCanvas = {
    Contour.plot(z)
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
    * @return the plot canvas which can be added other shapes.
    */
  def contour(z: Array[Array[Double]], levels: Array[Double], palette: Array[Color]): PlotCanvas = {
    Contour.plot(z, levels, palette)
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
    * @return the plot canvas which can be added other shapes.
    */
  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): PlotCanvas = {
    Contour.plot(x, y, z)
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
    * @return the plot canvas which can be added other shapes.
    */
  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]], levels: Array[Double], palette: Array[Color]): PlotCanvas = {
    Contour.plot(x, y, z, levels, palette)
  }

  /** 3D surface plot.
    *
    * @param z the z-axis values of surface.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def surface(z: Array[Array[Double]]): PlotCanvas = {
    Surface.plot(z)
  }

  /** 3D surface plot.
    *
    * @param z the z-axis values of surface.
    * @param palette the color palette.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def surface(z: Array[Array[Double]], palette: Array[Color]): PlotCanvas = {
    Surface.plot(z, palette)
  }

  /** 3D surface plot.
    *
    * @param x the x-axis values of surface.
    * @param y the y-axis values of surface.
    * @param z the z-axis values of surface.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def surface(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): PlotCanvas = {
    Surface.plot(x, y, z)
  }

  /** 3D surface plot.
    *
    * @param x the x-axis values of surface.
    * @param y the y-axis values of surface.
    * @param z the z-axis values of surface.
    * @param palette the color palette.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def surface(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): PlotCanvas = {
    Surface.plot(x, y, z, palette)
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
  def wireframe(vertices: Array[Array[Double]], edges: Array[Array[Int]]): PlotCanvas = {
    Wireframe.plot(vertices, edges)
  }

  /** 2D grid plot.
    *
    * @param data an m x n x 2 array which are coordinates of m x n grid.
    */
  def grid(data: Array[Array[Array[Double]]]): PlotCanvas = {
    Grid.plot(data)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def heatmap(z: Array[Array[Double]]): PlotCanvas = {
    Heatmap.plot(z)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def heatmap(z: Array[Array[Double]], palette: Array[Color]): PlotCanvas = {
    Heatmap.plot(z, palette)
  }

  /** Pseudo heat map plot.
    * @param x x coordinate of data matrix cells. Must be in ascending order.
    * @param y y coordinate of data matrix cells. Must be in ascending order.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): PlotCanvas = {
    Heatmap.plot(x, y, z)
  }

  /** Pseudo heat map plot.
    * @param x x coordinate of data matrix cells. Must be in ascending order.
    * @param y y coordinate of data matrix cells. Must be in ascending order.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): PlotCanvas = {
    Heatmap.plot(x, y, z, palette)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param rowLabels the labels for rows of data matrix.
    * @param columnLabels the labels for columns of data matrix.
    */
  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]]): PlotCanvas = {
    Heatmap.plot(rowLabels, columnLabels, z)
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param rowLabels the labels for rows of data matrix.
    * @param columnLabels the labels for columns of data matrix.
    * @param palette the color palette.
    */
  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]], palette: Array[Color]): PlotCanvas = {
    Heatmap.plot(rowLabels, columnLabels, z, palette)
  }

  /** Visualize sparsity pattern.
    * @param matrix a sparse matrix.
    */
  def spy(matrix: SparseMatrix): PlotCanvas = {
    SparseMatrixPlot.plot(matrix)
  }

  /** Heat map with hex shape.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def hexmap(z: Array[Array[Double]]): PlotCanvas = {
    Heatmap.plot(z)
  }

  /** Heat map with hex shape.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def hexmap(z: Array[Array[Double]], palette: Array[Color]): PlotCanvas = {
    Hexmap.plot(z, palette)
  }

  /** Heat map with hex shape.
    * @param labels the descriptions of each cell in the data matrix.
    * @param z a data matrix to be shown in pseudo heat map.
    */
  def hexmap(labels: Array[Array[String]], z: Array[Array[Double]]): PlotCanvas = {
    Hexmap.plot(labels, z)
  }

  /** Heat map with hex shape.
    * @param labels the descriptions of each cell in the data matrix.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def hexmap(labels: Array[Array[String]], z: Array[Array[Double]], palette: Array[Color]): PlotCanvas = {
    Hexmap.plot(labels, z, palette)
  }

  /** Histogram plot.
    * @param data a sample set.
    */
  def hist(data: Array[Double]): PlotCanvas = {
    Histogram.plot(data)
  }

  /** Histogram plot.
    * @param data a sample set.
    * @param k the number of bins.
    */
  def hist(data: Array[Double], k: Int): PlotCanvas = {
    Histogram.plot(data, k)
  }

  /** Histogram plot.
    * @param data a sample set.
    * @param breaks an array of size k+1 giving the breakpoints between
    *               histogram cells. Must be in ascending order.
    */
  def hist(data: Array[Double], breaks: Array[Double]): PlotCanvas = {
    Histogram.plot(data, breaks)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    */
  def hist(data: Array[Array[Double]]): PlotCanvas = {
    Histogram3D.plot(data)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    * @param k the number of bins.
    */
  def hist(data: Array[Array[Double]], k: Int): PlotCanvas = {
    Histogram3D.plot(data, k)
  }

  /** 3D histogram plot.
    * @param data a sample set.
    * @param xbins the number of bins on x-axis.
    * @param ybins the number of bins on y-axis.
    */
  def hist(data: Array[Array[Double]], xbins: Int, ybins: Int): PlotCanvas = {
    Histogram3D.plot(data, xbins, ybins)
  }

  /** QQ plot of samples to standard normal distribution.
    * The x-axis is the quantiles of x and the y-axis is the
    * quantiles of normal distribution.
    * @param x a sample set.
    */
  def qqplot(x: Array[Double]): PlotCanvas = {
    QQPlot.plot(x)
  }

  /** QQ plot of samples to given distribution.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of
    * given distribution.
    * @param x a sample set.
    * @param d a distribution.
    */
  def qqplot(x: Array[Double], d: Distribution): PlotCanvas = {
    QQPlot.plot(x, d)
  }

  /** QQ plot of two sample sets.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
    * @param x a sample set.
    * @param y a sample set.
    */
  def qqplot(x: Array[Double], y: Array[Double]): PlotCanvas = {
    QQPlot.plot(x, y)
  }

  /** QQ plot of samples to given distribution.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of
    * given distribution.
    * @param x a sample set.
    * @param d a distribution.
    */
  def qqplot(x: Array[Int], d: DiscreteDistribution): PlotCanvas = {
    QQPlot.plot(x, d)
  }

  /** QQ plot of two sample sets.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
    * @param x a sample set.
    * @param y a sample set.
    */
  def qqplot(x: Array[Int], y: Array[Int]): PlotCanvas = {
    QQPlot.plot(x, y)
  }

  /** Plots the classification boundary.
   *
   * @param x training data.
   * @param y training label.
   * @param model classification model.
   */
  def plot(x: Array[Array[Double]], y: Array[Int], model: Classifier[Array[Double]]): PlotCanvas = {
    require(x(0).size == 2, "plot of classification model supports only 2-dimensional data")

    val canvas = plot(x, y, 'o', Palette.COLORS)

    val lower = canvas.getLowerBounds
    val upper = canvas.getUpperBounds

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
        canvas.point('.', Palette.COLORS(c), p: _*)
      }
    }

    val levels = (0 until MathEx.max(y)).map(_ + 0.5).toArray
    val contour = new Contour(v1, v2, z, levels)
    contour.showLevelValue(false)
    canvas.add(contour)

    canvas
  }

  /** Plots the regression surface.
    *
    * @param x training data.
    * @param y response variable.
    * @param model regression model.
    */
  def plot(x: Array[Array[Double]], y: Array[Double], model: Regression[Array[Double]]): PlotCanvas = {
    require(x(0).size == 2, "plot of regression model supports only 2-dimensional data")

    val points = x.zip(y).map { case (x, y) => Array(x(0), x(1), y) }.toArray
    val canvas = plot(points, 'o')

    val lower = canvas.getLowerBounds
    val upper = canvas.getUpperBounds

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
    canvas.add(surface)

    canvas
  }

  /** The scree plot is a useful visual aid for determining an appropriate number of principal components.
    * The scree plot graphs the eigenvalue against the component number. To determine the appropriate
    * number of components, we look for an "elbow" in the scree plot. The component number is taken to
    * be the point at which the remaining eigenvalues are relatively small and all about the same size.
    *
    * @param pca principal component analysis object.
    */
  def screeplot(pca: PCA): PlotCanvas = {
    PlotCanvas.screeplot(pca)
  }

  /** A dendrogram is a tree diagram to illustrate the arrangement
    * of the clusters produced by hierarchical clustering.
    *
    * @param hc hierarchical clustering object.
    */
  def dendrogram(hc: HierarchicalClustering): PlotCanvas = {
    Dendrogram.plot("Dendrogram", hc.getTree, hc.getHeight)
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
  def dendrogram(merge: Array[Array[Int]], height: Array[Double]): PlotCanvas = {
    Dendrogram.plot(merge, height)
  }
}
